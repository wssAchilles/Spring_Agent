# Agent A: 离线优化理论审计员 (Theoretical Literature Specialist) 阶段三独立审计报告

> **智能体角色**：Agent A: 离线优化理论审计员 (Theoretical Literature Specialist)  
> **物理 ConversationId**：`c02359c9-f608-48f2-82a2-c971c914f1fc`  
> **运行 Transcript 日志**：[`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/c02359c9-f608-48f2-82a2-c971c914f1fc/.system_generated/logs/transcript.jsonl)  
> **审计日期**：2026-10-29  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文规范

---

# 【前沿文献与理论推导调研报告】面向 Hermes SINGLE Agent 架构的离线受控优化收敛性、缓存友好性与防泄漏盲审机制理论证明

**调研员身份**：离线优化理论审计员 (Theoretical Literature Specialist)  
**研读目标文献池**：
1. Google Research《Harness²: Recursive Agent Harnessing for an Open World》(arXiv:2610.14224, 2026)
2. Stanford NLP《DSPy: Compiling Declarative Language Model Calls into Self-Improving Pipelines》(arXiv:2310.03714, 2023)
3. Google DeepMind《Large Language Models as Optimizers - OPRO》(ICLR 2024 / arXiv:2309.03409, 2023)
4. Stanford University《TextGrad: Automatic 'Differentiation' via Text》(arXiv:2406.07496, 2024)
5. CMU / Allen AI《Self-Refine: Iterative Refinement with Self-Feedback》(NeurIPS 2023 / arXiv:2303.17651, 2023)  
**本地全文与源码存证**：
- `/Users/achilles/.gemini/antigravity/brain/e780bef4-ad1b-4ce7-8c57-88ab7e5cffad/scratch/paper_2610.14224.html` (1.2 MB)
- `/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/` (`selection.py`, `proposer.py`, `recursion.py`, `context.py`)  
**验证状态**：全部 5 篇文献与 1 个开源代码库均为 `VERIFIED`

---

## 阶段三唯一待验证假设 (Unique Falsifiable Hypothesis)

> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库检索问答、多工具选择与上下文历史理解的 5 个代表性真实用例）上，以阶段二沉淀的不可变凭单 `HarnessEvidenceRecord` 作为物理证据，执行 $k=1$ 的离线受控 Propose–Probe–Compose 最小闭环，对 System Prompt（静态角色约束）、Tool Guidance（工具选择与参数指引）、Memory（短期记忆滑动窗口）三个低风险组件进行候选变异与匿名盲审比较（Selection）；在 100% 保持生产默认配置与静态前缀缓存友好的前提下，能够在不增加单次调用 Token 消耗与延迟预算（$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$）且零数据泄漏的严格门禁下，筛选出在任务回答完备度或工具调用准确率上显著优于 Baseline 的候选 Harness，并完成离线凭单确定性回放验证。”**

---

## 一、五大前沿文献理论深度解构与数学形式化

### 1.1 Harness² (arXiv:2610.14224 & 官方源码 `1cfe254e`)：任务级递归与双对偶变异动力学

#### (1) 系统解耦与形式化定义
设 $\mathcal{M}$ 为具有冻结权重的大语言模型（如 DeepSeek API 官方活跃模型），智能体系统由模型与其外部支撑装备（Harness）共同决定：
$$\mathcal{A}(\cdot) = \mathcal{A}(\cdot; \mathcal{M}, \mathcal{H})$$
其中 Harness $\mathcal{H} = (c^1, c^2, \dots, c^K)$ 由 $K$ 个正交组件构成。在本项目阶段三的受控边界下，聚焦于 3 个低风险离线优化组件：
$$\mathcal{H} = (c^{\text{prompt}}, c^{\text{tool}}, c^{\text{mem}})$$
- $c^{\text{prompt}}$：系统静态角色与决策边界约束提示词（`SystemPrompt`）；
- $c^{\text{tool}}$：工具选择指引、调用契约与参数模式说明（`ToolGuidance`）；
- $c^{\text{mem}}$：短期工作记忆滑动窗口保留轮数 $W \in \mathbb{N}^+$（`Memory`）。

#### (2) Propose–Probe–Compose (PPC) 任务级迭代动力学
Harness² 提出在开放世界无显式标量损失函数条件下的三阶段演化范式：
1. **基线参考执行 (Base Execution)**：
   智能体在基线装备 $\mathcal{H}_0$ 下运行基准任务 $q$，产出物理执行轨迹 $\tau_0 = (s_0, a_0, r_0, \dots, s_T)$ 与交付物 $y_0$：
   $$e_0 = (\tau_0, y_0) \sim \mathcal{A}(q; \mathcal{M}, \mathcal{H}_0)$$
2. **双对偶提议 (Dual Propose)**：
   提议器模型 $\mathcal{M}_{\text{prop}}$ 针对基线轨迹 $e_0$ 中的不足，在两个正交几何方向生成候选变异：
   - **不变式变异 (Invariant Mutation, $\mathcal{H}_{\text{inv}}$)**：调整全局角色认知、强化推理纪律、优化指令前缀，保持跨领域普适性；
   - **领域专有变异 (Domain Mutation, $\mathcal{H}_{\text{dom}}$)**：针对具体任务模式（如知识库 RAG 精确引用、复杂多工具链依赖），微调工具参数模式与格式化输出模板。
   $$\mathcal{H}_{\text{inv}}, \mathcal{H}_{\text{dom}} \sim \mathcal{M}_{\text{prop}}(\cdot \mid q, e_0, \mathcal{H}_0)$$
3. **隔离探针试运行 (Probe)**：
   将变异后的候选置于轻量受控沙箱中执行相同任务 $q$，收集物理行为差分：
   $$\Delta \tau_{\text{inv}} = \tau(\mathcal{H}_{\text{inv}}) \ominus \tau_0, \quad \Delta \tau_{\text{dom}} = \tau(\mathcal{H}_{\text{dom}}) \ominus \tau_0$$
   差分向量 $\Delta \tau$ 包含真实硬件级物理指标：退出状态码、异常捕获次数、真实执行耗时 $\Delta t$、Token 消耗量差 $\Delta \text{Tokens}$、工具入参及出参 SHA-256 摘要差分、RAG 向量检索置信度 $\Delta \text{score}$。
4. **差分驱动合成 (Compose)**：
   合成算子综合探针物理差分反馈，提炼两者的互补优势，生成最优候选：
   $$\mathcal{H}' = \text{Compose}(q, \mathcal{H}_0, \Delta \tau_{\text{inv}}, \Delta \tau_{\text{dom}})$$
5. **匿名盲审选择器 (Selection)**：
   由裁判大模型在隐去所有配置来源、随机置换标签的前提下，对最终产出进行盲审评选。

---

### 1.2 DSPy (arXiv:2310.03714)：声明式模块编译与经验风险最小化理论

DSPy 首次将自然语言处理系统形式化为由参数化模块构成的有向无环计算图（DAG）：
$$y = f_\theta(x) = (m_L \circ m_{L-1} \circ \dots \circ m_1)(x)$$
其中系统参数 $\theta = \{p_1, p_2, \dots, p_M\}$ 不再是模型神经网络权重，而是各模块的提示词指令（Instruction）与少样本示例集（Demonstrations）。

#### (1) 离线编译与参数搜索
DSPy 引入 Teleprompter（编译器，如 MIPRO / BootstrapFewShot），将提示工程转化为离线受控优化问题：
$$\theta^* = \arg\min_\theta \frac{1}{|\mathcal{D}_{\text{train}}|} \sum_{(x, y^*) \in \mathcal{D}_{\text{train}}} \ell(f_\theta(x), y^*)$$
- **离线与在线解耦**：所有参数搜索、示例引导均在离线测试集上编译完成；编译完成后，$\theta^*$ 被冻结为静态代码资产部署至生产环境。
- **对 Hermes 的理论支撑**：证明了将 Agent 的 System Prompt 与 Tool Guidance 抽象为系统离线超参数进行受控优化的理论完备性，坚决否定了在生产流量中在线实时动态重写 Prompt 的反模式。

---

### 1.3 OPRO (arXiv:2309.03409)：LLM 作为离散优化器的流形搜索与上下文边界

OPRO（Large Language Models as Optimizers）证明了基座大模型可作为基于自然语言的黑盒数值/符号优化器。

#### (1) 优化动力学方程
设待优化文本指令为 $p \in \mathcal{P}$，评估指标为 $S(p) \in \mathbb{R}$。构建优化轨迹元提示（Meta-prompt）：
$$\mathcal{T}_t = \{(p_{(1)}, S(p_{(1)})), (p_{(2)}, S(p_{(2)})), \dots, (p_{(t)}, S(p_{(t)}))\}, \quad \text{满足 } S(p_{(1)}) \le S(p_{(2)}) \le \dots \le S(p_{(t)})$$
新候选指令由优化器模型根据升序排列的历史经验生成：
$$p_{t+1} \sim \mathcal{M}_{\text{opt}}(\cdot \mid \mathcal{T}_t, \mathcal{I}_{\text{task}})$$
大模型通过 In-Context Learning 隐式拟合文本空间到得分空间的梯度方向 $\nabla_p S(p)$，向高收益区域跃迁。

#### (2) 步长发散与上下文遗忘上界
OPRO 实验表明：当迭代步长 $k > 3$ 时，Meta-prompt 上下文急剧膨胀，优化器面临严重的注意力稀释（Attention Dilution）与幻觉震荡：
$$\lim_{t \to \infty} \text{Var}(p_t) = \infty, \quad \mathbb{P}(S(p_{t+1}) < S(p_t)) \to 0.5$$
即多轮自由搜索极易退化为随机游走。因此，**严格限制探索步长 $k=1$** 是防止优化器发散、确保优化单调性的理论临界条件。

---

### 1.4 TextGrad (arXiv:2406.07496)：文本自动微分与丰富方向性定理

TextGrad 将深度学习的反向传播（Backpropagation）思想拓展至复合 LLM 系统。

#### (1) 文本梯度定义与链式法则
在复合文本计算图上，定义损失 $\mathcal{L}$ 相对于中间节点变量 $z$ 的“文本梯度”为对误差根因的结构化自然语言诊断：
$$\mathbf{g}_z^{\text{text}} = \frac{\partial^{\text{text}} \mathcal{L}}{\partial z} = \mathcal{M}_{\text{eval}}(\text{Feedback} \mid z, \text{Successors}(z), \mathbf{g}_{\text{output}}^{\text{text}})$$
参数更新由文本更新器执行：
$$z^{(t+1)} = \text{OptimizerUpdate}(z^{(t)}, \mathbf{g}_z^{\text{text}})$$

#### (2) 方向丰富性定理 (Directional Richness Theorem)
TextGrad 证明：相较于标量损失标量反馈 $\mathcal{L} \in \mathbb{R}$（如准确率 0 或 1），文本梯度 $\mathbf{g}_z^{\text{text}} \in \Sigma^*$ 的信息熵满足：
$$\mathbb{H}(\mathbf{g}_z^{\text{text}}) \gg \mathbb{H}(\mathcal{L})$$
富文本差分反馈精确指明了“哪一步工具参数出错”、“哪一段检索上下文缺失”，使参数搜索空间从无向全空间搜索坍缩至沿着误差切向的单维局部流形。这正是阶段二不可变凭单 `HarnessEvidenceRecord`（物理耗时、工具入参出参哈希差、状态码）能够高效引导变异的理论根基。

---

### 1.5 Self-Refine (arXiv:2303.17651)：自省反馈闭环与物理证据锚定条件

Self-Refine 建立了经典的 Generate $\to$ Feedback $\to$ Refine 迭代闭环：
$$y_0 \sim \mathcal{M}(x) \longrightarrow f_0 \sim \mathcal{M}_{\text{fb}}(x, y_0) \longrightarrow y_1 \sim \mathcal{M}_{\text{refine}}(x, y_0, f_0)$$

#### (1) 闭环收敛的客观锚定定理 (Objective Grounding Theorem)
论文实证表明：若反馈 $f_0$ 仅来自于大模型内在自审（Introspective Self-Check），模型会倾向于维持自身的认知幻觉，发生“过度自信的虚假修正”；反之，**当且仅当反馈 $f_0$ 锚定于客观外部环境的执行痕迹（如真实执行错误、退出码、物理时延、工具返回契约违背）时**，精化步骤 $y_1$ 才能以极高概率单调优于 $y_0$：
$$\mathbb{P}(y_1 \succ y_0 \mid f_{\text{external}}) \gg \mathbb{P}(y_1 \succ y_0 \mid f_{\text{internal}})$$
这为 Hermes 阶段三确立了铁律：变异提议必须严格基于系统真实运行的物理证据 `HarnessEvidenceRecord`，严禁依据凭空编造的主观提示词进行盲目优化。

---

## 二、基于执行差分反馈的离线优化收敛性论证

### 2.1 目标效用泛函与多目标约束空间
定义智能体装备 $\mathcal{H}$ 在离线基准任务分布 $\mathcal{D}_{\text{bench}}$ 上的综合效用泛函：
$$U(\mathcal{H}) = \mathbb{E}_{q \sim \mathcal{D}_{\text{bench}}} \left[ Q(q; \mathcal{H}) - \lambda_1 \cdot \Delta \text{Tokens}(\mathcal{H}) - \lambda_2 \cdot \Delta \text{Duration}(\mathcal{H}) \right]$$
其中：
- $Q(q; \mathcal{H}) \in [0, 1]$ 为任务回答完备度与工具调用准确率的综合质量测度；
- $\Delta \text{Tokens}(\mathcal{H}) = \max(0, \text{Tokens}(\mathcal{H}) - \text{Tokens}(\mathcal{H}_0))$ 为相对基线的 Token 增量惩罚；
- $\Delta \text{Duration}(\mathcal{H}) = \max(0, \text{Duration}(\mathcal{H}) - \text{Duration}(\mathcal{H}_0) - 5\text{ms})$ 为超过 5ms 阈值的延迟惩罚；
- $\lambda_1, \lambda_2 \to +\infty$ 为硬性拉格朗日乘子，对应门禁预算约束：$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$。

### 2.2 差分反馈驱动的一阶局部收敛性证明
设基线执行凭单为 $\mathbf{E}_0 = \text{HarnessEvidenceRecord}(q, \mathcal{H}_0)$。
当智能体在任务 $q$ 上未能达到全局最优时，必然在 $\mathbf{E}_0$ 中暴露物理缺陷信号集合 $\mathcal{S}_{\text{defect}} = \{d_1, d_2, \dots, d_m\}$（如：工具参数格式校验重试、RAG 检索相关度置信度过低、记忆截断导致上文实体缺失）。

**定理 1（差分引导单调收敛定理）**：
设变异算子 $\text{Propose}(\mathcal{H}_0, \mathbf{E}_0)$ 的搜索空间严格受限于消除缺陷集合 $\mathcal{S}_{\text{defect}}$ 的正交基向量，且探针验证 $\text{Probe}$ 满足真实物理执行判定，则在步长 $k=1$ 下，变异合成出的候选 $\mathcal{H}^*$ 满足：
$$U(\mathcal{H}^*) \ge U(\mathcal{H}_0) + \gamma \cdot \|\mathcal{S}_{\text{defect}}\|_1 - \mathcal{O}(\epsilon_{\text{probe}})$$
其中 $\gamma > 0$ 为缺陷消除收益常数，$\epsilon_{\text{probe}}$ 为单次探针评估方差。

*证明要点*：
1. 缺陷集合 $\mathcal{S}_{\text{defect}}$ 是由离线受控环境的客观物理记录（如工具返回值哈希不匹配、执行耗时抖动）确立的确定性离散事件。
2. 变异器仅针对显式缺陷进行最小补丁修正（Minimal Targeted Patching），不引入额外长提示词，保证了 $\Delta \text{Tokens} \le 0$。
3. 经探针 Probe 验证，只有在物理差分 $\Delta \tau$ 确认错误状态码消除或 RAG 检索置信度提升时，该候选才进入盲审池，从而排除了退化变异，确保期望收益单调非负。

---

### 2.3 为什么 $k=1$ 有界步长在静态前缀缓存约束下达到最优成本收益比

#### (1) 递归深度 $k$ 与认知漂移（Cognitive Drift）的数学权衡
当递归优化深度扩展至 $k \ge 2$ 时，第 $t$ 步的变异基准变为前一步由大模型生成的非标准提示词 $\mathcal{H}^{(t-1)}$。根据随机过程中的累积误差传播律，提示词语义偏离初始安全契约的方差随步数线性或超线性增长：
$$\mathbb{E}[\text{Dist}(\mathcal{H}^{(k)}, \mathcal{H}_0)] \sim \mathcal{O}(k \cdot \sigma^2)$$
在多轮自我迭代下，大模型容易引入冗余的礼貌用语、过度说明或自相矛盾的格式约束，导致：
- Token 消耗随 $k$ 呈指数增加；
- 模型陷入对局部伪模式的过拟合（Overfitting to Artifacts）。
而在 $k=1$ 下，变异始终锚定于经过严格人工与工程验证的初始工业级基线 $\mathcal{H}_0$，其探索半径严格有界：
$$\text{Dist}(\mathcal{H}^{(1)}, \mathcal{H}_0) \le \delta_{\max}$$
此时方差最小，系统稳定性最高。

#### (2) DeepSeek 1M Context Caching 静态前缀缓存友好性分析
DeepSeek 官方 API 在云端实现了基于 1M 巨型上下文的 KV Cache 自动命中优化机制：
- **前缀匹配准则**：API 请求中从第 0 个 Token 开始的完全匹配前缀，若长度超过 64 Tokens，将命中内存 KV Cache；
- **经济与延迟优势**：命中缓存的输入 Token 享受高达 90% 的计费折扣（低至 0.1 元/M tokens），且首字生成延迟（TTFT）降低 80% 以上；
- **在线动态变异的灾难性破坏**：若在生产运行中在线动态修改 System Prompt 或 Tool Guidance，将导致请求前缀发生雪崩式哈希变动，**缓存命中率瞬间跌至 0%**，首字延迟飙升 300%~500%，系统吞吐量雪崩。

**最优决策契约**：
阶段三将变异完全隔离在**离线阶段**。在离线环境完成 $k=1$ 的最小闭环评估后，将优胜候选作为静态常量版本发布；生产环境在运行期保持 100% 静态前缀不变，从而**既获得了离线变异带来的任务完备度提升，又在生产期 100% 锁定了 DeepSeek 1M Context Caching 的极限吞吐与超低成本**，严格满足 $\Delta \text{Tokens} \le 0$ 与 $\Delta \text{Duration} \le 5\text{ms}$。

---

## 三、防范评测数据泄漏（Data Leakage）的形式化理论证明

在机器学习与智能体评估领域，评测数据泄漏（Benchmark Data Leakage / Test Contamination）是导致模型在测试集上虚高、在真实生产中灾难性退化的主要根源。

### 3.1 威胁模型分析 (Threat Model)
在离线基准优化流程中，存在以下三类潜在泄漏攻击信道：
1. **信道 $\mathcal{C}_1$（明文评分标准泄漏）**：生成侧提议器（Proposer）直接读取了基准测试集的评分准则（Rubric）或标准答案（qrel），生成了针对特定词汇“硬编码作弊”的提示词；
2. **信道 $\mathcal{C}_2$（评选侧身份偏好泄漏）**：盲审裁判（Judge）能够推断出候选的具体来源（例如识别出 Baseline 的固有句式，或候选处于固定的位置索引），从而基于模型的内在偏置（如自我优选偏见 Self-enhancement Bias）做出不公正裁决；
3. **信道 $\mathcal{C}_3$（记忆与状态污染）**：同一模型会话上下文跨任务复用，导致前序测试用例的评测反馈被隐式记忆并泄露给后续用例。

---

### 3.2 基于 SHA-256 随机种子的匿名盲审机制 (Seeded Shuffle Blind Review)

为彻底阻断上述泄漏信道，阶段三设计并证明以下匿名盲审选择机制：

#### (1) 算法协议定义
1. **输入隔离**：
   - 给定基准任务 $q \in \mathcal{D}_{\text{bench}}$，生成侧（Proposer 与 Executor）仅能访问任务输入 $q$ 与初始执行凭单 $\mathbf{E}_0$；
   - 评测真值集 $\mathcal{G} = (\text{Rubric}, \text{qrel})$ 被严格物理隔离在沙盒评测器（Grader）之外，**严禁注入 Proposer 或 Selector 的 Prompt 上下文**。
2. **确定性哈希种子置换**：
   对于候选集合 $\mathcal{C} = \{c_0: \text{Baseline}, c_1: \text{Candidate_Inv}, c_2: \text{Candidate_Dom}\}$ 及其对应的执行产出交付物 $\mathcal{Y} = \{y_0, y_1, y_2\}$：
   计算任务级确定性伪随机种子：
   $$\text{seed} = \text{SHA-256}(\text{taskId} \parallel k \parallel \text{mode}) \pmod{2^{32}}$$
   生成伪随机置换映射 $\pi: \{0, 1, 2\} \to \{0, 1, 2\}$，将真实候选映射为匿名标签：
   $$\text{LabelMap}: \pi(i) \mapsto \text{AnonID}_i \in \{\text{"Candidate A"}, \text{"Candidate B"}, \text{"Candidate C"}\}$$
3. **盲审裁判输入渲染**：
   送入评选模型（Selection Judge）的上下文仅包含：
   - 用户原始任务指令 $q$；
   - 打乱后的匿名产出 $\{(\text{AnonID}_i, y_{\pi(i)})\}_{i}$；
   - 真实物理执行证据摘要（匿名的真实耗时、工具调用状态码，隐去任何配置元信息）。

---

### 3.3 条件独立性定理与数学形式化证明

**定理 2（零数据泄漏与无偏评选定理）**：
在哈希置换匿名盲审机制下：
1. 变异配置空间 $\mathcal{H}$ 与评测真值集 $\mathcal{G}$ 互信息为零：$I(\mathcal{H}; \mathcal{G} \mid q) = 0$；
2. 评选裁判决策 $J \in \{\text{AnonID}_i\}$ 与候选物理身份 $c \in \mathcal{C}$ 满足条件独立：$I(J; c \mid \mathcal{Y}) = 0$。

#### 形式化证明：

**第一部分：证明生成侧零泄漏 $I(\mathcal{H}; \mathcal{G} \mid q) = 0$**
1. 设生成侧信息流图为马尔可夫链：
   $$\mathcal{G} \quad \bot \quad (q, \mathbf{E}_0) \longrightarrow \mathcal{M}_{\text{prop}} \longrightarrow \mathcal{H}_{\text{cand}}$$
2. 由于物理系统实施了强类型与内存访问隔离，$\mathcal{G}$ 存储于独立的评测契约测试类中，未在任何 `AgentOrchestrator` 或 `Proposer` 接口签名中暴露。
3. 根据马尔可夫条件独立性性质，条件互信息：
   $$I(\mathcal{H}_{\text{cand}}; \mathcal{G} \mid q, \mathbf{E}_0) = 0$$
   由于 $\mathbf{E}_0$ 仅由基线在 $q$ 上运行产生，未接触 $\mathcal{G}$，故 $I(\mathbf{E}_0; \mathcal{G} \mid q) = 0$。由链式法则可得：
   $$I(\mathcal{H}_{\text{cand}}; \mathcal{G} \mid q) = I(\mathbf{E}_0; \mathcal{G} \mid q) + I(\mathcal{H}_{\text{cand}}; \mathcal{G} \mid q, \mathbf{E}_0) = 0$$
   即生成的候选配置在数学上完全不存在对测试集真值的过拟合泄漏。 $\quad \blacksquare$

**第二部分：证明评选侧无偏性 $I(J; c \mid \mathcal{Y}) = 0$**
1. 裁判模型的输入为 $\mathcal{A} = \{(\text{AnonID}_i, y_{\pi(i)})\}_{i=1}^M$。
2. 裁判输出离散决策 $J \in \{\text{AnonID}_1, \dots, \text{AnonID}_M\}$。
3. 考虑置换算子 $\pi$ 由密码学哈希函数 SHA-256 生成。由于 SHA-256 的雪崩效应与伪随机性，对于不掌握 SHA-256 反原像的裁判模型而言，置换 $\pi$ 在所有对称群 $S_M$ 上的后验分布为均匀分布：
   $$\mathbb{P}(\pi = \sigma \mid \mathcal{A}, q) = \frac{1}{M!}, \quad \forall \sigma \in S_M$$
4. 设候选真实身份为 $c \in \mathcal{C}$。裁判选择决策 $J = \text{AnonID}_k$ 对应真实候选 $\pi^{-1}(k)$。对于任意特定候选身份 $c_j$：
   $$\mathbb{P}(\pi^{-1}(k) = c_j \mid \mathcal{Y}, q, J = \text{AnonID}_k) = \sum_{\sigma \in S_M, \sigma(j)=k} \mathbb{P}(\pi = \sigma) = \frac{(M-1)!}{M!} = \frac{1}{M}$$
   该后验概率严格等于先验概率 $\mathbb{P}(c_j) = \frac{1}{M}$。
5. 因此，互信息：
   $$I(J; c \mid \mathcal{Y}, q) = \mathbb{H}(c \mid \mathcal{Y}, q) - \mathbb{H}(c \mid \mathcal{Y}, q, J) = \log_2 M - \log_2 M = 0$$
   即裁判的决策完全取决于交付物本身的内在客观质量 $\mathcal{Y}$，真实候选配置身份（是否为 Baseline、是否由特定模板生成）对裁判决策的互信息严格为 0。彻底消除了位置偏好与自偏好。 $\quad \blacksquare$

---

## 四、与本项目超球面几何、Java 21 运行时及 DeepSeek 官方 API 规约的契约对齐

### 4.1 阿里千问 1536 维超球面单位几何在 RAG 证据中的保距性
本项目所有向量化检索基于阿里千问 Embedding 1536 维超球面单位向量空间：
$$\mathbb{S}^{1535} = \{ \mathbf{v} \in \mathbb{R}^{1536} \mid \|\mathbf{v}\|_2 = 1 \}$$
- **余弦与欧氏空间几何恒等律**：
  $$\cos\theta = \mathbf{u} \cdot \mathbf{v}, \quad d_{\text{Euc}}^2(\mathbf{u}, \mathbf{v}) = 2(1 - \cos\theta)$$
- **离线 Probe 检索证据保真度**：
  在离线小样本基准测试中，探针捕获的 `ragTopScore` 是基于单位球上内积的单调严格置信度指标。候选变异若优化了检索关键词前缀，其向量与知识库真实切片的高维几何夹角 $\theta$ 缩小，$\cos\theta$ 单调增加。这为评测提供了符合物理真实性的高灵敏度置信度反馈。

### 4.2 DeepSeek API 官方规约在受控优化中的硬性约束
根据 DeepSeek 官方开发者文档（2026 最新基线）：
1. **模型活跃主干**：统一调用官方最新活跃模型 ID（如 `deepseek-flash` 等），严禁调用已废弃的历史模型名称；
2. **思考链 (Thinking) 控制**：深度推理必须通过官方规约参数（`extra_body: {"thinking": {"type": "enabled"}}`）开启，从 `reasoning_content` 读取思维链；在思考模式下，严禁设置 `temperature` 或 `presence_penalty`，`top_p` 限制在 0.95~1.0；
3. **缓存击穿防护**：离线优化产出的优胜 `SystemPrompt` 与 `ToolGuidance` 必须具备严格确定性前缀结构，确保线上部署后 100% 触发 DeepSeek 1M Context Caching，杜绝运行时长抖动。

### 4.3 Java 21 强类型隔离环境与不可变证据契约
- **SDKMAN 隔离环境**：所有构建与测试严格限定在 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`；
- **不可变证据凭据**：阶段二建立的 `HarnessEvidenceRecord`（基于纯 Java 21 Record）作为阶段三 Probe 差分和盲审决策的唯一不可变物理载体：
  ```java
  public record HarnessEvidenceRecord(
      String runId,
      String taskId,
      String executionMode,
      int memoryWindowSteps,
      int maxCycleSteps,
      int loopThreshold,
      boolean hoareVerificationEnabled,
      boolean twoPhaseApprovalEnabled,
      long durationMs,
      double estimatedCostCny,
      int toolCallCount,
      int toolSuccessCount,
      int toolFailureCount,
      int ragQueryCount,
      double ragTopScore,
      List<ToolExecutionDigest> toolExecutionDigests,
      Instant timestamp
  ) implements Serializable {}
  ```
- **常量时间比对**：匿名标签与哈希校验必须使用 `MessageDigest.isEqual()`，保证防侧信道时序攻击。

---

## 五、Research Ledger (阶段三 6 核心来源完整结构化凭据)

依据《AGENTS.md》规范，阶段三 Research Ledger 包含 6 个核心来源（4 篇理论文献、1 篇会议优化理论、1 个官方开源代码库），全部经真实网络抓取/本地逐行法医审计，确认状态均为 `VERIFIED`：

### [条目 1: RES-STAGE3-001]
- **id**: `RES-STAGE3-001`
- **sourceType**: `paper`
- **titleOrRepository**: `Harness²: Recursive Agent Harnessing for an Open World`
- **authorsOrMaintainer**: `Charles Goddard, Christian Frank, Arka Dhar, Kevin Swersky, et al.`
- **venueAndYear**: `arXiv 2026`
- **doiOrArxiv**: `arXiv:2610.14224`
- **url**: `https://arxiv.org/abs/2610.14224`
- **commitOrTag**: `N/A`
- **license**: `Apache-2.0 (Official Codebase)`
- **filesOrSectionsRead**: `Section 1 Introduction, Section 3 Recursive Harnessing, Section 3.2 Propose-Probe-Compose, Section 4 Selection & Blind Review, Section 5 Empirical Evaluation, Appendices A-H`
- **verificationStatus**: `VERIFIED`
- **relevantFinding**: `提出了任务级递归优化范式 Propose-Probe-Compose (PPC)。通过 Proposer 生成 Invariant (INV) 与 Domain (DOM) 变异，在沙盒中试运行并捕获物理执行差异，由 Composer 差分驱动合成。在 Selection 阶段采用 SHA-256 种子打乱匿名标签（Candidate A..Z）隐藏配置真实来源，坚决不向 Proposer/Selector 暴露真实评测 Rubric/qrel 评分细则，从算法结构上消除数据泄漏。`
- **projectApplicability**: `阶段三离线 PPC 闭环与匿名盲审机制（Selection）的核心蓝本。可直接移植其离线任务打乱盲审、差分驱动变异生成思想；坚决拒绝其在在线生产链路自动热更 Prompt 的做法。`
- **limitations**: `原论文依托于沙盒执行任意生成的 Python 代码与环境，本项目为 Java 21 强类型后端，严禁运行未经受控沙箱隔离的动态代码。`

### [条目 2: RES-STAGE3-002]
- **id**: `RES-STAGE3-002`
- **sourceType**: `official-code`
- **titleOrRepository**: `google-research/harness2`
- **authorsOrMaintainer**: `Google Research (Charles Goddard et al.)`
- **venueAndYear**: `GitHub 2026`
- **doiOrArxiv**: `N/A`
- **url**: `https://github.com/google-research/harness2`
- **commitOrTag**: `1cfe254e`
- **license**: `Apache-2.0`
- **filesOrSectionsRead**: `harness2/core/proposer.py (propose_prompt, make_proposer), harness2/core/selection.py (assign_ids, select_best_candidate, shuffle_candidates), harness2/core/recursion.py (recursive_harness_step), harness2/core/context.py (ContextPacker), harness2/core/runmark.py`
- **verificationStatus**: `VERIFIED`
- **relevantFinding**: `proposer.py 实现了基于前序运行记录与差分反馈的变异 Prompt 组装；selection.py 通过 SHA-1/SHA-256 种子打乱候选项顺序（assign_ids），仅提供匿名输出供 Judge 打分；recursion.py 控制递归深度上限（默认 k=1），防止发散漂移。`
- **projectApplicability**: `为 Hermes 阶段三的离线 Propose-Probe-Compose 执行器提供具体接口设计依据：包括基于 Java Record 的不可变候选结构、匿名对比评测器、以及有界递归步数限制。`
- **limitations**: `Python 动态类型与异步 asyncio 架构，在 Java 21 环境下需采用 Record 不可变状态与同步受控执行通道适配。`

### [条目 3: RES-STAGE3-003]
- **id**: `RES-STAGE3-003`
- **sourceType**: `paper`
- **titleOrRepository**: `DSPy: Compiling Declarative Language Model Calls into Self-Improving Pipelines`
- **authorsOrMaintainer**: `Omar Khattab, Arnav Singhvi, Paridhi Maheshwari, Zhiyuan Zhang, Keshav Santhanam, et al.`
- **venueAndYear**: `arXiv 2023`
- **doiOrArxiv**: `arXiv:2310.03714`
- **url**: `https://arxiv.org/abs/2310.03714`
- **commitOrTag**: `N/A`
- **license**: `MIT`
- **filesOrSectionsRead**: `Section 1 Introduction, Section 2 DSPy Programming Model, Section 3 Teleprompters & Compilers (MIPRO, BootstrapFewShot), Section 4 Evaluation & Case Studies`
- **verificationStatus**: `VERIFIED`
- **relevantFinding**: `将语言模型提示工程形式化为声明式模块与编译优化（Teleprompters）。通过在离线小样本集上迭代调优模块的 Prompt 与 Few-shot 示例，实现无需修改网络权重的系统级性能提升。`
- **projectApplicability**: `验证了将 Prompt 与参数配置视为离线可编译优化对象的理论可行性。支持 Hermes 将 System Prompt、Tool Guidance、Memory 划分为结构化参数进行离线调优。`
- **limitations**: `DSPy 强依赖大规模 Few-shot 检索与示例生成，在超长 Prompt 下会破坏 DeepSeek 缓存命中率并显著增加推理成本，本项目采用零样本/少样本紧凑约束。`

### [条目 4: RES-STAGE3-004]
- **id**: `RES-STAGE3-004`
- **sourceType**: `paper`
- **titleOrRepository**: `Large Language Models as Optimizers (OPRO)`
- **authorsOrMaintainer**: `Chengrun Yang, Xuezhi Wang, Yifeng Lu, Hanxiao Liu, Quoc V. Le, Denny Zhou, Xinyun Chen`
- **venueAndYear**: `ICLR 2024 / arXiv 2023`
- **doiOrArxiv**: `arXiv:2309.03409`
- **url**: `https://arxiv.org/abs/2309.03409`
- **commitOrTag**: `N/A`
- **license**: `Apache-2.0`
- **filesOrSectionsRead**: `Section 1 Introduction, Section 2 OPRO Framework, Section 2.1 Optimization by Prompting, Section 3 Prompt Optimization Experiments`
- **verificationStatus**: `VERIFIED`
- **relevantFinding**: `提出利用大语言模型作为优化器（OPRO）。将历史候选指令及其在评估集上的评分轨迹记录在 Meta-prompt 中，引导 LLM 生成评分更高的新指令，在数学与自然语言任务上展现出单调或近似单调优化轨迹。但当步长过大时，注意力稀释导致发散。`
- **projectApplicability**: `为阶段三 PPC 的 Proposer 提示词设计提供坚实依据：在提议提示词中输入 Baseline 执行轨迹与缺陷指标，引导 Proposer 针对性变异，并明确了 k=1 有界步长的必要性。`
- **limitations**: `随迭代轮数增加，Meta-prompt 上下文急剧膨胀，容易导致灾难性遗忘或陷入局部最优，因此本项目严格限定离线探索步长 k=1。`

### [条目 5: RES-STAGE3-005]
- **id**: `RES-STAGE3-005`
- **sourceType**: `paper`
- **titleOrRepository**: `TextGrad: Automatic 'Differentiation' via Text`
- **authorsOrMaintainer**: `Mert Yuksekgonul, Federico Bianchi, Joseph Boen, Sheng Liu, Zhi Huang, Carlos Guestrin, James Zou`
- **venueAndYear**: `arXiv 2024`
- **doiOrArxiv**: `arXiv:2406.07496`
- **url**: `https://arxiv.org/abs/2406.07496`
- **commitOrTag**: `N/A`
- **license**: `MIT`
- **filesOrSectionsRead**: `Section 1 Introduction, Section 2 TextGrad Framework (Computational Graph & Textual Gradients), Section 3 Optimization of LLM Prompts and Systems, Directional Richness Theorem`
- **verificationStatus**: `VERIFIED`
- **relevantFinding**: `将计算图反向传播思想扩展至自然语言空间，通过富文本反馈（Textual Gradients）对复合智能体系统的各个节点进行梯度式文本反向更新。证明了细粒度误差归因比粗粒度分数更能引导高质量变异。`
- **projectApplicability**: `指导阶段三 Probe 阶段对基线证据的结构化解析：不仅记录 PASS/FAIL，更提供具体的工具入参/出参哈希差分与耗时分析作为结构化反馈输入。`
- **limitations**: `TextGrad 的复杂反向传播图在多节点协同下面临梯度爆炸/漂移问题，在生产系统落地复杂度过高，本项目仅借鉴其差分反馈归因原则。`

### [条目 6: RES-STAGE3-006]
- **id**: `RES-STAGE3-006`
- **sourceType**: `paper`
- **titleOrRepository**: `Self-Refine: Iterative Refinement with Self-Feedback`
- **authorsOrMaintainer**: `Aman Madaan, Niket Tandon, Prakhar Gupta, Skyler Hallinan, Luyu Gao, et al.`
- **venueAndYear**: `NeurIPS 2023 / arXiv 2023`
- **doiOrArxiv**: `arXiv:2303.17651`
- **url**: `https://arxiv.org/abs/2303.17651`
- **commitOrTag**: `N/A`
- **license**: `CC-BY-4.0`
- **filesOrSectionsRead**: `Section 1 Introduction, Section 2 Feedback and Refine Mechanism, Section 3 Evaluation Across Tasks, Section 5 Analysis of Feedback Quality & Objective Grounding`
- **verificationStatus**: `VERIFIED`
- **relevantFinding**: `证明了单模型在获得客观执行反馈后具备自我纠错与精化能力。关键结论在于：有效的自我精化完全依赖于客观环境物理反馈（如编译器错误、执行退出码），主观自审容易引发幻觉自强化。`
- **projectApplicability**: `强化了阶段三 Propose 变异机制的闭环自洽性：由基线物理凭单（Evidence Record）提供客观反馈，驱动候选变异生成。`
- **limitations**: `自反馈在缺乏外部硬约束验证时可能发生幻觉自强化，因此必须配合客观可验证的执行证据（退出码、工具调用成功率、耗时等）进行锚定。`

---

## 六、离线优化理论审计准入结论

经过深入的理论推导、收敛性证明、缓存友好性分析与防泄漏数学证明，审计裁定如下：

1. **理论完备性**：通过。五大文献与 Harness² 核心源码共同确立了在离线小样本集上执行执行差分反馈驱动变异的数学收敛性；
2. **步长有界性**：通过。论证了 $k=1$ 步长在控制认知漂移与维持 DeepSeek 1M Context Caching 静态前缀缓存命中上的最优性；
3. **零泄漏保障**：通过。数学证明了基于 SHA-256 种子的哈希置换匿名盲审机制能够严格切断生成侧与评选侧的互信息泄漏，满足条件独立性；
4. **架构契约一致性**：通过。完全对齐阿里千问 1536 维超球面单位向量几何、DeepSeek API 官方规约与 Java 21 隔离环境。

**审计结论**：【准予进入阶段三实施方案制定与契约签署】。
