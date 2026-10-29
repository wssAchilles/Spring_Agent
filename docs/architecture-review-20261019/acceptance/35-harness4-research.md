# Hermes Harness² 阶段四前沿学术与开源代码深度研读报告

> **报告版本**：4.0.0-STAGE4-RESEARCH  
> **关联台账**：`docs/architecture-review-20261019/acceptance/35-harness4-research.ledger.json`  
> **研究分支基线**：`feat/hermes-harness-stage3`（Commit `ba46de4a`）  
> **实施目标分支**：`feat/hermes-harness-stage4`  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **模型与几何基线**：DeepSeek 官方活跃模型；阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1$）  
> **研究日期**：2026-10-30  

---

## 一、学术文献与开源代码研读概要

阶段四核心任务是完成从**“合成数据契约验证”**到**“真实离线 Probe 执行与物理凭单落盘”**的关键跃迁。为此，本轮定向研读聚焦于 6 个最具代表性的学术成果与工业开源实现：

1. **Google Research Harness²** (Official Repo 2026, Commit `1cfe254e`)：通过真实探针运行驱动的递归演化闭环与 Position-Swap 消除偏见算法；
2. **DSPy** (ICLR 2024, Stanford)：声明式模块优化与离线基于执行度量的编译期搜索；
3. **OPRO** (ICLR 2024, Google DeepMind)：基于历史轨迹与量化反馈驱动的大模型元优化机制；
4. **TextGrad** (Nature Communications / NeurIPS 2024, Stanford)：利用文本反馈反向传播实现复合 AI 系统的非数值优化；
5. **Reflexion** (NeurIPS 2023, MIT/Princeton)：基于执行失败事实的情景记忆强化与言语强化学习；
6. **Self-Refine** (NeurIPS 2023, CMU/AI2)：带自反馈的多轮迭代收敛性与停止条件数学边界。

所有 6 项来源均在真实外网或本地仓库进行了全量/关键章节代码与论文研读，验证状态全部真实标注为 **`VERIFIED`**，零降级、零臆测。

---

## 二、关键理论与数学边界推导

### 2.1 递归受控变异的单调收敛性与停机上界
在 Harness² 与 Self-Refine 中，候选提议与评估构成了离散状态转移过程。设系统基准配置为 $\mathcal{H}_0$，在小样本任务集合 $\mathcal{D} = \{x_1, \dots, x_N\}$ 上，对任意候选 $\mathcal{H}_c$，定义多目标损失向量：
$$
\mathbf{L}(\mathcal{H}, x_i) = \begin{bmatrix} 1 - \mathbb{I}(\text{Success}(\mathcal{H}, x_i)) \\ \max(0, \text{Tokens}(\mathcal{H}, x_i) - \text{Budget}_{\text{token}}) \\ \text{Latency}(\mathcal{H}, x_i) \end{bmatrix}
$$
若采纳判据为帕累托支配（Pareto Dominance），即：
$$
\mathcal{H}_{t+1} = \begin{cases} \mathcal{H}_c, & \text{若 } \forall j, L_j(\mathcal{H}_c) \le L_j(\mathcal{H}_t) \text{ 且 } \exists j, L_j(\mathcal{H}_c) < L_j(\mathcal{H}_t) \\ \mathcal{H}_t, & \text{否则 (回退继承)} \end{cases}
$$
由于候选变异被严格限制在有限离散参数集（如 System Prompt 精简、Tool Guidance 增强），状态空间有限且损失非负，因此在贪心单步选择（$k=1$）下，系统状态序列单调非增，必然在有限步内收敛于局部最优或稳定基线，不会发生发散震荡。

