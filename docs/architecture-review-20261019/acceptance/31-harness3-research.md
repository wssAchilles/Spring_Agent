# Hermes Harness² 阶段三定向论文与代码检索报告

> **报告版本**：3.0.0-STAGE3-RESEARCH  
> **检索周期**：2026-10-29  
> **关联分支**：`feat/hermes-harness-stage2`  
> **研究主题**：离线 Propose–Probe–Compose (PPC) 架构、Prompt/策略离线优化理论与防泄漏匿名盲审机制  
> **准则遵循**：《AGENTS.md》科研门禁（Reading-First 真实抓取取证、14 字段规范、独占简体中文）

---

## 一、定向文献与代码池概览

依据《AGENTS.md》科研门禁铁律，本阶段聚焦于离线受控优化、差分反馈驱动变异与防泄漏匿名评测领域的 6 个权威核心来源（4 篇顶级理论文献、1 个顶级会议优化范式、1 个权威官方代码库）。所有来源均通过真实网络抓取/本地代码逐行审计，验证状态均为 `VERIFIED`。

| 来源 ID | 类别 | 标题 / 仓库 | 机构 / 作者 | 年份 / 载体 | 验证状态 |
|---|---|---|---|---|---|
| RES-STAGE3-001 | 论文 | Harness²: Recursive Agent Harnessing for an Open World | Google Research (Charles Goddard et al.) | arXiv 2026 | **VERIFIED** |
| RES-STAGE3-002 | 官方代码 | google-research/harness2 (commit `1cfe254e`) | Google Research | GitHub 2026 | **VERIFIED** |
| RES-STAGE3-003 | 论文 | DSPy: Compiling Declarative Language Model Calls into Self-Improving Pipelines | Stanford NLP (Omar Khattab et al.) | arXiv 2023 | **VERIFIED** |
| RES-STAGE3-004 | 论文 | Large Language Models as Optimizers (OPRO) | Google DeepMind (Chengrun Yang et al.) | ICLR 2024 | **VERIFIED** |
| RES-STAGE3-005 | 论文 | TextGrad: Automatic 'Differentiation' via Text | Stanford University (Mert Yuksekgonul et al.) | arXiv 2024 | **VERIFIED** |
| RES-STAGE3-006 | 论文 | Self-Refine: Iterative Refinement with Self-Feedback | CMU / Allen AI (Aman Madaan et al.) | NeurIPS 2023 | **VERIFIED** |

---

## 二、逐源法医级研读与核心发现

### 1. Harness²: Recursive Agent Harnessing for an Open World (arXiv:2610.14224 & 本地仓库 `1cfe254e`)
- **核心理论与机制**：
  提出 Propose–Probe–Compose (PPC) 三段式任务级迭代优化流水线：
  1. **Propose**：以初始 Harness 为基线，针对当前任务生成结构化变异（包括针对通用提示词与执行策略的 Invariant 变异，以及针对特定领域格式与工具参数约束的 Domain 变异）；
  2. **Probe**：在轻量隔离沙盒中试运行候选 Harness，收集真实的物理执行轨迹与差分数据（Exit Code、耗时、标准错误、输出内容差分）；
  3. **Compose**：综合 Probe 反馈的多维度差分信号，合成出优于基线的候选配置；
  4. **Selection（匿名盲审机制）**：在 `harness2/core/selection.py` 中，采用随机种子将基线与所有候选打乱（标签重映射为 Candidate A..Z），评测模型仅能观察到匿名产出物与执行证据，**绝不接触任务的真实评分细则（Rubric/qrel）**，从机制上杜绝过拟合与数据泄漏。
- **与阶段三假设的数学契合度**：
  阶段三的唯一假设要求以不可变证据 `HarnessEvidenceRecord` 为物理依据，在 $k=1$ 的受控步长下执行 PPC。Harness² 源码证明了 $k=1$ 步长下基于差分证据的变异能够稳定收敛且计算代价可控。

