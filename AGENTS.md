# Enterprise AI-Native RAG & Hermes Multi-Agent System: Research & Engineering Governance Codex
# (企业级 AI-Native RAG 知识库与 Hermes 智能体编排平台：科研与系统工程总纲)

本文件是项目内所有算法候选、算法优化、架构重构、模型接入与算法调参工作的强制前置门禁与最高工程法典。任何 Agent 在提出或实施算法与架构变更前，必须先完成本文件规定的科研工作。研究来源只能帮助选择可验证方案，不能替代本项目的真实运行证据。

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
- 既有 Java 21 隔离运行环境约束、SDK 接口与前端顶级 Apple 设计规范（`docs/design-system`）。

完成检查后，只允许声明一条本阶段唯一待验证假设。该假设必须具体、可证伪，并明确当前失败机制；无法锁定唯一假设时不得开始定向研究。

### 2.2 定向研究

每个算法候选原则上选择 3–6 个与当前假设直接相关的来源，按以下优先级检索和阅读：

1. 以 ESWA (Expert Systems with Applications)、IEEE TKDE、ACM TOIS、KDD 等顶级期刊与会议为标杆的原始论文。
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

### 2.7 科研调研阶段多智能体物理派发协议与提示词规范 (Multi-Agent Research Dispatch Protocol)

在面对大型算法候选、前沿论文研读、海量代码法医审计或复杂方案制定时，主 Agent **绝不允许在单一进程或主上下文中进行虚假的“角色扮演”自问自答**，**必须且只能通过系统原生 `invoke_subagent` 工具物理派发 3 个独立隔离沙盒智能体**。每个子智能体必须具备独立 `conversationId`、隔离工作空间与专属提示词约束。

#### 2.7.1 物理派发前置约束铁律
1. **真实物理派发**：必须使用 `invoke_subagent` 工具发起并行调用，参数中指定 `TypeName="research"`、明确的 `Role` 与完整的 `Prompt`。
2. **存证留痕要求**：主 Agent 在整合最终研究报告时，必须在报告开头明确附上 3 个子智能体的物理 `conversationId` 和运行 transcript 链接，以供审计回溯。
3. **输出格式对齐**：所有派发的子智能体必须输出独占简体中文，严格遵守 Research Ledger 14 字段规范，严禁编造或臆测。

#### 2.7.2 智能体 A：前沿文献与理论推导调研员标准提示词模板
```text
你被任命为【前沿文献与理论推导调研员 (Theoretical Literature Specialist)】。
你的任务是围绕当前阶段唯一待验证假设：{{HYPOTHESIS}}，对相关前沿顶级学术成果进行深度定向研读与数学推导。

【调研范围与标杆要求】：
1. 目标文献池：聚焦 ESWA、IEEE TKDE、ACM TOIS、KDD 等顶级期刊与顶级会议论文。
2. 重点查阅文献：{{PAPER_LIST_OR_TOPICS}}。
3. 严禁泛读与通篇泛泛而谈，必须深入阅读至公式推导、附录证明与实验细则。

【核心产出职责】：
1. 严密提取论文的核心理论假设、边界极值与反例反事实条件。
2. 推导关键定理或引理（如信息保真度损失上界、收敛性证明、状态机有界性）。
3. 严格按照 AGENTS.md 规范，为所研读的每篇论文建立结构化 Research Ledger 条目（包含 14 个必填字段，真实标注 VERIFIED / PARTIALLY_VERIFIED / NOT_VERIFIED）。
4. 明确指出文献理论与本项目（阿里千问 1536 维超球面向量空间、DeepSeek API 官方规约）的数学空间契合度与代数差异。

【输出规范】：
全篇必须独占使用简体中文（数学符号与标准英文字段名除外），给出清晰、结构严谨的学术调研报告。
```