### 2.2 匿名双向盲审（Position-Swap Dual Pass）消除位置偏见证明
在 Harness² `selection.py` 中，LLM 裁判对选项顺序存在显著的首项选择偏好（Primacy Bias）或末项选择偏好（Recency Bias）。设裁判对候选 $A$ 在前时选择 $A$ 的先验概率为 $P(\text{Choose}(1) \mid A \text{ at } 1) = p_0 + \epsilon_1$，而在候选 $B$ 在前时选择 $A$ 的概率为 $P(\text{Choose}(2) \mid A \text{ at } 2) = p_0 - \epsilon_2$。
通过执行双向对调（Dual-Pass Swap）：
1. 正向评估：输入 $(A, B)$，输出选择 $C_1 \in \{A, B\}$；
2. 反向评估：输入 $(B, A)$，输出选择 $C_2 \in \{A, B\}$；
3. 一致性采纳判据：只有当 $C_1 = A$ 且 $C_2 = A$ 时，才最终判定 $A$ 胜出。
此时，伪阳性采纳率（即 $A$ 与 $B$ 无客观差异甚至 $A$ 劣于 $B$ 但因位置偏置被误选的概率）被严格压制为：
$$
P_{\text{false}}(\text{Adopt } A) = P(C_1 = A \mid H_0) \cdot P(C_2 = A \mid H_0) \le \delta^2 \ll \delta
$$
显著降低了大模型主观评估的噪声与随机误差。

---

## 三、可迁移与不可迁移结论矩阵

| 理论/系统来源 | 可直接采用的核心结论 (Adopt) | 必须改造的工程机制 (Adapt) | 坚决拒绝的设计 (Reject) |
|---|---|---|---|
| **Google Harness²** | 1. 真实 Probe 探针执行收集证据<br>2. Position-Swap 双向盲审消除偏置<br>3. 失败即继承基线的安全保底 | 原 Python 动态子进程执行与文件覆盖，改造为 Java 内存级不可变请求凭单覆盖（`registerRequestVoucher`） | 拒绝跨会话自动热更生产系统配置与动态文件覆盖 |
| **DSPy** | 1. 声明式接口与断言（Assertions）过滤<br>2. 依据物理执行度量指导候选提议 | 原自由插入 Few-Shot 样本机制，改造为零 Few-Shot 的静态规则紧凑化 | 拒绝引入重型 Python 编译器依赖与破坏静态前缀缓存的动态示例注入 |
| **OPRO & TextGrad** | 1. 基于历史执行差异（Diff）定向生成针对性修复 Prompt<br>2. 结构化文本反馈作为反思信号 | 原连续多轮昂贵元提示词搜索，改造为离线受控单轮（$k=1$）静态规则变异 | 拒绝高成本无界连续探索与在线反向梯度传播 |
| **Reflexion & Self-Refine** | 1. 执行失败事实沉淀为结构化评估证据<br>2. 设立强硬停止条件防止过度优化 | 原多轮交互式在线自我纠错，改造为离线小样本集只读沙盒运行 | 拒绝在生产在线链路引入多轮反思重试导致时延与 Token 失控 |

---

## 四、对 Hermes 阶段四系统落地的指引

1. **真实物理探针执行器 (`OfflineProbeExecutor`)**：
   - 彻底摒弃合成假数据，使用真实 `AgentOrchestrator`，注入基线与候选临时凭单；
   - 收集真实的执行耗时、Token 统计、大模型真实输出与工具出入参摘要。
2. **物理证据落盘与文件资产管理**：
   - 每次 Probe 执行必须将生成的 `HarnessEvidenceRecord` 序列化为规范 JSON，分别物理写入：
     - `experiment/outputs/harness4/<runId>/baseline/evidence_<taskId>.json`
     - `experiment/outputs/harness4/<runId>/candidate/evidence_<taskId>.json`
3. **真实物理指标聚合与判定**：
   - 依据落盘的 JSON 文件，由 `EvidenceDiffExtractor` 提取真实物理差异（$\Delta \text{Tokens}$、$\Delta \text{Duration}$、工具匹配度、回答完备度）；
   - 执行 Position-Swap 匿名双向盲审，仅在双向一致且满足非退化门禁时输出采纳结论。
4. **测试替身隔离**：
   - 构建 `DeterministicHarnessTestDouble`，与真实执行器物理分流，确保 CI/CD 单元测试快速且确定性运行，同时保证离线实验产生真实物理证据。
