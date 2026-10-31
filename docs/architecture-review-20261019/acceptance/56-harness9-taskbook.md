# Hermes Harness² 阶段九（Stage 9）：真实 DeepSeek PPC 实验与可信成本闭环冻结任务书

- **文档编号**：`56-harness9-taskbook.md`
- **任务书状态**：`FROZEN`（已冻结）
- **基线分支与提交**：`feat/hermes-harness-stage8` (`HEAD: 198a6b69`)
- **目标工作分支**：`feat/hermes-harness-stage9`（从完成归档后的 `feat/hermes-harness-stage8` 切出，严禁修改 `main`）
- **Java 编译与运行环境**：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`
- **生成模型与向量基线**：DeepSeek 官方活跃模型 `deepseek-flash`（指向 `DeepSeek-V4.1-Flash-1017`）；千问 `text-embedding-v4`（仓库 `AGENTS.md` 已统一锁定 `1024` 维单位超球面 $\mathbb{S}^{1023}$；注：Stage 9 显式禁用 `ragScore` 且不调用 Embedding 向量计算，与 1024/1536 维度完全解耦，不构成实施冲突）
- **生产默认配置**：`SINGLE`；`KernelBridgeMode.OFF`；`hermes.orchestration.enabled=false`（Stage 9 真实 Probe 显式开启 `KernelBridgeMode.ENFORCE`）
- **物理派发三智能体审计存证**：
  - **Agent A（PPC 实验有效性审计员）**：[`d162a95c-99c5-42c8-855f-ac7dbda7413d`](conversation://d162a95c-99c5-42c8-855f-ac7dbda7413d) | [`55-harness9-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-agent-A.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/d162a95c-99c5-42c8-855f-ac7dbda7413d/.system_generated/logs/transcript.jsonl)
  - **Agent B（真实 Probe 与成本工程师）**：[`882b1226-330c-42aa-aa8d-b19b25567031`](conversation://882b1226-330c-42aa-aa8d-b19b25567031) | [`55-harness9-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-agent-B.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/882b1226-330c-42aa-aa8d-b19b25567031/.system_generated/logs/transcript.jsonl)
  - **Agent C（选择与防泄漏审计员）**：[`431fe51b-d0fc-46dc-a6b0-91f6395a4d1c`](conversation://431fe51b-d0fc-46dc-a6b0-91f6395a4d1c) | [`55-harness9-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-agent-C.md) | [`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/431fe51b-d0fc-46dc-a6b0-91f6395a4d1c/.system_generated/logs/transcript.jsonl)
- **前置预算、审计与研究文档**：[`55-harness9-budget.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-budget.md)、[`55-harness9-audit.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-audit.md)、[`55-harness9-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-research.ledger.json)、[`55-harness9-research.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-research.md)、[`55-harness9-debate.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-debate.md)
- **正文哈希（`body_sha256`，从 `^## 一、` 起算至文件末尾）**：`65f8f5b7095cc8f8f01b71b4a3b33f8a824b71f12a2506549bb6fd6b438240f7`
- **全文件哈希（`full_file_sha256`）**：见伴生文件 [`56-harness9-taskbook.md.sha256`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/56-harness9-taskbook.md.sha256)

---

## 一、实验模式与阶段定位（`REAL_PPC` / `EMPIRICAL_BLOCKED` / `BUDGET_EXHAUSTED`）

### 1.1 阶段九最高目标
用真实 DeepSeek API（`deepseek-flash`）与真实 `AgentOrchestrator`（`SINGLE` 模式 + `HarnessKernelBridge` `ENFORCE`）走完 **1 轮受控、可证伪、零泄漏、具备真实人民币成本闭环的 Propose-Probe-Compose (PPC) 实验**（$k=1$）。

### 1.2 三态实验模式枚举（`PpcMode`）
在 `tech.qiantong.qknow.hermes.agent.harness.ppc.PpcMode` 中严格定义以下三种运行终态：
1. **`REAL_PPC`**：已检测到有效真实 `DEEPSEEK_API_KEY`，所有必需的真实 DeepSeek Probe 执行完成，Provider 真实返回 `UsageStatus.KNOWN` 与非零 `actualCostCny`，并由匿名选择器产出可证伪结论（采纳最优候选或诚实输出 `REJECTED_AFTER_REAL_PPC` 保留 `baseline`）。
2. **`EMPIRICAL_BLOCKED`**：运行环境缺失有效 `DEEPSEEK_API_KEY`（为空、长度 `< 16` 或含占位词）、或 `baseline` 出现 `UsageStatus.UNKNOWN` / 网络不可达，系统**严禁回退到 Mock / 测试替身伪造 `REAL_PRODUCTION` 证据**，而是诚实阻断并标记为 `EMPIRICAL_BLOCKED`。
3. **`BUDGET_EXHAUSTED`**：在真实 Probe 运行期间，累计 Probe 数、累计模型调用次数、累计总 Token 数、单次输出 Token 数或累计人民币费用触及硬上限阈值，预算看门狗立即物理阻断后续网络调用，保存已完成 Probe 证据并将整轮运行标记为 `BUDGET_EXHAUSTED`。

### 1.3 明确不做的事项（硬性排除边界）
- **不做**完整生产认证、无限嵌套 JSON Schema、企业级 SLA/运维；
- **不做** MCP stdio / HITL 人工审批 / 在线自修改；
- **不做**多轮高成本递归（锁定 $k=1$，拒绝 $k \ge 2$）；
- **不做**在默认 `mvn test` 中跑外网付费测试（真实 PPC 测试必须标注 `@Tag("live")` 并通过 `-Plive` 显式触发）；
- **不做**伪造 `ragScore`（未接通真实 1024 维余弦向量打分前，在 Stage 9 显式禁用 `ragScore`）。

---

## 二、5 个基准任务集、真实能力夹具与 `PROMPT_ONLY` 降级规则

### 2.1 5 个基准任务与 5 类 `CapabilityTag` 的 1:1 完备映射
为在 **15 个默认 Probe**（5 cases × 3 candidates）预算内 100% 覆盖 `PROMPT_ONLY / RAG / TOOL / HISTORY / COMPOSITE` 五大能力域，冻结以下 5 个标准基准任务（`BenchmarkCaseSpec`）：

| 任务 ID (`caseId`) | 声明能力标签 (`CapabilityTag`) | 任务问题 (`question`) | 真实能力上下文夹具（Fixture） | 离线质量门槛 (`qualityPass` 判定锚点，严禁泄漏给 Proposer/Selector) |
| :--- | :--- | :--- | :--- | :--- |
| **`kb-qa-01`** | **`RAG`** | 请根据《企业订单支付系统中间件技术规范》，说明订单支付网关超时重试的最大次数、退避间隔，以及幂等键（Idempotency-Key）的标准生成规则。 | 注入非空 `RAGContext`（`knowledgeId="_MiddleWare_Spec"`, 切片 ID `"chunk-pay-idem-01"`，含重试 3 次、1000/2000/4000ms 退避及 SHA-256 幂等键规范）；`inputParams` 显式设 `"harness.capabilityTag"="RAG"`。 | 回答非空、`ragRetrieved == true`、`ragChunkIds` 含 `"chunk-pay-idem-01"`，且回答包含 `"3"` 与 `"幂等"`（或 `"SHA-256"`）。 |
| **`tool-route-02`** | **`TOOL`** | 客户反馈交易流水 `TX-20261029-8891` 已扣款但未生成凭证，请调用合适的金融查询工具核查该流水的扣款状态与关联账户号，并给出核查结论。 | 注册确定性沙盒 `ToolCallback`：`banking_transaction_query`（返回 `TX-20261029-8891` 扣款 `SUCCESS`、账户 `ACC-9901`、金额 `5800.00`）与 `banking_account_balance`；注入 `toolMethodIds`；`inputParams` 设 `"harness.capabilityTag"="TOOL"`。 | 回答非空、真实触发 `banking_transaction_query` 工具调用（`toolCallCount >= 1`）、`guardBlocked == false`，且回答包含 `"ACC-9901"` 或 `"5800"`。 |
| **`param-guard-03`** | **`PROMPT_ONLY`** | 用户请求创建日程：`{"title": "架构评审会", "startTime": "2026/10/29 25:99:99"}`。请直接依据时间格式公理检查该请求的参数合法性，指出具体错误原因并给出修正建议。 | **不注入**外部 `RAGContext`、**不挂载**外部 `ToolCallback`、**不注入**历史消息；`inputParams` 设 `"harness.capabilityTag"="PROMPT_ONLY"`，纯粹检验紧凑提示词契约下模型的指令遵循与非法时间（`25:99:99`）前置拒止能力。 | 回答非空、`effectiveCapabilityTag == PROMPT_ONLY`，且回答明确指出 `"25:99:99"`（或小时/分钟/秒越界、ISO-8601 格式非法）。 |
| **`ctx-history-04`** | **`HISTORY`** | 针对前文对话中我们讨论的“方案 A（基于 Envoy Sidecar 的服务网格）”最担忧的性能瓶颈，业界在网络数据面上有哪些绕过优化方案？ | 注入 2 轮（4 条 `user`/`assistant`）真实 `ChatMessage` 历史（明确讨论方案 A 因内核态/用户态上下文切换与两次 socket 转发导致 P99 延迟增加 3.5~5ms）；`inputParams` 设 `"harness.capabilityTag"="HISTORY"`。 | 回答非空、`coldBufferSnapshot.hotSize() >= 4`，且回答准确衔接前文延迟/上下文切换瓶颈并给出数据面优化方案（如 `"eBPF"`、`"sockmap"`、`"Ambient"` 或 `"旁路"`）。 |
| **`comp-rag-tool-05`** | **`COMPOSITE`** | 请结合《微服务架构演进评估标准》知识库文档，并调用 `arch_metrics_calculator` 工具查询当前集群的基线指标，对比单体架构、Sidecar Mesh 与 eBPF 加速网格的延迟与资源开销。 | 同时注入微服务架构规范 `RAGContext`（切片 `"chunk-arch-ebpf-01"`）并挂载确定性沙盒工具 `arch_metrics_calculator`（返回 P99 延迟与 CPU 占用基准数据）；`inputParams` 设 `"harness.capabilityTag"="COMPOSITE"`。 | 回答非空、同时满足 `ragRetrieved == true` 与 `toolCallCount >= 1`（调用 `arch_metrics_calculator`），且回答包含三类架构的对比结论。 |

### 2.2 动态 `PROMPT_ONLY` 降级披露铁律
- 若在任何自定义执行配置下，某个声明为 `RAG / TOOL / HISTORY / COMPOSITE` 的基准任务未提供对应的非空 `RAGContext`、可解析 `ToolCallback` 或非空 `history`：
  1. 在 `KernelBridgeMode.ENFORCE` 下，`CapabilityContextAssembler` 会直接触发前置检查失败（`failClosed = true`）；
  2. 若调用方未显式锁定标签而由内核自动推导，则 `effectiveCapabilityTag` 必须诚实降级为 **`CapabilityTag.PROMPT_ONLY`**，并在 `PpcRun` 汇总中记录降级数量，**严禁将缺失上下文的裸 Prompt 调用伪装成 `RAG` / `TOOL` / `HISTORY` / `COMPOSITE`**。

---

## 三、Baseline 与单变量候选算子矩阵（Single-Variable Control, $k=1$）

为严格遵循控制变量法（Ceteris Paribus），避免多变量混杂与小样本过拟合，阶段九定义以下候选配置（由 `PpcComposer` 基于 `AlgebraicHarnessMutator` 生成，且**跨全部 5 个任务共享同一个静态不可变 `HermesHarnessVoucher` 指纹**）：

1. **`baseline`（基线配置 $\mathcal{H}^{(0)}$）**：
   - `invariantSystemPrompt`：标准详细系统提示词（包含完整角色定义、分步详尽说明要求與 5 条安全边界指令）；
   - `guardrails`：`maxCycleSteps = 3`，`loopThreshold = 2`，`maxTokenBudget = 200_000L`，`maxCostCny = 10.0D`；
   - 其余 6 大组件保持默认值。
2. **`candidate-a`（单变量候选 A $\mathcal{H}^{(A)}$：算子 `CONCISE_SYSTEM_PROMPT`）**：
   - **唯一变更变量**：仅精简 `invariantSystemPrompt` 中的冗余寒暄与铺垫修饰（要求直接给出结构化结论与关键依据，省略客套开场白与重复总结），保持 `safetyInstructions` 100% 不变，且字符长度严格小于 `baseline`；
   - **严格不变量**：`guardrails`（`maxCycleSteps = 3`）及展示给模型的工具/上下文/参数与 `baseline` 100% 恒等，组件级汉明距离 $d_{\text{comp}}(\mathcal{H}^{(0)}, \mathcal{H}^{(A)}) = 1$。
3. **`candidate-b`（单变量候选 B $\mathcal{H}^{(B)}$：算子 `GUARDRAIL_TIGHTENING`）**：
   - **唯一变更变量**：仅将 `GuardrailsSpecificationRecord` 的 `maxCycleSteps` 从 `3` 收紧为紧凑值 `2`（抑制多余工具试探轮次）；
   - **严格不变量**：`invariantSystemPrompt` 与 `baseline` 逐字节 100% 相同，其余组件完全不变，组件级汉明距离 $d_{\text{comp}}(\mathcal{H}^{(0)}, \mathcal{H}^{(B)}) = 1$。
4. **`candidate-c`（可选组合候选 C $\mathcal{H}^{(C)}$：复合算子 `CONCISE_SYSTEM_PROMPT + GUARDRAIL_TIGHTENING`）**：
   - 同时应用候选 A 与候选 B 的正交变更（$d_{\text{comp}}(\mathcal{H}^{(0)}, \mathcal{H}^{(C)}) = 2$）；
   - **严格解锁门禁**：默认先跑 **15 个 probe**（5 cases × `[baseline, candidate-a, candidate-b]`）。**仅当这 15 个 probe 全部完成、累计总费用 `< ¥5.00`、且候选 A 与候选 B 均未触发严重质量崩溃时**，才允许追加 `candidate-c` 的 5 个 probe（总 probe 数 $\le 20$）。

---

## 四、真实 Probe 执行规范与主干 6 类极简架构

### 4.1 真实 Probe 执行要求
1. **100% 真实生产路径**：必须通过真实 `AgentOrchestrator.chat(request)` 执行，连接真实 DeepSeek 官方端点（`deepseek-flash`），显式注入 `HarnessKernelBridge(..., true, KernelBridgeMode.ENFORCE)`，挂载 `UsageCapturingChatModel`。
2. **严禁旁路模型调用放大**：显式关闭 `PlanSolveConfig`（`enabled=false, rpReactEnabled=false, maxReflectionRetries=0`）以及旁路 `RetrievalEvaluator`、`AiJudgeService`、`RagasEvaluator`，并将单次调用的 `max_tokens` 硬限制在 `<= 2,000`，确保每次 Probe 的底层模型调用次数严格控制在 **1～3 次**以内。

### 4.2 主干 `tech.qiantong.qknow.hermes.agent.harness.ppc` 最小 6 类契约
在 `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/ppc/` 下**严格控制在以下 6 个核心类以内**：
1. `PpcMode.java`：`REAL_PPC`、`EMPIRICAL_BLOCKED`、`BUDGET_EXHAUSTED` 枚举。
2. `PpcCandidate.java`：封装 `candidateId`、`HermesHarnessVoucher`、`voucherFingerprint`、应用的算子列表以及聚合后的真实 Token、真实 `totalCostCny`、`allUsageKnown`、`costKnown` 等不可变 Record。
3. `PpcProbeResult.java`：封装单次 `(candidateId, caseId)` Probe 的 `CapabilityTag`、`executedAt`、`peakPricingPeriod`、`ExecutionMode`、`UsageStatus`、`costKnown`、`promptTokens`、`completionTokens`、`totalTokens`、`cacheHitTokens`、`reasoningTokens`、`actualCostCny`、`meteredCallCount`、`durationMs`、`qualityPass`、`guardBlocked`、`cycleDetected`、`ragAttribution`（显式禁用 `ragScore`）与 `HarnessEvidenceRecord` 的不可变 Record。
4. `PpcRun.java`：封装 `runId`、`PpcMode`、预算上限与实际消耗（`totalProbesExecuted`, `totalModelCallsExecuted`, `totalTokensConsumed`, `totalCostCnyConsumed`）、候选列表、Probe 结果列表、Position-Swap 盲评审计记录、`selectedCandidateId`、`finalVerdict`（`ADOPTED` 或 `REJECTED_AFTER_REAL_PPC`）及文件 SHA-256 校验表的不可变 Record。
5. `PpcComposer.java`：负责单变量候选生成、零 qrel 泄漏硬断言（`assertNoQrelLeakage`）、`UNKNOWN` usage/cost 候选硬剔除、确定性哈希种子匿名映射（`Option_A` / `Option_B`）、Position-Swap 双向对调一致性检验以及帕累托最优选择。
6. `PpcOrchestrator.java`：负责组装 5 个基准任务的真实上下文夹具、驱动真实 `AgentOrchestrator` + `UsageCapturingChatModel` 执行 Probe、实施双层原子硬预算看门狗拦截，以及将证据原子落盘至 `experiment/outputs/harness9-live/<runId>/`。

---

## 五、真实 `actualCostCny` 官方分时段计费公式与硬预算熔断契约

### 5.1 修复 `UsageCapturingChatModel` 硬编码 `0.0D` 并内聚官方分时段计费
在 [`UsageCapturingChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/UsageCapturingChatModel.java) 与 [`HarnessUsageSnapshot.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessUsageSnapshot.java) 中实现 DeepSeek 官方 `deepseek-flash`（`DeepSeek-V4.1-Flash-1017`）北京时间分时段计费：
1. **时段判定 `isPeakBeijingTime(Instant timestamp)`**：
   转换至 `ZoneId.of("Asia/Shanghai")`：周一至周五 `[09:00, 12:00)` 与 `[14:00, 18:00)` 为 **高峰时段（Peak）**；其余时间及周六、周日全天为 **空闲时段（Off-Peak）**。
2. **官方单价定义（严格区分“元 / 百万 tokens”与“纳元 / token”两套等价口径）**：
   - **口径 A：每百万 tokens 人民币单价 $P$（单位：元 / $10^6$ tokens，搭配分母 $10^6$）**：
     - **高峰时段（Peak）**：$P_{\text{hit}} = 0.04$ 元/M、$P_{\text{miss}} = 2.00$ 元/M、$P_{\text{out}} = 8.00$ 元/M；
     - **空闲时段（Off-Peak）**：$P_{\text{hit}} = 0.02$ 元/M、$P_{\text{miss}} = 1.00$ 元/M、$P_{\text{out}} = 4.00$ 元/M。
   - **口径 B：定点纳元单价 $p$（单位：纳元 / token，即 $10^{-9}$ 元/token，搭配分母 $10^9$）**：
     - **高峰时段（Peak）**：$p_{\text{hit}} = 40\text{L}$ 纳元/token、$p_{\text{miss}} = 2\_000\text{L}$ 纳元/token、$p_{\text{out}} = 8\_000\text{L}$ 纳元/token；
     - **空闲时段（Off-Peak）**：$p_{\text{hit}} = 20\text{L}$ 纳元/token、$p_{\text{miss}} = 1\_000\text{L}$ 纳元/token、$p_{\text{out}} = 4\_000\text{L}$ 纳元/token。
3. **精确计费公式（两套口径数学 100% 恒等）**：
   $$\begin{aligned}
   T_{\text{hit}} &= \min(\text{promptTokens}, \max(0L, \text{cacheHitTokens})) \\
   T_{\text{miss}} &= \max(0L, \text{promptTokens} - T_{\text{hit}}) \\
   \text{actualCostCny} &= \frac{T_{\text{hit}} \cdot P_{\text{hit}}(t) + T_{\text{miss}} \cdot P_{\text{miss}}(t) + \text{completionTokens} \cdot P_{\text{out}}(t)}{10^6} \\
   &= \frac{T_{\text{hit}} \cdot p_{\text{hit}}(t) + T_{\text{miss}} \cdot p_{\text{miss}}(t) + \text{completionTokens} \cdot p_{\text{out}}(t)}{10^9}
   \end{aligned}$$
4. **`HarnessUsageSnapshot.isCostKnown()` 契约**：
   仅当 `usageStatus == UsageStatus.KNOWN && totalTokens > 0L && Double.isFinite(actualCostCny) && actualCostCny > 0.0D` 时返回 `true`；当 `usageStatus == UsageStatus.UNKNOWN` 时，`isCostKnown()` 必为 `false`。

### 5.2 硬预算上限与实时熔断规则（`BUDGET_EXHAUSTED`）
- **最大 Probe 数**：默认 `15`（满足 `< ¥5.00` 追加候选 C 条件时最大 `20`）
- **最大模型调用次数**：`60`
- **最大总 Token 数**：`200,000`
- **单次最大输出 Token 数**：`2,000`
- **硬性人民币费用上限**：`¥10.00`
- **实时拦截机制**：`PpcOrchestrator` 在每次 Probe 启动前及每次底层 `ChatModel.call/stream` 前后实时核验上述 5 项预算指标；一旦触及任一上限，立即物理阻断后续网络调用，将 `PpcRun.mode` 设为 `PpcMode.BUDGET_EXHAUSTED` 并落盘已完成证据。

---

## 六、指标体系与显式禁用 `ragScore`

1. **阶段九启用并采集的真实指标**：
   - 质量与契约门禁：`qualityPass`（布尔）、`effectiveCapabilityTag`、`toolCallCount`、`toolNames`、`guardBlocked`、`guardBlockedCount`、`cycleDetected`、`ragRetrieved`（布尔）、`ragChunkIds`（列表）；
   - 真实资源与成本指标：`usageStatus`（`KNOWN`/`UNKNOWN`）、`costKnown`（布尔）、`promptTokens`、`completionTokens`、`cacheHitTokens`、`reasoningTokens`、`totalTokens`、`actualCostCny`、`durationMs`。
2. **显式禁用 `ragScore`**：
   - 在未接通千问 `text-embedding-v4` 1024 维单位超球面实时余弦相似度打分前，**严禁在阶段九使用任何伪造常数或启发式数字作为 `ragScore` 参与候选比较**；`PpcProbeResult` 中显式标记 `ragScoreEnabled = false`。

---

## 七、匿名 Selection、Position-Swap 一致性与零泄漏铁律

1. **零 qrel 泄漏断言（`assertNoQrelLeakage`）**：
   - Proposer 严禁接收 `BenchmarkCaseSpec` 或 `expectedKeywords`（qrel）；
   - 同一候选在 5 个任务上的 `voucherFingerprint` 必须 100% 恒等；
   - 在候选生成后与进入选择器前，强制扫描候选 `systemPrompt` 与选择器上下文，若包含任何任务的专属 `expectedKeywords` 或评分器标记词，立即抛出 `IllegalStateException("QREL_LEAKAGE_DETECTED")`。
2. **不可信指标（`UNKNOWN` Usage / Cost）一票否决剔除**：
   - 凡任一候选在任一任务上的 Probe 出现 `usageStatus != UsageStatus.KNOWN` 或 `!costKnown` 或 `actualCostCny <= 0.0D`，`PpcComposer` 必须在入口处直接将其标记为 `DROPPED_UNKNOWN_USAGE_OR_COST` 剔除，**绝不允许以 `0 token / 0.0 元` 参与比较**。
3. **匿名映射与 Position-Swap 双向对调协议**：
   - 使用确定性 SHA-256 种子将 `baseline` 与 `candidate` 映射为身份剥离的 `Option_A` 与 `Option_B`（`AnonymousOptionView` 严禁包含 `candidateId` 或 `isBaseline`）；
   - 执行 Pass 1 `(Option_A, Option_B)` 与 Pass 2 `(Option_B, Option_A)` 双向对调比较；
   - 只有当两次对调评估指向同一个物理实体（`W_1 == W_2`）时才判定 `positionSwapConsistent = true`；若两次结果矛盾，一律判定为 `INCONSISTENT_TIE` 并保守回退保留 `baseline`。
4. **采纳（`ADOPTED`）与诚实拒绝（`REJECTED_AFTER_REAL_PPC`）判据**：
   - 候选被采纳当且仅当同时满足：(1) 5/5 任务 `usageStatus == KNOWN` 且 `costKnown == true`；(2) 5/5 任务 `qualityPass == true`、无非预期 `guardBlocked`、无 `cycleDetected`；(3) 5/5 任务通过 Position-Swap 一致性检验且无质量劣败；(4) 5 个任务累计真实成本 $\sum \text{actualCostCny}(\text{cand}) < \sum \text{actualCostCny}(\text{base})$（或总成本不增且总 Token 严格减少），且至少 $3/5$ 任务取得成本或 Token 净下降。
   - 若没有任何候选同时满足上述全部条件，`PpcComposer` 必须保留 `baseline` 并诚实输出最终结论 **`REJECTED_AFTER_REAL_PPC`**。

---

## 八、证据隔离落盘结构与 `.gitignore` 零污染规则

1. **专属输出目录**：
   所有 Stage 9 Live PPC 证据统一写入：
   `experiment/outputs/harness9-live/<runId>/`
   - `probes/<candidateId>-<caseId>.json`（每完成一个 Probe 立即通过 `.tmp` + `ATOMIC_MOVE` 落盘）
   - `ppc_summary.json`（完整记录预算配置、各候选逐任务真实 Token/CacheHit/CostCny/Duration、Position-Swap 结论与最终裁决）
   - `selected_candidate.json`（最终入选凭单或保留的 `baseline` 凭单及原因）
   - `checksums.sha256`（目录下全部 JSON 证据的 SHA-256 清单）
2. **历史资产零污染与 `.gitignore` 保护**：
   - 根目录 `.gitignore` 第 113 行已配置 `experiment/outputs/*-live/`，原生忽略 `experiment/outputs/harness9-live/`；
   - `PpcOrchestrator` 强制校验输出路径，**严禁读取、修改或覆盖已跟踪的 `experiment/outputs/harness4/` 与 `experiment/outputs/harness5/`**；
   - 默认测试与 Live 测试执行完毕后，`git status --short` 必须保持 clean。

---

## 九、阶段 4–7 实施与验证蓝图（供代码实施 Agent 严格执行）

### 9.1 阶段 0 & 分支准备（实施 Agent 启动动作）
1. 在 `feat/hermes-harness-stage8` 提交 Stage 9 预研与冻结任务书文档（`55-harness9-*.md`, `55-harness9-research.ledger.json`, `56-harness9-taskbook.md`, `56-harness9-taskbook.md.sha256`）；
2. 从 `feat/hermes-harness-stage8` 切出新分支：`git checkout -b feat/hermes-harness-stage9`（严禁修改 `main`）。

### 9.2 阶段 4：实现最小 PPC 闭环与官方分时段计费
1. 修改 [`HarnessUsageSnapshot.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/HarnessUsageSnapshot.java) 与 [`UsageCapturingChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/UsageCapturingChatModel.java)：实现 `isPeakBeijingTime`、`computeOfficialCostNano`、`computeOfficialCostCny`、`isCostKnown()`，修复 `snapshot()` 硬编码 `0.0D` 问题，并支持调用前后预算拦截回调。
2. 在 [`DeepSeekCompatibleChatModel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/chat/DeepSeekCompatibleChatModel.java) 中确保通用 `ChatOptions.getMaxTokens()` 能正确写入请求体 `"max_tokens"`，保障单次输出 $\le 2,000$ tokens。
3. 在 `tech.qiantong.qknow.hermes.agent.harness.ppc` 包下创建 6 个核心类：`PpcMode`、`PpcCandidate`、`PpcProbeResult`、`PpcRun`、`PpcComposer`、`PpcOrchestrator`。

### 9.3 阶段 5：执行真实 DeepSeek PPC（`@Tag("live")`）
- 创建 `HermesHarnessStage9LivePpcTest.java`（标注 `@Tag("live")`）：
  - 当存在有效 `DEEPSEEK_API_KEY` 时，调用 `PpcOrchestrator` 对 5 个基准任务 × (`baseline`, `candidate-a`, `candidate-b`，及满足 `< ¥5` 时的 `candidate-c`) 执行真实 DeepSeek PPC，将证据写入 `experiment/outputs/harness9-live/<runId>/`，并校验 `checksums.sha256`；
  - 当缺失 `DEEPSEEK_API_KEY` 时，验证其诚实返回 `PpcMode.EMPIRICAL_BLOCKED`，绝不调用 Mock 伪造证据。

### 9.4 阶段 6：验证门禁与构建纪律（先单独构建 `qknow-hermes-proto`）
> **构建纪律铁律**：避免并发构建 `qknow-hermes/qknow-hermes-proto` 产生 protobuf 临时目录竞争，必须**先单独构建 `qknow-hermes/qknow-hermes-proto`**，再运行测试：

1. **前置步骤：单独构建 `qknow-hermes/qknow-hermes-proto`**：
   ```bash
   cd /Users/achilles/Documents/许子祺/Agent/backend && \
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn test-compile -pl qknow-hermes/qknow-hermes-proto
   ```
2. **门禁 1：Stage 9 离线契约测试（默认测试，0 外网，0 API Key）**：
   - `PpcCostAndSelectionContractTest`（验证官方 Peak/Off-Peak 价格计算、缓存命中拆账、`UNKNOWN` usage/cost 硬剔除、零 qrel 泄漏断言、匿名映射与 Position-Swap 双向对调）
   - `PpcOrchestratorOfflineTest`（使用显式标记 `DETERMINISTIC_TEST_DOUBLE` 的离线仿真验证 5 类 `CapabilityTag` 装配、`PROMPT_ONLY` 降级、硬预算熔断 `BUDGET_EXHAUSTED`、缺 Key 时 `EMPIRICAL_BLOCKED` 与 `ATOMIC_MOVE` 落盘）
   ```bash
   cd /Users/achilles/Documents/许子祺/Agent/backend && \
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn test -pl qknow-hermes/qknow-hermes-core \
     -Dtest=PpcCostAndSelectionContractTest,PpcOrchestratorOfflineTest
   ```
3. **门禁 2：Stage 8 + Stage 9 联合回归测试（引用仓库内真实 Stage 8 测试类名）**：
   ```bash
   cd /Users/achilles/Documents/许子祺/Agent/backend && \
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn test -pl qknow-hermes/qknow-hermes-core \
     -Dtest=UsageCapturingChatModelTest,CapabilityContextStage8Test,HarnessEvidenceStage8Test,RagAttributionContractTest,AgentOrchestratorRagAttributionTest,HarnessKernelBridgeStage8Test,HermesHarnessStage8PpcReadinessTest,PpcCostAndSelectionContractTest,PpcOrchestratorOfflineTest
   ```
4. **门禁 3：付费真实 PPC 测试（仅通过 `-Plive` 显式触发）**：
   ```bash
   cd /Users/achilles/Documents/许子祺/Agent/backend && \
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn test -pl qknow-hermes/qknow-hermes-core -Plive \
     -Dtest=HermesHarnessStage9LivePpcTest
   ```
5. **门禁 4：全仓默认测试（确认默认不跑 `@Tag("live")` 且工作区 clean）**：
   ```bash
   cd /Users/achilles/Documents/许子祺/Agent/backend && \
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn test
   git status --short
   ```

### 9.5 阶段 7：提交实施与实验报告
- 代码实施 Agent 在完成阶段 4–6 后，输出 [`57-harness9-implementation.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/57-harness9-implementation.md)，如实汇报：真实运行模式（`REAL_PPC` / `EMPIRICAL_BLOCKED` / `BUDGET_EXHAUSTED`）、实际探针数、实际模型调用数、实际总 Token、实际人民币总成本、每个候选的逐题真实表现、最终入选候选或 `REJECTED_AFTER_REAL_PPC` 结论，以及全量门禁结果。
