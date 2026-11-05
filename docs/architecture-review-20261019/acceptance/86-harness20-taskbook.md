# Hermes Harness Stage 20 Tool Action DFA Guard 与统一工具调用状态机任务书

- 状态：`FROZEN CONTRACT`；仅授权离线、确定性、静态批准 spec 的实现与测试；默认 OFF。
- 基线：Stage 19 合入后的 `main`，`1a70d81202c3ada0ccd601457d8227a510a7428f`；分支 `feat/hermes-harness-stage20`。
- 审计：`85-harness20-audit.md`。
- 正文 SHA-256：`7bea2b054787513656b69fa3642883c5d1ffade67642ab1acd328c2f7166767f`；全文件 SHA-256 以伴生 `.sha256` 为准。

## 一、唯一假设与范围

唯一假设：对 request-local、静态批准的确定性 DFA，Alibaba `ToolInterceptor` dispatch 边界能够在 ReAct、Plan-and-Solve 与 Kernel ENFORCE 路径取得稳定 toolCallId；非法转移可在执行前拒绝；只有 framework 成功且未被既有明确 guard 拒绝的工具调用才推进状态。OFF 不改变现有行为；SHADOW 只观测、不阻止。该假设不代表工具业务语义安全、模型输出真实性、外部副作用 exactly-once 或生产可用性。

只修改 `qknow-hermes-core` 的 DFA、dispatcher、request lifecycle 接线、必要的 `AgentOrchestrator` / `WorkerAgent` 接线、契约测试与 `85/86/87` 文档。Stage 16–19 的既有 profile、pipeline、router、Cycle、Hoare 行为不重写；不加外部依赖、不启用生产默认、不调用真实 DeepSeek/API、不从模型输出生成 spec。

## 二、核心契约

### 2.1 Spec 与身份

新增包：`tech.qiantong.qknow.hermes.agent.harness.neurosymbolic.toolaction`。

- `ToolActionDfaState`：不可变、非空状态标识。
- `ToolActionDfaTransition`：不可变 `(fromState, toolName, toState)`；工具名非空，不支持 epsilon。
- `ToolActionDfaSpec`：不可变 `specId/version/initialState/acceptingStates/states/transitions/approved/fingerprint`。
- 校验：initial/accepting/transition endpoints 必须属于 states；同一 `(state, toolName)` 至多一个目标；状态数 `1..64`，转移数 `0..256`；未经批准 spec 不得被 registry 选中。
- fingerprint 使用 UTF-8、长度前缀或等价无歧义编码；states、accepting states、transitions 稳定排序后 SHA-256。相同规范指纹相同，语义变化指纹变化；不依赖 Set/Map 迭代顺序。
- `ToolActionDfaRegistry` 只读；只接受构造期静态 approved spec；无运行时任意注册/LLM 创建接口。测试 spec 不作为生产默认启用项。
- `ToolCallIdentity` 优先使用 `ToolCallRequest.getToolCallId()`；缺失时由 request session 递增生成 ordinal。参数摘要仅用于检测同 ID payload 冲突，不作为身份。

### 2.2 Request session、状态与幂等

- 每次 `executeAgent` 拥有独立 `ToolActionDfaSession`，保存 currentState、单调 ordinal、身份 reservation/结果与 typed receipts。不得跨 request 共享，不得使用静态全局 session，不得复用 `HarnessSessionBinder`。
- `ToolActionDfaSessionRegistry` 有界、线程安全；request execution 打开时注册，所有完成/异常/取消/早退分支清理。相同 requestId 的并发 execution 不得复用 session；容量满时 ENFORCE fail-close，SHADOW 记录不可观测原因但保持工具行为。
- `check(toolName)` 纯读并返回 `DFA_ALLOWED`、`DFA_UNKNOWN_TOOL`、`DFA_TRANSITION_NOT_FOUND`、`DFA_TERMINAL_STATE` 或 `DFA_SPEC_INVALID`；不修改 currentState。
- `commitSuccess(identity, expectedFrom, toolName)` 仅当 identity 未提交、expectedFrom 仍等于 currentState 且存在合法边时推进一次。重复提交、陈旧状态、身份 payload 冲突均显式不推进。
- 调用前 reserve identity；同 ID、同工具、同参数摘要已完成时复用缓存 response，不再次调用 handler；同 ID 不同工具/参数拒绝；IN_PROGRESS 重入返回确定性 duplicate 状态，不再次执行。失败 identity 也保留 response，防止不确定副作用后的重放。
- 幂等保证仅是进程内 at-most-once dispatch，不是跨进程 exactly-once。若外部副作用完成后进程在保存 response 前崩溃，本阶段无法恢复或去重。