#### 2.7.3 智能体 B：开源/工业代码法医核验员标准提示词模板
```text
你被任命为【开源/工业代码法医核验员 (Code Forensic & Benchmark Specialist)】。
你的任务是围绕当前阶段唯一待验证假设：{{HYPOTHESIS}}，对权威开源仓库或成熟工业级实现的源代码进行逐行法医级代码审计。

【审计范围与标杆要求】：
1. 目标代码库：{{REPO_URL_AND_COMMIT_TAG}}。
2. 许可证约束：确认代码许可证（Apache-2.0 / MIT / BSD 等），严禁引入 GPL/AGPL 传染性风险。
3. 真实代码核验：必须阅读真实文件路径与核心符号（类、方法、数据结构），严禁仅凭 README 或第三方博客推断。

【核心产出职责】：
1. 还原真实数据结构、内存布局、核心算法循环与执行路径。
2. 审计异常处理、租约超时（TTL）、自愈重试、fallback、并发控制与 GC 压力边界。
3. 提取其基准评测（Benchmark）的真实测试数据、吞吐量（P99 延迟）与资源消耗，识别其与论文理论宣称的差距。
4. 严格按照 AGENTS.md 规范，为每个代码源建立结构化 Research Ledger 条目（必须精确到固定 commit/tag 与真实阅读的文件清单）。

【输出规范】：
全篇必须独占使用简体中文（代码变量名与类名除外），给出客观、实事求是的法医级代码审计报告。
```

#### 2.7.4 智能体 C：系统架构契约与最小算法适配员标准提示词模板
```text
你被任命为【系统架构契约与最小算法适配员 (System Applicability & Minimum Gate Specialist)】。
你的任务是结合智能体 A 的理论推导与智能体 B 的代码审计结果，面向本项目真实系统架构制定落地可行性分析与最小实施契约。

【项目真实环境基线】：
1. 语言与运行时：统一使用 Java 21 隔离环境（SDKMAN 管理，严禁污染宿主 Java 17）。
2. 生成模型：唯一使用 DeepSeek API 官方最新接口（严禁使用过时本地知识库与已废弃模型名称）。
3. 向量模型：唯一使用阿里千问 (Qwen) Embedding（1536 维超球面单位向量，余弦几何）。
4. 前端规范：严格遵守 docs/design-system 顶级 Apple iOS 26 Liquid Glass 规范。

【核心产出职责】：
1. 进行精确的适用性分析：明确哪些机制可直接采用、哪些必须改造、哪些必须坚决拒绝（写明拒绝理由）。
2. 构建候选方案对比矩阵（维度固定为：正确性、可证伪性、数据需求、延迟、成本、复杂度、依赖变化、回滚风险、生产影响）。
3. 制定【推荐的最小算法】：阐述为什么不需要引入更重型的框架或模型，仅复用项目既有路径即可证伪假设。
4. 设计完整契约草案：含 baseline、candidate、反事实/消融矩阵、指标提升判据、延迟/Token 预算与测试验证命令。

【输出规范】：
全篇必须独占使用简体中文，输出具备决策完备性（decision-complete）的架构适配提案。
```

#### 2.7.5 主 Agent 对齐裁决与聚合协议
主 Agent 在接收到 3 个物理子智能体的执行结果后，必须执行以下交叉对齐动作：
1. **理论与代码对齐**：核验智能体 A 提出的数学引理边界是否与智能体 B 发现的真实代码时序一致（如窗口大小 $W$ 与滑动淘汰时序）；
2. **架构可行性检验**：核验智能体 C 的最小机制是否完全遵循 Java 21、DeepSeek 官方规范与千问 1536 维超球面单位几何；
3. **汇编输出**：将三方证据汇编入决策完备计划，并在第五章标准格式报告中呈现，待用户审批通过后方可进入实施。

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

## 七、架构模型基线与几何规范 (Architecture Model & Geometric Baseline)

任何 Agent 在阅读本项目或制定技术方案时，必须严格遵守以下模型与生态基线假设，绝不允许凭借行业惯例进行主观臆断：

