# Academic Agent Architecture Research & Top-Tier Publication Codex

# (面向毕业学术论文与 Agent 顶会/顶刊发表的创新型智能体优化架构研究与核心框架研发总纲)

本文件是本项目内所有 Agent 架构创新、算法设计、形式化建模、开源基准对标与核心实验框架研发的最高学术与工程总纲。

> **【最高学术定位声明（2026-11-27 正式修订）】**：
>
> 1. **唯一核心定位：毕业学术论文研究工具与 Agent 顶刊/顶会孵化基座**：本项目**彻底剥离商业交付平台与日常业务运维定位**，其唯一存在意义是作为**完成毕业学术论文的核心研究工具与可复现实验平台**，并服务于持续产出发表在 **NeurIPS、ICLR、ICML、ACL、EMNLP、KDD、WWW、IEEE TKDE、ACM TOIS、ESWA** 等顶级会议与期刊上的 Agent 方向学术论文（涵盖 Methodology、Formal Theoretical Analysis、System Architecture 与 Empirical Evaluation 全链条）。
> 2. **核心科研目标：探索创新性 Agent 优化架构机制**：所有研发算力与工程设计必须围绕**“如何提出并形式化验证具有学术原创性的 Agent 优化架构机制”**展开，聚焦认知编排、确定性状态机、神经符号工具推理、超球面记忆演化与可解释因果回溯等核心科学问题。
> 3. **当前阶段研发重心**：全量加码于**Agent 架构创新机制设计、数学形式化证明与核心框架主干代码（`src/main/java`）同构实现**；不消耗精力于传统业务后台 CRUD、商业化运维或前端视觉打磨。
> 4. **分级实验纪律：必要验证实验准许通过，仅禁止写论文阶段的全量实验（2026-11-27 永久修订）**：
>    - 距离明年毕业论文答辩尚有多轮 Agent 框架代际跨越迭代，现阶段直接跑写论文阶段的全量多数据集、多种子完整消融矩阵会导致实验数据因后续演进迅速过时失效；
>    - **准许通过（Necessary Verification Permitted）**：在每一轮核心框架突破后，**准许**为了确认机制真实效果、暴露“真实大模型 × 框架机制”耦合瓶颈或锚定下一代突破方向而开展**必要的轻量级验证实验**（涵盖离线确定性契约单测、单种子小样本如 `N=15–20/库` 的核心对照诊断探针与定向修复复测）；
>    - **严格禁止（Full-Scale Thesis Experiments Frozen）**：**严禁**主动建议、规划或启动面向**完整撰写论文阶段的全量实验**（全量数据集、多种子全矩阵正交消融及终稿统计显著性检验）；
>    - **唯一解锁条件**：唯有当用户明确下达指令**“我开始写论文了”**（或等价的明确全面开跑终稿实验指令）时，方才基于当时彻底封版的终极架构统一执行全量多数据集、多种子正交消融矩阵与统计检验。

---

## 一、适用范围与学术研发策略

1. **核心主干直接演进**：所有具有学术创新价值的 Agent 优化架构与算法机制，必须直接沉淀并编写进 `qknow-hermes-core`、`qknow-ai` 等核心模块的 `src/main/java` 主干框架中，严禁仅在测试目录堆砌一次性脚本。
2. **形式化理论与代码同构**：每一个架构组件与算法模块的设计，必须同时具备严密的数学定义（形式化元组、状态转移方程、定理/引理、复杂度或有界性上界）与高内聚、可复现的 Java 21 主干框架代码实现，确保代码结构可直接映射为论文中的 **System Architecture 图** 与 **Algorithm Block 伪代码**。
3. **架构代际跨越与必要轻量验证协同**：在“我开始写论文了”正式信号下达前的研发周期内，以**架构机制原创性、理论自洽性与确定性契约单测 100% 通过**为基本准入，同时**准许按需执行必要轻量级真实验证探针**以指导下一代架构迭代方向；面向终稿撰写的全量多数据集、多种子实证消融与统计显著性检验严格延后至封版写论文阶段统一集中执行。

---

## 二、Research-to-Implementation Gate 与 `anysearch` MCP 学术取证协议

### 2.1 先读项目

研究开始前必须先追踪项目既有真实实现，严禁先拍脑袋选论文算法再反向寻找用途。只读检查至少覆盖：

- 当前真实执行路径及关键调用关系。
- 输入、输出、不可变数据结构与持久化边界（PostgreSQL / Neo4j / Redis）。
- 运行配置、模型开关、Provider 与环境约束。
- Fallback、Fail-Open、Fail-Close、异常恢复与确定性边界。
- 既有契约测试、实验结论及已被证伪的设计路径。
- Java 21 隔离运行环境约束与前端视觉观测规范（`docs/design-system`）。

完成只读检查后，必须声明**本阶段唯一待验证科学假设**。该假设必须具体、可证伪，并明确揭示现有基准架构的缺陷或失败机制；无法锁定唯一假设时，不得开始定向文献与代码调研。

### 2.2 定向研究与 `anysearch` MCP 检索铁律

每个算法候选原则上精选 **3–6 个**与当前科学假设强相关的一手学术来源，按以下优先级检索与精读：

1. **Agent 与 AI/DB 顶会及顶刊原始论文**（NeurIPS、ICLR、ICML、ACL、EMNLP、KDD、WWW、IEEE TKDE、ACM TOIS、ESWA）。
2. **论文作者维护的官方代码仓库及固定 commit/tag**（核验真实实现与论文宣称是否一致）。
3. **代表性高水平学术开源 Agent 框架/项目**（如状态机编排、记忆图谱、代码沙盒、护栏验证基准仓库）。
4. **官方工程文档、模型 API 规范与标准评测基准说明**。

#### 2.2.1 `anysearch` MCP 工具标准检索协议 (Mandatory Tool Workflow)