### 2.3 Hoare / Cycle / DFA 时序

```text
提取 ToolCallRequest / identity
→ DFA check（只读）
→ ENFORCE 非法边：不调用 handler，返回 typed error
→ 其他情况调用现有 handler / callback stack
→ 观察 ToolCallResponse 与既有明确 guard rejection
→ 仅成功调用 commitSuccess
→ typed receipt
```

- `ToolActionDfaGuard.check` 与 `commitSuccess` 分离。异常、`ToolCallResponse.isError()`、既有 Cycle/Hoare/permission 明确拒绝 marker、Kernel Hoare postcondition reject 均不推进 DFA。
- Hoare pre/post 和 Cycle Guard 仍由当前 wrapper/Kernel 执行。DFA 不替代权限，不验证业务语义，不改变旧 Cycle 在失败前已 inspect 的状态语义。
- Kernel ENFORCE 选 C1：共同 interceptor 位于 Kernel guarded callback 外侧；现有拒绝 marker 使 postcondition reject 不推进。DFA 独立维护状态，不二次执行 Kernel/工具，不修改 `HermesHarnessKernel.ExecutionSession.invokeTool` 顺序。
- “成功”限 framework response 非 error 且不包含明确既有 guard rejection。工具以普通字符串/JSON 表达的业务失败不可通用识别，不能提升为业务操作成功或语义安全。

### 2.4 OFF / SHADOW / ENFORCE

- **OFF**：默认；不创建/注册 session，不查 spec，不检查、不推进、不生成 DFA receipt；handler/tool 顺序与结果保持现状。
- **SHADOW**：request session 检查并记录“ENFORCE 会否拒绝”；所有调用仍执行。成功 response 尝试推进；异常/明确既有拒绝不推进。并发完成导致 expectedFrom 陈旧时记录 `SHADOW_STALE_COMPLETION` 并不推进，不能改变工具结果。
- **ENFORCE**：须显式选择 approved spec；非法转移在 handler 前返回 `ToolCallResponse.error`，工具不执行、不推进。合法调用执行后，只有成功 response 才推进；失败/拒绝/postcondition reject 不推进。
- mode 默认 OFF；不新增生产默认开关，不改变 Stage 17–19 默认行为。

### 2.5 Receipt

使用独立不可变 `ToolActionDfaReceipt`，至少含 `requestId/sessionId/toolCallId-or-ordinal/specId/specVersion/specFingerprint/fromState/toState/toolName/mode/allowed/blocked/advanced/reason/failureCode`。不记录参数正文、密钥、完整工具结果。Receipt 不从 `HarnessEvidenceRecord` 的通用 blocked 前缀推断，不修改既有 evidence digest schema；request 执行完成前可从 session snapshot 读取，registry 管理有界生命周期。

## 三、覆盖矩阵与 Checkpoint

| 路径 | Checkpoint | DFA 检查点 | 成功推进 | 失败不推进 | 状态/约束 |
|---|---|---|---|---|---|
| ReAct SINGLE / resolver | 20A | `ToolInterceptor` | 是 | 是 | `COVERED` |
| Plan-and-Solve initial/recovery workers | 20B | 同 request interceptor/session；使用 effective bridge/guarded resolver | 是 | 是 | `COVERED`；ENFORCE ready tasks 按计划顺序串行，ReAct error recovery 不新开 session |
| Kernel ENFORCE | 20C/C1 | Kernel guarded callback 外同一 interceptor | Hoare post 通过后 | marker、异常、post reject 不推进 | `COVERED`；不改 Kernel 顺序 |
| MultiAgent SWARM workers | 20B 边界 | 不接入并行 workers | N/A | N/A | `UNCOVERED`；ENFORCE 进入前 fail-close；SHADOW 不覆盖 |
| MultiAgent DEBATE | 20B 边界 | 当前无工具 dispatch | N/A | N/A | `NO_TOOL_PATH`；未来新增工具须重新验收 |
| MCP direct helper / resolver 外直接执行 | 20B 边界 | 不经过 Agent ToolInterceptor 的路径 | N/A | N/A | `UNCOVERED`；不得声称全局物理工具覆盖 |

- **20A**：spec/registry/session/guard/receipt/ToolInterceptor + ReAct；Plan、MultiAgent、Kernel 明确未覆盖。
- **20B**：统一 dispatcher 进入 Plan workers，使用 effective resolver，同 request 共用 session；ENFORCE ready tasks 串行。MultiAgent ENFORCE 执行前拒绝，避免绕过。
- **20C**：接入 Kernel ENFORCE 或诚实标 UNCOVERED。本契约选择 C1，验证 Kernel pre/post/Cycle marker 与异常不推进。