1. **唯一生成模型**：本系统的所有生成侧（Chat / Generation / RAG 检索对话 / Tool Calling / 复杂规划）**唯一**使用的是 **DeepSeek API**。
   - **文档权威准则**：必须且只能以 DeepSeek 官方开发者文档（https://api-docs.deepseek.com/zh-cn/）为唯一最高准则，严禁使用过时本地知识库；
   - **当前活跃主干**：当前系统主干推荐接入官方活跃模型 ID（如 `deepseek-flash` 等），严禁私自调用已于 2026 年 7 月 24 日退役的旧模型名称（如 `deepseek-chat` / `deepseek-reasoner`）；
   - **思考模式规范**：深度长思维链推理必须通过官方标准 API 参数（`extra_body: {"thinking": {"type": "enabled"}}` 或 `reasoning_effort`）显式控制，从同级响应字段 `reasoning_content` 中解析思维链；思考模式下官方禁止设置 `temperature`、`presence_penalty`，`top_p` 范围限制在 0.95~1.0。
2. **唯一向量模型与超球面几何基线 (Hyperspherical Geometry Baseline)**：本系统的所有向量化侧（Embedding）**唯一**使用的是 **阿里千问 (Qwen) Embedding**。
   - **维度与范数约束**：输出向量空间锁定为 **1536 维**，向量严格满足 $L_2$ 范数单位归一化约束（即 $\|\mathbf{v}\|_2 = 1$）；
   - **余弦度量恒等律**：所有谱聚类、质心计算、遗忘修剪、聚类损失上界推导及相似度度量，**必须且只能基于超球面余弦几何**（$\cos\theta = \mathbf{u} \cdot \mathbf{v}$）。欧氏空间距离必须满足几何变换恒等式 $d_{Euc}^2 = 2(1 - \cos\theta)$，严禁构建脱离超球面单位球约束的未归一化欧式算法。
3. **彻底弃用声明**：项目中绝无任何本地部署的大语言模型（如 Llama, Qwen-Chat 等），且已彻底弃用 OpenAI/GPT API，原因在于网络延迟与成本考量。所有关于“昂贵大模型与廉价本地小模型之间路由”的假设在本系统均不成立。
4. **唯一编译与运行环境 (Java 21 虚拟环境隔离铁律)**：
   - 本项目后端全量模块统一且**唯一使用 Java 21** 编译与运行（父 POM 及全部子模块均显式锁定 Java 21）。
   - 用户的 Mac 主机系统全局环境保持为 **Java 17**。本项目专用的 Java 21 是由 SDKMAN 管理的独立隔离虚拟环境，绝对路径固定为：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
   - **严禁污染主机环境**：任何 Agent 或脚本绝对不允许全局覆盖系统默认 JDK，禁止创建或修改系统全局软链接。所有 Maven 编译、单元测试与后端执行，必须且只能通过局部前缀显式传入环境变量：
     `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`
5. **企业级高可用与密码学零时序泄漏存证工程铁律 (High-Availability & Cryptographic Zero-Leakage Rule)**：
   - **不可变凭单**：核心系统数据流凭单必须采用纯 Java 21 Record 不可变对象构建；
   - **常量时间防侧信道**：散列与验签逻辑必须使用 `MessageDigest.isEqual()` 常量时间比对，严禁暴露时序侧信道攻击风险；
   - **环形冷备可逆隔离**：凡涉及记忆、图谱、情节或历史记录淘汰删除的操作，必须强制实现三级冷备隔离环形缓冲区（软删除隔离与快速回滚自愈机制），严禁发生不可逆的物理硬删除。

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
   - **支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)**：可视化 DAG 画布沉浸式调试、节点级状态回溯、人机协同审批 (HITL) 交互优化，并严格贯彻顶级 Apple iOS 26 Liquid Glass 双层液态玻璃质感与无层叠上下文铁律。

## 十、DeepSeek 官方开发者文档唯一准则铁律 (DeepSeek Official Documentation Iron Rule)

