# 智能体 A：阶段九（Stage 9）PPC 实验有效性法医审计与方法论推导报告

- **物理子智能体 `conversationId`**：[`d162a95c-99c5-42c8-855f-ac7dbda7413d`](conversation://d162a95c-99c5-42c8-855f-ac7dbda7413d)
- **物理运行 Transcript 存证**：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/d162a95c-99c5-42c8-855f-ac7dbda7413d/.system_generated/logs/transcript.jsonl)
- **审计员角色**：智能体 A — PPC 实验有效性审计员 (PPC Experiment Validity Auditor)
- **证据等级**：Auditor (Tier 3) —— 100% 源码级与真实历史运行证据核验
- **审计范围**：
  - 核心主干：`HarnessKernelBridge.java`、`HermesHarnessKernel.java`、`CapabilityContextAssembler.java`、`CapabilityTag.java`、`AlgebraicHarnessMutator.java`、`ParetoFrontierComposer.java`、`TrajectoryAttributionAnalyzer.java`、`AgentOrchestrator.java`、`HermesHarnessAssembler.java`、`UsageCapturingChatModel.java`、`HarnessEvidenceCollector.java`、`DeepSeekCostGovernor.java`
  - 离线实验框架：`OfflineProbeRunner.java`、`OfflineBenchmarkTestCase.java`、`DefaultOfflineProbeExecutor.java`、`OfflineHarnessProposer.java`、`PositionSwapBlindEvaluator.java`、`EvidenceDiffExtractor.java`、`ProbeExecutionDiff.java`、`OfflineBudgetCircuitBreaker.java`
  - 历史真实运行资产：`experiment/outputs/harness5/run-20261030-stage5-real/`
  - 上游基准仓库：`/Users/achilles/Documents/优秀开源项目/harness2`（Commit: `1cfe254e8d25b15f37048c3730134686abe60335`）

---

## 一、执行摘要与四大源码级致命缺陷发现（Executive Forensic Findings）

在对阶段 4–8 既有代码与阶段 5 真实实验产物（`experiment/outputs/harness5/run-20261030-stage5-real/eval/ppc_summary.json`）进行逐行法医审计后，我们定位了导致此前真实探针实验无法闭合或被错误拦截的 **4 个源码级缺陷**，必须在阶段九 `PpcOrchestrator` 中予以根治：

1. **缺陷 1（上下文静默剥离导致全量退化为 `PROMPT_ONLY`）**：`DefaultOfflineProbeExecutor.java#L70-L85` 在构造 `ChatRequest` 时仅设置了 `question`、`systemPrompt` 和 `modelConfig`，完全遗漏了 `.addAllRagContexts(...)`、`.addAllToolMethodIds(...)`、`.addAllHistory(...)` 与 `.setInputParams(...)`，且未向 `AgentOrchestrator` 注册离线确定性 `ToolCallback`。这直接导致阶段 5 真实运行中 `ragDocumentCount=0`、`toolExecutions=[]`（见 `baseline/evidence_kb-qa-01.json#L9-L15`）。
2. **缺陷 2（`UsageCapturingChatModel` 硬编码 `actualCostCny = 0.0D`）**：`UsageCapturingChatModel.java#L76-L79` 在构造 `HarnessUsageSnapshot` 时将第 7 个参数 `actualCostCny` 硬编码为 `0.0D`；而 `HarnessEvidenceCollector.java#L143-L144` 规定当 `usageKnown == true` 时直接采用 `usage.actualCostCny()`。这导致一旦捕获到真实 Provider Token，产出凭单中的 `estimatedCostCny` 反而会归零为 `0.0D`！阶段九必须接入官方分时段/缓存命中纳元计价器计算出真实的 `actualCostCny`。
3. **缺陷 3（真实网络抖动被 `5ms` 玩具级耗时阈值 100% 误杀）**：`PositionSwapBlindEvaluator.java#L77-L83` 与 `ProbeExecutionDiff.java#L57` 硬编码了 `deltaDurationMs > 5` 即触发 `SHORT_CIRCUITED_REJECT`。在阶段 5 真实 DeepSeek API 调用中，`tool-route-02` 的基线耗时为 `4678ms`、候选耗时为 `4684ms`（仅相差 `+6ms`，属于正常公网抖动，而其 Token 从 `340` 降至 `175`，净省 `48.5%`），却因 `+6ms > 5ms` 被直接短路拒绝（见 `ppc_summary.json#L93`）。
4. **缺陷 4（守卫拦截用例 `param-guard-03` 的工具匹配极性反转）**：`EvidenceDiffExtractor.java#L35-L38` 要求所有带 `expectedToolName` 的用例必须满足 `entry.success() == true`。但 `param-guard-03` 测试的是非法日期参数（`"2026/10/29 25:99:99"`）被 `HoareTripleToolGuard` 前置拦截并引导模型安全自愈；当 Guard 正确拦截时，`entry.guardBlocked() == true` 且 `entry.success() == false`（见 `HarnessEvidenceRecord.java#L67`），导致被 `EvidenceDiffExtractor` 误判为 `toolSelectionMatched = false`！

