# Hermes Harness Stage 20 工具调用路径审计

- 审计基线：`feat/hermes-harness-stage20` / `1a70d81202c3ada0ccd601457d8227a510a7428f`，由 Stage 19 合入后的 `main` 切出。
- 范围：只读核对工具执行入口、DFA / Hoare / Cycle 边界、调用身份、失败表示、并发和 Stage 19 session 组件。
- 结论：可在 Alibaba `ToolInterceptor` dispatch 边界建立请求级 DFA。ReAct、Plan-and-Solve 与 Kernel ENFORCE 可分 checkpoint 接入；MultiAgent 存在并行 worker，Stage 20 不共享其单一顺序状态，ENFORCE 必须在工具执行前 fail-close。
- 本审计未修改源码、未运行测试、未调用真实模型或 API。

## 一、真实调用路径

### ReAct 主路径

`HermesGrpcService.chat` → `AgentOrchestrator.chat` → 异步 `executeAgent`。`executeAgent` 准备工具和 Kernel bridge，在第 743 行先尝试 `planAndSolve`；仅 Plan 未返回答案时才建立 ReAct Cycle Guard。ReAct 同时配置 `.tools(guardedTools)` 与 `.resolver(guardedResolver)`。非 Kernel ENFORCE 路径通过 `wrapWithCycleGuard`；Kernel ENFORCE 直接使用 Kernel guard callback/resolver，跳过外层 Cycle wrapper。

- 位置：`AgentOrchestrator.java:477-493, 665-695, 743-835, 1708-1761`。
- 现有 Hoare：仅 Kernel ENFORCE 的 `HarnessKernelBridge.guard` 内执行。
- 现有 Cycle：非 Kernel 由 `wrapWithCycleGuard` 检查；Kernel 内由 `sessionCycles.inspect` 检查。
- 两个 callback 入口不等于一次调用经过两次 wrapper：模型 tool call 由框架按 callback/list 或 resolver 入口选取。Stage 20 仅在 `ToolInterceptor` 安装一个 DFA dispatcher，不再包一层 callback，避免双重推进。

### Plan-and-Solve

Plan 在 ReAct 包装前运行。`executePlanTasks` 并行处理同一依赖层的 ready tasks，每个任务新建 `WorkerAgent`。当前 WorkerAgent 获得 `bridgeTools`，但收到原始 `resolver` 字段；其 ReactAgent 仅配置 `.tools(activeTools).resolver(resolver)`，没有 ToolInterceptor。resolver 工具可能绕过外层 Cycle/evidence；Kernel ENFORCE 还可能绕过 Kernel guarded resolver。

- 位置：`AgentOrchestrator.java:743, 1043-1072, 1135-1178`；`WorkerAgent.java:80-108, 177-200`。
- Stage 20 要求 Plan worker 使用当前请求的 effective resolver 和同一 DFA session/interceptor；ENFORCE 时 ready tasks 按计划顺序串行，保证共享状态机的可定义顺序。
- Plan worker 错误常被转换成 `执行失败:` 文本。DFA 仅依据框架 response error、异常和既有明确拒绝 marker 判定，不从自然语言推断业务成功。
- 另有 ReAct 流失败后的 `recoverReactFailure` → revised Plan → `executePlanTasks` 路径（`AgentOrchestrator.java:997, 1297-1310`）；它仍在原请求流生命周期内，必须复用原 request session/interceptor，不得新开 DFA 状态。

### Kernel ENFORCE

Kernel bridge 在 ENFORCE 下包装静态 callbacks 与 resolver，并替换到 AgentOrchestrator。此时跳过外层 Cycle wrapper。Kernel 内部顺序为：`stepIndex++` → Cycle `inspect` → Hoare precondition → 原工具执行 → Hoare postcondition。前置拒绝、工具异常与后置拒绝前 Cycle 状态均已被 inspect；这是既有语义，DFA 不复用或改变。

