# P0-15 最终 Research Gate 报告：多智能体生产接线

> 状态：`RESEARCH_GATE_PASS_WITH_CORRECTIONS`（研究证据已起，A/B/C 报告存在需修正的事实与代码片段；修正后的本文件为唯一实施契约）
> 基线：`b6516ee3`　|　日期：2026-10-23　|　范围：SWARM / DEBATE / Consensus 的生产接线
> 输入：`docs/architecture-review-20261019/p015/research/` 下 A/B/C 报告与 ledger
> 纪律：本文件只做只读研究与契约；未修改任何业务代码。

## 0. 对 A/B/C 报告的交叉核验与修正

| 报告 | 结论 | 必须修正的内容 |
|---|---|---|
| A-theory | 理论与成本分析可用 | ① AutoGen/MetaGPT/Multiagent-Debate/MoA 的会议 venue 未由 arXiv 元数据（无 journal_ref）确认，不能写成已确认的 ICLR/ICML/NeurIPS；② DeepSeek 定价数字有误：官方当前 `deepseek-flash` 为缓存命中输入 0.02/0.04 元/百万 tokens、未命中 1/2 元、输出 4/8 元（空闲/高峰），不是 0.15/0.30 与 1/2；③ “缓存命中是未命中的 1/7”应为 1/50（0.02 vs 1）；④ “LangGraph 官方弃用全量广播”在已抓取页面中没有直接原文，只保留“将 sub-agent 包装为 tools”的可验证结论 |
| B-code-audit | 调用图、零生产调用、硬编码 1536、仿真组件审计可用 | “16 条证据全部 VERIFIED”可作为代码证据；但 `SupervisorAgent` 的黑板污染是“类实例成员”事实，**最小接线可通过每次请求新建实例规避**，不必把它列为必须修改的 4 个生产文件之一 |
| C-architecture | 方向与 A–G 结构正确 | ① F.1 的 proto 片段是**虚构字段**：实际 `ChatRequest` 字段为 `request_id/bot_id/workspace_id/question/input_params/model_config/system_prompt/knowledge_ids/tool_method_ids/history/model_credentials/rag_contexts/user_id/conversation_id`，没有 `session_id/model/api_key/context`；② 实际 `executeAgent(...)` 返回 `void`，不能 `return executeAgent(request)`；③ 不存在 `emitStreamingChunk/emitModelFinished` 这些方法，应复用现有 `emitSingleAnswer`；④ proto 已有 `input_params`，不需要再新增 `parameters` 字段；⑤ “4 个生产文件”中的 `SupervisorAgent.java` 在本最小方案中可不改，改为在 Orchestrator 内请求级构造实例；⑥ Phase 2 DEBATE 还需要新增 `LlmDebateCoordinator.java`，不能只靠现成 4 文件 |
| 00-README/00-INDEX | 索引结构可用 | 其“22 项全 VERIFIED / RESEARCH_GATE_PASSED”结论过强；本文件改为 `PASS_WITH_CORRECTIONS`，对应修正后的 Ledger |

## A. 当前代码与失败机制

### A.1 真实路径

```
前端 POST /admin/kb/conversation/send
→ KbConversationController#sendMessage
→ KbAgentConfigServiceImpl#chatMessage
→ ChatRequest（gRPC）
→ HermesGrpcService#chat
→ AgentOrchestrator#chat
→ AgentOrchestrator#executeAgent
→ ReactAgent 单 Agent 流式
```

- `AgentOrchestrator#chat` 目前只调用 `executeAgent`；`SupervisorAgent`、`StructuredDebateCoordinator`、`MoAExpertConsensusEngine`、`DefaultConsensusArbiterEngine`、`TripartiteDebateArbiterEngine` 在 Controller→Service→gRPC→Orchestrator 主链中的生产调用点均为 **0**（B 报告已逐符号核验）。
- `KbAgentConfigDO.parameters` 已从 Controller 传到 `KbAgentConfigReqVO`，但在 `KbAgentConfigServiceImpl#chatMessage` 组装 `ChatRequest` 时未写入任何 proto 字段（`ChatRequest` 也没有编排字段），形成协议/控制面断流。
- `SupervisorAgent` 有实例成员 `SharedBlackboard`、`sessionQualityReceipts`；如果复用单例会造成跨请求状态污染。最小接线采用**每次请求 new 实例**，不修改该类。
- 现有 debate/MoA 类内部存在固定 1536 维、关键词假打分、`sha256_mock_qwen_1536_p` 等仿真逻辑；真实生产 embedding 是 `text-embedding-v4` 1024 维。禁止直接接线。
- 当前流式路径是 `executeAgent` 内直接向 `FluxSink<ChatEvent>` 发 `StreamingChunk/ModelFinished/Done`；`executeAgent` 返回 `void`，不是 `Flux`。