当需要检索**顶刊/顶会论文、前沿学术预印本或学术开源仓库项目**时，**优先使用 `anysearch` MCP 工具链**（`get_sub_domains`、`batch_search`、`search`、`extract`、`score`），并严格遵守以下 4 步硬门禁：

1. **步骤 1 — 域发现（禁止猜测参数）**：凡涉足学术论文（`academic`）或开源代码仓库（`code`）垂直检索时，**必须先调用** `get_sub_domains(domains=["academic", "code"])` 获取合法的 `sub_domain` 与 `sub_domain_params` 约束。若某个标记为 `(required)` 的结构化参数当前不适用，必须显式传入空字符串 `""`，严禁省略或捏造。
2. **步骤 2 — 并行混合检索（`batch_search` / `search`）**：
   - 使用 `batch_search`（单次最多 5 条子查询）同时向 `academic`（检索原始论文/arXiv/顶刊条目）、`code`（检索官方开源仓库/Benchmark/实现符号）发起垂直检索；对跨领域或模糊前沿概念，同步附加一条无 `domain` 限制的通用查询作为覆盖兜底；
   - `query` 字段必须保持纯自然语言、单一检索意图；结构化过滤条件严禁混入 `query`，必须放入 `sub_domain_params`。
3. **步骤 3 — 全文精读取证（`extract`）**：搜索引擎摘要严禁直接充当理论依据。命中目标论文（如 `https://arxiv.org/html/<id>`）、官方文档子路由或开源仓库具体源码页后，**必须调用 `extract(url=...)` 抓取正文内容**，精读至公式推导、算法步骤、实验设定与真实源码实现。
4. **步骤 4 — 检索反馈闭环（`score`）**：遵守 `anysearch` 强制反馈契约——每累计 10 条成功检索及本轮检索结束生成最终回复前，**必须调用 `score`**，按结果对实际学术问题的支撑度提交每个 `request_id` 与 `1–5` 分值评价。

#### 2.2.2 取证前置铁律 (Reading-First Rule)

1. **未读全文时，必须先实际抓取，不得先降级。** 论文优先抓取 `https://arxiv.org/html/<id>` 全文或 `/abs/<id>`；代码必须核对固定 commit/tag 下的具体源文件；官方文档必须读取目标子路由原文。
2. **`PARTIALLY_VERIFIED` 仅表示“尚未读完关键部分”，不代表检索不可达。**
3. **仅在真实抓取失败时**（如付费墙、登录墙、PDF 二进制解析失败或连接超时），才可标注 `PARTIALLY_VERIFIED` 或 `NOT_VERIFIED`，且必须在 `limitations` 字段中写明**具体失败的 URL、HTTP 状态码或解析错误**，严禁使用“网络受限”等无证据的泛化归因。
4. **能抓取不等于已读完**：`VERIFIED` 必须严格对应实际精读过的论文章节、数学附录或源码文件与符号名。严禁将搜索摘要、二手博客或未核实代码包装为已验证证据。

论文来源必须记录：标题、作者、顶会或期刊名、年份、DOI 和/或 arXiv 标识、实际精读的章节/附录/实验部分。  
代码来源必须记录：仓库 URL、固定 commit 或 tag、开源许可证（优先 Apache-2.0 / MIT / BSD，严禁引入 GPL/AGPL 传染风险）、实际阅读的文件路径与核心符号。

### 2.3 Research Ledger

每个来源必须进入 Research Ledger，14 个必填字段缺失即无效（不适用填 `N/A`，严禁留空或臆测）：

```text
id
sourceType=paper|official-code|production-implementation|official-doc
titleOrRepository
authorsOrMaintainer
venueAndYear
doiOrArxiv
url
commitOrTag
license
filesOrSectionsRead
verificationStatus=VERIFIED|PARTIALLY_VERIFIED|NOT_VERIFIED
relevantFinding
projectApplicability
limitations
```

### 2.4 适用性分析

对每个候选思想必须分别剖析：

- 它原本解决的核心科学/架构问题及假设前提。
- 所依赖的数据分布、索引结构、推理算力、模型能力与运行条件。
- 与本项目条件（Java 21、`DeepSeek API`、`text-embedding-v4` 1024 维超球面空间）相同与不同之处。
- 可直接迁移借鉴、需要理论改造重塑、以及必须拒绝采纳的部分。
- 对推理准确性、时间/空间复杂度、Token 效率、确定性可复现性与实验对照价值的影响。

论文原数据集的质量结论严禁直接外推至本项目，若条件不一致必须显式降低证据强度。

### 2.5 候选方案比较

候选方案比较必须至少覆盖：当前系统实现（Baseline）、不改变核心算法的最小结构诊断方案、一个或多个与本阶段唯一科学假设直接相关的架构创新候选、以及保持 Baseline 或拒绝实施的选项。

对比维度固定为：**理论严谨性、架构创新度、可证伪性与数学边界、评测与数据需求、延迟与 Token 效率、实现复杂度与可复现性、消融对照价值**。被否决方案须记录精确否决理由，防止后续换名重提。

### 2.6 最小机制验证原则

方案演进遵循以下优先级：

1. 复用并重构项目现有核心架构路径与数学原语；
2. 利用 Java 21 标准库与已验证的底层能力（含 FFM / 持久化检查点 / 图谱索引）；
3. 仅实现能够直接检验当前唯一科学假设、具备独立论文贡献点的最小闭环创新机制；
4. 严禁因文献堆积而盲目引入无关训练流程、重量级外部框架或偏离假设的耦合模块。

### 2.7 科研调研阶段多智能体物理派发协议与提示词规范 (Multi-Agent Research Dispatch Protocol)