- 位置：`HarnessKernelBridge.java:136-183, 272-354`；`HermesHarnessKernel.java:141-166`；`AgentOrchestrator.java:677-695, 783-795`。
- `HarnessKernelBridge.guard` 将后置拒绝转换成 `[HARNESS_GUARD_REJECTED:<violation>]`，外层 ToolInterceptor 可将其判为拒绝且不推进 DFA。
- Checkpoint C 选择 C1：在 Kernel guard 外侧、共同 ToolInterceptor 内接入 DFA；保持 Kernel 的现有内部顺序。

### MultiAgent 与其他入口

SWARM 复用 guarded callbacks/resolver，再套 cancellation/budget wrapper；WorkerAgent ReactAgent 当前没有 ToolInterceptor。Swarm worker 与 DAG phase 可并行，多个 worker 共享 request 工具集时调用顺序不确定。DEBATE 当前不执行工具。MCP adapter 有直接执行辅助方法，审计范围内未发现 AgentOrchestrator 的直接调用方；Spring resolver 仍可解析普通 bean callback。

- 位置：`AgentOrchestrator.java:798-806`；`MultiAgentOrchestrationService.java:302-360, 478-517, 763-840, 862-906`；`TopologicalPhasedDispatcher.java:19-66`。
- Stage 20 将 MultiAgent 标为 `UNCOVERED`；DFA ENFORCE 下须在 `executeIfEnabled` 前 fail-close，防止静默绕过。OFF 保持现状，SHADOW 不声称覆盖。

## 二、守卫职责与时序

| 机制 | 负责内容 | 状态推进点 | Stage 20 约束 |
|---|---|---|---|
| HoareTripleToolGuard | 已声明 contract 的参数前置/结果后置条件 | Kernel session 调用前后检查 | 不替代 DFA；仅 Kernel ENFORCE 当前启用 |
| ReActCycleGuard / LocallyDenseCycleAutomaton | 步数与局部重复调用熔断 | ReAct wrapper 执行前检查；Kernel `inspect` 先更新状态 | 不复用为 DFA session，不改既有失败推进语义 |
| Tool Action DFA | 已批准有限状态中的工具顺序 | 工具成功且无已知 guard rejection 后提交 | check 只读；拒绝、异常、postcondition reject 不推进 |

dispatch：提取 ToolCallRequest/identity → DFA check → ENFORCE 非法边时不调用 handler，SHADOW 记录后继续 → 执行现有 handler/wrapper → 观察 framework response 和已有拒绝标记 → 成功才 commit DFA → 保存脱敏 receipt。非 Kernel 路径没有 Hoare postcondition，不得宣称覆盖。

成功仅指 `ToolCallResponse.isError()==false` 且未匹配显式既有 guard marker；普通字符串/JSON 中的业务失败不可通用识别。DFA 不解释业务 payload，不保证业务操作达成或语义安全。

## 三、调用身份、幂等与并发

- Spring `ToolCallback.call(String)` / `call(String, ToolContext)` 不暴露专用调用 ID；`AssistantMessage.ToolCall.id()` 存在。安装的 Alibaba Agent Framework `1.1.2.2` 提供 `ToolInterceptor`、`ToolCallRequest.getToolCallId()`、`ToolCallResponse.isError()`，ReactAgent Builder 提供 `.interceptors(...)`。仓库当前没有接入 ToolInterceptor。
- dispatcher 优先用非空 `toolCallId`；缺失时用 request session 单调 ordinal。参数摘要只用于同 ID payload 冲突检测，不作为调用身份。
- 同 ID 先 reserve；已完成重放复用缓存 response、不重放工具；同 ID 不同 tool/payload 拒绝；IN_PROGRESS 重入不再次执行。该保证限当前进程，不是外部副作用 exactly-once。
- 每次 request execution 独立创建和清理 DFA session。`HarnessSessionBinder` 是 `(sessionId, capabilityTag, domain)` Profile 绑定表，有 TTL/容量淘汰，不可复用作 DFA request 状态；`PipelineRuntimeBridge` 只保存最后一次 profile route receipt，不是逐调用证据。
- ReAct ENFORCE 下禁用同一 assistant response 的并行 tool dispatch；Plan ENFORCE 的 ready tasks 串行。SHADOW 不串行化 handler；若并发完成使 expected-from 变陈旧，则 receipt 标为 stale/indeterminate，不覆盖状态。MultiAgent 并发保持 UNCOVERED。