---

## 二、结构化研究账本（14-Field Research Ledger）

```text
id: RL-S9-01
sourceType: paper
titleOrRepository: Harness^2: Recursive Agent Harnessing for an Open World
authorsOrMaintainer: Ruiyao Xu, Yanfei Chen, Zhongying CuiZhu, Bhavana Dalvi Mishra, Yifei Ming, Han Yu, Rujun Han, Chen-Yu Lee, Tomas Pfister
venueAndYear: arXiv Preprint, 2026
doiOrArxiv: arXiv:2610.14224
url: https://arxiv.org/html/2610.14224
commitOrTag: v1 (2026-10)
license: Apache-2.0
filesOrSectionsRead: Abstract, Section 2.2.1 (A Contrastive Improvement Step, Eq. 1), Section 2.2.2 (Parallel and Sequential Recursion), Section 3.2 (Main Results, Table 1)
verificationStatus: VERIFIED
relevantFinding: 任务级递归（Task-level recursion）的核心单元是单步对比式 Propose-Probe-Compose (PPC)：(H', e', \xi) = \Phi(q_t, H, E; R_t)。首先获取基线参考轨迹 e^0 \sim A(q_t; M, H)，然后在固定模型权重 M 与环境配置下提议通用不变（INV）与领域特定（DOM）两类候选并在相同任务上执行 Probe，通过行为对比（Behavioral Contrast）无需地面真值（ground-truth verifier）即可识别退化或改进。Table 1 证实 k=1 单步 PPC 即构成主实验的核心改进基线。
projectApplicability: 直接支撑阶段九将实验锁定为 k=1 轮受控对比 PPC（1 个 baseline + 2 个正交单变量候选 candidate_A/B + 预算门禁受控合成 candidate_C），通过真实执行轨迹差分与无 qrel 盲评实现闭环。
limitations: 论文针对长时程开放式任务（每个任务独立跑 4k rollouts），而本系统面向高并发企业级智能体，需将单任务重度递归转化为跨 5 个异构能力域任务的共享静态凭单（Shared Static Voucher）评估，以保护 DeepSeek 前缀缓存。
```

```text
id: RL-S9-02
sourceType: official-code
titleOrRepository: google-research/harness2 (Local Audit Mirror: /Users/achilles/Documents/优秀开源项目/harness2)
authorsOrMaintainer: Google LLC
venueAndYear: Google Open Source Release, 2026
doiOrArxiv: arXiv:2610.14224
url: https://github.com/google-research/harness2
commitOrTag: 1cfe254e8d25b15f37048c3730134686abe60335
license: Apache-2.0
filesOrSectionsRead: harness2/core/recursion.py (L1-L579), harness2/core/selection.py (L1-L506), harness2/core/context.py (L1-L547), harness2/core/ledger.py (L1-L133), README.md, docs/EXPERIMENTS.md
verificationStatus: VERIFIED
relevantFinding: 1) ledger.py (L26-L33, L89-L113) 强制执行账本恒等式断言 R=4k, P=3k 且严禁重复标签覆盖；2) context.py (L399-L519) 通过 assert_no_leak_paths 与 assert_no_grader_verdicts（扫描 deterministic_eval_result、pass_rate 等 7 类评分标记）在文件系统边界阻断标准答案与评分器结论流入 Proposer/Selector；3) selection.py (L71-L86, L237-L252) 使用 SHA-1(task_id|k|mode) 确定性种子打乱候选分配匿名 ID (A..Z)，并在选择器异常或无优势时通过 _fallback 安全回退至已交付的 base。
projectApplicability: 直接指导阶段九 PpcOrchestrator 的三大硬机制：1) 探针账本完整性与成本硬门禁；2) Proposer 与 BlindEvaluator 的零 qrel 泄漏红线检查（Tripwire）；3) 匿名盲评冲突或无显著收益时安全回退至 baseline（输出 REJECTED_AFTER_REAL_PPC）。
limitations: 原仓库基于文件树（Directory Tree）传递证据并调用外部 CLI（OpenCode/Codex），本项目需将其映射为 Java 21 强类型不可变 Record（HermesHarnessVoucher 与 HarnessEvidenceRecord）。
```