### A.2 唯一待验证假设

> 默认单 Agent 路径完全不变；把真实 LLM 驱动的 SupervisorAgent（SWARM）与最小三角色 LLM 辩论（DEBATE）作为按 Bot 配置、默认关闭的编排策略接入 `AgentOrchestrator`，能在固定多步问题集上获得可测量的答案质量收益，且 p95 延迟 ≤3×、token 成本 ≤3×、默认路径零回归。

失败机制：若控制面参数无法到达 Hermes、或复用单例 Supervisor/Worker 造成跨请求污染、或直接接仿真 debate 引擎，则多智能体路径不可用或产生伪共识；若串行执行、无超时/成本闸门，则延迟与成本必破 3×。

## B. Research Ledger（6 个来源，修正后）

| id | sourceType | titleOrRepository | authorsOrMaintainer | venueAndYear | doiOrArxiv | url | commitOrTag | license | filesOrSectionsRead | verificationStatus | relevantFinding | projectApplicability | limitations |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| AUTOGEN-2023 | paper | AutoGen: Enabling Next-Gen LLM Applications via Multi-Agent Conversation | Wu et al. | arXiv 2023；A 报告称 ICLR 2024，**arXiv 元数据未确认** | arXiv:2308.08155 | https://arxiv.org/abs/2308.08155 | N/A | N/A | Abstract、Introduction、System Design、Evaluation、Limitations（A 报告阅读范围） | PARTIALLY_VERIFIED | ConversableAgent + GroupChatManager；自由多轮对话需显式终止；广播会导致 token 超线性增长 | 支持 Supervisor/Worker 两级解耦；拒绝无界广播 | 会议 venue 未由元数据确认；未独立复核全部数字 |
| MULTIAGENT-DEBATE-2023 | paper | Improving Factuality and Reasoning in Language Models through Multiagent Debate | Du et al. | arXiv 2023；A 报告称 ICML 2024，**未确认** | arXiv:2305.14325 | https://arxiv.org/abs/2305.14325 | N/A | N/A | Abstract、Method、Experiments、Analysis（A 报告阅读范围） | PARTIALLY_VERIFIED | 多轮辩论可提升事实/推理，2–3 轮后收益递减；存在谄媚/假共识 | DEBATE 限 1–2 轮 + 独立 judge；必须做成本/质量消融 | 公开基准，不能直接外推到私有 RAG；venue 未确认 |
| METAGPT-2023 | paper | MetaGPT: Meta Programming for A Multi-Agent Collaborative Framework | Hong et al. | arXiv 2023；A 报告称 ICLR 2024，**未确认** | arXiv:2308.00352 | https://arxiv.org/abs/2308.00352 | N/A | N/A | Abstract、Method、Experiments（A 报告阅读范围） | PARTIALLY_VERIFIED | SOP/结构化产物可降低通信噪声，但流程刚性与错误级联明显 | 只借角色分工/结构化输出，不引入完整 SOP 框架 | venue 未确认；未独立复核实验数字 |
| MOA-2024 | paper | Mixture-of-Agents Enhances Large Language Model Capabilities | Wang et al. | arXiv 2024；A 报告称 NeurIPS 2024，**未确认** | arXiv:2406.04692 | https://arxiv.org/abs/2406.04692 | N/A | N/A | Abstract、Method、Experiments、Cost（A 报告阅读范围） | PARTIALLY_VERIFIED | 聚合器可从多样候选获益，但输入 token/延迟随层数与人数上升 | 只借“单层、≤3 候选、独立聚合”的思想；拒绝接现有仿真 MoA | venue 未确认；通用排行榜导向 |
| LANGGRAPH-SUPERVISOR | official-doc | LangChain / LangGraph Multi-Agent Supervisor Guide | LangChain | 2024–2026 | N/A | https://docs.langchain.com/oss/python/langchain/supervisor | N/A | 文档站点条款 | 实际页面抓取 200；确认 Supervisor + “Wrap sub-agents as tools”结构 | PARTIALLY_VERIFIED | 官方推荐 supervisor 将 sub-agent 包装为 tools；页面未找到“弃用全量广播”的直接原文 | 支持 Supervisor/Worker 与工具化 handoff；按 Bot 默认关闭 | 未读取页面全部高级章节；“弃用广播”不可引用 |
| DEEPSEEK-API-DOCS | official-doc | DeepSeek 官方文档：模型与价格 | DeepSeek | 2026 | N/A | https://api-docs.deepseek.com/zh-cn/quick_start/pricing | N/A | 官方文档条款 | 实际抓取 200；读取模型表、价格表、缓存/输出价格 | VERIFIED | `deepseek-flash` 对应 DeepSeek-V4.1-Flash-1017；1M 上下文/384K 输出；缓存命中输入 0.02/0.04、未命中 1/2、输出 4/8 元每百万 tokens（空闲/高峰） | 作为成本预算唯一依据；修正 A 的错误价格 | 价格/限速随时间变化，实施前需再次核对 |