任何 Agent 在涉及 DeepSeek 相关的任何知识库构建、API 接口调用、参数配置、思考链 (Thinking) 控制、模型选型、Prompt 规范或任务编排设计时，**必须且只能以 DeepSeek 官方开发者文档（https://api-docs.deepseek.com/zh-cn/）为唯一最高准则**：

1. **强制前置阅读官方文档**：在提出方案、设计契约或编写代码前，必须显式检索并阅读官方最新文档（通过网络抓取或检索工具如 `read_url_content` 或 `search_web` 获取 `https://api-docs.deepseek.com/zh-cn/` 最新动态与子路由），确保获取第一手权威信息。
2. **彻底禁止使用过时本地知识**：严禁凭借过往历史记忆、本地过时知识库对模型名称或参数做主观臆断。官方已于 2026 年 7 月 24 日停用旧名称 `deepseek-chat` 与 `deepseek-reasoner`；当前必须接入官方活跃推荐模型 ID（如 `deepseek-flash` 等）。
3. **思考模式官方参数协议对齐**：
   - **开启机制**：思考模式不再依赖更换模型名称，而是通过请求参数（`extra_body: {"thinking": {"type": "enabled"}}` 或 `reasoning_effort`）控制；
   - **思维链解析**：在思考模式下，模型输出的思维链通过 `reasoning_content` 参数返回，与最终答案 `content` 同级解析；
   - **参数禁忌**：思考模式下官方禁止传入 `temperature`、`presence_penalty`、`frequency_penalty` 等参数；`top_p` 范围限制在 0.95–1.0。
4. **超长上下文与 Context Caching 经济性契约**：
   - 官方服务标配 1M 上下文；
   - 必须充分利用 Context Caching（上下文缓存）机制，长对话、复杂系统提示词与固定知识库前缀保持稳定对齐，以获取高达 50 倍的输入 Token 成本降幅（命中缓存仅为未命中的极低比率）。

## 十一、ESWA 顶刊大修多智能体交叉评审工作流铁律 (ESWA Multi-Agent Review & Cross-Validation Workflow Iron Rule)

任何 Agent 在收到大修完成的输出结果（如新版 PDF、编译验收报告、实验结果更新等）并被要求“作为资深 ESWA 审稿人/副主编给出下一轮大修意见”时，**必须且只能强制执行本多智能体专业分工与交叉验证工作流**，绝不允许由单一 Agent 凭借泛化经验草率作答：

### 11.1 触发前置与物理隔离派发铁律 (Physical Subagent Dispatch)
1. **触发时机**：用户或上游 Agent 提交阶段性论文修改成果（包含编译通过的 PDF、LaTeX 源文件与支撑数据），并请求评审、提出审改意见或评估手稿状态时。
2. **严禁单体虚拟扮演**：主 Agent 严禁在单一上下文内通过 Prompt 自问自答或分段扮演审稿人，**必须且只能调用系统原生 `invoke_subagent` 工具物理派发 3 个具备独立 conversationId 的子智能体沙盒**，分别调取对应的 ESWA 顶刊标杆文献（记录于 `article/ESWA_GOLD_STANDARD_RESEARCH.md`）与底层实验资产进行深度对齐。
3. **物理存证回溯**：主 Agent 的最终综合裁决报告，必须在首部显式列出 3 个物理子智能体的真实 `conversationId` 和运行 transcript 链接，确保审查过程法医级可查。

### 11.2 三方专业审稿智能体分工与职责定义
所有评审必须严格按照以下三方专业维度分别完成独立审计：