```text
id: RL-S9-03
sourceType: paper
titleOrRepository: DSPy: Compiling Declarative Language Model Calls into Self-Improving Pipelines
authorsOrMaintainer: Omar Khattab, Arnav Singhvi, Paridhi Maheshwari, Zhiyuan Zhang, Keshav Santhanam, Sri Vardhamanan, Saiful Haq, Ashutosh Sharma, Thomas T. Joshi, Hanna Moazam, Heather Miller, Matei Zaharia, Christopher Potts
venueAndYear: ICLR 2024
doiOrArxiv: arXiv:2310.03714
url: https://arxiv.org/html/2310.03714
commitOrTag: N/A
license: CC-BY-4.0
filesOrSectionsRead: Abstract, Section 3 (The DSPy Programming Model), Section 3.3 (Teleprompters), Section 4 (The DSPy Compiler)
verificationStatus: VERIFIED
relevantFinding: 提示词工程应被抽象为参数化模块空间上的声明式编译优化问题；在小样本（handful of examples）下编译优化器（Teleprompters）极易对单个样本的表层词汇过拟合，必须将优化提议（Proposal/Bootstrapping）与评估验证（Validation Metric）解耦，保持模块签名（Signatures）的任务通用性。
projectApplicability: 支撑阶段九将 Harness 变异算子限制在任务无关的代数算子空间（AlgebraicHarnessMutator），严禁针对 5 个测试题目的具体实体词定制提示词，确保编译出的凭单在全部 5 个能力域上具备泛化性。
limitations: DSPy 侧重于 Few-shot Demonstrations 自举（会显著增加 Prompt Token 长度与成本），而阶段九的核心目标是验证在不降低质量的前提下能否降低 Token 与真实人民币成本，因此必须拒绝插入长篇 Few-shot 示例。
```

```text
id: RL-S9-04
sourceType: paper
titleOrRepository: Self-Refine: Iterative Refinement with Self-Feedback
authorsOrMaintainer: Aman Madaan, Niket Tandon, Prakhar Gupta, Skyler Hallinan, Luyu Gao, Sarah Wiegreffe, Uri Alon, Nouha Dziri, Shrimai Prabhumoye, Yiming Yang, Shashank Gupta, Bodhisattwa Prasad Majumder, Katherine Hermann, Sean Welleck, Amir Yazdanbakhsh, Peter Clark
venueAndYear: NeurIPS 2023
doiOrArxiv: arXiv:2303.17651
url: https://arxiv.org/html/2303.17651
commitOrTag: N/A
license: CC-BY-4.0
filesOrSectionsRead: Abstract, Section 3 (Evaluation), Section 4 (Analysis: How important are the multiple iterations of feedback-refine? Figure 4)
verificationStatus: VERIFIED
relevantFinding: Section 4 与 Figure 4 实证表明，多轮自反馈修正具有显著的边际收益递减特性（Diminishing Marginal Returns）：第一轮修正（y_0 -> y_1）捕获了绝大部分结构性收益，随迭代轮次增加，边际增益迅速衰减，同时 Token 消耗成倍累加且在弱约束指标下容易引发过度修正（Over-refinement）退化。
projectApplicability: 从信息论与收益成本比（ROI）角度证实了在小预算（<=15-20 probes，硬上限 ¥10）下锁定 k=1 轮 PPC 的最优性，拒绝 k>=3 的高成本低信噪比盲目迭代。
limitations: Self-Refine 是在单次推理输出空间 y_t 上做迭代，而阶段九是在元级（Meta-level）Harness 凭单空间 H 上做单步对比择优。
```

---

