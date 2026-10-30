# Hermes Harness² 阶段七：Kernel 生产桥接、架构语义收紧与组件真实编排 定向学术与工程研究报告

> **研究阶段**：Stage 7 (Kernel Production Bridge & Real Component Orchestration)  
> **基准分支与提交**：`feat/hermes-harness-stage6` (`4411fa2f`)  
> **目标分支**：先在 `feat/hermes-harness-stage6` 完成阶段 0 语义修正 Commit，再切出 `feat/hermes-harness-stage7` 实施生产桥接  
> **生成模型基线**：DeepSeek 官方活跃模型（`deepseek-flash`）  
> **向量嵌入基线**：阿里千问 Embedding（`text-embedding-v4`，**1024 维**单位超球面 $\mathbb{S}^{1023} \subset \mathbb{R}^{1024},\ \|\mathbf{v}\|_2 = 1$）  
> **运行时环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  

---

## A. 现有架构短板与顶刊级理论动机 (Motivation & Formal Gap)

在阶段六（Commit `4411fa2f`）中，项目已在 `src/main/java/tech/qiantong/qknow/hermes/agent/harness/` 下构建了 10 个核心架构类并通过了 9 项确定性架构契约测试。然而，经过严格的法医级代码审计与顶刊论文审稿标准检验，当前 Stage 6 实现仍存在 **5 大核心断层**，阻碍其从“孤立的架构骨架”走向“顶刊论文可发表、生产主干可验证的认知内核”：

1. **门面伪编排断层（Facade-Only Kernel Gap）**：
   - [`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java) 的 `prepare()` 仅执行了 `capabilityAssembler.assemble()` 与 `harnessAssembler.assembleSystemPrompt()`，而 `execute()` 仅执行了 `execution.apply(prepared)`；
   - `HoareTripleToolGuard`、`LocallyDenseCycleAutomaton`、`ReversibleRingColdBuffer`、`TrajectoryAttributionAnalyzer`、`AlgebraicHarnessMutator` 与 `ParetoFrontierComposer` 仅通过 getter 暴露，从未在 `HermesHarnessKernel` 的生命周期或工具调用链路中被真实编排调用。
2. **流形几何术语与上界证明断层（Extrinsic vs. Intrinsic Fréchet Mean Gap）**：
   - [`HypersphericalManifoldProjector.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/HypersphericalManifoldProjector.java) 中的 `frechetMean(List<double[]>)` 计算的是欧氏均值在单位超球面 $\mathbb{S}^{1023}$ 上的正交投影 $\boldsymbol{\mu}_{\mathrm{ext}} = \bar{\mathbf{v}} / \|\bar{\mathbf{v}}\|_2$ 及平均平方弦距损失 $\mathcal{L}_{\mathrm{chord}} = 2(1 - \|\bar{\mathbf{v}}\|_2)$；
   - 在微分几何与方向统计学（Bhattacharya & Patrangenaru, AoS 2005; Rygaard et al., arXiv 2025）中，该量严格称为**外在弦质心（Extrinsic / Chordal Centroid）**，而非最小化平方测地线距离 $\sum d_{\mathrm{geo}}^2$ 的内蕴黎曼 Fréchet 均值。若方法名与论文表述混用，将面临顶刊审稿人的严厉质疑。