面对前沿论文精读、开源 Agent 仓库审计与架构适配建模等复杂调研任务时，主 Agent **严禁在单上下文中通过文字假装多视角自问自答**，**必须且只能调用原生 `invoke_subagent` 工具物理派发 3 个独立隔离沙盒智能体**，并在最终综合调研报告开头显式附上 3 个子智能体的 `conversationId` 与运行 transcript 链接存证。

#### 2.7.1 智能体 A：前沿文献与理论推导调研员提示词模板

```text
你被任命为【前沿文献与理论推导调研员 (Theoretical Literature Specialist)】。
你的任务是围绕当前阶段唯一待验证科学假设：{{HYPOTHESIS}}，对相关前沿顶级学术成果进行深度定向研读与数学推导。

【检索与文献标杆要求】：
1. 目标文献池：聚焦 NeurIPS、ICLR、ICML、ACL、KDD、IEEE TKDE、ACM TOIS、ESWA 等顶级会议与期刊论文。
2. 检索工具规范：优先使用 anysearch MCP 工具链进行检索——先调用 get_sub_domains(domains=["academic"]) 确认参数，再通过 batch_search / search 检索一手论文条目，并用 extract 抓取原文正文精读；本轮检索结束前必须按规约调用 score 提交反馈。
3. 重点查阅文献：{{PAPER_LIST_OR_TOPICS}}。必须精读至核心假设、数学定义、公式推导、附录证明与实验边界。

【核心产出职责】：
1. 提取论文核心理论假设、边界极值与反例反事实条件，揭示其架构局限性。
2. 推导适配本项目的关键定理或引理（如超球面信息重构损失上界、状态机有界终止性、工具循环可证明拒绝上界）。
3. 严格按照 AGENTS.md 2.3 节输出 14 字段 Research Ledger（真实标注 VERIFIED / PARTIALLY_VERIFIED / NOT_VERIFIED）。
4. 明确评估相关理论与本项目（千问 1024 维单位超球面空间、DeepSeek API 官方思考/缓存规约）的代数契合度与创新切入点。

【输出规范】：
全篇独占使用简体中文（数学符号、学术专有名词与标准英文字段名除外），输出结构严谨的理论调研报告。
```

#### 2.7.2 智能体 B：学术开源仓库与 Benchmark 代码法医核验员提示词模板

```text
你被任命为【学术开源仓库与 Benchmark 代码法医核验员 (Code Forensic & Benchmark Specialist)】。
你的任务是围绕当前阶段唯一待验证科学假设：{{HYPOTHESIS}}，对相关论文官方实现或代表性开源 Agent 框架仓库进行法医级源码审计。

【检索与审计标杆要求】：
1. 检索工具规范：如需定位或核验开源仓库与官方基准实现，优先使用 anysearch MCP 工具链——先调用 get_sub_domains(domains=["code", "academic"]) 获取 sub_domain 与参数，再通过 batch_search 检索目标仓库、固定 commit/tag 与具体代码路径，使用 extract 抓取关键源码原文核对，结束前调用 score 提交评价。
2. 目标代码库：{{REPO_URL_AND_COMMIT_TAG}}。确认开源许可证（Apache-2.0 / MIT / BSD 等），严禁引入 GPL/AGPL 传染性风险。
3. 真实源码核验：必须定位并阅读具体文件路径与核心符号（类、方法、数据结构），严禁仅凭 README 或二手博文推断。

【核心产出职责】：
1. 还原开源实现的数据结构、控制流循环、状态持久化边界与内存/并发机制。
2. 审计异常处理、超时/重试、工具拦截、记忆修剪等实现细节，识别“代码实现与论文宣称不一致”的隐含 Gap。
3. 提取其基准评测设置、真实指标表现、Token 成本与延迟特征，为我方毕业论文 Baseline 对标与消融设计提供参考。
4. 严格按照 AGENTS.md 2.3 节建立 14 字段 Research Ledger。

【输出规范】：
全篇独占使用简体中文（代码变量名、文件路径与类名除外），输出客观详实的开源代码法医审计报告。
```

#### 2.7.3 智能体 C：Agent 架构创新适配与论文方法论契约员提示词模板

```text
你被任命为【Agent 架构创新适配与论文方法论契约员 (Architecture Innovation & Methodology Specialist)】。
你的任务是综合智能体 A 的理论推导与智能体 B 的开源源码审计，面向本项目制定具备学术原创性、可映射为毕业论文 Methodology 章节的架构契约草案。

【项目真实研究基线】：
1. 运行与编译环境：唯一使用 Java 21 隔离环境（/Users/achilles/.sdkman/candidates/java/21.0.5-tem，严禁污染宿主 Java 17）。
2. 唯一生成模型：DeepSeek API 官方最新接口（如 deepseek-flash 及思考模式/Context Caching 官方协议）。
3. 唯一向量模型：阿里千问 Embedding（text-embedding-v4，1024 维单位超球面 S^1023，余弦几何）。
4. 观测与可视化界面：遵循 docs/design-system Apple iOS 26 Liquid Glass 规范，服务于论文实验观测与 Case Study 展示。

【核心产出职责】：
1. 精准适用性裁决：明确哪些机制可直接借鉴、哪些需重构创新、哪些必须拒绝（写明理论/工程否决理由）。
2. 输出候选架构对比矩阵（固定 7 维：理论严谨性、架构创新度、可证伪性与数学边界、评测与数据需求、延迟与 Token 效率、实现复杂度与可复现性、消融对照价值）。
3. 提出可沉淀于 src/main/java 核心框架的最小原创架构机制，阐明其核心科学贡献点及与现有开源 SOTA 的本质区别。
4. 拟定完整架构契约草案：含形式化模型定义、Baseline 设定、消融维度、契约单元测试与预期论文实证验证路径。

【输出规范】：
全篇独占使用简体中文，输出具备决策完备性的学术架构适配方案。
```

#### 2.7.4 主 Agent 交叉裁决与汇编协议

主 Agent 汇总 3 个物理子智能体报告后，必须执行交叉对齐：

