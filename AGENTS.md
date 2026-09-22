# Research-to-Implementation Gate

本文件是项目内所有算法候选、算法优化和算法调参工作的强制前置门禁。任何 Agent 在提出或实施算法变更前，必须先完成本文件规定的科研工作。研究来源只能帮助选择可验证方案，不能替代本项目的运行证据。

## 一、适用范围

以下工作必须通过本门禁：

- 引入新的算法候选或替换现有算法。
- 改变现有算法机制、数据流、排序逻辑或决策规则。
- 调整会影响算法结果的阈值、窗口、topK、权重、fixture 或 qrel。
- 根据首次实验结果重新设计数据、指标或通过条件。

纯环境诊断、普通缺陷修复以及执行已经冻结且获批的算法契约，不视为新的算法候选；但只要需要改变算法、阈值、窗口、fixture 或 qrel，就必须重新进入本门禁。

## 二、Research-to-Implementation Gate

### 2.1 先读项目

研究开始前必须先追踪项目的真实实现，不得先选论文算法再反向寻找项目用途。只读检查至少覆盖：

- 当前真实执行路径及关键调用关系。
- 输入、输出、数据结构和持久化边界。
- 运行配置、开关、provider、外部依赖和环境约束。
- fallback、fail-open、fail-close、异常和部分结果行为。
- 既有测试、诊断、实验结论及已经否决的方案。
- 生产路径与测试、mock、shadow、diagnostic 路径之间的差异。

完成检查后，只允许声明一条本阶段唯一待验证假设。该假设必须具体、可证伪，并明确当前失败机制；无法锁定唯一假设时不得开始定向研究。

### 2.2 定向研究

每个算法候选原则上选择 3–6 个与当前假设直接相关的来源，按以下优先级检索和阅读：

1. 顶级会议或期刊的原始论文。
2. 作者维护的官方代码及固定 commit/tag。
3. 成熟开源项目的生产实现。
4. 官方工程文档、故障说明和评测方法。

禁止无边界堆积文献。每个来源必须进入 Research Ledger，并记录实际读取范围。

论文来源必须记录：

- 标题。
- 作者。
- 会议或期刊。
- 年份。
- DOI 和/或 arXiv 标识。
- 实际读取的章节、附录或实验部分。

代码来源必须记录：

- 仓库 URL。
- 固定 commit 或 tag。
- 许可证。
- 实际阅读的文件和符号。

无法读取全文、固定版本或源代码时，必须标记 `NOT_VERIFIED`，说明缺失内容及其对结论的限制。不得把摘要、二手文章、搜索片段或未读取代码表述为已经验证的证据。

### 2.3 Research Ledger

每条 Research Ledger 记录必须包含：

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

不适用的字段使用 `N/A`，不得省略字段或用猜测补齐。

### 2.4 适用性分析

对每个候选思想必须分别说明：

- 它原本解决的问题。
- 所依赖的数据、训练、索引、模型、硬件和运行条件。
- 与当前项目条件相同和不同之处。
- 可以直接采用、需要改造和应拒绝的部分。
- 对正确性、延迟、成本、复杂度、运维和回滚的影响。

论文或上游项目的质量结论不得直接外推到本项目。缺少相同数据、训练、索引或运行条件时，必须明确降低证据强度。

### 2.5 候选方案比较

候选比较必须至少包含：

- 当前实现作为 baseline。
- 不改变算法的最小诊断或数据修正方案。
- 一个或多个与唯一假设直接相关的算法候选。
- 保持现状或拒绝实施的选项。

比较维度固定为正确性、可证伪性、数据需求、延迟、成本、实现复杂度、依赖变化、回滚风险和生产影响。被拒绝的方案必须简要记录拒绝理由，防止后续换名或重新包装后重复引入。

### 2.6 最小算法选择

方案选择遵循以下顺序：

1. 复用项目现有算法、数据结构和调用路径。
2. 使用标准库或平台现有能力。
3. 使用已经安装且已验证的依赖。
4. 仅实现能直接验证当前唯一假设的最小机制。

不得因为论文存在而引入训练流程、新模型、新依赖、大规模重构或与当前假设无关的扩展。无法证明新增机制比现有路径更小、更直接时，保持 baseline。

## 三、转化为项目契约