3. **冷备档案无界内存泄漏断层（Unbounded Cold Archive Gap）**：
   - [`ReversibleRingColdBuffer.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/manifold/ReversibleRingColdBuffer.java) 第 48 行的 `archive` 为无界 `LinkedHashMap<String, Stored<T>>`，当 `cold` 环形区溢出或调用 `softDelete` 时无限追加，缺乏 `maxArchiveCapacity` 容量上限与超限拒绝/有界驱逐统计，违背高可用有界内存工程准则。
4. **霍尔工具守卫脱离 Guardrails 阈值断层（Decoupled Guardrails Gap）**：
   - [`HoareTripleToolGuard.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/neurosymbolic/HoareTripleToolGuard.java) 的 `verifyBefore()` 仅检查了 `guardrails == null`，完全未读取 [`GuardrailsSpecificationRecord.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/GuardrailsSpecificationRecord.java) 中的 `maxCycleSteps`、`loopThreshold`、`maxTokenBudget`、`maxCostCny` 四大护栏阈值。
5. **生产编排器零桥接断层（Unbridged AgentOrchestrator Gap）**：
   - [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 尚未与 `HermesHarnessKernel` 建立任何受控桥接，缺乏默认关闭（`OFF`）、旁路影子观测（`SHADOW`）与强制护栏执行（`ENFORCE`）的三态运行机制及单次请求级状态隔离。

### 本阶段唯一待验证核心架构命题 (Locked Hypothesis)
> **“在不改变 `AgentOrchestrator` 公共方法签名与默认生产行为（`hermes.harness.kernel.enabled=false`，默认模式 `OFF`）的前提下，通过阶段 0 收紧 Stage 6 四大语义边界（外在弦质心重命名与测地线夹逼定理、`ReversibleRingColdBuffer` 档案容量硬上限、`HoareTripleToolGuard` 联合校验动作契约与 `GuardrailsSpecificationRecord` 四维系统预算、`HermesHarnessKernel` 真实调用守卫/自动机/冷备/演化流水线），并在阶段 4 引入请求级状态隔离的 `HarnessKernelBridge`（支持 `OFF` / `SHADOW` / `ENFORCE` 三态切换），即可将 Stage 6 认知组件无损桥接进真实 `AgentOrchestrator` 执行主干，且在确定性测试中 100% 验证组件真实调用、违规 fail-close 与跨请求零状态污染。”**

---

## B. Research Ledger 汇总

完整结构化 14 字段记录已存证于同目录 [`49-harness7-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/49-harness7-research.ledger.json)：

| ID | 类型 | 标题 / 仓库 | 年份 / 来源 | 验证状态 | 核心结论与对阶段七的指导意义 |
|---|---|---|---|---|---|
| `HARNESS7-SRC-01` | `paper` | *Simultaneous Optimization of Geodesics and Fréchet Means* | arXiv:2511.04301 (2025) | `VERIFIED` | 明确区分内蕴测地线 Fréchet 均值与外在弦均值；指导将 `frechetMean` 重命名为 `extrinsicCentroid` / `chordalCentroid`，并建立弦距损失与测地线二阶矩的双边夹逼不等式。 |
| `HARNESS7-SRC-02` | `paper` | *GuardAgent: Safeguard LLM Agents via Knowledge-Enabled Reasoning* | ICML / arXiv:2406.09187 (2025) | `VERIFIED` | 证明非侵入式旁路/包装守卫可保持 100% 良性任务准确率（FRA=100%）并实现 >98% 违规拦截；支撑 `HarnessKernelBridge` 的 `OFF / SHADOW / ENFORCE` 非侵入式三态桥接。 |
| `HARNESS7-SRC-03` | `paper` | *ShieldAgent: Shielding Agents via Verifiable Safety Policy Reasoning* | ICML / arXiv:2503.22738 (2025) | `VERIFIED` | 将安全规则划分为动作谓词 $\mathcal{P}_a$ 与系统状态谓词 $\mathcal{P}_s$，并规定短期验证缓存必须按单次轨迹隔离销毁；直接指导 `HoareTripleToolGuard` 联合校验 `Contract` + `GuardrailsSpecificationRecord` 及桥接层的请求级状态隔离。 |
| `HARNESS7-SRC-04` | `paper` | *Large sample theory of intrinsic and extrinsic sample means on manifolds—II* | Annals of Statistics (2005) | `PARTIALLY_VERIFIED` | 严格定义欧氏嵌入下的外在样本均值（Extrinsic Mean）及焦集（Focal Locus，$\|\bar{\mathbf{v}}\|_2 = 0$），为反极点未定义异常提供微分几何依据。 |
| `HARNESS7-SRC-05` | `production-implementation` | `qknow-hermes-core` (`AgentOrchestrator` & Stage 6 Kernel) | Commit `4411fa2f` (2026) | `VERIFIED` | 精确锁定 Stage 6 的 4 处实现短板与 `AgentOrchestrator` 第 472–616 行的无侵入桥接切入点。 |

---

## C. 核心数学建模与机制设计 (Formal Modeling & Mechanism Design)

### C.1 1024 维单位超球面 $\mathbb{S}^{1023}$ 外在弦质心与内蕴测地线二阶矩夹逼定理