1. **理论—源码一致性校验**：核对智能体 A 推导的数学公式、窗口参数、状态转移条件是否与智能体 B 审计出的真实算法时序严格一致；
2. **创新性与基线约束核验**：核对智能体 C 的架构契约是否真正切中 Agent 核心优化问题，且 100% 遵循 Java 21、DeepSeek 官方规范与 1024 维超球面单位几何；
3. **形成第五章标准提案**：汇编为结构化学术架构提案并经确认后，转入核心框架主干实现。

---

## 三、转化为架构与论文方法论实现契约

调研裁决后必须转化为可直接落入 `src/main/java` 主干框架的架构设计契约，明确以下 4 项要素：

- **形式化理论建模**：给出核心数学定义（状态空间、转移算子、目标函数、定理或引理上界）。
- **主干架构组件边界**：明确新增或演进的核心 Agent 框架类、接口、状态机流转及与现有模块的耦合边界。
- **不可变数据协议**：统一使用 Java 21 `record` 与强类型枚举表达执行凭单、推理轨迹与审验证据。
- **确定性契约测试**：编写不依赖外部脆性网络的 JUnit 5 契约单测，锁定核心状态机与数学不变量的可复现行为。

---

## 四、实施纪律

- **直接沉淀至主干框架**：一切服务于论文方法论的核心机制必须直接编写在 `qknow-hermes-core`、`qknow-ai` 等模块的 `src/main/java` 主干代码库中。
- **代码即论文 Method 原型**：主干类命名、职责拆分与算法流程必须逻辑清晰、可读性强，能够一一对应论文中的系统架构框图（Architecture Diagram）与伪代码（Algorithm Block）。
- **实验评估服务于论文论证**：构建可编程、参数化、可复现的开关与遥测埋点（如消融切换开关、指标追踪与凭单记录），为后期毕业论文及投稿论文的系统性消融与多数据集实验预留干净接口，当前开发期不在琐碎微调跑分上浪费时间。

---

## 五、架构设计与算法提案输出格式

每个阶段的 Agent 架构创新提案必须按以下标准五段式结构输出：

### A. 问题诊断与学术创新动机 (Motivation & Formal Gap)
指出当前基准架构在推理编排、状态确定性、工具推理或记忆演化上的科学短板，声明本阶段唯一可证伪假设与目标投稿级别创新点。

### B. Research Ledger
列出 3–6 个通过 `anysearch` / 一手抓取精读得到的顶会/顶刊论文与开源仓库来源，逐条填齐 14 字段并如实标注 `VERIFIED` / `PARTIALLY_VERIFIED` / `NOT_VERIFIED`。

### C. 核心数学建模与优化机制设计 (Formal Modeling & Optimization Mechanism)
给出严格数学定义、状态转移方程、定理/引理上界推演以及对应到本项目系统架构的核心创新机制。

### D. 主干框架实现蓝图 (`src/main/java`)
列出在核心模块（如 `qknow-hermes-core`、`qknow-ai`）中新增或演进的包路径、核心类、接口契约与关键执行逻辑。

### E. 契约验证与后期实验映射计划
给出保证编译与核心机制不变量通过的确定性测试命令，并说明该模块在毕业论文实验章节对应的指标、Baseline 与消融设计维度。

---

## 六、准入判定

架构提案满足以下条件即可进入主干代码实现：

- 具备明确、可证伪的 Agent 架构优化科学假设与数学形式化支撑。
- Research Ledger 包含 3–6 个经过核验的顶会/顶刊或高水平学术开源来源，无未核实臆测。
- 明确了在 `src/main/java` 核心主干中的完整架构落点与测试契约。
- 完全遵循 Java 21 隔离环境、DeepSeek API 官方规范与千问 1024 维超球面单位几何基线。

---

## 七、架构模型基线与几何规范 (Architecture Model & Geometric Baseline)

任何 Agent 在检索文献、提出理论模型或编写实验代码时，必须严格遵守以下系统固有基线，严禁凭通用惯例主观臆断：

1. **唯一生成模型**：本系统所有生成侧（推理、规划、对话、Tool Calling、总结生成）**唯一**使用 **DeepSeek API（含阿里云百炼兼容托管通道）**。
   - **官方文档唯一准则**：必须且只能以 DeepSeek 官方开发者文档（<https://api-docs.deepseek.com/zh-cn/>）与阿里云百炼 OpenAI 兼容接口规范为唯一权威基准；
   - **活跃主干模型**：官方直连统一接入活跃模型 ID（如 `deepseek-flash` 等），科研大规模实证允许经由阿里云百炼兼容托管通道（`https://dashscope.aliyuncs.com/compatible-mode/v1`）统一接入 `deepseek-v4.1-flash`，严禁调用已于 2026 年 7 月 24 日退役的旧模型名称（如 `deepseek-chat` / `deepseek-reasoner`）；
   - **思考模式规范**：深度长思维链在 DeepSeek 官方直连通过 `{"thinking": {"type": "enabled"}}` + `reasoning_effort` 控制，在阿里云百炼兼容端点自动适配为 `enable_thinking` + `reasoning_effort` + `max_completion_tokens`，并统一从同级响应字段 `reasoning_content` 解析思维链；思考模式下严禁设置 `temperature`、`presence_penalty`、`frequency_penalty`，`top_p` 严格限定在 0.95–1.0。
2. **唯一向量模型与超球面几何基线 (Hyperspherical Geometry Baseline)**：本系统所有向量化侧 **唯一**使用 **阿里千问 (Qwen) Embedding (`text-embedding-v4`)**。
   - **维度与单位范数约束**：向量空间锁定为 **1024 维单位超球面**（$\mathbb{S}^{1023} \subset \mathbb{R}^{1024}$），向量严格满足 $L_2$ 单位归一化约束（$\|\mathbf{v}\|_2 = 1$）；
   - **余弦度量恒等律**：所有聚类、质心归一化、图谱检索、相似性度量与信息损失上界推导，必须建立在超球面余弦几何（$\cos\theta = \mathbf{u} \cdot \mathbf{v}$）与恒等式 $d_{Euc}^2 = 2(1 - \cos\theta)$ 之上，严禁构造脱离单位超球面约束的未归一化欧式推导。