研究结论必须转换为一份 decision-complete、可复现、可证伪的项目契约。契约必须固定：

- 一条明确的算法假设。
- 输入、输出、数据来源和不可变量。
- baseline 与 candidate 的精确定义。
- 反事实或消融设计。
- 训练集、Selection、Holdout、qrel 和运行结果之间的数据泄漏防护。
- 指标定义、聚合方法和改善/退化判据。
- 延迟、token、外部调用、网络、数据库和成本预算。
- 固定失败码、INVALID 语义和停止条件。
- 最小实现文件集合及明确禁止修改的边界。
- 完整、可复制的验证命令和精确测试计数。
- 后续生产化、A/B、promotion 和线上启用的独立授权边界。

如果研究无法形成上述可证伪实验，必须输出 `RESEARCH_GATE_BLOCKED`，说明缺失证据或无法控制的变量，并停止进入实现。

## 四、实施纪律

- 第一回合只能进行只读检查、定向研究并提交 decision-complete 计划。
- 未获得用户明确批准前，不得修改代码、fixture、配置或 artifact，不得运行正式一次性入口。
- 获批后只能实施批准计划中的最小文件集合和验证命令。
- 不得根据首次结果调整 fixture、qrel、阈值、窗口、topK、通过条件或数据分布。
- 环境修复、调参、算法变更、生产实现、A/B、promotion 和线上启用必须分别授权。
- 测试失败必须区分环境故障、实现缺陷和算法假设不成立，不得通过修改数据或期望值掩盖失败。
- 研究结论不能替代本项目的 contract test、counterfactual、Freeze、Diagnostic 或生产证据。

## 五、强制输出格式

每个算法候选的首轮研究报告必须严格按以下顺序输出：

### A. 当前代码与失败机制

记录真实执行路径、数据边界、配置、失败模式、历史实验结论，以及本阶段唯一待验证假设。

### B. Research Ledger

列出 3–6 个定向来源及全部必填字段，明确 `VERIFIED`、`PARTIALLY_VERIFIED` 或 `NOT_VERIFIED`。

### C. 可迁移与不可迁移结论

逐项说明可直接采用、需要改造和必须拒绝的研究结论，以及项目条件差异。

### D. 候选方案比较

用统一维度比较 baseline、最小诊断方案、候选算法和保持现状选项，并记录拒绝理由。

### E. 推荐的最小算法

只推荐能直接验证唯一假设的最小机制；写明为什么不需要更复杂的模型、依赖或重构。

### F. 实验与实现计划

给出固定契约、反事实/消融、泄漏防护、指标、预算、失败码、最小文件集合和复现命令。

### G. 风险、停止条件和后续授权边界

列出残余风险、立即停止条件以及调参、生产化、A/B、promotion 和线上启用的独立授权要求。

## 六、准入判定

只有同时满足以下条件，算法候选才可请求实施授权：

- 已追踪真实项目路径并锁定唯一可证伪假设。
- Research Ledger 包含 3–6 个高相关来源，且未伪造验证状态。
- 已完成项目适用性和候选方案比较。
- 推荐方案是验证当前假设所需的最小机制。
- 已形成包含 baseline、candidate、反事实、泄漏防护、指标、预算、失败码和停止条件的完整契约。
- 已明确最小修改范围、复现命令和后续授权边界。

任一条件不满足时输出 `RESEARCH_GATE_BLOCKED`，不得进入实现。

## 七、架构模型基线 (Architecture Model Baseline)

任何 Agent 在阅读本项目或制定技术方案时，必须严格遵守以下模型与生态基线假设，绝不允许凭借行业惯例进行主观臆断：

1. **唯一生成模型**：本系统的所有生成侧（Chat / Generation / RAG 检索对话 / Tool Calling）**唯一**使用的是 **DeepSeek API**。
2. **唯一向量模型**：本系统的所有向量化侧（Embedding）**唯一**使用的是 **阿里千问 (Qwen) Embedding**。
3. **彻底弃用声明**：项目中绝无任何本地部署的大语言模型（如 Llama, Qwen-Chat 等），且已彻底弃用 OpenAI/GPT API，原因在于网络延迟与成本考量。所有关于“昂贵大模型与廉价本地小模型之间路由”的假设在本系统均不成立。
4. **唯一编译与运行环境 (Java 21 虚拟环境隔离铁律)**：
   - 本项目后端全量模块统一且**唯一使用 Java 21** 编译与运行（父 POM 及全部子模块均显式锁定 Java 21）。
   - 用户的 Mac 主机系统全局环境保持为 **Java 17**。本项目专用的 Java 21 是由 SDKMAN 管理的独立隔离虚拟环境，绝对路径固定为：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
   - **严禁污染主机环境**：任何 Agent 或脚本绝对不允许全局覆盖系统默认 JDK，禁止创建或修改系统全局软链接。所有 Maven 编译、单元测试与后端执行，必须且只能通过局部前缀显式传入环境变量：
     `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`