## 三、四大核心审计问题的源码级与数学级深度论证

### 审计问题 1：5 个基准任务集的能力覆盖与真实降级边界

#### 1.1 现有 5 个基准任务与 `CapabilityTag` 五元空间的映射矩阵审计
根据 `CapabilityTag.java#L5-L10` 与 `CapabilityContextAssembler.java#L36-L72`，系统内核定义了 5 种互斥且完备的能力域标签：
$$\mathcal{C}_{\text{tag}} = \{\text{PROMPT\_ONLY},\; \text{RAG},\; \text{TOOL},\; \text{HISTORY},\; \text{COMPOSITE}\}$$
审计 `OfflineBenchmarkTestCase.java#L40-L88` 中冻结的 5 个基准用例，其设计意图与当前真实执行状态的对比如下：

| 任务 ID | `TaskCategory` | 目标能力域 (`CapabilityTag`) | 核心验证机制 | Stage 5 真实执行状态（法医实测） | 根因定位 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `kb-qa-01` | `KNOWLEDGE_QA` | `RAG` | 预检索知识块挂载、核心术语归因 (`RagAttributionSummary`) 与事实遵循 | 退化为 `PROMPT_ONLY` (`ragDocumentCount=0`) | `DefaultOfflineProbeExecutor` 未注入 `RAGContext` |
| `tool-route-02` | `MULTI_TOOL_SELECTION` | `TOOL` | 从 3 个候选金融工具中路由选择 `banking_transaction_query` 并解析返回 | 退化为 `PROMPT_ONLY` (`toolExecutions=[]`) | 未注入 `toolMethodIds` 且 `ToolCallbackResolver` 无对应 Bean |
| `param-guard-03` | `TOOL_PARAM_GUARD` | `TOOL` (Guardrail) | 拦截非法日期参数或引导模型对 `2026/10/29 25:99:99` 进行前置校验自愈 | 退化为 `PROMPT_ONLY` (`toolExecutions=[]`) | 未挂载带 Schema 契约的 `calendar_event_creator` |
| `ctx-history-04` | `CONTEXT_HISTORY_RECALL` | `HISTORY` | 跨多轮对话代词消解（“前文提到的方案 A 的最担忧瓶颈”）与冷备环注入 | 退化为 `PROMPT_ONLY` (`history=[]`) | 未向 `ChatRequest` 注入 `history` 消息列表 |
| `comp-rag-tool-05` | `COMPOSITE_REASONING` | `COMPOSITE` | 同时结合架构规范 `RAGContext` 与工具调用，输出 4 列 Markdown 矩阵 | 退化为 `PROMPT_ONLY` (`rag=0, tools=[]`) | 同时缺失 `RAGContext` 与 `ToolCallback` |

> **【关键架构耦合发现：为什么 `RAG` 必须防备被自动升级为 `COMPOSITE`？】**
> 审计 `AgentOrchestrator.java#L483-L525` 与 `HarnessKernelBridge.java#L244-L257` 发现：当 `request.getRagContextsList()` 非空时，`AgentOrchestrator` 会在第 485–512 行自动把每个 `RAGContext` 包装成名为 `knowledgeBase<id>` 和 `knowledgeQuery` 的 `FunctionToolCallback` 加入 `tools` 列表，随后在第 615 行传入 `harnessKernelBridge.bridgeExecution(...)`。
> 而在 `HarnessKernelBridge.resolveCapabilityTag()`（第 244–253 行）中，若 `inputParams` 未显式声明 `harness.capabilityTag`，它会判定 `if (rag && mountedTools) return CapabilityTag.COMPOSITE;`！这意味着如果不显式声明 `"harness.capabilityTag": "RAG"`，所有带 `RAGContext` 的请求都会被隐式判定为 `COMPOSITE`！
> **修复规约**：在阶段九中，每个测试用例必须在 `ChatRequest.inputParams` 中显式写入 `"harness.capabilityTag"`，确保 `kb-qa-01` 的 `effectiveCapabilityTag` 精确落入 `CapabilityTag.RAG`。同时，为了显式覆盖全部五类 `CapabilityTag`，既可在 5 个基准任务中覆盖 `RAG` / `TOOL` / `HISTORY` / `COMPOSITE` 并在缺失载荷或专门契约用例中验证 `PROMPT_ONLY`，也可在 `PpcOrchestrator` 中明确记录每个 case 的 `declaredCapabilityTag` 与 `effectiveCapabilityTag`。