### 2. DSPy: Compiling Declarative Language Model Calls (arXiv:2310.03714)
- **核心理论与机制**：
  将大模型提示词工程抽象为声明式流水线与编译器（Teleprompters）。在无需微调底层模型权重的前提下，通过离线小样本集驱动的参数搜索与少样本编译，系统性提升管线准确率。
- **可借鉴点**：
  证明了将 Agent 运行时的 System Prompt、Tool Guidance、Memory 参数视为离线声明式模块并在离线小样本集上调优的理论完备性。

### 3. Large Language Models as Optimizers - OPRO (arXiv:2309.03409)
- **核心理论与机制**：
  利用大模型自身的自然语言推理能力充当离线优化器。将历史候选提示词及其在评估指标上的表现按升序排列构建 Meta-prompt，指示 LLM 生成能在目标指标上获得更高回报的候选指令。
- **工程启发**：
  为阶段三 Proposer 的提示词模板构建提供依据：必须将 Baseline 的执行结果（如检索分数、工具耗时、缺失项）作为上下文喂入，使变异生成具备明确的梯级改进指向。

### 4. TextGrad: Automatic 'Differentiation' via Text (arXiv:2406.07496)
- **核心理论与机制**：
  在文本计算图上反向传播“文本梯度”。相较于标量损失（例如单纯的 PASS/FAIL），结构化的自然语言评价与错误归因能提供方向性更精确的更新动力。
- **阶段三落地指导**：
  阶段二实现的 `HarnessEvidenceRecord` 提供了真实的执行耗时、工具调用输入/输出哈希、检索分数等丰富物理属性，这正是 Probe 输出给 Proposer 的高质量“文本梯度”载体。

### 5. Self-Refine (arXiv:2303.17651)
- **核心理论与机制**：
  证实大模型在获得客观执行反馈后具备自我纠错与精化能力。闭环的有效性完全取决于反馈（Feedback）的真实性与可观察性。
- **防伪纪律**：
  再次警示反馈必须来源于真实的系统物理证据（真实工具执行耗时与返回内容摘要），严禁凭借大模型主观臆断虚构执行轨迹。

---

## 三、可迁移与不可迁移结论（架构裁决）

| 研究结论 / 机制 | Hermes 适用性判断 | 阶段三裁决 | 架构理由与工程边界 |
|---|---|---|---|
| **离线 Propose–Probe–Compose 受控闭环** | 高度契合 | **完全采纳** | 仅在离线小样本基准集上执行，绝不干扰线上运行时。 |
| **匿名盲审选择器（Selection）** | 高度契合 | **完全采纳** | 对候选实施 Hash 随机打乱与匿名标记，隔绝真实评分标准，消除泄漏。 |
| **物理凭单差分驱动变异** | 高度契合 | **完全采纳** | 将真实执行耗时、状态码、输入输出摘要作为探针信号。 |
| **在线生产流量自动变异 Prompt** | 违背工程铁律 | **坚决拒绝** | 破坏静态前缀缓存，极大增加延迟与生产故障率。 |
| **大模型动态生成 Python 代码执行** | 存在高安全隐患 | **坚决拒绝** | 违反企业环境安全防线，Java 21 平台坚持受控静态类契约。 |
| **多轮次 (k>2) 开放式递归搜索** | 存在发散风险 | **严格限制** | 阶段三严格限制探索步长 $k=1$，确保搜索空间紧凑且完全可控。 |

---

## 四、研究结论对阶段三实施的指导意义

1. **变异范围收敛**：限定在 `SystemPrompt`（角色与约束边界）、`ToolGuidance`（工具选择指引）、`Memory`（短期记忆保留步数）三个纯文本与数值超参低风险维度；
2. **零泄漏匿名评估**：评测器只比对 Candidate A 与 Candidate B 在同一任务下的输出完整性、事实准确性与工具使用合理性，评测过程完全屏蔽配置来源信息；
3. **不可变凭单存证与回放**：所有离线探针运行结果与匿名盲审决策均序列化为不可变 Java 21 Record 存证，确保实验 100% 具备确定性回放能力。