## 八、Git 提交信息规范铁律 (Git Commit Message Iron Rule)

任何 Agent 在本项目中执行代码与文档提交（git commit）时，必须严格遵守 `.github/copilot-instructions.md` 规范准则，绝不允许提交单行或格式随意的信息：

1. **结构格式**：严格使用 Conventional Commits 格式（`type(scope): 简短说明`）。
2. **语言要求**：提交标题（Title）与正文（Body）必须独占使用**中文（简体中文）**。
3. **空行间隔**：标题与正文之间必须且只能由**一个真实物理空行**隔开。
4. **正文要点**：
   - 正文必须使用真实物理换行符与 `- ` 项目符号列出具体变更要点；
   - 严禁在提交信息中出现字面量 `\n` 转义字符；
   - 正文要点优先保持在 **2-4 条**核心要点。
5. **精炼聚焦**：标题必须简短集中、聚焦核心，正文清晰说明修改的核心动机与关键变更。
6. **标准模板形态**：
   ```text
   type(scope): 中文标题

   - 要点 1
   - 要点 2
   - 要点 3
   ```

## 九、业务定位与领域边界铁律 (Business Domain & Agent Core Boundary Iron Rule)

任何 Agent 在本项目中规划后续研发阶段、提出算法假设或制定方案时，必须严格遵守以下业务定位与领域边界铁律，坚决杜绝概念漂移 (Scope Creep) 与脱离业务实际的过度力学推演：

1. **唯一核心业务定位**：
   本项目唯一且绝对的系统定位为**“企业级 AI-Native RAG 知识库与软件智能体编排平台 (Knowledge Hub)”**。系统所有的研发重心、架构演进与技术资源，必须且只能服务于组织知识资产管理、高效精确检索、大模型认知推理、工具链编排与人机协同交互。
2. **力学与机器人硬件彻底止步与资产封存声明**：
   - **全面叫停力学发散**：严禁任何 Agent 继续向航天深空、在轨微重力、机械弹道跳跃、悬链线缆绳力学、流体力学偏微分方程、微纳物理接触等纯物理力学仿真与机械工程控制方向做脱节发散；
   - **彻底废除空间在轨课题**：即刻叫停并作废原拟定的 Phase 84（空间在轨失重动力学与漂浮基反作用力矩平衡）及一切后续硬件动力学设想；
   - **具身力学代码资产封存**：现存的 `tech.qiantong.qknow.ai.embodied.*`（含力觉、阻抗、大变形、软体、跳跃、系留、微纳等模块）作为“具身物理验证沙箱 (Embodied Physics Sandbox)”技术资产**永久冻结归档**，继续作为全量回归测试与历史高难度算法工程资产保持运行，但严禁继续向非软件业务的极端物理环境扩展。
3. **开发权重 100% 加码 Agent 业务核心**：
   后续所有研发阶段必须 100% 聚焦于 Agent 业务核心主战场，划定以下四大战略攻坚支柱：
   - **支柱一：复杂业务 Agent 认知与编排 (Cognitive Orchestration)**：深度重构与增强 Hermes 内核、多智能体协同与竞争网络（Swarm / Debate / Teamwork）、状态机工作流容错与自愈；
   - **支柱二：生产级企业 MCP 工具生态 (Enterprise MCP Ecosystem)**：真实打通企业 API、数据库动态交互、中间件适配、本地工具运行时安全与工具链智能选择；
   - **支柱三：高保真 RAG 知识引擎与多模态图谱 (Advanced RAG & Multimodal Knowledge)**：超长文档层次化理解、图谱子图推理与 GraphRAG 深度融合、极低延迟流式打字机与上下文对齐；
   - **支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)**：可视化 DAG 画布沉浸式调试、节点级状态回溯、人机协同审批 (HITL) 交互优化与单色钛金毛玻璃体验升华。