> 说明：本地代码审计证据（B）不重复列入外部 Ledger；其 16 条 `file:line` 证据见 `B-code-audit.evidence.jsonl`。

## C. 可迁移与不可迁移结论

### 可直接采用

- Supervisor 负责分解/聚合，Worker 执行子任务；子 Agent 以工具/任务边界隔离，不做全量对话广播。
- 默认关闭、按 Bot 选择；未知/未配置模式一律 SINGLE。
- 每次请求创建独立 `SupervisorAgent`/`WorkerAgent`/`SharedBlackboard`，禁止复用 `AgentRegistry` 单例 Worker。
- 复用现有 `ChatModel`、`LLM_CALL_SEMAPHORE`、成本治理器、工具白名单；不新增外部框架。
- 成本预算以 DeepSeek 官方当前价格为准，不采用 A 报告的旧价格。

### 需要改造

- proto 增加强类型 `OrchestrationMode`（默认 SINGLE），控制面解析 `parameters.orchestrationMode`。
- `AgentOrchestrator` 在 `Flux.create` 内分流；SWARM 用请求级 Supervisor；DEBATE 用新 `LlmDebateCoordinator`；失败/超时回退 `executeAgent`。
- 多智能体最终答案通过现有 `emitSingleAnswer` 发出一个 `StreamingChunk` + `ModelFinished` + `Done`；不伪造逐 token 流。
- Phase 2 DEBATE 使用真实 LLM proposer/critic/judge；不得用现有仿真引擎。

### 必须拒绝

- C 报告 F.1 的虚构 proto 片段、`request.getSessionId()`、`return executeAgent(request)`、`emitStreamingChunk` 等直接照抄。
- `StructuredDebateCoordinator`、`MoAExpertConsensusEngine`、`DefaultConsensusArbiterEngine`、`TripartiteDebateArbiterEngine` 直接接生产。
- 固定 1536 维、全量广播、无界辩论轮次、复用单例 Worker/黑板。
- 以 A 报告的错误价格或未确认 venue 作为实施依据。

## D. 候选方案比较

| 维度 | Baseline SINGLE | 诊断：仅透传 parameters | 推荐 Phase 1 SWARM | Phase 2 SWARM+DEBATE | 拒绝：现有仿真引擎 |
|---|---|---|---|---|---|
| 正确性 | 现有生产 | 不变 | 真实 LLM 规划+请求级 Worker | 增加真实三角色辩论 | 伪共识/硬编码 1536 |
| 可证伪性 | 基准 | 无新变量 | 可测质量/延迟/成本 | 可测边际收益 | 不可证伪 |
| 数据需求 | 无 | 无 | 15–20 条多步问题 | 同左 + 对抗提示 | 无 |
| 延迟 | 1× | 1× | ≤3×（15s 硬超时） | 约 3–4× | 崩溃 |
| 成本 | 1× | 1× | ≤3×（单次 ≤0.05 元） | ≤6×（若保留） | 不适用 |
| 复杂度 | 低 | 极低 | 中（3 生产文件 + 1 测试） | 中高（+1 新类） | 高但无效 |
| 依赖变化 | 无 | 无 | 无 | 无 | 无 |
| 回滚风险 | 无 | 无 | 模式改回 SINGLE 即回滚 | 同左 | 高 |
| 生产影响 | 零 | 零 | 仅开启 Bot 生效 | 仅开启 Bot 生效 | 事故 |

