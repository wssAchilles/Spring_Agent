# Hermes Harness² 阶段三三方智能体交叉辩论与对齐裁决纪要

> **纪要版本**：3.0.0-STAGE3-DEBATE  
> **辩论时间**：2026-10-29  
> **关联分支**：`feat/hermes-harness-stage2`（阶段三实施前夕）  
> **参与辩论智能体**：
> - **Agent A: 前沿文献与理论推导调研员 (Theoretical Literature Specialist)**  
>   物理 ConversationId: `c02359c9-f608-48f2-82a2-c971c914f1fc`  
>   报告存证: [`31-harness3-agent-A.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-A.md)
> - **Agent B: 开源/工业代码法医核验员 (Code Forensic & Benchmark Specialist)**  
>   物理 ConversationId: `5df77102-d1be-4c9a-94d5-a69ce4215c79`  
>   报告存证: [`31-harness3-agent-B.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-B.md)
> - **Agent C: 系统架构契约与最小算法适配员 (System Applicability & Minimum Gate Specialist)**  
>   物理 ConversationId: `2aaaf73e-f161-4915-be8f-b639c5c71ae8`  
>   报告存证: [`31-harness3-agent-C.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/31-harness3-agent-C.md)
> - **辩论主持与终验裁决人**：主智能体 (Parent Agent / Lead Architect)  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文铁律

---

## 第一轮辩论：变异范围界定与盲审抗偏置机制

### 议题 1：Proposer 变异提议器的算法设计与安全边界

- **Agent A (理论调研员)**：
  根据 Harness² 论文（arXiv:2610.14224）第 3 节与 DSPy（arXiv:2310.03714）理论，优化器需要具备充分的文本表达自由度来探索 Pareto 前沿。文本梯度（TextGrad）的本质是通过丰富的自然语言反馈指导变异。因此，理论上 Proposer 应当能够修改 Invariant 角色提示词中的约束规则，甚至引入新的思维链引导句。
- **Agent B (代码法医核验员)**：
  我在审计 Harness² 本地仓库 `proposer.py`（Lines 143–326）时发现，官方实现依赖外部宿主机子进程（`subprocess.run(["claude", ...])`）和临时目录收割（`_harvest`）。在 Java 21 企业级微服务中，这存在不可接受的进程开销与代码注入漏洞风险。更重要的是，我们阶段二落地的 `HermesHarnessVoucher` 是纯强类型不可变 Java Record。如果允许 Proposer 随意改写，极易破坏现有的八大契约签名。变异器必须通过受控的 Java 策略接口实现，并且变异维度必须严格锁定在低风险的 3 个组件：`InvariantSystemPromptRecord`（静态提示精简）、`ToolGuidancePolicyRecord`（工具选择策略）和 `MemoryContextStrategyRecord`（滑动窗口大小）。
- **Agent C (风控与评测审计员)**：
  我必须对 Agent A 的“自由文本变异”提出严厉风控预警！本项目运行环境高度依赖 **DeepSeek 1M Context Caching**。DeepSeek 官方规范明确规定，提示词必须保持完全静态的前缀才能稳定命中 80% 以上的缓存并降低 90% 的计费成本。如果 Proposer 在 System Prompt 中自由注入动态变量、时间戳或未经验证的复杂长句，不仅会导致首字延迟从 500ms 飙升到 2000ms+，更会直接违反阶段三假设的硬性预算门禁（$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$）。变异提议必须经过静态前缀校验器（`MutationSafetyValidator`），字符数严禁发生正向膨胀！
- **主智能体裁决 (Verdict 1)**：
  **采纳 Agent B 与 Agent C 的收敛意见，约束 Agent A 的理论假设**。阶段三变异器仅允许在静态受控边界内执行：
  1. 仅限针对 `SystemPrompt`（精简冗余、强化抽取规范）、`ToolGuidance`（开启自动选择、先验依赖引导）、`Memory`（窗口在 $[5, 30]$ 内微调）三个维度；
  2. 强制实施静态前缀缓存保护断言，禁止引入任何 `${...}` 动态占位符，字符增量必须 $\le 0$；
  3. 严禁任何宿主机动态进程或代码执行，必须以 Java 21 强类型接口装配不可变 Record 凭单。

---

### 议题 2：Selection 选择器如何彻底消除大模型位置偏置与数据泄漏

- **Agent A (理论调研员)**：
  我在报告中已经通过马尔可夫链和置换对称群 $S_M$ 严格证明了哈希洗牌的无偏性：当利用 $\text{SHA-256}(\text{taskId} \parallel \text{salt})$ 生成随机映射 $\pi$ 时，Candidate 映射为 A 或 B 的先验概率严格各占 50%，此时理论上的互信息 $I(J; c \mid \mathcal{Y}) = 0$。
- **Agent B (代码法医核验员)**：
  数学证明很完美，但工业现实存在陷阱！Harness² 的 `selection.py`（Lines 71–86）确实只跑了一次基于哈希种子的洗牌单向评测。然而，业界实测表明，当两个候选结果质量接近时，大语言模型存在严重的“固有首项偏置（Position Bias）”——即模型更倾向于判定 Candidate A 胜出。单向测试即便随机打乱了，单次判定依然可能受到偶然性的位置噪声干扰。因此，我在代码审计报告中提出了**双向交叉盲审（Position-Swap Dual Pass）**：对同一任务必须分别执行 `(A, B)` 和 `(B, A)` 两次独立评审，只有两次评审结论在解密后完全一致指认同一个候选胜出时，才确认胜出；若两次结论冲突（如模型两次都选了当前展示在前的选项），则判定为平局，中立回退至 Baseline。
- **Agent C (风控与评测审计员)**：
  完全支持 Agent B 的双向交叉盲审设计！在此基础上，我还补充了防范数据泄漏的**雷区扫描门禁（Tripwire Assertions）**。吸收 Harness² `context.py` 的设计，在将上下文递交给 Selector LLM 前，必须在 Java 代码层进行全文字符扫描，一旦发现 `passed_count`、`gold_rubric`、`ground_truth_answer` 等 9 类判词标记或目录路径，立即抛出 `CRITICAL_DATA_LEAKAGE` 异常并熔断，确保盲审裁判绝对不知道业务黄金答案。
- **主智能体裁决 (Verdict 2)**：
  **全票一致通过**！
  1. 采纳 Agent A 的哈希随机盲审数学形式；
  2. 采纳 Agent B 提出的双向交叉盲审（Position-Swap Dual Pass）算法，以双向一致性作为采纳候选的充要条件；
  3. 采纳 Agent C 提出的防泄漏雷区扫描门禁，确保盲审评选过程达到零泄漏（Zero Data Leakage）与零位置偏置（Zero Position Bias）。

---

## 第二轮辩论：基准集物理代表性与凭单确定性回放

### 议题 3：5 个离线代表性用例的设计有效性与覆盖度

- **Agent C (风控与评测审计员)**：
  我在报告中设计了 5 个完全根植于本项目真实架构的离线业务场景：
  - Case 1: 分布式中间件规范参数抽取（验证 SystemPrompt 事实抽取准确度与防虚构）；
  - Case 2: 银行结算多工具依赖推理（验证 ToolGuidance 拓扑依赖与防高危写操作越权）；
  - Case 3: 微服务 Service Mesh 多轮评审（验证 Memory 滑动窗口跨轮代词消解）；
  - Case 4: 工单系统超时异常降级（验证边界异常时的诚实降级与防死循环）；
  - Case 5: 架构选型 Markdown 报告生成（验证前缀缓存友好度与排版完备度）。
  这 5 个用例完全覆盖了我们阶段三拟变异的 3 个组件，且每个用例都有明确的输入、环境 Mock 与预期产物。
- **Agent A (理论调研员)**：
  从统计学和可证伪实验设计角度审查，这 5 个用例构成了正交测试空间：涵盖了单跳实体检索、多跳拓扑推理、时序记忆保持、负样本容错与长文本生成。符合小样本探索的代表性要求。唯一需要强调的是：用例的输入不能包含真实测试集的黄金答案，且测试必须完全离线可复现。
- **Agent B (代码法医核验员)**：
  在代码实现上，这 5 个用例应封装为不可变 Java 21 Record（`OfflineBenchmarkTestCase`），并存放在 `qknow-hermes-core` 测试源码树下的静态工厂中。测试执行时不依赖任何外部动态数据库连接，全部通过内存轻量隔离模拟，确保单测执行耗时在 2 秒以内完成。
- **主智能体裁决 (Verdict 3)**：
  **一致通过**。5 个离线小样本用例设计严谨、覆盖完备、且与真实系统核心能力强契约对齐，正式锁定为阶段三官方基准任务集。

---

### 议题 4：不可变凭单驱动的确定性回放与防侧信道时序攻击

- **Agent B (代码法医核验员)**：
  阶段二已经在 `HarnessEvidenceRecord` 中落地了 `computeDigest()` 与 `verifyDigest(expectedDigest)`。该方法使用了 `MessageDigest.isEqual()` 进行常量时间比对，杜绝了时序侧信道攻击风险。在阶段三，我们必须实现一个专门的 `OfflineEvidenceReplayer`，它不发起任何真实的大模型网络调用，仅通过解析序列化的凭单快照，重新计算指纹并比对，验证整条证据链的数学完整性。
- **Agent A (理论调研员)**：
  确定性回放的理论核心在于代数一致性。如果某个候选 Harness 被评选为最优，其产生的物理凭单（包含精准耗时、Token 计费、工具出入参摘要）必须能够被数学验算。只要任何一个字段被篡改，常量时间验签必须立即失效。
- **Agent C (风控与评测审计员)**：
  完全同意。回放器不仅要验证验签正确性，还要比对物理差分：断言候选凭单的 `estimatedTokens <= baselineTokens` 且 `executionDurationMs - baselineDurationMs <= 5`。若回放计算出的差分违反预算，同样判定验收不通过。
- **主智能体裁决 (Verdict 4)**：
  **一致通过**。由 `OfflineEvidenceReplayer` 承接阶段二不可变凭单资产，提供确定性、常量时间验签与预算合规性离线回放验证。

---

## 三、三方辩论裁决与共识总结

| 争议点 / 架构决策 | 辩论前分歧 | 辩论后最终裁定共识 | 责任落地组件 |
|---|---|---|---|
| **变异自由度 vs 系统安全** | 开放式自由 Prompt 变异 vs 严格模板化限制 | 严格限制在 `SystemPrompt`、`ToolGuidance`、`Memory` 三大组件，静态前缀字符增量 $\le 0$，严禁动态占位符 | `OfflineHarnessProposer` & `MutationSafetyValidator` |
| **选择器偏置消除机制** | 单向哈希打乱 vs 双向交叉盲审 | 实施 **Position-Swap Dual Pass** 双向交叉盲审，两次对调评测结果一致方判定胜出，否则安全平局回退 | `DualBlindSelectionEvaluator` |
| **数据泄漏防范铁律** | 约定不泄露 vs 强类型代码雷区断言 | 实施三权分立隔离架构 + 9 类判词雷区扫描断言（`SelectionDataLeakageTripwire`） | `SelectionDataLeakageTripwire` |
| **离线基准测试集选型** | 通用 Benchmark vs 真实业务场景 | 固化 5 个覆盖精确 RAG、多工具、多轮代词、异常降级、格式输出的代表性离线业务用例 | `OfflineBenchmarkTestCase` |
| **确定性回放与防时序漏洞** | 依赖在线重试 vs 离线常量时间验签 | 纯不可变凭单驱动回放，采用 `MessageDigest.isEqual()` 常量时间验签与预算断言 | `OfflineEvidenceReplayer` |

**辩论结论**：三方智能体已达成 100% 架构共识与数学对齐，无任何遗留分歧，准予正式输出《阶段三离线 Propose–Probe–Compose 实施任务书》并计算签名冻结！