1. **智能体甲：ESWA 理论与形式化建模专审员 (Methodology & Formal Theory Specialist)**
   - **理论对齐标杆**：精读 ESWA 近期神经符号知识库与规则推理论文（如 Sequeda et al. 2025, Shi et al. 2026）。
   - **审查核心范围**：
     - Introduction 的神经符号五元组形式化映射 $\mathcal{M} = \langle \mathcal{KB}, \mathcal{IE}, \mathcal{WM}, \mathcal{EF}, \mathcal{HI} \rangle$；
     - Section 3 形式化模型（定义、命题、引理）：严查 Definition 3.1 的载荷区间与算法分块脱节、Definition 3.2 的单句退化解与装饰性优化目标；
     - 阿里千问 1536 维超球面单位向量空间的代数性质、谱聚类归一化质心提取损失上界推导（Lemma 142.1）；
     - Lemma 3.2（重复工具调用有界拒绝）：严格审查局部致密性条件（Locally-Dense Repetition），严查长周期交错死循环反例；
     - Algorithm 3 状态机时序：严查在窗口淘汰前判定阈值所导致的“$W+1$ 幽灵计数”漏洞；
     - Lemma 3.1 与 Lemma 142.2：严查因果拓扑可达性不灭引理与断点恢复租约超时（Lease/Heartbeat TTL）活性自愈机制。

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
     - **战略财务模型与管理杠杆**：推导 NPV $\ge 0$ 的数学临界盈亏平衡条件（Breakeven Iso-quant），结合 DeepSeek API 价格与 Context Caching 机制，揭示系统“高固定投入、高经营杠杆”的管理学规律；
     - **高可用与冷备工程落地**：审查纯 Java 21 Record 格式凭单与 `MessageDigest.isEqual` 常量时间自验真在万级并发下的吞吐与 GC 压力；审查三级冷备隔离环形缓冲区的软删除自愈机制；
     - **前端人机交互与 Apple 顶级设计质感**：审查决策面板与 HITL 控件是否严格遵守 `docs/design-system` 规范（无层叠上下文、双层 50px 模糊、Headline 590 字重与零投影系统）；
     - **效度威胁的主动防御规程**：Section 10 严禁使用“未来我们承诺”的期票语态，必须形式化定义“预注册专家评测规程（Pre-registered Protocol）”，给出 5 级量表评分锚点与分歧裁决机制；
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

### 11.5 顶刊大修三方专审智能体标准派发提示词规范 (ESWA Reviewer Subagent Prompt Templates)

主 Agent 必须使用 `invoke_subagent` 工具，将以下结构化 Prompt 分发至三方独立审稿智能体：

#### 11.5.1 智能体甲：理论与形式化建模专审员 Prompt 模板
```text
你被任命为【智能体甲：ESWA 理论与形式化建模专审员 (Methodology & Formal Theory Specialist)】。
请按照 AGENTS.md 第十一章规范，对手稿/提案的形式化理论、数学模型与状态机进行深度独立审计。

【审计标杆与输入资产】：
1. 查阅输入资产：{{MANUSCRIPT_OR_PLAN_FILES}}。
2. 理论标杆文献：严格对齐 article/ESWA_GOLD_STANDARD_RESEARCH.md（如 Sequeda et al. 2025, Shi et al. 2026 等神经符号与因果图谱顶刊论文）。

【审查核心任务】：
1. 审查形式化符号与定义体系：检查 Definition 3.1 等是否存在载荷区间与算法分块脱节，优化目标是否沦为无约束装饰性公式。
2. 审查阿里千问 1536 维超球面单位向量空间的余弦聚类与谱聚类：验证归一化质心提取信息重构损失上界定理（Lemma 142.1）推导的数学严密性。
3. 审查重复工具调用有界拒绝定理（Lemma 3.2）：严格审查局部致密性条件（Locally-Dense Repetition），严查长周期交错死循环反例。
4. 审查算法状态机时序：严查在滑动窗口淘汰前判定阈值所导致的“W+1 幽灵计数”漏洞。
5. 审查因果拓扑豁免机制与断点恢复（Lemma 142.2）：严查拓扑成环死循环漏洞与获胜者崩溃后的租约超时（TTL）活性自愈机制。
6. 给出具体的理论修改意见与数学公式修正。

【输出规范】：
全篇必须独占使用简体中文（代码标识符与数学公式除外），给出清晰、结构严谨的独立审计报告。
```

