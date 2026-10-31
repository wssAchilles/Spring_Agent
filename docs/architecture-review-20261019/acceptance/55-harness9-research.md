# Hermes Harness² 阶段九（Stage 9）：定向学术与源码研究报告

- **文档编号**：`55-harness9-research.md`
- **所属阶段**：Stage 9 — 阶段 2（定向研究）
- **伴生账本**：[`55-harness9-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/55-harness9-research.ledger.json)（8 条 `VERIFIED` 来源）

---

## 一、研究背景与核心问题

在 Stage 8-lite 完成可信 Usage 捕获（`UsageCapturingChatModel`）、能力上下文真实注入（`CapabilityContextAssembler`）、神经符号工具守卫（`HoareTripleToolGuard`）、语义环检测（`LocallyDenseCycleAutomaton`）与 RAG 切片归因底座后，Stage 9 面临的核心科学问题是：

> **如何在严格受控的真实 API 预算（$\le 15\text{--}20$ probes，$\le 60$ 次模型调用，$\le 200,000$ tokens，$\le ¥10.00$）下，用真实 DeepSeek API（`deepseek-flash`）与真实 `AgentOrchestrator` 走完 1 轮零泄漏、可证伪、具备真实人民币成本闭环的 Propose-Probe-Compose (PPC) 实验？**

围绕这一核心问题，我们使用 MCP 全文提取工具与本地源码精读了 **8 个权威来源**（含 Harness² 论文与本地源码、DSPy、OPRO、TextGrad、Reflexion、Self-Refine 以及 DeepSeek 官方定价文档），全部达到 `VERIFIED` 状态。

---

## 二、核心文献与源码精读发现

### 2.1 Harness² 论文 (`arXiv:2610.14224`) 与本地官方源码 (`/Users/achilles/Documents/优秀开源项目/harness2`)

1. **Propose-Probe-Compose (PPC) 的本质**：
   - Harness² 明确将智能体的外围控制层（系统提示词契约、工具前置守卫、上下文装配策略、循环步数上界）形式化为可演化的配置空间 $\mathcal{H}$，在不改动底层 LLM 参数 $\theta$ 的前提下，通过基准探针（Probe）采集真实运行轨迹与开销，再由合成器（Composer）优选帕累托占优配置。
2. **`selection.py` 的匿名化防偏置机制**：
   - 在 `/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/selection.py`（行 71–76）中，`assign_ids(candidates, seed)` 基于确定性 SHA-1 种子对所有候选执行伪随机洗牌，并将其重命名为匿名符号 `A, B, C...`。选择器在比较时只看到匿名符号与脱敏执行摘要，彻底消除了“默认偏袒 `baseline` 或偏袒名字叫 `optimized` 的候选”的身份偏置。
3. **`context.py` 的零泄漏红线（Anti-Leakage Invariants）**：
   - 在 `/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/context.py`（行 167–169）中，`assert_no_leak_paths` 与 `assert_no_grader_verdicts` 严厉禁止任何评测真值（qrel / gold answers / grader verdicts）流入 Proposer 或匿名 Selector。

### 2.2 DSPy (`arXiv:2310.03714v1`, ICLR 2024) 与 OPRO (`arXiv:2309.03409v3`, ICLR 2024)

1. **声明式配置空间与单变量控制（Ceteris Paribus）**：
   - DSPy 将 LM 调用管线抽象为声明式配置参数，通过验证集信号筛选配置；而 OPRO 的实验表明，在小样本验证集（如 5–10 个样本）上如果允许 LLM 自由改写提示词，会同时触发多个维度的语义漂移并造成严重的小样本过拟合（Overfitting）。
   - **对 Stage 9 的启示**：Stage 9 只有 5 个基准任务，因此**绝不能让 LLM 自由改写或把期望关键词塞进提示词**，而必须采用确定性单变量代数变异算子：
     - **Candidate A（`CONCISE_SYSTEM_PROMPT`）**：仅精简系统提示词中的冗余寒暄与铺垫，保留核心角色与输出契约，其余参数（`maxCycleSteps`、`guardPolicy`）与 `baseline` 完全一致；
     - **Candidate B（`TIGHT_CYCLE_STEPS`）**：仅将 `maxCycleSteps` 从默认 `5` 收紧为紧凑值 `3`（并附带单句紧凑工具调用约束），系统提示词主体与 `baseline` 完全一致；
     - **Candidate C（可选组合 `COMBINED_AB`）**：仅当 15 个默认 probe 完成且累计费用 `< ¥5.00` 时才允许追加。

### 2.3 TextGrad (`arXiv:2406.07496v1`)、Reflexion (`arXiv:2303.11366v4`) 与 Self-Refine (`arXiv:2303.17651v2`)

1. **Position-Swap 消除位置偏置（源自 TextGrad 评估器分析）**：
   - 任何基于成对比较（Pairwise Comparison）的选择器都存在显著的位置偏置（倾向于选第一个或第二个选项）。因此 Stage 9 的 `PpcComposer` 必须对 `(baseline, candidate)` 执行双向位置对调：
     - Pass 1：`(Option_A = baseline, Option_B = candidate)`
     - Pass 2：`(Option_A = candidate, Option_B = baseline)`
     - 仅当 Pass 1 与 Pass 2 指向同一个物理候选时，才承认偏序胜出；若两次矛盾，一律判为 `INCONSISTENT_TIE` 并保留 `baseline`。
2. **硬核符号信号一票否决（源自 Reflexion）**：
   - 纯文本自评容易产生幻觉，必须结合外部确定性符号信号：在 Stage 9 中，若任一 Probe 触发 `cycleDetected == true`（由 `LocallyDenseCycleAutomaton` 检出）或非预期的 `guardBlocked`（由 `HoareTripleToolGuard` 检出）或 `usageStatus == UNKNOWN`，该候选直接一票否决。
3. **为什么 $k=1$ 轮足以构成完备可证伪实验（源自 Self-Refine 迭代步数分析）**：
   - Self-Refine 的步数消融证明，单轮受控改进（$k=1$）捕获了最高信噪比的收益，而多轮无监督迭代（$k \ge 2$）不仅使 API 成本线性倍增，还会引入累积漂移。在 Stage 9 的 `<= ¥10` 预算下，$k=1$ 轮真实 PPC 完全足以检验候选 A/B 是否比 baseline 更省 Token/成本且不降质；若两者均未达到采纳门槛，诚实输出 `REJECTED_AFTER_REAL_PPC` 本身就是顶刊级严谨性的直接体现。

### 2.4 DeepSeek 官方分时段计费规范 (`https://api-docs.deepseek.com/zh-cn/quick_start/pricing/`)

1. 官方文档明确 `deepseek-flash`（`DeepSeek-V4.1-Flash-1017`）采用北京时间（`Asia/Shanghai`，UTC+8）高峰/空闲双档计费，且区分 `prompt_cache_hit_tokens` 与 `prompt_cache_miss_tokens`（价格相差 **50 倍**：空闲时段命中 0.02 元/M vs 未命中 1.00 元/M；高峰时段命中 0.04 元/M vs 未命中 2.00 元/M；输出空闲 4.00 元/M vs 高峰 8.00 元/M）。
2. 因此，Stage 9 必须在 `UsageCapturingChatModel` 中废除硬编码的 `0.0D`，改为调用 `DeepSeekOfficialPricingCalculator` 基于真实 `promptTokens`、`cacheHitTokens`、`completionTokens` 与北京时间调用时刻精确计算 `actualCostCny`。

---

## 三、候选方案对比与最小算法选择

| 评估维度 | 方案 0：保持 Stage 8 现状 (`actualCostCny=0.0D`) | 方案 1：引入多轮在线 LLM Meta-Optimizer ($k \ge 3$, 类似 OPRO/TextGrad) | **方案 2（推荐采纳）：Stage 9 受控单轮真实 PPC ($k=1$ + 单变量候选 A/B + 官方分时段精确计费 + 匿名 Position-Swap)** |
| :--- | :--- | :--- | :--- |
| **正确性与真实性** | FAIL（无真实成本，未跑真实 PPC） | 中（小样本极易过拟合且多变量混淆） | **高（100% 真实 DeepSeek 调用、真实官方价格、单变量可归因）** |
| **可证伪性** | 无 | 弱（多轮随机改写难以复现和证伪） | **强（明确给出采纳或 `REJECTED_AFTER_REAL_PPC` 判据）** |
| **数据需求** | N/A | 需大规模训练集/验证集划分 | **仅需 5 个覆盖全能力域的标准基准任务** |
| **延迟与成本** | N/A | 高（$>100$ 次调用，易突破 ¥10 预算） | **极低（15~20 probes，20~35 次调用，预计 ¥0.05~¥0.50）** |
| **实现复杂度** | 无 | 极高（需构建复杂在线提示词生成图） | **极简（主干 `ppc` 包严格控制在 6 个核心类以内）** |
| **依赖变化** | 无 | 需引入额外模板引擎 | **零新增第三方依赖，纯 Java 21 标准实现** |
| **回滚风险与生产影响** | 无 | 高 | **零风险（默认 `SINGLE` 且 kernel `OFF`；默认 `mvn test` 零外网；Live 证据隔离在 `harness9-live`）** |

**最小算法裁决**：拒绝方案 0 与方案 1，采纳**方案 2**。
