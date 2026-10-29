# Agent B: 生产工程与 Java 适配审计员 (Code Forensic & Benchmark Specialist) 独立审计报告

> **智能体角色**：Agent B: 生产工程与 Java 适配审计员 (Code Forensic & Benchmark Specialist)  
> **物理 ConversationId**：`478dab7a-15d7-4840-9255-c6f950f378de`  
> **运行 Transcript 日志**：[`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/478dab7a-15d7-4840-9255-c6f950f378de/.system_generated/logs/transcript.jsonl)  
> **审计日期**：2026-10-29  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文规范

---

# Google Research `harness2` 源代码与评测基准法医级代码审计报告

**审计员**：开源/工业代码法医核验员 (Code Forensic & Benchmark Specialist)  
**审计目标仓库**：https://github.com/google-research/harness2  
**审计基准 Commit**：`1cfe254e8d25b15f37048c3730134686abe60335`  
**开源许可证**：Apache License 2.0（已通过仓库根目录 `LICENSE` 确认，无传染性版权风险）  
**本地绝对代码路径**：`/Users/achilles/.gemini/antigravity/brain/e780bef4-ad1b-4ce7-8c57-88ab7e5cffad/scratch/harness2`

---

## 一、8 大 Harness 组件数据结构与基质映射法医还原

在 `harness2` 体系中，“Harness（护具）”被严格定义为围绕大模型构建的运行时认知与执行约束环境。框架在 `harness2/core/spec.py`、`tree.py` 以及两大执行基质 `harness2/substrate/opencode.py` 和 `codex.py` 中，将智能体护具解构为 8 大可编辑组件（代号 `c1` 至 `c8`），并根据底层 Substrate 的差异实施差异化的数据映射与文件生命周期管理。

### 1. 8 大组件跨基质映射矩阵与生命周期

| 组件代号 | 抽象组件定义 | OpenCode 基质（`OPENCODE_SURFACE`） | Codex 基质（`CODEX_SURFACE`） | 注入时机与上下文生命周期 |
| :--- | :--- | :--- | :--- | :--- |
| **c1** | **System Prompt (系统提示词)** | `systemprompt.md` | 合并在 `AGENTS.md` 根文件 | **ALWAYS ON (常驻)**：每一轮全量注入，直接竞争输出上下文预算 |
| **c2** | **Guardrails (护栏/规则)** | `guardrails.md` | 合并在 `AGENTS.md` 规则段 | **ALWAYS ON (常驻)**：硬性执行约束与安全防线 |
| **c3** | **Memory (持久长程记忆)** | `LongTermMEMORY.md` | 合并在 `AGENTS.md` 事实段 | **ALWAYS ON (常驻)**：跨任务/同领域可持久化的专业事实 |
| **c4** | **Tool Guidance (工具指南)** | `tool_descriptions/tool_guidance.md` | 合并在 `AGENTS.md` 工具段 | **ALWAYS ON (常驻)**：指导何时及如何使用原生工具 |
| **c5** | **Subagents (委托子智能体)** | `sub_agents/<name>/AGENT.md` | `sub_agents/<name>/AGENT.md` | **ON DEMAND (按需委托)**：在**独立隔离上下文**中启动，主智能体不可见其中间过程，仅返回最终文本结果 |
| **c6** | **Skills (工作流技能)** | `skills/<name>/SKILL.md` | `.agents/skills/<name>/SKILL.md` | **ON DEMAND (按需加载)**：正文不常驻，仅名称与描述在常驻层暴露，被触发时动态加载进主上下文 |
| **c7** | **Scripts (确定性脚本)** | `scripts/<name>.py` | `scripts/<name>.py` | **ON DEMAND (按需执行)**：作为确定性 Python 辅助程序，由智能体通过 Bash 命令行显式调用 |
| **c8** | **Plugins / Hooks (拦截插件)** | `plugin/<name>.ts` | `.codex/hooks.json` | **IN THE LOOP (工具循环内拦截)**：唯一不可被模型跳过的拦截层，在工具调用前后实施确定性阻断与干预 |

### 2. 字段规范、校验规则与防呆机制（`tree.py` 法医审计）

1. **常驻字符上限硬门禁（Always-On Cap）**：
   - OpenCode 模式限制上限为 **24,000 字符**（`always_on_max=24_000`）；
   - Codex 模式限制上限为 **8,000 字符**（`always_on_max=8_000`），原因在于 Codex-CLI 在底层会对超过 32,768 字节的 `AGENTS.md` 实施静默截断；
   - 一旦超出上限，`validate()` 直接抛出 `ValueError`，拒绝提案生效。
2. **技能触发防呆与自动修复（`autotrigger` 机制）**：
   - 规定技能不可作为“孤岛”存在。若 `skills/` 中存在 `SKILL.md`，但常驻文本中未提及该技能名称，该技能即被判定为“无效死重（dead weight）”；
   - 系统内置 `autotrigger()` 自动在 `guardrails.md` 或 `AGENTS.md` 中按模板补全触发行（如 `- When {desc}, load the \`{name}\` skill.`），若补全后未超上限则自动救赎，否则拦截报错。
3. **子智能体（Subagents）上下文与元数据约束**：
   - OpenCode 强制要求 YAML Frontmatter 包含 `mode: subagent` 与 `description:`；
   - 仅适用于“输入体积极大、提炼结果极小”的收敛型子任务（如阅读数十份长 PDF 提取汇总表格）；严禁用于输出规模等同于输入的大型扩散任务。
4. **确定性脚本（Scripts）可执行性核验**：
   - `tree.py` 中的 `_py_compiles()` 函数通过 Python 原生 `compile(src, rel, "exec")` 对所有 `.py` 脚本进行实时 AST 语法编译检查，若存在语法错误（SyntaxError）立刻阻断；
   - `_check_script_refs()` 严格核验常驻文本与技能中通过正则（`python3? scripts/xxx.py`）引用的脚本文件是否在 `tree` 中真实存在，禁止“幻觉调用”。
5. **生命周期钩子（Hooks）Schema 校验**：
   - Codex 基质支持 10 类生命周期事件：`PreToolUse`, `PostToolUse`, `PermissionRequest`, `PreCompact`, `PostCompact`, `SessionStart`, `SubagentStart`, `SubagentStop`, `UserPromptSubmit`, `Stop`；
   - 强校验 `hooks.json` 的 JSON 格式与 command 拦截规范，例如 `PostToolUse` 钩子可通过输出 `{"decision":"block","reason":"..."}` 向模型反馈阻断原因。

---

## 二、核心算法循环与双层递归执行路径法医还原

Harness² 实现了两层不改变模型权重的递归机制：面向单一任务的**任务级递归（Task-level Recursion）**与面向跨任务经验迁移的**护具级递归（Harnessing-level Recursion）**。

### 1. 提案机制：Invariant（不变性）与 Domain（领域特异性）（`proposer.py` / `prompts.py`）

提案生成器（Harness Editor）通过双维度解耦编辑策略避免维度混乱：
- **Invariant 维度（INV）**：定义智能体**如何工作（How the agent works）**。聚焦通用程序、工具链调用规范、自检验证流程、防御性提示与代码执行辅助机制，完全独立于具体业务主题；
- **Domain 维度（DOM）**：定义智能体**工作所需的领域知识（What knowledge the agent needs）**。聚焦特定领域行业标准、专业术语定义、法定格式规范、计算公式与经验法则；
- **Prompt 模板设计与无真值监督**：`prompts.py` 严格禁止在任务描述与提示词中包含评测集基准答案、Rubric 评分细则或 Grader 判定条件；仅提供被执行任务的标准输入材料与历史失败轨迹。

### 2. 状态机驱动与并发调度：Parallel vs Sequential（`recursion.py`）

框架通过统一的预算预算控制（$k$ 为任务级递归预算参数）：
- **每次任务的总 Rollout 预算**：$R = 4k$（$k$ 次共享 Base + $2k$ 次 Candidate Probes + $k$ 次 Composed Finals）；
- **每次任务的总改进会话预算**：$P = 3k$（$k$ 次 INV 提案 + $k$ 次 DOM 提案 + $k$ 次 Composition 合成会话）。

#### (1) Parallel（并行聚合）递归调度时序
- **Phase 1 (Propose)**：基于共享的 Base 执行证据，并行派发 $k$ 个 INV 提案（`inv1..invk`）和 $k$ 个 DOM 提案（`dom1..domk`），由 `ThreadPoolExecutor` 驱动；
- **Phase 2 (Probe Rollout)**：将这 $2k$ 个候选 Harness 部署到 Substrate 沙盒中，并行执行 Rollout，收集真实的执行轨迹（Trajectories）与交付物（Deliverables）；
- **Phase 3 (Compose)**：将所有 $2k$ 个候选者的改动差异（Diff）、执行轨迹、交付物状态与编辑笔记全量注入合成上下文，并行发起 $k$ 个独立的 Composition 会话（`agg1..aggk`）；
- **Phase 4 (Final Rollout)**：并行执行这 $k$ 个聚合后的 Final Harness，产出最终待选交付物；
- **Diversity 评估**：调用 `tree.diversity()` 计算各候选树之间的两两行级 Jaccard 相似度（Line-Jaccard），监控候选多样性与坍缩风险。

#### (2) Sequential（串行深度）递归调度时序
- 执行 $k$ 轮深度嵌套迭代（Round $t = 1 \dots k$）：
  - 设初始 Harness $H_0 = \text{base}$；
  - **Round $t$**：
    1. 基于当前基线 $H_{t-1}$，提出当前轮次的候选 `inv_t` 与 `dom_t`；
    2. 并行 Rollout 验证 `inv_t` 与 `dom_t`；
    3. 结合本轮候选表现及前序所有轮次累积的历史执行池，运行合成阶段产出合成护具 $c_t$（`dim="COMPOSE"`）；
    4. 对 $c_t$ 进行单独 Rollout 验证；
    5. 将当前基线更新为 $H_t = c_t$，并追加进历史经验流，作为第 $t+1$ 轮的输入。

### 3. 跨任务经验持久化机制（`library.py` 法医审计）

护具级递归通过领域经验库（Domain Experience Library）实现跨任务知识沉淀：
1. **物理存储拓扑**：
   ```text
   <library_root>/<run-tag>/<mode>/<domain>/
   ├── INDEX.md                                   # 领域索引文件
   └── harnesses/
       └── <task_slug>/
           ├── harness/                           # 采纳护具的完整 c1..c8 物理目录树
           └── EXPERIENCE.md                      # 经验沉淀卡片
   ```
2. **结构化经验卡片（EXPERIENCE.md）**：
   必须包含完整的四元组：
   - `cue`：触发该经验的业务线索/任务特征；
   - `lesson`：可跨任务迁移的护具设计经验法则；
   - `evidence`：支持该法则的真实执行证据对比；
   - `confidence`：置信度评估。
3. **领域自然隔离与物理防泄露**：
   - 检索无需昂贵的向量数据库，而是采用**构造即隔离（Retrieval by Construction）**原则，严格限定任务只能读取同领域目录；
   - **零泄露硬校验**：`library.add()` 在写入前强制调用 `context.assert_tree_has_no_grader_verdicts()`，扫描 Harness 文件及 `EXPERIENCE.md`，一旦发现评分判定词直接拒绝入库。

### 4. 执行器基质接口与沙盒隔离（`substrate/` 法医审计）

- **上下文与数据流隔离**：OpenCode 执行器在调用 Bun 运行时时，通过环境变量注入 `OPENCODE_PERMISSION`，显式配置 `{"external_directory": {"*": "deny", "<cwd>": "allow"}}`，实现严格的文件系统越权拦截；同时为每个 Session 分配专属的临时 `XDG_DATA_HOME` 和 `XDG_STATE_HOME`，杜绝 SQLite 会话历史跨任务污染；
- **Jinja 模板渲染安全（`assert_jinja_safe`）**：在 WorkBuddy 容器模式下，常驻提示词通过 Jinja 模板组合。系统严格限定模板必须且只能包含一个末尾的 `{{ instruction }}` 占位符，严禁出现任何额外的 `{{`、`{%` 或 `{#`，防止由于 `StrictUndefined` 导致容器在评测中途直接崩溃或静默截断注释。

---

## 三、并发控制、异常处理与系统边界法医审计

1. **超时与进程看门狗**：
   - Proposer 单次调用设置明确看门狗超时时间（默认 `timeout=3600s`），通过 `subprocess.TimeoutExpired` 捕获并记录耗时；
   - 评测基质 Rollout 设置单独执行超时，由各基准适配器负责超时杀进程并标记 `ok=False`。
2. **Token 与上下文预算硬约束**：
   - OpenCode 模式输出 Token 硬限为 `OUTPUT_TOKEN_MAX = 64_000`；
   - 观测截断保护：`obs_trunc` 默认设置为 50,000 字符，低于 10,000 会抛出异常（防止过度截断导致人工制造虚假缺陷）；
   - 上下文外置化：轨迹与交付物不直接塞进 LLM Prompt，而是写入 `context/` 临时目录，以短指针（Short Pointer）形式指引 Agent 自行阅读，彻底规避 Prompt 溢出截断。
3. **提案校验失败与自动重试机制（Self-Correction Retry）**：
   - `STAGE_ATTEMPTS = 2`：当 LLM 生成的 Harness 违反 `SurfaceSpec`（如语法错误、超出字符上限、引用的脚本不存在）时，系统不会直接丢弃，而是捕获具体的 `ValueError` 报错文本，拼装为 `# YOUR PREVIOUS ATTEMPT WAS REJECTED` 反馈块，给 LLM 提供第 2 次修正机会；
   - 仅在重试依然失败时，标记 `fell_back=True`，安全回退到当前阶段的基线 Harness（`fallback` 树），确保主执行状态机绝不崩溃。
4. **探针异常隔离与幂等断点自愈（Resume）**：
   - 单个 Rollout 抛出异常或缺失产物时，仅生成 `ok=False, delivered=False` 的 `RunView`，不阻断并发池；
   - 基于 `RunMark` 机制实现严格幂等，根据 `summary.json`、`run_id` 和指纹缓存断点状态。若检测到历史生成的脚本或答案文件被篡改或删除，系统会自动识别并精准重新执行受损任务。

---

## 四、Benchmark 基准评测数据核验与代价比 (ROI) 分析

### 1. 真实评测基线与提升幅度核验

根据官方在三大专业评测基准（JobBench、LAB、WorkBuddy-Bench）及四大模型/护具配置下的实测记录（以 GLM-5 与 Gemini 3.5 Flash 为基准）：

| 评测基准 (Benchmark) | 业务领域定位 | 核心评估指标 | 官方实测 Baseline 与 Harness² 提升表现 |
| :--- | :--- | :--- | :--- |
| **WorkBuddy-Bench** | 企业日常办公、网页操作、代码工程 (Docker 沙盒) | 真实环境任务执行成功率 (Task Success Score %) | **单步提升高达 +11.5 分**（在 Office/Web/Code 子集中，尤其在多步骤工具链调用与环境自愈能力上获得巨大增益） |
| **JobBench** | 8 大行业专业职业任务（会计、管理、法律、IT等） | 专家制定的 Rubric 综合加权得分 (Rubric Score %) | **单步提升高达 +4.5 分**（专业领域工具引导与确定性 Python 辅助脚本显著降低格式和事实错误） |
| **LAB (Harvey-Labs)** | 顶级专业法律分析与复杂法规检索对比 | Partial Pass Rate (通过的评分标准比例) & All-pass | **Partial Pass Rate 提升高达 +11.8 个百分点**（通过子智能体解耦海量长法案阅读，大幅提升法条命中率） |

### 2. Token 与算力代价比（ROI）深度法医评估

1. **预算成本恒等式分析**：
   对于给定递归深度 $k$：
   - **Rollout 次数**：$4k$ 次前向模拟；
   - **LLM Improver 改进会话**：$3k$ 次深度长推理。
   在 $k=1$ 时，单任务需消耗 4 次执行 Rollout 与 3 次模型改进调用；在 $k=3$ 时，单任务消耗 12 次 Rollout 与 9 次改进调用。
2. **$k=1$ 时的“噪声底（Noise Floor）”现象（`report.py` 核心洞察）**：
   - 官方代码在 `report.py` 中明确指出了一个重要的实验事实：**在 $k=1$ 时，Parallel 模式与 Sequential 模式在数学与执行路径上完全等价**（均为 1 次 INV 抽取 + 1 次 DOM 抽取 + 1 次 Composition 合成）；
   - 因此，在 $k=1$ 时两种模式产生的微小分差（如 0.5~1.5 分）纯属模型采样的**噪声底（Noise Floor）**；
   - **Scaling 规律**：只有当 $k \ge 2$ 时，Parallel 的“广度并发探索多样性”与 Sequential 的“深度多轮累积提炼”才会展现出统计学显著的真实算法分水岭。
3. **投入产出比建议**：
   - 生产落地首推 **Parallel 模式 ($k=2$)**：在保证多样性（Line-Jaccard 维持在健康区间）的同时，可在 8 次 Rollout + 6 次 Session 的可控预算内快速收敛，取得最佳的性价比收益；
   - Sequential 模式更适合超高难度、长上下文且依赖前序探索反馈的极端专业任务，但其串行依赖会导致端到端延迟大幅增加。

---

## 五、结构化 Research Ledger 条目（严格遵循 AGENTS.md 规范）

```text
id: CODE-FORENSIC-HARNESS2-001
sourceType: official-code
titleOrRepository: google-research/harness2
authorsOrMaintainer: Ruiyao Xu, Yanfei Chen, Zhongying CuiZhu, Bhavana Dalvi Mishra, Yifei Ming, Han Yu, Rujun Han, Chen-Yu Lee, Tomas Pfister (Google Research)
venueAndYear: arXiv:2610.14224 (2026)
doiOrArxiv: arXiv:2610.14224
url: https://github.com/google-research/harness2
commitOrTag: 1cfe254e8d25b15f37048c3730134686abe60335
license: Apache-2.0
filesOrSectionsRead: harness2/core/spec.py, harness2/core/tree.py, harness2/core/proposer.py, harness2/core/recursion.py, harness2/core/ledger.py, harness2/core/library.py, harness2/core/selection.py, harness2/core/context.py, harness2/core/report.py, harness2/core/schedule.py, harness2/substrate/base.py, harness2/substrate/opencode.py, harness2/substrate/codex.py, harness2/config.py, docs/BENCHMARKS.md, docs/EXPERIMENTS.md, README.md, LICENSE, tests/test_workflow.py
verificationStatus: VERIFIED
relevantFinding: 证实了 Harness² 提出的 8 大组件解耦体系（c1..c8）在真实代码中的落地实现，验证了严格的预算恒等式（R=4k rollouts, P=3k sessions）。确认了 Invariant 与 Domain 的双维度提示词解耦机制，以及基于无真值监督的黑盒匿名选择策略（Selection Pool 严格只含 k 个 finals，排除 base）。核实了在 OpenCode 与 Codex 两种基质上的沙盒权限隔离（OPENCODE_PERMISSION 与临时 XDG 隔离）及断点自愈逻辑。实测基准数据支持其在 GLM-5 与 Gemini 3.5 Flash 上的显著性能提升（WorkBuddy-Bench +11.5, JobBench +4.5, LAB +11.8%）。
projectApplicability: 本项目的 Hermes 多智能体平台与 RAG 知识库系统可直接借鉴其 8 大组件分层解耦思想（将常驻 System Prompt、Guardrails、Memory、Tool Guidance 与按需加载的 Skills、Subagents、Scripts、Hooks 分离），有效避免长上下文膨胀竞争；其 Invariant/Domain 双维度自省与无真值监督 Propose-Probe-Compose 闭环可直接作为生产级智能体离线自演进或测试集自愈的核心参考架构。
limitations: Harness² 依赖外部 CLI 工具链（如 Bun 运行 OpenCode 或 Codex-CLI），且默认环境偏向 Linux/Node.js/Docker 生态；在移植至 Java 21 纯隔离运行时环境下时，其基于 Bash/CLI 的探针执行基质必须改造为进程内 Java 调度或标准容器接口；此外其在 k>=2 时的高算力开销需结合 DeepSeek API 的成本与延迟预算进行限流管理。
```

---

## 六、法医核验总结与落地工程指引

1. **工程真实性认定**：经法医级逐行代码审计，`google-research/harness2` 是一套真实、严谨、完全闭环的工业级智能体护具递归优化系统，其论文中所述的双层递归、8 大组件、4k/3k 预算恒等式、沙盒权限隔离与评测增益均在代码中有完整且严密的实现，绝非概念性演示玩具。
2. **对当前系统架构契约的迁移建议**：
   - **完全契合项**：8 大组件的设计哲学完全符合本项目“系统常驻与按需加载分离”的原则，可直接用于重塑 Hermes 智能体的 Prompt 与工具装配流水线；
   - **改造建议**：将 `tree.py` 中的文件系统 Tree 抽象转化为 Java 21 Record 不可变对象，并通过常量时间散列进行组件指纹计算；将 Python `ThreadPoolExecutor` 替换为 Java 21 虚拟线程（Virtual Threads）驱动的并发调度器，以极低资源开销支撑 $R=4k$ 的高并发探针 Rollout。

以上审计报告全部核验完成，请主智能体（parent agent）审阅并纳入后续多智能体交叉对齐与架构契约决策！
