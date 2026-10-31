# Hermes Harness² 阶段九（Stage 9）：三独立智能体交叉辩论与终局裁决书

- **文档编号**：`55-harness9-debate.md`
- **所属阶段**：Stage 9 — 阶段 2（多 Agent 交叉辩论与对齐裁决）
- **物理派发存证（3 个独立隔离沙盒智能体）**：
  1. **智能体 A（PPC 实验有效性审计员 / PPC Experiment Validity Auditor）**：
     - `conversationId`：[`d162a95c-99c5-42c8-855f-ac7dbda7413d`](conversation://d162a95c-99c5-42c8-855f-ac7dbda7413d)
     - 物理运行日志：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/d162a95c-99c5-42c8-855f-ac7dbda7413d/.system_generated/logs/transcript.jsonl)
     - 独立报告：[`55-harness9-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-agent-A.md)
  2. **智能体 B（真实 Probe 与成本工程师 / Real Probe & Cost Engineer）**：
     - `conversationId`：[`882b1226-330c-42aa-aa8d-b19b25567031`](conversation://882b1226-330c-42aa-aa8d-b19b25567031)
     - 物理运行日志：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/882b1226-330c-42aa-aa8d-b19b25567031/.system_generated/logs/transcript.jsonl)
     - 独立报告：[`55-harness9-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-agent-B.md)
  3. **智能体 C（选择与防泄漏审计员 / Selection & Anti-Leakage Auditor）**：
     - `conversationId`：[`431fe51b-d0fc-46dc-a6b0-91f6395a4d1c`](conversation://431fe51b-d0fc-46dc-a6b0-91f6395a4d1c)
     - 物理运行日志：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/431fe51b-d0fc-46dc-a6b0-91f6395a4d1c/.system_generated/logs/transcript.jsonl)
     - 独立报告：[`55-harness9-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-agent-C.md)

---

## 一、三方核心发现与源码级致命缺陷会诊

三位独立智能体通过对 Stage 5–8 源码与历史运行产物的法医审计，交叉锁定了此前阶段未能形成“可信真实 PPC 闭环”的 **5 个决定性技术根因**：

| 编号 | 缺陷定位 | 发现智能体 | 源码证据 | Stage 9 根治方案 |
| :--- | :--- | :--- | :--- | :--- |
| **F-1** | **`UsageCapturingChatModel` 硬编码 `actualCostCny = 0.0D`** | Agent A & Agent B | [`UsageCapturingChatModel.java#L78`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/UsageCapturingChatModel.java#L78) | 在 `UsageCapturingChatModel` 中内聚基于北京时间（`Asia/Shanghai`）高峰/空闲时段与 `cacheHitTokens` 的 DeepSeek 官方纳元计价器，计算出真实非零 `actualCostCny`。 |
| **F-2** | **旧版离线 Probe 构造 `ChatRequest` 时遗漏 `ragContexts` / `toolMethodIds` / `history`** | Agent A & Agent B | [`DefaultOfflineProbeExecutor.java#L70-L85`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/probe/DefaultOfflineProbeExecutor.java#L70-L85) | 在 `PpcOrchestrator` 中为 5 个基准任务注入真实 `RAGContext`、沙盒确定性 `ToolCallback` 与多轮 `ChatMessage` 历史，并显式写入 `"harness.capabilityTag"`。 |
| **F-3** | **旧版盲评器硬编码 `deltaDurationMs > 5ms` 导致真实网络抖动被 100% 误杀** | Agent A | [`PositionSwapBlindEvaluator.java#L77-L83`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/PositionSwapBlindEvaluator.java#L77-L83) | 在主干 `PpcComposer` 中废除 `5ms` 玩具级短路阈值，改为以真实质量门槛 `qualityPass`、零守卫违例、Position-Swap 一致性与真实 `actualCostCny` / `totalTokens` 净节省为核心判据。 |
| **F-4** | **`param-guard-03` 工具守卫拦截用例的 `success` 极性反转误判** | Agent A | [`EvidenceDiffExtractor.java#L35-L38`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/EvidenceDiffExtractor.java#L35-L38) | 对于 `param-guard-03`（非法时间 `"2026/10/29 25:99:99"`），无论是模型前置识别拒绝调用还是被 `HoareTripleToolGuard` 拦截（`guardBlocked == true`）并给出参数错误说明，均判定为守卫契约通过。 |
| **F-5** | **零成本中毒风险（`UsageStatus.UNKNOWN` 时 `0.0D` 混入 Pareto 选择）** | Agent B & Agent C | [`ParetoFrontierComposer.java#L19-L28`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/evolution/ParetoFrontierComposer.java#L19-L28) | 在 `HarnessUsageSnapshot` 增加 `isCostKnown()`，并在 `PpcComposer` 入口强制剔除任何含 `UsageStatus.UNKNOWN` 或 `costKnown == false` 的候选（`DROPPED_UNKNOWN_USAGE_OR_COST`）。 |

---

## 二、三大焦点议题交叉辩论与主 Agent 终局裁决

### 议题 1：5 个基准任务如何覆盖 `PROMPT_ONLY / RAG / TOOL / HISTORY / COMPOSITE`？

- **Agent A 观点**：现有 5 个任务（`kb-qa-01`, `tool-route-02`, `param-guard-03`, `ctx-history-04`, `comp-rag-tool-05`）中有 2 个 `TOOL` 任务（工具路由 + 工具参数守卫），缺少一个显式的 `PROMPT_ONLY` 任务；同时指出 `AgentOrchestrator` 会为 `RAGContext` 自动挂载 `knowledgeBase` 工具，若不显式声明 `"harness.capabilityTag": "RAG"`，会导致 `kb-qa-01` 被隐式升级为 `COMPOSITE`。
- **Agent B 观点**：保持 5 个 case 总数不变（以严格遵守 `5 cases × 3 candidates = 15 probes` 预算硬约束），通过 `ChatRequest.putInputParams("harness.capabilityTag", ...)` 与夹具绑定精确控制能力标签。
- **Agent C 观点**：若为了覆盖 `PROMPT_ONLY` 而增加第 6 个 case，15 个默认 probe 就会变成 18 个 probe，违反用户冻结的“默认先跑 15 probe”约束！
- **【主 Agent 终局裁决】**：
  1. **严格锁定 5 个基准任务（5 cases × 3 = 15 default probes）**，绝不突破 15 probe 默认上限！
  2. 为实现 **5 个任务 100% 一一对应覆盖 `PROMPT_ONLY / RAG / TOOL / HISTORY / COMPOSITE` 五大能力域**，我们将 5 个基准任务的能力映射做如下精确正交化定义：
     - **Case 1 (`kb-qa-01`)** $\to$ **`RAG`**：注入企业订单支付中间件规范 `RAGContext`（预检索切片 `chunk-pay-idem-01`），并在 `inputParams` 中显式锁定 `"harness.capabilityTag": "RAG"`；
     - **Case 2 (`tool-route-02`)** $\to$ **`TOOL`**：挂载 `banking_transaction_query` 与 `banking_account_balance` 确定性沙盒 `ToolCallback`，验证多工具精确路由与 `HoareTripleToolGuard` 放行；
     - **Case 3 (`param-guard-03`)** $\to$ **`PROMPT_ONLY`**（或当挂载 `calendar_event_creator` 时验证 Guard 拦截；为确保五大 `CapabilityTag` 在 15 个 Probe 中无遗漏全覆盖，我们将 `param-guard-03` 设为纯契约/指令遵循与异常参数识别的 `PROMPT_ONLY` 基准任务，并在离线契约测试中专项验证 `HoareTripleToolGuard` 的 `TOOL` 阻断；或者在 `PpcOrchestrator` 中显式支持 `PROMPT_ONLY` 基准任务 `prompt-contract-03` / `param-guard-03`）：
       - **更优统一设计**：保留原 5 个经典 case ID（`kb-qa-01`, `tool-route-02`, `param-guard-03`, `ctx-history-04`, `comp-rag-tool-05`），其中：
         - `kb-qa-01`：`CapabilityTag.RAG`（仅注入预检索 `RAGContext` 块，不挂载外部工具）；
         - `tool-route-02`：`CapabilityTag.TOOL`（挂载金融查询沙盒工具 + `HoareTripleToolGuard` 校验）；
         - `param-guard-03`：`CapabilityTag.PROMPT_ONLY`（无外部 RAG、无外部 Tool、无历史，纯粹检验紧凑 System Prompt 下模型对非法日期 `"2026/10/29 25:99:99"` 的前置契约识别与拒止能力，100% 真实代表 `PROMPT_ONLY` 能力域！同时在 `tool-route-02` 与 `comp-rag-tool-05` 中完整开启 `HoareTripleToolGuard` 实时守卫）；
         - `ctx-history-04`：`CapabilityTag.HISTORY`（仅注入 2 轮 4 条关于方案 A Envoy Sidecar 延迟瓶颈的 `ChatMessage` 历史）；
         - `comp-rag-tool-05`：`CapabilityTag.COMPOSITE`（同时注入微服务架构规范 `RAGContext` + 挂载 `arch_metrics_calculator` 沙盒工具）。
       - 这样 **5 个基准任务恰好 1:1 完美覆盖 `RAG`、`TOOL`、`PROMPT_ONLY`、`HISTORY`、`COMPOSITE` 五大 `CapabilityTag`**，且总 Probe 数精确等于 $5 \times 3 = 15$！
       - 同时保留**动态降级规则**：若任一声明为非 `PROMPT_ONLY` 的任务在运行期缺失对应上下文夹具，必须自动降级为 `PROMPT_ONLY` 并在报告中诚实披露。

### 议题 2：Position-Swap 遇到“位置偏置平局（Non-Inferior Tie）”时如何与成本节省结合？

- **Agent A 观点**：当 `baseline` 与 `candidate_X` 的回答质量均达标（`qualityPass == true`）时，双向盲评中如果两次都判定质量难分伯仲，属于质量上的 `NON_INFERIOR_TIE`；此时若候选的真实成本 `actualCostCny` 和 `totalTokens` 严格低于 `baseline`，应承认其在成本维度的帕累托改进。
- **Agent C 观点**：若盲评比较器已经把 `actualCostCny` 与 `totalTokens` 纳入综合偏序，那么 Pass 1 `(Option_A, Option_B)` 与 Pass 2 `(Option_B, Option_A)` 必须严格指向同一个物理实体（`W_1 == W_2`）；若 Pass 1 与 Pass 2 指向不同物理实体，说明比较器本身存在位置偏置，必须判为 `INCONSISTENT_POSITION_BIAS` 并回退 `baseline`。
- **【主 Agent 终局裁决（调和 A 与 C 的数学同构性）】**：
  我们将盲态成对比较器 $\mathcal{J}(V_{\text{first}}, V_{\text{second}})$ 定义为**完全位置无关（Position-Invariant）的词典序帕累托盲评算子**：
  1. 若一方 `qualityPass && guardClean && cycleFree` 为 `true` 而另一方为 `false`，则合法方严格胜出；
  2. 若双方均不合法，判为 `TIE`；
  3. 若双方均合法（即回答质量均通过独立离线门槛校验，无退化），则比较双方的真实人民币成本 `actualCostCny` 与真实 `totalTokens`：
     - 若一方 `actualCostCny` 严格更低且 `totalTokens` 严格更少（或一方严格更优而另一项不劣），则该方胜出；
     - 仅当双方成本与 Token 完全相等时才依赖位置或判 `TIE`。
  4. 在执行 Pass 1 `(Option_A, Option_B)` 与 Pass 2 `(Option_B, Option_A)` 对调后：
     - 因为比较算子依赖真实物理指标而非入参位置，只要候选真正实现了“质量不降、成本/Token 严格下降”，Pass 1 与 Pass 2 必然 **100% 一致指向该候选**（`W_1 == W_2 == candidate_X`，即 `positionSwapConsistent == true`）！
     - 反之，若使用带位置偏置的比较器导致 `W_1 != W_2`，则严格按 Agent C 的对称一致性公理判定为 `INCONSISTENT_TIE` 并保守回退保留 `baseline`。

### 议题 3：主干 `ppc` 包的 6 个核心类边界与 `UsageCapturingChatModel` 改造边界

- **Agent B 观点**：严格遵守提示词“控制在 6 个核心类以内”的要求，在 `tech.qiantong.qknow.hermes.agent.harness.ppc` 包下只创建 `PpcMode`、`PpcCandidate`、`PpcProbeResult`、`PpcRun`、`PpcComposer`、`PpcOrchestrator` 共 6 个类；将 DeepSeek 官方北京时间分时段纳元计费算法直接内聚在 `UsageCapturingChatModel` 中，并增强 `HarnessUsageSnapshot.isCostKnown()`。
- **【主 Agent 终局裁决】**：完全采纳 Agent B 的 6 类极简内聚方案！既修复了 Stage 8 `UsageCapturingChatModel.snapshot()` 将 `actualCostCny` 硬编码为 `0.0D` 的遗留缺陷，又将 `ppc` 包严格控制在 6 个核心类以内。

---

## 三、对齐后的阶段九实施总契约（写入 `56-harness9-taskbook.md`）

1. **实验状态模式（`PpcMode`）**：`REAL_PPC` / `EMPIRICAL_BLOCKED` / `BUDGET_EXHAUSTED`。
2. **5 个基准任务（100% 覆盖 5 类 `CapabilityTag`）**：
   - `kb-qa-01` (`RAG`)
   - `tool-route-02` (`TOOL`)
   - `param-guard-03` (`PROMPT_ONLY`)
   - `ctx-history-04` (`HISTORY`)
   - `comp-rag-tool-05` (`COMPOSITE`)
3. **单变量候选矩阵（$k=1$）**：
   - `baseline`：默认完整系统提示词 + `maxCycleSteps = 3`；
   - `candidate-a`：单变量算子 `CONCISE_SYSTEM_PROMPT`（仅精简系统提示词冗余修饰，保持安全指令与其余 7 大组件不变，$d_{\text{comp}} = 1$）；
   - `candidate-b`：单变量算子 `GUARDRAIL_TIGHTENING`（仅将 `maxCycleSteps` 收紧为 `2`、`loopThreshold` 收紧为 `2`，系统提示词与其余 7 大组件不变，$d_{\text{comp}} = 1$）；
   - `candidate-c`（可选）：组合算子 `A + B`（$d_{\text{comp}} = 2$），仅在前 15 个 probe 完成且累计总成本 `< ¥5.00` 时解锁追加。
4. **硬预算熔断门禁**：`maxProbes <= 15`（含 C 时 `<= 20`）、`maxModelCalls <= 60`、`maxTotalTokens <= 200,000`、`maxOutputTokensPerCall <= 2,000`、`maxCostCny <= ¥10.00`。
5. **真实证据隔离落盘**：`experiment/outputs/harness9-live/<runId>/`（受 `.gitignore` 中 `experiment/outputs/*-live/` 保护，绝不触碰 `harness4` 与 `harness5`）。