设 $\mathbf{v}_1, \dots, \mathbf{v}_k \in \mathbb{S}^{1023} \subset \mathbb{R}^{1024}$ 为阿里千问 `text-embedding-v4` 输出的 $L_2$ 归一化向量（$\|\mathbf{v}_i\|_2 = 1$），记环境欧氏空间中的算术均值为 $\bar{\mathbf{v}} = \frac{1}{k}\sum_{i=1}^k \mathbf{v}_i$。当 $\|\bar{\mathbf{v}}\|_2 > 0$（样本不落入外在焦集）时：

1. **定理 1（外在弦质心最优性与精确弦损失恒等式）**：
   定义任意球面候选代表元 $\mathbf{u} \in \mathbb{S}^{1023}$ 的平均平方弦距损失（Average Squared Chordal Loss）为：
   $$\mathcal{L}_{\mathrm{chord}}(\mathbf{u}) = \frac{1}{k}\sum_{i=1}^k \|\mathbf{u} - \mathbf{v}_i\|_2^2 = \frac{1}{k}\sum_{i=1}^k 2(1 - \mathbf{u}\cdot\mathbf{v}_i) = 2(1 - \mathbf{u}\cdot\bar{\mathbf{v}})$$
   由 Cauchy-Schwarz 不等式，$\mathbf{u}\cdot\bar{\mathbf{v}} \le \|\mathbf{u}\|_2 \|\bar{\mathbf{v}}\|_2 = \|\bar{\mathbf{v}}\|_2$，等号当且仅当 $\mathbf{u} = \boldsymbol{\mu}_{\mathrm{ext}} := \frac{\bar{\mathbf{v}}}{\|\bar{\mathbf{v}}\|_2}$ 时成立。因此，$\boldsymbol{\mu}_{\mathrm{ext}}$ 是 $\mathbb{S}^{1023}$ 上的唯一**外在弦质心（Extrinsic / Chordal Centroid）**，其最小平均平方弦距损失精确等于：
   $$\mathcal{L}_{\mathrm{chord}}(\boldsymbol{\mu}_{\mathrm{ext}}) = 2\left(1 - \|\bar{\mathbf{v}}\|_2\right)$$

2. **引理 1（外在弦损失对内蕴测地线二阶矩的局部夹逼界）**：
   对于任意 $\mathbf{u}, \mathbf{v} \in \mathbb{S}^{1023}$，设测地线距离为 $\theta = d_{\mathrm{geo}}(\mathbf{u}, \mathbf{v}) = \arccos(\mathbf{u}\cdot\mathbf{v}) \in [0, \pi]$，则平方弦距为 $d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v}) = 2(1 - \cos\theta) = 4\sin^2(\theta/2)$。利用不等式 $\sin x \le x \le \frac{\pi}{2}\sin x\ (\forall x \in [0, \pi/2])$，恒有全局双边夹逼：
   $$d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v}) \le d_{\mathrm{geo}}^2(\mathbf{u}, \mathbf{v}) \le \frac{\pi^2}{4} d_{\mathrm{chord}}^2(\mathbf{u}, \mathbf{v})$$
   进一步，若记忆簇位于以 $\boldsymbol{\mu}_{\mathrm{ext}}$ 为中心、最大测地线半径为 $\theta_{\max} \in [0, \pi)$ 的球冠内，记 $\kappa(\theta_{\max}) = \left(\frac{\theta_{\max}/2}{\sin(\theta_{\max}/2)}\right)^2$，则外在弦质心的平均平方测地线失真满足紧上界：
   $$2(1 - \|\bar{\mathbf{v}}\|_2) = \mathcal{L}_{\mathrm{chord}}(\boldsymbol{\mu}_{\mathrm{ext}}) \le \frac{1}{k}\sum_{i=1}^k d_{\mathrm{geo}}^2(\boldsymbol{\mu}_{\mathrm{ext}}, \mathbf{v}_i) \le 2\kappa(\theta_{\max})\left(1 - \|\bar{\mathbf{v}}\|_2\right)$$
   且内蕴 Fréchet 均值 $\boldsymbol{\mu}_{\mathrm{FM}}$ 的目标函数值 $\mathcal{L}_{\mathrm{geo}}(\boldsymbol{\mu}_{\mathrm{FM}}) = \min_{\mathbf{y}\in\mathbb{S}^{1023}} \frac{1}{k}\sum_{i=1}^k d_{\mathrm{geo}}^2(\mathbf{y}, \mathbf{v}_i)$ 自然受限于 $\frac{1}{k}\sum_{i=1}^k d_{\mathrm{geo}}^2(\boldsymbol{\mu}_{\mathrm{ext}}, \mathbf{v}_i)$。这从数学上严格厘清了 `extrinsicCentroid` 与内蕴 Fréchet 均值的关系，既无需高昂的 $\mathcal{O}(NTd^3)$ 迭代，又具备严密的流形几何保证。