## 十、DeepSeek 官方开发者文档唯一准则铁律 (DeepSeek Official Documentation Iron Rule)

任何 Agent 在涉及 DeepSeek 相关的任何知识库构建、API 接口调用、参数配置、思考链 (Thinking) 控制、模型选型、Prompt 规范或任务编排设计时，**必须且只能以 DeepSeek 官方开发者文档（https://api-docs.deepseek.com/zh-cn/）为唯一最高准则**：

1. **强制前置阅读官方文档**：在提出方案、设计契约或编写代码前，必须显式检索并阅读官方最新文档（通过网络抓取或检索工具如 `read_url_content` 获取 `https://api-docs.deepseek.com/zh-cn/` 及相关子路由，如 `/guides/reasoning_model`、`/api/create-chat-completion` 等），确保获取第一手权威信息。
2. **彻底禁止使用过时本地知识**：严禁凭借过往训练记忆、历史知识库或过时经验对 DeepSeek 的模型命名、参数结构、上下文窗口或能力边界做主观臆断（例如：严禁使用已被官方弃用或非官方标准的模型名称与参数组合）。
3. **参数结构与协议严格对齐**：所有与 DeepSeek API 交互的请求体（如 `model`、`messages`、`thinking: {"type": ...}`、`stream`、`tools`、`response_format` 等）及响应解析逻辑，必须与官方文档中声明的最新 JSON Schema 保持绝对一致，确保代码库达到极轻量、零侵入与零误差兼容。

## 十一、ESWA 顶刊大修多智能体交叉评审工作流铁律 (ESWA Multi-Agent Review & Cross-Validation Workflow Iron Rule)

任何 Agent 在收到大修完成的输出结果（如新版 PDF、编译验收报告、实验结果更新等）并被要求“作为资深 ESWA 审稿人/副主编给出下一轮大修意见”时，**必须且只能强制执行本多智能体专业分工与交叉验证工作流**，绝不允许由单一 Agent 凭借泛化经验草率作答：

### 11.1 触发前置与执行总则
1. **触发时机**：用户或上游 Agent 提交阶段性论文修改成果（包含编译通过的 PDF、LaTeX 源文件与支撑数据），并请求评审、提出审改意见或评估手稿状态时。
2. **严禁单体直出**：主 Agent 严禁直接输出笼统套话，必须立即并发派发或顺序执行三名专业审稿人角色，分别调取对应的 ESWA 顶刊标杆文献（记录于 `article/ESWA_GOLD_STANDARD_RESEARCH.md`）与底层实验资产进行深度对齐。

### 11.2 三方专业审稿智能体分工与职责定义
所有评审必须严格按照以下三方专业维度分别完成独立审计：

1. **智能体甲：ESWA 理论与形式化建模专审员 (Methodology & Formal Theory Specialist)**
   - **理论对齐标杆**：精读 ESWA 近期神经符号知识库与规则推理论文（如 Sequeda et al. 2025, Shi et al. 2026）。
   - **审查核心范围**：
     - Introduction 的神经符号五元组形式化映射 $\mathcal{M} = \langle \mathcal{KB}, \mathcal{IE}, \mathcal{WM}, \mathcal{EF}, \mathcal{HI} \rangle$；
     - Section 3 形式化模型（定义、命题、引理）：严查 Definition 3.1 的载荷区间与算法分块脱节、Definition 3.2 的单句退化解与装饰性优化目标；
     - Lemma 3.2（重复工具调用有界拒绝）：严格审查局部致密性条件（Locally-Dense Repetition），严查长周期交错死循环反例；
     - Algorithm 3 状态机时序：严查在窗口淘汰前判定阈值所导致的“$W+1$ 幽灵计数”漏洞；
     - Lemma 3.1（断点恢复）：严查获胜者崩溃后的租约超时（Lease/Heartbeat TTL）活性自愈机制。