## 四、并发、失败与生命周期

- ReAct ENFORCE 禁用同一 assistant response 内并行 tool dispatch，确保顺序 DFA 有确定执行顺序；只影响显式启用 DFA 的请求。SHADOW 不串行化实际 handler；receipt 必须表达 stale/indeterminate，不伪造 transition 成功。
- Plan ENFORCE 的 ready tasks 按原 task 列表顺序串行；OFF/SHADOW 保留现有并发语义。
- MultiAgent 并行 worker 不共享 DFA transition state；ENFORCE 在 orchestration resolution 后、worker/tool 启动前 fail-close。
- 工具失败以 framework response error / 异常表示；既有显式 guard marker 为拒绝。普通业务文本/JSON 不是可靠失败 oracle。
- ToolInterceptor 与 callback wrapper 不重复执行 handler。相同 identity 重放在 handler 前处理。冲突摘要只记录指纹，不打印参数正文。
- 清理覆盖正常 done、错误、取消、Plan answer 早退、Kernel fail-close、容量拒绝等 lifecycle；任一异步终止路径无法清理则 checkpoint 不通过。
- Stage 19 `PipelineRuntimeBridge` / `HarnessSessionBinder` 语义保持不变；与现有 Kernel/Pipeline 冲突行为保持 fail-close。

## 五、测试矩阵

1. Spec 合法性与 invalid initial/accepting/endpoints、非确定性转移、epsilon/空名、64/256 上限；fingerprint 对输入顺序稳定且对语义变化敏感；未批准 spec 不可选。
2. `check` 不改 currentState；合法/非法边、terminal state；成功单次推进；失败不推进；重复 commit/stale expectedFrom 显式拒绝。
3. request 隔离、并发 request 隔离、同 requestId 活跃重复注册拒绝、capacity fail-close、close 清理。
4. 相同 toolCallId 重放只执行 handler 一次并复用 response；不同 payload/tool 同 ID 拒绝；IN_PROGRESS 不重复执行；缺失 ID 生成 ordinal。
5. OFF 无 session/receipt 且 handler 原样执行一次；SHADOW 非法转移仍执行、有 would-block receipt；ENFORCE 非法边在 handler 前拒绝。
6. 异常、ToolCallResponse error、已知 Cycle/Hoare/permission marker、Kernel postcondition reject 均不推进；成功才推进。
7. ReAct interceptor 读取真实 SDK toolCallId；callback list/resolver 没有双重 dispatch。
8. Plan initial/recovery worker 使用同一 request session/interceptor 与 effective resolver；ENFORCE ready tasks 顺序稳定；ReAct error recovery 不新开状态。
9. Kernel C1 保持既有 Cycle/Hoare 语义；拒绝不推进 DFA、工具不重复执行。
10. MultiAgent ENFORCE 在任何 worker/tool 执行前 fail-close；OFF/SHADOW 不回归且 coverage 明确 UNCOVERED。
11. Stage 16–19 pipeline/router/Kernel/Cycle/Hoare 定向测试不回归；测试默认无外网/真实 API。

## 六、交付与验证

定向命令：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml \
  -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest='ToolActionDfaSpecTest,ToolActionDfaSessionTest,ToolActionDfaGuardTest,ToolCallDispatchTest,ToolActionDfaIntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

全量命令：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

要求 `BUILD SUCCESS`、0 fail、0 error，精确记录 tests/skipped；不得重试掩盖失败。提交后在同 HEAD 全新本地 APFS 检出串行执行单步 clean test，工作区 clean。若 proto 间歇失败重现，保全现场并保持根因 `UNPROVEN`，不得归因给 DFA 或用预构建替代单步结论。

## 七、禁止事项与结论边界

禁止真实 API/付费调用、生产默认启用、模型生成 spec、运行时任意注册、静态全局 session、参数 hash 作为调用身份、失败/拒绝/postcondition 失败推进、ENFORCE 拒绝后继续 handler、SHADOW 阻止工具或改变工具结果、外层包装冒称覆盖 Kernel/MultiAgent、记录工具参数/结果正文、新增 XGrammar/PyTorch/CUDA/C++ 或其他外部依赖、修改 Stage 16–19 既有语义。

Stage 20 通过后只能表述为：静态批准、request-local 工具转移 guard 在 ReAct、Plan-and-Solve、Kernel ENFORCE 覆盖矩阵范围具有确定性契约；MultiAgent 和 direct helper 明确未覆盖。DFA 不等于权限系统，不保证业务语义安全、外部 exactly-once、真实模型效果或生产可用性。