**推荐**：先 Phase 1 SWARM；Phase 2 DEBATE 独立授权，仅在 SWARM 质量达标且成本 ≤6× 时保留。

## E. 推荐的最小机制

### Phase 1（SWARM）

生产文件 3 个 + 测试 1 个：

1. `backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto`
   - 在**现有** `ChatRequest` 末尾新增字段 15：`OrchestrationMode orchestration_mode = 15;`
   - 顶层新增 `enum OrchestrationMode { ORCHESTRATION_MODE_SINGLE=0; ORCHESTRATION_MODE_SWARM=1; ORCHESTRATION_MODE_DEBATE=2; }`
   - 不新增 `parameters` 字段（已有 `input_params`）。
2. `backend/qknow-module-kb/qknow-module-kb-biz/.../KbAgentConfigServiceImpl.java`
   - 用 Fastjson 解析 `kbAgentConfig.getParameters()` 的 `orchestrationMode`，映射为 proto enum；未知/缺省/解析失败 → SINGLE。
   - 在现有 `ChatRequest.newBuilder()` 链末尾 `.setOrchestrationMode(mode)`。
3. `backend/qknow-hermes/qknow-hermes-core/.../AgentOrchestrator.java`
   - 在 `chat` 的 `Flux.create(emitter -> ...)` 中先按 `request.getOrchestrationMode()` 分流；SINGLE 仍调用原 `executeAgent(...)`，零行为变化。
   - SWARM：构造请求级 `List<WorkerAgent>`（3 个不同 systemPrompt，使用本次 `tools/agentModel/resolver`），再 `new SupervisorAgent(..., workers)`；在 `CompletableFuture` + 15s `get(timeout)` 中调用；成功后 `fullAnswer.append(answer)` 并 `emitSingleAnswer(request, emitter, answer)`。
   - 超时/异常：不发射半成品，直接 `executeAgent(request, emitter, ...)` 回退；记录审计日志/失败码。
   - 若 `SupervisorAgent.chat` 返回以 `"执行失败:"` 开头，按异常处理，不把失败文本当成功。
   - 请求结束清理请求级对象；不复用 `AgentRegistry` 单例 Worker。
4. `backend/tests/src/test/java/tech/qiantong/qknow/acceptance/OrchestrationModeRoutingTest.java`
   - 默认 SINGLE 走原路径；未知枚举降级；SWARM mock 成功单 chunk/done；超时回退；worker 请求隔离；`parameters` 解析。

**不修改** `SupervisorAgent.java`：每次请求新建实例已隔离 `SharedBlackboard`/`sessionQualityReceipts`。若未来复用，则必须改为请求级状态。

### Phase 2（DEBATE）

新增 `backend/qknow-hermes/qknow-hermes-core/.../agent/debate/LlmDebateCoordinator.java`：

- `proposer(question, ragContext)` → 初稿；
- `critic(question, proposerDraft)` → 事实/逻辑漏洞；
- `judge(question, proposerDraft, critique)` → 最终答案；
- `R=1`（一次交锋即裁决），同一 `ChatModel`，token/时间上限；失败返回 null 并回退 SINGLE；
- 不接 1536/仿真 debate 类；Phase 2 文件与测试单独授权。

### 配置约定

```json
{"orchestrationMode":"SINGLE|SWARM|DEBATE","swarmMaxWorkers":3,"swarmTimeoutMs":15000}
```

默认 `SINGLE`；未知值降级；前端可后续做选择器，首期不改前端。

## F. 实验与实现契约

### F.1 固定问题集

- 15 条多步问题，从真实 KB1/KB8 语料中选取，覆盖：跨文档比较、矛盾仲裁、流程规划、长文规范解释、数值/日期核对。
- 参考答案用现有 P0-16 的 gold 文档/证据片段固定；DeepSeek judge 按 5 分制打“事实准确性/证据覆盖/逻辑自洽”，另做 20% 人工盲审。
- 不修改 qrel，不据结果调题。