3. **彻底弃用声明**：本系统无任何本地部署的私有大模型（如 Llama、Qwen-Chat 等），亦已彻底停用 OpenAI/GPT API。任何基于“昂贵云端大模型 vs. 廉价本地小模型”双模型路由的前提在本项目中均不成立。
4. **唯一编译与运行环境 (Java 21 虚拟环境隔离铁律)**：
   - 后端全量模块统一且**唯一使用 Java 21** 编译与运行（父 POM 及子模块均显式锁定 Java 21）；
   - 宿主 Mac 系统全局默认环境维持 **Java 17**，本项目专用的 Java 21 为 SDKMAN 独立虚拟环境，固定路径：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`；
   - **严禁污染宿主环境**：严禁修改全局 JDK 或系统软链接。所有 Maven 构建、单测与后端命令必须显式前缀环境变量：`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
5. **实验可复现性、不可变凭单与密码学零时序泄漏存证铁律 (Reproducibility, Immutable Receipts & Cryptographic Zero-Leakage Rule)**：
   - **不可变凭单**：核心状态转移、路由决策与审验证据流必须采用纯 Java 21 `record` 不可变对象承载；
   - **常量时间防侧信道**：所有散列比较与审计验签逻辑必须使用 `MessageDigest.isEqual()` 常量时间比对，杜绝时序侧信道风险；
   - **三级冷备可逆环形隔离**：涉及记忆压缩、超图边淘汰、情节修剪或历史轨迹清理时，必须经过可逆软隔离环形缓冲区以支持反事实回放与状态恢复，严禁不可逆硬删除破坏实验可复现性。

---

## 八、Git 提交信息规范铁律 (Git Commit Message Iron Rule)

执行版本控制提交（`git commit`）时，必须严格遵守 `.github/copilot-instructions.md` 规范：

1. **结构格式**：严格采用 Conventional Commits 格式（`type(scope): 简短说明`）。
2. **语言要求**：提交标题（Title）与正文（Body）必须独占使用**简体中文**。
3. **空行间隔**：标题与正文之间有且仅有一个真实物理空行。
4. **正文要点**：使用真实物理换行符与 `-` 项目符号列出 **2–4 条**核心变更要点，严禁出现字面量 `\n` 转义字符。
5. **标准模板**：

   ```text
   type(scope): 中文标题

   - 要点 1
   - 要点 2
   - 要点 3
   ```

---

## 九、毕业论文定位与 Agent 优化架构四大核心研究方向总纲

本项目致力于构建**支撑毕业学术论文发表与后续 Agent 顶会/顶刊论文矩阵的核心优化架构与可复现实验底座**。所有技术选型、模块设计与实验平台界面必须严格服务于以下研究边界：

1. **彻底清除商业业务与力学发散**：
   - **严禁偏离学术主线**：不再为传统商业后台开发业务功能、计费系统或普通运维特性；
   - **全面叫停物理力学发散**：严禁向航天深空、在轨微重力、机械弹道、悬链线、流体偏微分方程等具身力学仿真方向发散；现存 `tech.qiantong.qknow.ai.embodied.*` 作为历史资产**永久冻结归档**，严禁继续扩展。
2. **四大核心 Agent 优化架构研究方向 (Four Core Research Pillars)**：
   后续所有创新机制探究、数学建模、代码撰写与论文产出，均严格框定在以下四大研究方向之内：
   - **方向一：自适应认知编排与动态推理拓扑优化 (Adaptive Cognitive Orchestration & Dynamic Reasoning Topology)**
     - *核心科学问题*：面向异质任务复杂度，如何在计算开销（Token/Latency）与推理精度间实现在线帕累托最优调控？
     - *架构与算法主轴*：基于 1024 维超球面几何特征与期望效用后验的超网自适应路由（`SupernetRouter`）、多模态推理深度弹性控制（Fast Direct vs. Deep Thinking 开关调度）、多智能体协同拓扑动态演化（Single-ReAct / Swarm / Debate / Hierarchical Mesh）、Propose-Probe-Compose (PPC) 闭环架构自演化及面向前缀复用的 64-Token KV Cache 对齐调度。
     - *研究落点与论文贡献*：聚焦 `qknow-hermes-core` 认知编排内核，形成可量化的复杂度—效用权衡定理与动态拓扑切换实验基准。
   - **方向二：持久化确定性状态机与因果时间旅行执行内核 (Durable Deterministic State Machine & Causal Time-Travel Execution)**
     - *核心科学问题*：在长周期、易中断的多智能体并发执行流中，如何保证超步状态演化的精确一次（Exactly-Once）、防脑裂活性与反事实零幽灵分叉？
     - *架构与算法主轴*：基于 PG Step Journal（`PgStepJournalStateCheckpointer`）与 `DeltaChannel` 增量状态补丁的持久化状态图引擎、基于单调 `FencingToken` 与租约超时（TTL）自愈的分布式调度活性证明、以及面向多智能体规划回溯的无幽灵变量因果时间旅行分叉（`forkBranchFromJournal`）。
     - *研究落点与论文贡献*：聚焦工作流/状态图持久化主干与并发 Frontier 事务引擎，支撑长程 Agent 可靠性与反事实归因论文实验。
   - **方向三：神经符号工具推理、代码沙盒与形式化安全守卫 (Neuro-Symbolic Tool Reasoning, Code-Mode Sandbox & Formal Guardrails)**
     - *核心科学问题*：如何克服多工具膨胀导致的上下文噪声与幻觉调用，并在不依赖高代价二次大模型判别的前提下提供可证明的工具调用有界性与安全守卫？
     - *架构与算法主轴*：Code-Mode MCP 延迟工具发现与隔离沙盒组合执行（以轻量可执行脚本/计划替代全量 Schema 注入）、基于霍尔逻辑前置/后置条件的形式化工具契约检查、局部致密有界循环拒绝守卫（Locally-Dense Cycle Guard，消除窗口淘汰竞态漏洞）、密码学零明文泄漏自验真凭单与输入/输出双向安全护栏。
     - *研究落点与论文贡献*：聚焦工具网关、Code-Mode 执行器与 `qknow-ai` 审计守卫模块，构建工具调用效率与可证明安全性（Formal Safety Guarantees）学术贡献。
   - **方向四：超球面流形记忆演化与双时态图谱推理架构 (Hyperspherical Manifold Memory & Bi-Temporal GraphRAG)**
     - *核心科学问题*：当 Agent 跨长周期积累海量片段与演化事实时，如何在严格的单位超球面几何约束下实现低重构损失的记忆压缩、时态一致的因果推理与亚毫秒级向量召回？
     - *架构与算法主轴*：基于 $\mathbb{S}^{1023}$ 单位超球面余弦几何与时间演化矩阵的双时态海马体超图（Bi-Temporal Hippocampal Hypergraph）、具备理论重构损失上界保证的谱聚类归一化质心记忆凝练、三级冷备可逆遗忘环形缓冲区、以及结合多跳 Personalized PageRank (PPR) 与 Java 21 FFM + Rust SIMD 的硬件级加速检索引擎。
     - *研究落点与论文贡献*：聚焦海马体超图记忆模块与 GraphRAG 检索引擎，面向长期记忆保真度、时态冲突消解与硬件协同优化撰写高水平实证分析。