### C.2 有界三级冷备环形缓冲区（Bounded Reversible Ring Cold Buffer）
为同时满足“软删除可逆恢复”与“总内存严格有界”，将 `ReversibleRingColdBuffer` 建模为四元组容量约束 $(C_{\mathrm{hot}}, C_{\mathrm{warm}}, C_{\mathrm{cold}}, C_{\mathrm{archive}})$：
- 活跃总内存上界恒满足：$|Q_{\mathrm{hot}}| \le C_{\mathrm{hot}},\ |Q_{\mathrm{warm}}| \le C_{\mathrm{warm}},\ |M_{\mathrm{cold}}| \le C_{\mathrm{cold}},\ |M_{\mathrm{archive}}| \le C_{\mathrm{archive}}$；
- 当条目从 `cold` 溢出进入 `archive` 或通过 `softDelete` 转入 `archive`，且 $|M_{\mathrm{archive}}| == C_{\mathrm{archive}}$ 时，支持可配置的溢出策略 `ArchiveOverflowPolicy`：
  - `REJECT_NEW_ARCHIVE`（默认 fail-close 策略：拒绝写入 `archive`，递增 `rejectedArchive` 计数器，并在 `softDelete` 时返回 `false` 或保持在有界状态）；
  - 或在 `cold` 溢出至已满 `archive` 时，不扩充 `archive` 并递增 `rejectedArchive`，从而保证 `archive.size() <= maxArchiveCapacity` 为绝对不变量。
- `Statistics` 扩展为：
  `record Statistics(int hot, int warm, int cold, int archived, int archiveCapacity, long softDeleted, long restored, long rejectedArchive)`。

### C.3 联合动作契约与系统护栏的霍尔三元组 $\langle \mathcal{P}_a \wedge \mathcal{P}_s \rangle\ C\ \langle \mathcal{Q} \rangle$
依据 ShieldAgent（ICML 2025）的谓词空间分解，将 `HoareTripleToolGuard.verifyBefore` 的前置条件形式化为动作谓词 $\mathcal{P}_a(\mathrm{tool}, \mathrm{args}, \mathrm{Contract})$ 与系统状态谓词 $\mathcal{P}_s(\sigma, \mathrm{Guardrails})$ 的合取：
$$\mathcal{P}_{\mathrm{total}} = \underbrace{\mathcal{P}_{\mathrm{perm}} \wedge \mathcal{P}_{\mathrm{schema}} \wedge \mathcal{P}_{\mathrm{boundary}}}_{\mathcal{P}_a\ (\text{Tool Contract})} \wedge \underbrace{\left(\sigma.\mathrm{step} \le G.\mathrm{maxCycleSteps}\right) \wedge \left(\sigma.\mathrm{loopFreq} < G.\mathrm{loopThreshold}\right) \wedge \left(\sigma.\mathrm{tokens} \le G.\mathrm{maxTokenBudget}\right) \wedge \left(\sigma.\mathrm{costCny} \le G.\mathrm{maxCostCny}\right)}_{\mathcal{P}_s\ (\text{Guardrails Specification})}$$
其中运行期状态向量定义为不可变 Record：
`ExecutionState(int currentStep, int currentLoopFrequency, long accumulatedTokens, double accumulatedCostCny)`。
任何一项谓词违约立即产生精确分类的 `Violation`（新增 `GUARDRAIL_STEP_LIMIT`, `GUARDRAIL_LOOP_THRESHOLD`, `GUARDRAIL_TOKEN_BUDGET`, `GUARDRAIL_COST_BUDGET`）与可解释的 `Counterexample`。