#### 1.2 Stage 9 `PpcOrchestrator` 真实上下文 Fixture 构造契约与 `PROMPT_ONLY` 降级披露铁律
阶段九必须在 `PpcOrchestrator` 中为 5 个用例构造 100% 真实的本地上下文与确定性工具回调（严禁外部网络爬虫或随机数据库状态）：

1. **`kb-qa-01` (`CapabilityTag.RAG`)**：
   - 构造 `RAGContext`：`knowledgeId = "_MiddleWare_Spec"`, `knowledgeName = "企业订单支付系统中间件技术规范"`, `preRetrievedContent = "【企业中间件规范 v3.2 第4章】1. 订单支付网关超时重试机制：最大超时重试次数严格限制为 3 次，采用指数退避策略（间隔分别为 1000ms、2000ms、4000ms），超过 3 次仍超时则标记为 UNKNOWN_STATE 并转入异步对账队列。2. 幂等键（Idempotency-Key）生成规则：必须由客户端在发起首次请求前生成，标准格式为『商户号(merchantId) + ':' + 业务订单号(bizOrderNo) + ':' + 支付流水序号(paySeq)』的 SHA-256 十六进制小写摘要（64位字符），服务端在 Redis 中保持 24 小时原子锁。"`。
2. **`tool-route-02` (`CapabilityTag.TOOL`)**：
   - 在本地确定性 `ToolCallbackResolver` 中注册 3 个 `ToolCallback`（`banking_transaction_query`、`banking_account_balance`、`banking_settlement_remedy`），并向 `ChatRequest` 注入 `toolMethodIds`。
3. **`param-guard-03` (`CapabilityTag.TOOL` + `HoareTripleToolGuard`)**：
   - 注册 `calendar_event_creator`，并在问题中要求处理非法时间 `"2026/10/29 25:99:99"`。
   - **修复工具匹配判据**：对于 `TOOL_PARAM_GUARD`，合法通过条件定义为：要么模型在前置推理中识别出 `25:99:99` 违反 ISO-8601 日期公理并拒绝盲目调用，要么发起的非法调用被 `HarnessKernelBridge.guard()` 拦截（`guardBlocked == true`）且模型在最终回答中明确给出参数格式错误提示。
4. **`ctx-history-04` (`CapabilityTag.HISTORY`)**：
   - 向 `ChatRequest.addAllHistory(...)` 注入 4 条真实历史 `ChatMessage`（2 轮关于方案 A Envoy Sidecar 服务网格延迟瓶颈的对话）。
5. **`comp-rag-tool-05` (`CapabilityTag.COMPOSITE`)**：
   - 同时注入微服务架构演进评估标准 `RAGContext` 与架构基准工具 `ToolCallback`，要求同时满足 `ragDocumentCount >= 1` 且 `toolExecutions.size() >= 1`。

**`PROMPT_ONLY` 降级披露铁律（Degradation Disclosure Iron Rule）**：
- 若某个测试配置缺少真实上下文夹具，必须将请求标记为 `CapabilityTag.PROMPT_ONLY`，且在实验汇总凭单中强制记录降级次数；**凡发生未声明降级的运行，一律不得宣称完成了多维能力域的全覆盖验证**。

---

### 审计问题 2：候选算子的单变量控制（Single-Variable Control / Ceteris Paribus）

设不可变 Harness 凭单 $\mathcal{H} \in \Omega_{\mathcal{H}}$ 由 8 个正交组件构成：
$$\mathcal{H} = \big(C_1, C_2, C_3, C_4, C_5, C_6, C_7, C_8\big) = \big(\text{Prompt}, \text{Guardrails}, \text{Memory}, \text{ToolGuidance}, \text{Subagents}, \text{SkillBank}, \text{Sandbox}, \text{McpPlugins}\big)$$

定义组件级汉明距离（Component-level Hamming Distance）：
$$d_{\text{comp}}\big(\mathcal{H}^{(0)}, \mathcal{H}^{(c)}\big) = \sum_{m=1}^{8} \mathbb{I}\Big[C_m^{(0)}.\text{canonical}() \neq C_m^{(c)}.\text{canonical}()\Big]$$