3. **实验观测与可视化控制台定位**：
   前端交互画布、`H-NSCDS` 认知遥测台、时间旅行分叉工作台与神经符号审计界面，统一定位为**毕业论文与学术论文的实验可观测平台（Experiment Telemetry Studio）、Case Study 可视化取证工具与答辩交互演示前端**。

---

## 十、DeepSeek 官方开发者文档唯一准则铁律 (DeepSeek Official Documentation Iron Rule)

任何涉及 DeepSeek 模型选型、参数配置、思考链控制、Prompt 构造或 Context Caching 机制的研究与实现，**必须且只能以 DeepSeek 官方开发者文档（<https://api-docs.deepseek.com/zh-cn/>）为唯一最高准则**：

1. **强制前置抓取核实官方文档**：编写涉及模型接口的实验或架构代码前，必须通过 `anysearch`（如 `search` / `extract`）或网页读取工具拉取 `https://api-docs.deepseek.com/zh-cn/` 目标子路由原文，严禁依赖过时本地记忆。
2. **禁止使用已退役旧模型名**：官方已于 2026 年 7 月 24 日下线 `deepseek-chat` 与 `deepseek-reasoner`；当前系统统一使用官方最新推荐活跃模型 ID（如 `deepseek-flash` 等）。
3. **思考模式官方参数协议**：思考链通过 `extra_body: {"thinking": {"type": "enabled"}}` 或 `reasoning_effort` 显式开关，思维链内容从同级响应字段 `reasoning_content` 读取；开启思考模式时禁止传入 `temperature`、`presence_penalty`、`frequency_penalty`，`top_p` 需保持在 `0.95–1.0`。
4. **1M 上下文与 Context Caching 对齐设计**：在实验架构设计中必须保持系统提示词与固定前缀的 64-Token 分块对齐稳定性，充分利用官方 Context Caching 降低多轮长轨迹评测成本与首字延迟。

---

## XI / 十一、毕业论文与 Agent 顶会/顶刊多智能体交叉评审工作流铁律

每当阶段性输出学术手稿（如毕业论文章节草稿、顶会/顶刊投稿手稿、实验设计方案、新版 PDF/LaTeX 或完整核心架构评审报告）并需要严格审稿评估时，**必须且只能执行本三方物理隔离交叉评审工作流**，严禁单一 Agent 泛泛评价。

### 11.1 物理派发与审稿存证铁律

1. **物理派发强制执行**：主 Agent 必须通过 `invoke_subagent` 并行启动 3 个具备独立 `conversationId` 的子智能体沙盒执行独立审计，并在综合裁决开头完整披露 3 个子智能体的 `conversationId` 与 transcript 链接。
2. **学术对比取证要求**：审稿智能体如需比对最新顶会/顶刊标杆文献（NeurIPS / ICLR / ACL / IEEE TKDE / ESWA）或对照开源 Benchmark，**必须通过 `anysearch` MCP 工具链检索提取一手原始出处**（遵循 2.2.1 节 4 步规约），严禁凭空编造引用或指标。

### 11.2 三方专业审稿智能体职责划分

1. **智能体甲：理论与形式化建模专审员 (Theoretical & Formal Modeling Reviewer)**
   - **核查重点**：
     - 形式化框架定义（元组、状态空间、优化目标函数）是否存在参数脱节、定义退化或无实际约束的“装饰性公式”；
     - 核心定理与引理（如 1024 维 $\mathbb{S}^{1023}$ 超球面聚类/遗忘信息损失上界、有界工具循环拒绝定理、状态机活性与无幽灵分叉性质）的假设条件、反例边界与推导严密性；
     - **理论—代码—伪代码三方同构**：逐行检查论文中的 Algorithm 伪代码与 `src/main/java` 主干代码实现是否时序一致（严查诸如在淘汰旧元素前提前触发阈值计数等边界 Bug）。