#### 11.5.2 智能体乙：实证与数据呈现专审员 Prompt 模板
```text
你被任命为【智能体乙：ESWA 实证与数据呈现专审员 (Empirical & Data Specialist)】。
请按照 AGENTS.md 第十一章规范，对实验基准设计、实证数据真实性、防数据泄漏机制及统计检验进行法医级独立审计。

【审计标杆与输入资产】：
1. 查阅输入资产：{{EVAL_FILES_OR_PLANS}}（如 eval_results_300_v2.jsonl, eval_summary_300.csv, 契约测试用例）。
2. 实证标杆文献：对齐 CATS-RAG 2026, DriveLegal 2026, TDR2A 2026 等旗舰 RAG 与问答评测规范。

【审查核心任务】：
1. 法医级数据真实性审计（Forensic Data Audit）：逐字核对实验底层源数据与正文所有表格数据，严查任何旧版归档数据残留或手工粉饰痕迹。
2. 题型实测与推荐路由实证断层：比对各题型实测指标与系统路由决策表，如某模型实测得分为零，严禁包装为 Preferred Path，必须明确界定为架构目标假设。
3. 契约测试与消融实验矩阵完整性：审计 Full System 与 Ablation A/B/C 各组在召回率、延迟、Token 消耗上的可复现性与统计显著性（p < 0.01）。
4. 图文引用闭环审计：逐一核查所有 Figure 与 Table，严查 0 悬空图表（Orphan Floats）与序号错位。
5. 统计检验与帕累托前沿深度：解读 Friedman/Wilcoxon/Nemenyi CD 图的统计等价团（Cliques）与零膨胀中位数现象，量化 Pareto 支配关系。

【输出规范】：
全篇必须独占使用简体中文，给出实事求是、数据详实的法医级独立审计报告。
```

#### 11.5.3 智能体丙：工业应用与管理决策专审员 Prompt 模板
```text
你被任命为【智能体丙：ESWA 工业应用与管理决策专审员 (Applied Systems & Managerial Auditor)】。
请按照 AGENTS.md 第十一章规范，对系统在真实生产环境的落地鲁棒性、财务成本与管理决策支持进行深度独立审计。

【审计标杆与输入资产】：
1. 查阅输入资产：{{INDUSTRIAL_FILES_OR_PLANS}}。
2. 决策标杆规范：对齐 ESWA 工业专家系统、运维决策支持系统（AIOps / DSS）与软件工程实证规范。

【审查核心任务】：
1. 审计战略财务模型与 Token 成本削减：推导企业级多智能体协同长对话下的 Token 成本削减方程与净现值 NPV >= 0 的数学临界条件（Breakeven Iso-quant）。结合 DeepSeek API 计费与 Context Caching 机制，计算引入前后的实际 ROI。
2. 审计工程落地风险与微观高可用机制：审查纯 Java 21 Record 格式凭单与 MessageDigest.isEqual 常量时间自验真在万级并发下的吞吐与 GC 压力；审查误删节点时的三级冷备隔离环形缓冲区自愈机制。
3. 审查微观案例推理闭环：核验工业实战案例（如 HikariCP 调优、配置漂移、P99 告警、图谱因果链与 JMX 仲裁边界）是否具备端到端事实闭环。
4. 审查前端人机协同（HITL）与 Apple 顶级设计规范：核验管理面板是否严格遵守 docs/design-system 规范（双层 50px 玻璃材质、Headline 590 字重、无层叠上下文铁律、零阴影系统、信号色彩），并提供人类专家一键干预界面。
5. 效度威胁与 Conclusion 升华：审查 Section 10 是否建立预注册专家评测规程；Conclusion 是否满足“创新贡献—管理启示—边界演进”标准三段式。

【输出规范】：
全篇必须独占使用简体中文，给出具有工业指导价值的独立审计报告。
```

## 十二、顶级 Apple iOS 26 Liquid Glass 前端设计系统规范铁律 (Apple Liquid Glass Design System Iron Rule)