**硬性单变量门禁公理**：对于任意单变量候选 $c \in \{A, B\}$，必须严格满足：
$$d_{\text{comp}}\big(\mathcal{H}^{(0)}, \mathcal{H}^{(c)}\big) = 1$$

- **单变量候选 A $\mathcal{H}^{(A)}$ (`candidate-a`：`CONCISE_SYSTEM_PROMPT`)**：
  - **唯一变异组件 $C_1$（$m=1$）**：保持 `safetyInstructions` 100% 不变，将 `roleDefinition` 与 `corePrinciples` 中的冗余礼貌铺垫压缩为高信息密度的紧凑指令契约，其余 7 大组件与 `baseline` 100% 恒等。
- **单变量候选 B $\mathcal{H}^{(B)}$ (`candidate-b`：`GUARDRAIL_TIGHTENING`)**：
  - **唯一变异组件 $C_2$（$m=2$）**：仅将 `GuardrailsSpecificationRecord` 的 `maxCycleSteps` 与 `loopThreshold` 紧缩，强制抑制冗余工具试探循环；同时保持 $C_1^{(B)} \equiv C_1^{(0)}$（系统提示词一字不改）以及其余 6 大组件 100% 恒等！
- **可选组合候选 C $\mathcal{H}^{(C)}$ (`candidate-c`：`COMPOSED_A_AND_B`)**：
  - 满足 $d_{\text{comp}}(\mathcal{H}^{(0)}, \mathcal{H}^{(C)}) = 2$。仅当 15 个默认 probes 全部完成、累计真实成本 $< 5.00\text{ CNY}$、且至少一个单变量算子表现出非退化改善信号时，才允许追加 5 个 probes（总 probes $\le 20$）。

---

### 审计问题 3：$k=1$ 轮受控 PPC 的可证伪性（Falsifiability of $k=1$）

1. **调用复杂度与上下文二次方膨胀下界**：
   在 $N=5$ 个任务上若执行 $k$ 轮在线 LLM 递归，总 Token 消耗为 $\Theta(N \cdot k^2 \cdot \bar{L}_{\text{traj}})$。当 $k=1$ 时，受控代数变异仅需 $15 \sim 20$ 个 probes（20~35 次底层模型调用），真实成本约 `¥0.02 ~ ¥0.20`，具备极高的预算安全裕度。
2. **小样本 $N=5$ 下防止赢家诅咒（Winner's Curse）**：
   根据次高斯极大值不等式，在 $N=5$ 的小样本上若执行多轮自适应贪心搜索，期望虚假改进按 $\frac{\sigma}{\sqrt{N}}\sqrt{2\ln(kM)}$ 增长。锁定 $k=1$ 轮受控 PPC 既是成本最优解，也是小样本下保障卡尔·波普尔可证伪性的唯一严密设计。

---

### 审计问题 4：防过拟合（Anti-Overfitting）与诚实阴性结论机制

1. **三道反作弊硬防线（Zero-Qrel Tripwires）**：
   - **防线一**：Proposer 签名物理隔离，严禁传入测试用例或 `expectedKeywords`；
   - **防线二**：跨任务唯一指纹不变量，$\forall i, j \in \{1..5\}, \text{voucherFingerprint}(v, t_i) \equiv \text{voucherFingerprint}(v, t_j)$；
   - **防线三**：Qrel 与测试实体词汇黑名单扫描器（`assertNoQrelLeakage`）。
2. **`ADOPTED` vs `REJECTED_AFTER_REAL_PPC` 的严密数学判据**：
   - 移除旧版 `deltaDurationMs > 5ms` 的荒谬网络抖动短路；
   - 候选 $\mathcal{H}^{(c)}$ 被采纳当且仅当同时满足：(1) 5/5 任务 `usageStatus == KNOWN` 且 `actualCostCny > 0`；(2) 5/5 任务 `qualityPass == true`、无盲评劣败、无非预期 Guard/Cycle 违例；(3) 累计真实成本 $\Delta C_{\text{total}}(c) < 0$（或成本不增且 $\Delta T_{\text{total}}(c) < 0$）；(4) 至少 $3/5$ 任务取得成本或 Token 净改善。
   - 若没有任何候选同时满足上述条件，系统必须诚实输出 `REJECTED_AFTER_REAL_PPC`，保留 `baseline`。