2. **智能体乙：实证评测与消融实验专审员 (Empirical Evaluation & Ablation Reviewer)**
   - **核查重点**：
     - **法医级数据一致性审计**：逐字比对底层日志/CSV/JSONL 原始实验结果与手稿表格数据，零容忍旧版数据残留、数据粉饰或悬空图表（Orphan Floats）；
     - **Baseline 公平性与 SOTA 覆盖度**：检查对比基线是否包含近期有代表性的顶会/顶刊方法与开源 Agent 框架，且在同口径模型（DeepSeek / Qwen Embedding）、同预算条件下公平对比；
     - **消融实验矩阵完备性**：验证各核心创新模块是否均有独立消融（w/o Module A/B/C），是否能从准确率、幻觉率、Token 开销、端到端延迟等维度呈现清晰的帕累托改进；
     - **统计检验规范性**：审核重复试验方差、置信区间及非参数显著性检验（如 Wilcoxon / Friedman / Nemenyi 检验）的严谨性，如实报告负面结果与失效边界而非强行拔高。
3. **智能体丙：Agent 架构创新与系统可复现性专审员 (Architecture Novelty & Reproducibility Reviewer)**
   - **核查重点**：
     - **核心架构原创性界定**：从学术贡献视角审视该优化架构与已有开源 Agent 框架或经典范式（ReAct / Plan-and-Solve / Reflexion / GraphRAG）的本质差异，提炼不可替代的机制创新点；
     - **端到端 Case Study 因果闭环**：核查微观案例轨迹是否具备可追溯的多步推理链、工具前置/后置条件拦截记录、Step Journal 超步检查点或图谱多跳证据闭环（严禁黑盒流水账式案例）；
     - **可复现性与效度威胁防御**：检查超参数设定、提示词模版、数据集切分、Java/Rust 运行环境锁与实验隔离条件是否足以支撑第三方研究者完整复现；审核效度威胁（Threats to Validity）章节是否诚实剖析 Construct / Internal / External Validity，并严格采用“创新贡献总结—机理洞察—局限与未来演进”结构收束全文 Conclusion。

### 11.3 三方交叉对齐裁决协议

主 Agent 在收齐三方独立审稿意见后，执行交叉互证：

1. 检查理论假定的窗口、阈值、几何度量假设是否与实证脚本参数及主干源码保持严格一致性；
2. 检查实证中暴露出的短板或退化题型，是否在理论边界分析与效度威胁部分给出了诚实且自洽的机理解释；
3. 汇总形成包含**总体录用/答辩就绪度裁定、阻断性理论与实验漏洞清单、逐章节微观改写指令**以及 **JIT 按需撰写指南**的综合审稿报告。

### 11.4 编写 Agent 的 JIT 按需参考规范

润色或撰写特定论文章节时，实行单点精准上下文加载（结合 `/research-paper-writing` 规范如有对应参考文件）：

- **Introduction / Related Work** $\rightarrow$ 聚焦研究动机、科学缺口界定与顶会/顶刊最新文献脉络；
- **Methodology / Formal Proof** $\rightarrow$ 聚焦数学符号一致性、定理引理推导与源码/Algorithm Block 对齐；
- **Experiments / Ablation** $\rightarrow$ 聚焦实验协议可复现性、原始数据保真、消融矩阵与统计检验；
- **Case Study / Threats to Validity / Conclusion** $\rightarrow$ 聚焦因果闭环案例、效度防御与学术边界升华。

### 11.5 毕业论文与顶会/顶刊三方专审智能体标准 Prompt 模板

#### 11.5.1 智能体甲：理论与形式化建模专审员 Prompt 模板

```text
你被任命为【智能体甲：理论与形式化建模专审员 (Theoretical & Formal Modeling Reviewer)】。
请按照 AGENTS.md 第十一章规范，以 NeurIPS / ICLR / IEEE TKDE 审稿人标准，对手稿或架构提案的数学理论与算法同构性进行独立审计。

【审计依据与外部对标】：
1. 查阅输入资产：{{MANUSCRIPT_OR_PLAN_FILES}} 与对应的 src/main/java 核心代码文件。
2. 如需比对权威理论参考文献，请使用 anysearch MCP 工具链（get_sub_domains -> batch_search -> extract -> score）核验原文公式与假设。

【审查核心任务】：
1. 检查形式化符号、系统元组定义与目标函数是否存在语义断裂、退化解或无用装饰公式。
2. 审核核心定理与引理（包括 1024 维超球面单位向量空间性质、信息损失上界、工具调用有界拒绝、状态机活性与防脑裂保证）在极限或对抗反例下的严密性。
3. 核查论文 Algorithm 伪代码与项目实际 Java 21 主干源码的时序一致性，排查幽灵计数或并发竞态偏差。
4. 给出具体的数学修正建议与伪代码修订方案。

【输出规范】：
全篇独占使用简体中文（数学公式与代码符号除外），给出严谨、直击要害的审稿报告。
```

#### 11.5.2 智能体乙：实证评测与消融实验专审员 Prompt 模板

```text
你被任命为【智能体乙：实证评测与消融实验专审员 (Empirical Evaluation & Ablation Reviewer)】。
请按照 AGENTS.md 第十一章规范，对实验基准设计、数据真实性、消融对照与统计检验进行法医级独立审计。

【审计依据与外部对标】：
1. 查阅输入资产：{{EVAL_FILES_OR_PLANS}}（原始日志、评测结果文件、单测报告与论文图表）。
2. 如需核对学术界公开 Benchmark 划分、指标口径或 SOTA 基线原文，请调用 anysearch MCP 工具链取证核实并提交 score 反馈。

【审查核心任务】：
1. 法医级数据一致性审计：核对手稿图表数值与原始实验产出，杜绝陈旧数据残留、孤立未引用图表与未经验证的外推宣称。
2. Baseline 公平性审计：检查是否已覆盖高相关开源 Agent/RAG 代表性基线，且在统一模型与检索约束下公平比较。
3. 消融实验矩阵审查：检验各核心创新模块是否均完成了有控变量的独立消融，并从效果、延迟、Token 与稳定性多维度呈现帕累托权衡。
4. 统计规范性审查：评估多轮运行波动度、非参数显著性检验（如 p 值与效应量）以及失败案例/零分边界是否诚实披露。

【输出规范】：
全篇独占使用简体中文，输出客观、量化、数据零水分的实验审计报告。
```