2. **智能体乙：ESWA 实证与数据呈现专审员 (Empirical & Data Specialist)**
   - **实证对齐标杆**：精读 ESWA 旗舰 RAG 与问答评测论文（如 CATS-RAG 2026, DriveLegal 2026, TDR2A 2026）。
   - **审查核心范围**：
     - **法医级数据真实性审计（Forensic Data Audit）**：严格比对 `eval_results_300_v2.jsonl`、`eval_summary_300.csv` 与手稿中所有表格数据，严防任何旧版 v1 归档数据残留（如敏感性表格中的历史异常高召回率）；
     - **题型实测与推荐路由的实证断层**：严格比对 Table 6（题型细分准确率）与 Table 7/8（路由决策表），若某一模型在某题型上实测得分为零，严禁直接声称为 Preferred Path，必须明确将其定性为“生产架构目标假设”而非离线实测结论；
     - **图文引用闭环（0 悬空图表）**：逐一检索 `Figure 4, 5, 6` 及 `Table 11, 12, ...`，严查正文未引用（Orphan Floats）或错位引用的严重排版失误；
     - **统计检验与帕累托前沿深度**：深度解读 Friedman/Wilcoxon/Nemenyi CD 图揭示的统计等价团（Cliques）与零膨胀中位数现象，量化剖析帕累托前沿的多目标权衡（Pareto Dominance）。

3. **智能体丙：ESWA 工业应用与管理决策专审员 (Applied Systems & Managerial Auditor)**
   - **决策对齐标杆**：精读 ESWA 工业专家系统、运维决策支持系统（AIOps / DSS）与软件工程实证规范。
   - **审查核心范围**：
     - **微观案例推理闭环**：Section 9 HikariCP 案例严禁黑盒叙事，必须明确写出配置漂移键值（`maximumPoolSize`）、监控告警指标异动（连接池饱和与 P99 暴涨）、图谱两跳因果链、只读降级与高危写操作 JMX 的人机仲裁边界；
     - **战略财务模型与管理杠杆**：推导 NPV $\ge 0$ 的数学临界盈亏平衡条件（Breakeven Iso-quant），揭示系统“高固定投入、高经营杠杆”的管理学规律，反思多级缓存面临的“陈旧配置误导”风险与组织自动化偏置；
     - **效度威胁的主动防御规程**：Section 10 严禁使用“未来我们承诺”的期票语态，必须形式化定义“预注册专家评测规程（Pre-registered Protocol）”，给出 5 级量表评分锚点与分歧裁决机制；显式正面防御 DeepSeek 同族自评偏置，将其定性为消融实验间的相对比较标尺；
     - **Conclusion 系统工程升华**：必须采用“创新贡献总结—实证管理启示—边界未来演进”标准三段式重构。

### 11.3 多智能体交叉验证与对齐裁决协议 (Cross-Validation Protocol)
1. **交叉数据流核验**：三方智能体输出独立纪要后，主审 Agent 必须对齐三方结论，重点检查：
   - 理论引理的参数假设（如窗口大小 $W$）是否与实验代码和伪代码绝对一致；
   - 实验评测中的全零与负面表现，是否在管理启示与路由建议中得到了合理解耦；
   - 案例中的时序与数字，是否能严格代入财务模型并实现公式级闭环。
2. **输出规范**：评审结论必须整合为结构化报告，包含总体裁定、四大核心大修方向、微观改写清单、以及针对编写 Agent 的 JIT 按需查阅指南。

### 11.4 编写 Agent 的 JIT 即时技能翻书规范 (JIT Skill Ingestion Protocol)
为防止编写 Agent 在大修执行时产生注意力稀释与遗忘，强制其在润色不同章节时，**仅且只能单点读取 `/research-paper-writing` 下的指定文件**：
- 润色 Section 1 & 4 专家系统架构与五元组时 $\rightarrow$ 仅查阅 `references/introduction.md`；
- 推导 Section 3 & 5 形式化引理、状态机与算法时 $\rightarrow$ 仅查阅 `references/method.md`；
- 撰写 Section 6 & 8 题型细分、路由表与统计检验时 $\rightarrow$ 仅查阅 `references/experiments.md`；
- 撰写 Section 9 工业案例、财务方程与 Section 10 效度防御时 $\rightarrow$ 仅查阅 `references/paper-review.md`；
- 全文段落连贯性与 Known-to-New 信息流推进时 $\rightarrow$ 仅查阅 `references/does-my-writing-flow-source.md`。