### F.2 运行矩阵

| 组 | 配置 |
|---|---|
| EXP-00 | SINGLE baseline |
| EXP-01 | SWARM（3 workers，15s 超时） |
| EXP-02 | SWARM（1 worker，诊断并发收益） |
| EXP-03 | DEBATE R=1（Phase 2，仅 SWARM 达标后） |
| EXP-04 | 关闭知识库工具（诊断检索依赖） |
| EXP-05 | Worker 注入 16s 延迟，验证 100% 回退 |

每配置 3 seeds，共 15 题 × 3 = 45 次/配置；先跑 EXP-00/01，再按结果决定 Phase 2。

### F.3 指标与预算

- 指标：质量均分、单题最大退化、p95 延迟、prompt/completion tokens、人民币成本、降级率。
- 预注册判据：SWARM 质量 ≥ SINGLE +10% 绝对值（0.5/5）；无单题下降 >1 分；p95 ≤3× 且 ≤15s；成本 ≤3× 且单次 ≤0.05 元；降级率 <5%。
- 成本按官方 `deepseek-flash`：缓存未命中输入 1/2 元、命中 0.02/0.04 元、输出 4/8 元每百万 tokens（空闲/高峰）；按实测 token 计算，不以 A 的旧价格为准。
- DEBATE 仅在 SWARM 达标后评估：质量再 ≥5%，成本 ≤6×，否则永久不启用。

### F.4 失败码

`ORCHESTRATION_UNKNOWN_MODE`、`ORCHESTRATION_DISABLED`、`SUPERVISOR_TIMEOUT`、`SUPERVISOR_FAILED`、`DEBATE_FAILED`、`BUDGET_EXCEEDED`。所有失败均回退 SINGLE，并记录 `mode/fallback/reason/latency/tokens`。

### F.5 验证命令

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am test -Dtest='OrchestrationModeRoutingTest,DagUtilsTest'
```

全量回归：`mvn -B -ntp -pl tests -am test`，要求 0 fail；当前基线 1910 tests / 0 fail / 8 skipped，实施后精确计数写入验收报告。

## G. 风险、停止条件与授权边界

| 风险 | 缓解 |
|---|---|
| Supervisor 串行导致延迟超 15s | worker 并发 + 15s 硬超时 + 回退 SINGLE |
| Worker 复用导致跨请求污染 | 每请求 new Supervisor/Worker/Blackboard；禁止 AgentRegistry 单例 Worker |
| 失败文本被当成功 | 显式检测 `"执行失败:"` 前缀；异常/超时统一回退 |
| 成本/429 | `LLM_CALL_SEMAPHORE`、单请求 token 上限、单次 ≤0.05 元、降级 |
| 伪共识/仿真 | 拒绝 1536/启发式 debate；DEBATE 仅真实三角色 LLM |
| C 报告错误片段被照抄 | 以本文件 E/F 节为准；实施前 review diff |

**立即停止**：默认 SINGLE 回归失败；p95 >3×；成本 >3×；降级率 >5%；单题质量下降 >1 分；出现跨请求数据泄漏；出现 1536 维硬编码进入新路径。

**授权边界**：本文件仅批准研究结论与最小契约；代码实施、Phase 2 DEBATE、参数调优、灰度、生产启用分别授权。当前不改代码。

## 附录：本次交叉核验证据

- 实际 proto：`backend/qknow-hermes/qknow-hermes-proto/src/main/proto/hermes.proto`（14 个 ChatRequest 字段，无 `session_id`）。
- 实际控制面：`KbAgentConfigServiceImpl#chatMessage` 的 `ChatRequest.newBuilder()` 链未设置编排字段。
- 实际执行器：`AgentOrchestrator#executeAgent` 返回 `void`；`emitSingleAnswer` 存在。
- 实际 Supervisor：`SupervisorAgent` 的 `SharedBlackboard`/`sessionQualityReceipts` 是实例成员，请求级新建即可隔离。
- DeepSeek 官方价格页：https://api-docs.deepseek.com/zh-cn/quick_start/pricing（实际抓取 200，已核对价格）。