#### 11.5.3 智能体丙：Agent 架构创新与系统可复现性专审员 Prompt 模板

```text
你被任命为【智能体丙：Agent 架构创新与系统可复现性专审员 (Architecture Novelty & Reproducibility Reviewer)】。
请按照 AGENTS.md 第十一章规范，站在毕业论文评审专家与 Agent 顶会元审稿人（Area Chair）视角，评估本项目架构的原创学术价值与可复现质量。

【审计依据与外部对标】：
1. 查阅输入资产：{{SYSTEM_OR_PAPER_FILES}} 与系统核心实现路径。
2. 请使用 anysearch MCP 工具链检索近两年 Agent 优化架构代表性顶会论文与开源框架，核验创新边界的新颖性。

【审查核心任务】：
1. 凝练并检验原创学术贡献：与主流开源 Agent 架构相比，本项目在认知路由、确定性状态机、安全工具推理或超球面记忆上的机制创新是否清晰成立、有无同类工作重名重复之嫌。
2. 审查 Case Study 因果闭环：核验论文案例是否呈现完整可控的状态演化轨迹、工具守卫判断依据与图谱归因链条，而非空洞定性展示。
3. 审查全栈实验可复现性：检查核心依赖、环境隔离参数（Java 21 / Rust FFM / PG / Neo4j）、模型配置与可视化观测工作台是否具备可复现交付说明。
4. 审查效度威胁与 Conclusion 结构：评估 Threats to Validity 是否深入分析构念效度、内部效度与外部效度限制，Conclusion 是否完成清晰的“学术贡献—机理启示—研究展望”收束。

【输出规范】：
全篇独占使用简体中文，输出具有高标准学术指导价值的综合评审意见。
```

---

## 十二、顶级 Apple iOS 26 Liquid Glass 前端设计系统规范铁律 (Apple Liquid Glass Design System Iron Rule)

前端与实验工作台是展示本课题认知演化轨迹、论文 Case Study 因果回溯与答辩交互体验的科研观测窗口。任何涉及前端 UI/UX 改造或观测面板优化的 Agent，**必须且只能以 `docs/design-system/00_MASTER_frontend_guide.md` 和 `tokens/ios26-tokens.json` 为最高权威准则**，坚决杜绝平庸与违背光学材质体系的错误实现：

### 12.1 文档权威链

当文档或实现之间出现冲突时，严格按以下优先级取值：

1. **最高权威**：`tokens/ios26-tokens.json`（设计 Token 唯一真实数据源）；
2. **实现层准则**：`frontend/src/assets/system/styles/ios26-liquid-glass.scss`（材质统一样式源）；
3. **指南层**：`docs/design-system/00_MASTER_frontend_guide.md`；
4. **废弃预警**：严禁采信 `01/02/03/04` 过程稿中已证伪的错误数据（如 20/30px 模糊、加粗标题、带扩散大投影体系等）。

### 12.2 前端设计系统四大绝不可违背铁律

1. **铁律一：玻璃容器及其祖先，绝对禁止创建层叠上下文 (Stacking Context Ban)**：
   - 任何层叠上下文均会阻断 `mix-blend-mode`，使 `color-dodge` 退化为暗沉灰蒙层；
   - 严禁在玻璃容器或其父级祖先使用 `transform`（如 `translateZ(0)`、`translateY(-2px)`）、`will-change: transform`、`opacity < 1`、`filter`、`isolation: isolate` 及容器级 `z-index`；
   - 悬停/按压交互严禁使用位移动画，统一改用背景亮度微调、无层叠边框微亮或轻量触感反馈。
2. **铁律二：单层 `backdrop-filter` 禁止冒充液态玻璃 (Dual-Layer Optical Formula)**：
   - 必须严格执行「高光层 + 统一 50px 模糊层」的双层光学配方，模糊层同时承载 `backdrop-filter: blur(50px)` 与 `mix-blend-mode: color-dodge`；
   - 模糊半径统一锁定 `50px`，材质厚薄（Ultrathin / Thin / Regular / Thick）仅由填充不透明度与混合模式控制。
3. **铁律三：Headline 独占 590 字重，其余文字全部 400 (Optical Typography Rule)**：
   - 严禁对标题滥用 `font-weight: 600/700`；
   - 层级依靠字号阶梯、颜色深度、Apple 原生光学字距（Letter-spacing）与系统字体栈传达。
4. **铁律四：界面主体绝对零投影，色彩严格作为功能信号 (Zero-Shadow & Signal Color Rule)**：
   - 严禁使用 Material Design 样式的扩散大阴影，纵深由双层玻璃厚度与边缘高光呈现；
   - 系统强调色（如 `#0088ff`、`#34c759` 等）仅用于交互态、状态信号与科学指标强调，严禁铺设成背景装饰。

### 12.3 观测界面设计五大核心原则

- **① 层级靠材质，不靠阴影**：通过 Thin / Regular / Thick 表达界面纵深；
- **② 色彩是信号，不是装饰**：保持数据图表与状态观测主画布纯净中性；
- **③ 排版靠光学修正，不靠粗暴加粗**：严格遵循 590 字重规范与精确字距；
- **④ 响应敏捷优先于繁冗动效**：交互反馈追求 150ms 级即时触感；
- **⑤ 内容与材质“浸润”融合**：呈现高对比度、低干扰度的科学观测读数体验。