## 四、证据与错误表示

`HarnessEvidenceCollector` 保存 requestId 和工具输入/输出摘要；`HarnessEvidenceRecord.ToolExecutionEntry` 不含 toolCallId、spec fingerprint、DFA from/to state。不得由通用 blocked 字符串推断完整 DFA 事实，也不把工具正文添加到证据字段。

- 位置：`HarnessEvidenceCollector.java:29-56, 71-95, 138-169`；`HarnessEvidenceRecord.java:53-80, 161-205`。
- DFA 使用独立 typed receipt/snapshot，记录 request/session/call identity、spec identity/fingerprint、from/to、mode、decision、advanced 与 failure code；不记录参数正文、密钥或完整工具结果。
- 仅识别明确既有 marker：`[HARNESS_GUARD_REJECTED:...]`、`CIRCUIT_BREAKER_*`、`【系统熔断提示】`、`【系统纠偏提示】`；不作宽泛文本推断。
- `MultiAgentOrchestrationService.wrapCancellableTool` 当前将完整 tool input 写入 INFO 日志（862-906）。本阶段不扩展该既有日志，亦不把它当作 DFA 证据；这是独立隐私治理边界。

## 五、DeepInfer 借鉴记录

| 字段 | 记录 |
|---|---|
| id | `S20-DEEPINFER-01` |
| sourceType | `production-implementation` |
| titleOrRepository | `DeepInfer` |
| authorsOrMaintainer | `DeepSeek-AI` |
| venueAndYear | `N/A` |
| doiOrArxiv | `N/A` |
| url | `https://github.com/deepseek-ai/DeepInfer` |
| commitOrTag | `a679bb86ce36e883a98a97733492b686e5b9a08e` |
| license | `Apache-2.0`（`pyproject.toml` 与 `LICENSE`） |
| filesOrSectionsRead | `deepinfer/structure_output/matcher.py`（状态/接受/rollback）；`deepinfer/structure_output/compiler.py`（compile/runtime 分离）；`doc/inference_deployment_qa.md` Structured outputs 与 DeepSeek Recipe streaming parser 段落 |
| verificationStatus | `VERIFIED`，仅限上述实际读取范围 |
| relevantFinding | grammar compilation 与请求运行状态分离；增量解析状态跨 chunk 保留，并有显式 rollback。 |
| projectApplicability | 仅借鉴静态 spec 与 request-local mutable session 分离、显式转移状态的原则；工具级状态与成功后提交必须由 Hermes 自行定义。 |
| limitations | DeepInfer 代码是 token grammar/structured output，不是工具调用顺序 DFA，也没有工具副作用幂等证据。XGrammar/PyTorch/CUDA/NPU/rollback 不移植；本阶段不新增依赖、不复制代码。 |

## 六、唯一待验证假设

对 request-local、静态批准的确定性 DFA，ToolInterceptor 可在 ReAct、Plan-and-Solve、Kernel ENFORCE 共同 dispatch 边界取得调用身份，非法转移在执行前拒绝，且只有 framework 成功和无明确既有 guard rejection 的调用才推进；OFF 零行为变化、SHADOW 不阻止执行。假设不包括 MultiAgent 并行、业务语义成功、外部 exactly-once 或全局语义安全。

实施门禁：86 任务书 body/full 双 hash 必须一致且 `shasum -c` 为 OK。审计本身不代表实现验收。