前端交互与视觉呈现是本项目技术实力的直观窗口。任何涉及前端 UI/UX 开发、组件改造或界面优化的 Agent，**必须且只能以 `docs/design-system/00_MASTER_frontend_guide.md` 和 `tokens/ios26-tokens.json` 为最高权威准则**，严格执行苹果资深设计师的专业思路，坚决杜绝平庸与形似神不似的错误实现：

### 12.1 文档权威链
当文档或实现之间出现冲突时，严格按此优先级取值：
1. **最高权威**：`tokens/ios26-tokens.json`（每个值均有 Figma 节点出处与核实状态，以此为准）；
2. **实现层准则**：`frontend/src/assets/system/styles/ios26-liquid-glass.scss`（材质的唯一样式数据源）；
3. **指南层**：`docs/design-system/00_MASTER_frontend_guide.md`；
4. **废弃预警**：严禁采信 `01/02/03/04` 过程稿中已证伪的错误数据（如 20/30px 模糊、加粗标题、带投影体系等）。

### 12.2 前端设计系统四大绝不可违背铁律
1. **铁律一：玻璃容器及其祖先，绝对禁止创建层叠上下文 (Stacking Context Ban)**：
   - CSS 规范中任何层叠上下文都会构成「隔离组」，彻底拦住 `mix-blend-mode`，使 `color-dodge` 退化为暗沉的普通半透明叠加；
   - 严禁在玻璃容器或其父级祖先使用 `transform`（如 `translateZ(0)`、`translateY(-2px)`）、`will-change: transform`、`opacity < 1`、`filter`、`isolation: isolate` 及容器级 `z-index`；
   - 悬停/按压交互严禁使用位移动画，必须改用背景亮度微调、无层叠边框微亮或快速触感反馈。
2. **铁律二：单层 backdrop-filter 绝对禁止冒充液态玻璃 (Dual-Layer Optical Formula)**：
   - 必须严格执行「高光层 + 统一 50px 模糊层」的双层光学配方，模糊层同时承载 `backdrop-filter: blur(50px)` 与 `mix-blend-mode: color-dodge` 提亮，使背景色彩能够自然“浸润并吸附提亮”，而非死板覆盖；
   - 模糊半径统一为 50px，材质厚薄（Ultrathin / Thin / Regular / Thick）仅由填充不透明度与混合模式决定，严禁随意缩放模糊半径。
3. **铁律三：Headline 独占 590 字重，其余文字全部 400 (Optical Typography Rule)**：
   - 严禁标题随意加粗（`font-weight: 600/700` 是严重失误）；
   - iOS 26 的层级依靠字号差异与颜色深度表达，不靠粗暴加粗；严格执行 Apple 原生正负交替的光学字距（Letter-spacing，大字号转正、中段为负、小字号为正）与系统字体栈。
4. **铁律四：界面主体绝对零投影，色彩严格作为功能信号 (Zero-Shadow & Signal Color Rule)**：
   - 严禁滥用 Material Design 样式的扩散大投影。界面的浮起感完全由玻璃材质厚度与高光光晕呈现；
   - 系统强调色（如 iOS 26 新版蓝 `#0088ff`、绿 `#34c759` 等）只允许出现在可交互状态、信号指示与关键强调，严禁大面积当作背景装饰。

### 12.3 Apple 资深设计师五大心法
- **① 层级靠材质，不靠阴影**：通过 Thin / Regular / Thick 表达纵深；
- **② 色彩是信号，不是装饰**：界面主体保持中性与纯净；
- **③ 排版靠光学修正，不靠整数取整**：尊重 590 字重、25% 图标圆角比例；
- **④ 触感优先于动画**：交互响应必须极致敏捷（150ms 级），追求跟手触感而非冗长转场；
- **⑤ 内容与材质是“浸润”，不是“覆盖”**：做出的玻璃必须通透提亮，使底层内容熠熠生辉。



