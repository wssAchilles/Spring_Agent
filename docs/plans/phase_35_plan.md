# Phase 35 决策完备实施方案：企业级异构数据智能分析智能体与符号因果自验证 (Text-to-SQL / Cypher Dynamic Schema Linking & Reflexion Data Agent)

> **遵循规范**：`AGENTS.md` Research-to-Implementation Gate 强制准入规范  
> **学术依据**：`docs/plans/phase_35_academic_report.md`（77.3KB，基于互信息最大化与阿里千问 1536 维超球面投影的百表模式动态剪枝算法推导、外键传递闭包与 Steiner 树关系恢复、定理 1.1 模式覆盖完备性引理证明、基于关系代数 $\sigma, \pi, \bowtie, \rho, \gamma$ 的只读语言子集 $\mathcal{L}_{readonly}$ 定义、定理 2.1 只读 AST 隔离不变量定理证明与 Cypher 语法同构性证明、基于有限视界 MDP 的 Reflexion 自愈闭环建模与条件熵压缩推导、定理 3.1 Reflexion 有限视界自愈收敛界证明、Wilkinson 图形语法与 Mackinlay APT 准则、定理 4.1 可视化映射无歧义性与图形完整性定理证明、6 篇顶级学术文献全部 14 项字段 Research Ledger）  
> **工程依据**：`docs/plans/phase_35_industrial_report.md`（80.2KB，Vanna.ai RAG 模式索引、DB-GPT 外键因果扩展、Apache Superset 深度只读 AST 遍历、Neo4j 只读 Session 隔离规范、Reflexion 口头强化学习错误自愈范式、Alibaba Druid & JSqlParser 访问者解析、大厂 3 大典型生产级事故复盘与避坑防线）  
> **核心假设**：唯一核心待验证假设 H-PHASE35-001（基于阿里千问 1536 维超球面投影与外键传递闭包构建动态模式索引器、基于 JSqlParser 与 Druid AST 访问者模式构建多源只读安全防火墙、基于受限只读连接池与 3000ms 超时构建物理执行沙箱、基于 Reflexion 与 DeepSeek-R1 链式推理构建自愈闭环、基于 Wilkinson/Mackinlay 准则构建自适应图表推荐器：实现百表规模下 Schema 上下文压缩率 $\ge 88\%$ 且控制在 4KB 内、模式链接召回率 $\ge 95\%$、堆叠语句与写操作 AST 100% 拦截并强制注入 LIMIT 1000、典型模式语法错误 3 轮内自愈率 $\ge 85\%$、图表推荐符合单色钛金毛玻璃前端规范）  
> **架构模型基线**：唯一生成模型为 DeepSeek API；唯一向量模型为阿里千问 (Qwen) Embedding（1536 维超球面归一化）；后端全量统一 Java 21 隔离环境 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、当前代码与失败机制诊断 (A. 当前代码与失败机制)

### 1.1 现存代码审查与真实链路断层分析
在 Phase 29（GraphRAG 2.0）、Phase 30（代码智能体沙箱）、Phase 32（可解释性护栏）与 Phase 34（因果态势大屏）交付后，系统底层已有部分数据与分析基础设施：
- `tech.qiantong.qknow.ai.service.impl.EmbeddingServiceImpl`：集成阿里千问（DashScope）1536 维向量化模型与内存缓存；
- `tech.qiantong.qknow.ai.code.guard.AstSecurityInspector`：初步实现了 Python 与极简 SQL 关键词拦截；
- `tech.qiantong.qknow.neo4j.service.CypherQueryService`：封装了 `Neo4jClient` 的 Cypher 执行与图元数据探测基础；
- `qknow-mybatis` 引入了 `com.alibaba:druid-spring-boot-3-starter:1.2.27` 与 `com.baomidou:mybatis-plus-jsqlparser:3.5.16`（底层含 JSqlParser）。

然而，面对企业级复杂异构数据智能分析（Text-to-SQL / Cypher），现有系统存在三大核心断层与失败机制：
1. **模式膨胀与上下文雪崩（Schema Explosion）**：百表千列（100+ Tables, 1000+ Columns）的真实企业库 DDL 高达 40KB~100KB，全量塞入 Prompt 导致注意力严重稀释，列名与表名幻觉率飙升，Token 开销不可承受；且缺乏外键传递闭包导致孤立表剪枝丢失 JOIN 桥梁；
2. **文本级正则防线的虚假安全与删表穿透**：现有 `AstSecurityInspector` 仅对 SQL 做了粗糙子串匹配，黑客可通过注释穿透（`SELECT /* comment */ ...`）、分号堆叠多语句（`; DROP TABLE ...`）或嵌套子查询轻松越权删表；
3. **无沙箱全表扫描与盲目重试**：大模型生成笛卡尔积或无 `LIMIT` 的巨表查询直接霸占业务连接池，导致数据库 CPU 100% 与连接池耗尽雪崩；且执行报错时缺乏基于精确 SQLState 错误码的符号自愈反思闭环，查询结果无法自适应推荐图表。

### 1.2 本阶段唯一核心待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-PHASE35-001)**：  
> 构建解耦的**动态模式索引器（SchemaCatalogService）**、**多源 AST 只读安全防火墙（SqlAstSecurityFilter / CypherAstSecurityFilter）**、**受限只读执行沙箱（ReadOnlyExecutionSandbox）**、**基于 Reflexion 与 DeepSeek-R1 的链式自愈引擎（QuerySelfHealingAgent）** 与 **自适应 ECharts 5.5 图表推荐器（ChartRecommenderService）**：  
> 1. 面对 100+ 表复杂模式，向量检索与外键传递闭包剪枝可将相关表精准控制在 3~5 张，Prompt 上下文稳定压缩在 **$\le \text{4KB}$**（Token 节省 $\ge 88\%$），模式链接召回率 $\ge 95\%$（定理 1.1）；  
> 2. AST 防火墙 100% 阻断堆叠多语句与一切写操作 AST 节点，强制注入 `LIMIT 1000`，配合受限连接池（3000ms 硬超时、只读事务、MaxRows 1000）实现 **零数据破坏、零内存 OOM、零连接池耗尽**（定理 2.1）；  
> 3. 在典型表名/列名笔误与语法错误场景下，基于精确 SQLState 报错反馈的 DeepSeek-R1 链式反思机制在 **$\le 3$ 轮内实现 $\ge 85\%$ 的执行自愈成功率**（定理 3.1）；  
> 4. 结果矩阵自适应映射推荐满足 Wilkinson 图形语法与 Mackinlay APT 准则，生成符合单色钛金毛玻璃规范的 ECharts 5.5 结构化 VO（定理 4.1）。

---

## 二、Research Ledger 索引与学术/工程依据 (B. Research Ledger)

方案严格建立在以下 12 篇顶会论文与工业级开源实证之上：

### 2.1 学术理论来源（详见 `docs/plans/phase_35_academic_report.md`）
1. **Yu et al. 2018 (EMNLP)**：Spider: A Large-Scale Cross-Domain Semantic Parsing and Text-to-SQL Benchmark. 确立跨域复杂多表 Text-to-SQL 形式化评估框架；
2. **Pourreza & Rafiei 2023 (NeurIPS)**：DIN-SQL: Decomposed In-Context Learning of Text-to-SQL with Self-Correction. 证明模式链接分解与语法反思能将准确率提升 10%+；
3. **Li et al. 2024 (ICLR)**：Can LLM Already Serve as A Database Interface? A BIG Bench for Large-Scale Database Grounded Text-to-SQL (BIRD). 证明真实脏数据与百表规模下 Schema 剪枝与样本值对齐的决定性意义；
4. **Codd 1970 / Date 2004**：Relational Completeness of Data Base Sublanguages. 确立关系代数基本算子 $\sigma, \pi, \bowtie, \rho, \gamma$ 与只读语言子集 $\mathcal{L}_{readonly}$，推导定理 2.1 只读 AST 隔离不变量；
5. **Shinn et al. 2023 (NeurIPS)**：Reflexion: Language Agents with Verbal Reinforcement Learning. 确立基于环境反馈的口头强化学习收敛框架，推导定理 3.1 有限视界自愈收敛界；
6. **Wilkinson 2005 / Mackinlay 1986 (ACM TOG)**：The Grammar of Graphics & Automating the Design of Graphical Presentations. 确立数据测度空间（$\mathbf{N}, \mathbf{O}, \mathbf{Q}, \mathbf{T}$）到视觉通道的有效性单射，证明定理 4.1 图形完整性定理。

### 2.2 工业实现对标（详见 `docs/plans/phase_35_industrial_report.md`）
1. **Vanna.ai (MIT)**：RAG 驱动模式元数据分层索引，表 DDL 与业务注释向量化召回；
2. **DB-GPT (MIT)**：两阶段 Schema Linking、外键因果拓扑扩展、高基数列样本值注入；
3. **Apache Superset (Apache-2.0)**：深度 AST 只读白名单校验，强制 PlainSelect 断言与 LIMIT 重写注入；
4. **Neo4j Official / LangChain Security**：Cypher 驱动层 AccessMode.READ 隔离与只读子句白名单；
5. **Alibaba Druid (Apache-2.0)**：PostgreSQL 方言级词法分析器与 AST 访问者递归审查；
6. **JSqlParser (Apache-2.0 / LGPL-2.1)**：强类型 Statement AST 模型与 Limit 对象程序化重写注入。

---

## 三、可迁移与不可迁移结论 (C. 可迁移与不可迁移结论)

### 3.1 可直接采纳的结论
- **两阶段动态模式剪枝**：表元数据千问 1536 维超球面向量粗筛 + 外键传递闭包扩展 + 高基数列样本值注入；
- **AST 树深度只读白名单**：顶级语句必须且只能为 `PlainSelect`，AST 遍历阻断所有 DDL/DML/DCL，强制注入 `LIMIT 1000`；
- **物理只读连接池隔离**：专用只读账号、`connection.setReadOnly(true)`、硬超时 3000ms、MaxRows 1000；
- **精确 SQLState 驱动的 Reflexion 自愈**：仅对 42P01/42703/42601 等可自愈错误触发 DeepSeek-R1 链式反思（上限 3 轮），不可恢复错误立即熔断。

### 3.2 必须改造与拒绝的结论
- **拒绝 Python 与 ChromaDB 依赖**：全量使用纯 Java 21 实现模式索引与向量缓存，基于阿里千问 1536 维向量；
- **拒绝大模型自由编写前端 HTML/JS**：由后端 `ChartRecommenderService` 输出严格符合 Monochromatic Titanium Glassmorphism 的 ECharts 5.5 结构化 JSON；
- **拒绝在生产中开启 JDBC `allowMultiQueries=true`**；
- **拒绝引入任何本地小模型**，保持 DeepSeek API 作为唯一生成推理基座。

---

## 四、候选方案全维度矩阵比较 (D. 候选方案比较)

| 评价维度 | 方案 0：当前基线 | 方案 1：简易正则与全量模式 | 方案 2：外挂外部 Python 框架 | **方案 3：本实施方案 (Phase 35 Proposed)** |
|:---|:---|:---|:---|:---|
| **模式上下文体积** | N/A | 40KB ~ 120KB（极易溢出） | 20KB ~ 50KB | **严格 $\le \text{4KB}$（压缩率 $\ge 88\%$）** |
| **SQL 安全防线** | 仅简单子串匹配 | 正则白名单（易被注释/分号穿透） | 简单黑名单（CVE 隐患） | **JSqlParser + Druid 双重 AST 只读遍历 + 强制 LIMIT 1000 注入** |
| **资源与连接隔离** | 共享业务连接 | 共享业务连接池，极易 OOM 与耗尽 | 依赖 DB 权限，无事务只读 | **受限物理只读连接池 + 事务 setReadOnly + 3000ms 超时 + MaxRows 1000** |
| **执行错误自愈** | 直接抛出 500 | 无自愈能力 | 盲目重试 | **精确 SQLState 错误分类 + DeepSeek-R1 链式反思（最多 3 轮）** |
| **图表可视化契合** | 纯裸数据表格 | 模型任意写代码（XSS 风险） | 返回原始数据前端写死 | **结果集元数据自适应推导 + ECharts 5.5 钛金单色毛玻璃标准 VO** |
| **综合裁决** | 无法满足需求 | **拒绝**（高危漏洞与性能雪崩） | **拒绝**（架构分裂不受控） | **强力推荐（唯一采纳候选）** |

---

## 五、推荐的最小架构与设计原则 (E. 推荐的最小架构)

```text
┌───────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                 Phase 35 企业级异构数据智能分析智能体架构全景 (DataAgent Engine)                      │
│                                                                                                       │
│  [ 用户自然语言提问 (User NL Query) ]                                                                  │
│         │                                                                                             │
│         ▼                                                                                             │
│  [ SchemaCatalogService (动态模式索引与智能剪枝器) ]                                                  │
│  ├─ 模式元数据抽取：PostgreSQL 表结构/列名/注释/主外键/高基数 Top-5 样本值                            │
│  ├─ 阿里千问 1536 维超球面向量索引与语义余弦粗筛 (Top-3 核心表)                                       │
│  ├─ 外键拓扑因果传递闭包补全 (Steiner Minimal Tree Closure)                                            │
│  └─ 格式化输出 Compact Schema Prompt (严格 <= 4KB, 上下文压缩率 >= 88%)                               │
│         │                                                                                             │
│         ▼                                                                                             │
│  [ 初次生成：DeepSeek-V3 (deepseek-chat) ]                                                            │
│  └─ 输出原始 SQL (PostgreSQL) 或 Cypher (Neo4j)                                                       │
│         │                                                                                             │
│         ▼                                                                                             │
│  [ SqlAstSecurityFilter / CypherAstSecurityFilter (多源 AST 只读安全防火墙) ]                         │
│  ├─ 堆叠多语句检测：Statements.size() == 1，阻断分号注入                                              │
│  ├─ JSqlParser 严格只读遍历：assert stmt instanceof PlainSelect，阻断 Insert/Update/Delete/Drop 等    │
│  ├─ 强制 AST 重写：未指定 LIMIT 或 LIMIT > 1000 时，重写注入 LIMIT 1000                               │
│  └─ Cypher 语法只读预检：只允许 MATCH/RETURN/WITH，阻断 CREATE/MERGE/DELETE/SET/REMOVE/CALL apoc.*    │
│         │                                                                                             │
│         ▼                                                                                             │
│  [ ReadOnlyExecutionSandbox (受限只读执行沙箱) ]                                                      │
│  ├─ 独立物理只读连接池隔离，connection.setReadOnly(true)                                              │
│  ├─ 硬超时限制 statement.setQueryTimeout(3) (3000ms)，防慢查询拖垮连接池                                │
│  └─ 内存防爆限制 statement.setMaxRows(1000)，防千万级结果集撑爆 JVM OOM                                │
│         │                                                                                             │
│         ├────────────────────────── 执行状态分支 ───────────────────────────┐                         │
│         │                                                                   │                         │
│         ▼ 执行异常 (SQLState 42P01 / 42703 / 42601)                         │ 首次执行直接成功        │
│  [ QuerySelfHealingAgent (基于 Reflexion 的自愈引擎) ]                      │                         │
│  ├─ 错误可自愈性分类判定（超时/安全违规立即熔断，语法/表名列名错误允许自愈） │                         │
│  ├─ 组装富错误诊断上下文 (SQLState, 异常消息, 目标 Schema, 候选列推荐)       │                         │
│  ├─ 调用 DeepSeek-R1 (deepseek-reasoner) 链式深度反思推导与符号重写          │                         │
│  └─ 修正后重新进入 AST 防火墙与沙箱试执行 (严格限制最多 3 轮循环)           │                         │
│         │                                                                   │                         │
│         └─────────────────────────────────┬─────────────────────────────────┘                         │
│                                           │                                                           │
│                                           ▼ 执行成功返回 RawData                                      │
│  [ ChartRecommenderService (自适应图表推荐与结构化组装) ]                                              │
│  ├─ 列属性推导：维度列 (Temporal / Categorical) vs 度量列 (Numerical)                                │
│  ├─ 规则矩阵匹配：指标卡 (MetricCard) / 折线图 (Line) / 柱状图 (Bar) / 饼图 (Pie) / 透视表格 (Table)   │
│  └─ 构造符合 Monochromatic Titanium Glassmorphism 规范的 ECharts 5.5 JSON 配置与 QueryResultVO        │
└───────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 六、决策完备契约规范 (10 项自动化契约测试设计)

契约测试类固定为：`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase35DataAgentAndSqlSecurityContractTest.java`。

### 契约 01：动态模式索引与超球面向量粗筛及 4KB 上下文压缩契约
- **目标**：验证在模拟 100 张业务表、1000+ 字段的大型模式库下，`SchemaCatalogService` 通过千问 1536 维向量检索能精准定位与用户提问最相关的 Top-K 核心表，且提取生成的 Schema 上下文体积严格 $\le 4096$ 字节（压缩率 $\ge 88\%$）。

### 契约 02：外键传递闭包与 Steiner 树关系依赖自动恢复契约 (定理 1.1)
- **目标**：验证当用户提问命中两个无直接外键关联的端点实体表（如 `users` 与 `products`）时，系统能自动沿外键拓扑图计算 Steiner 树闭包，将中间关联桥接表（如 `orders` 与 `order_items`）自动并入上下文，杜绝孤立表引起的幻觉笛卡尔积。

### 契约 03：JSqlParser 深度遍历与纯只读 PlainSelect 强制断言契约 (定理 2.1)
- **目标**：验证 `SqlAstSecurityFilter` 对所有输入 SQL 进行严格的 AST 解析，当输入包含 `INSERT`, `UPDATE`, `DELETE`, `DROP TABLE`, `ALTER TABLE`, `CREATE TABLE`, `TRUNCATE`, `EXEC` 等写操作或 DDL 节点时，100% 抛出 `AstSecurityException` 并在毫秒级内予以阻断。

### 契约 04：堆叠多语句 (Semicolon Stacked Queries) 与多根 AST 100% 阻断契约
- **目标**：验证当攻击载荷尝试利用分号堆叠多语句（如 `SELECT * FROM users; DROP TABLE orders; --` 或 `SELECT 1; SELECT pg_sleep(5);`）时，AST 语法树解析器断言 `statements.size() > 1`，立即触发拦截，彻底封死后门执行。

### 契约 05：LIMIT 1000 安全阈值自动检测与强制 AST 重写注入契约
- **目标**：验证当大模型生成的 SQL 未携带 `LIMIT` 子句，或者指定的 `LIMIT > 1000` 时，`SqlAstSecurityFilter` 自动在 AST 对象层面重写并注入 `LIMIT 1000`，重写后的 SQL 格式标准且严格受控。

### 契约 06：Cypher 读写分离预检与变异子句 (CREATE/MERGE/DELETE) 拦截契约
- **目标**：验证 `CypherAstSecurityFilter` 针对 Neo4j Cypher 语言的只读检测，严格只允许 `MATCH`, `OPTIONAL MATCH`, `WITH`, `RETURN`, `UNWIND` 等纯读子句；对任何包含 `CREATE`, `MERGE`, `DELETE`, `DETACH DELETE`, `SET`, `REMOVE` 以及高危存储过程 `CALL apoc.*` 的语句执行 100% 拦截。

### 契约 07：基于 Reflexion 的 PostgreSQL 精确错误码自愈闭环契约 (定理 3.1)
- **目标**：模拟初始生成的 SQL 存在表名幻觉（SQLState `42P01`）或列名拼写笔误（SQLState `42703`），验证 `QuerySelfHealingAgent` 捕获原生错误码后，准确组装反思上下文驱动 DeepSeek-R1 链式重写，在 $\le 3$ 轮内成功自愈并执行成功。

### 契约 08：受限执行沙箱超时 (3000ms) 与不可恢复错误立即熔断契约
- **目标**：验证当执行触发长查询慢操作或超时（SQLState `57014`）时，系统不进行无谓的反思重试，立即判定为不可自愈并触发安全熔断，有效释放连接池资源。

### 契约 09：自适应 ECharts 图表推荐 (维度/度量识别与类型单射) 契约 (定理 4.1)
- **目标**：验证 `ChartRecommenderService` 根据查询结果集的列类型与行数统计特征，精确映射图表类型：单行单数值映射为 `metric-card`、时间序列映射为 `line`、低基数分类映射为 `pie`、常规分类度量映射为 `bar`、多维数据映射为 `table`，并组装出单色钛金毛玻璃标准的 ECharts 5.5 配置。

### 契约 10：端到端 DataAgent 查询服务与单色钛金毛玻璃 VO 输出契约
- **目标**：验证 `DataAgentService.query()` 端到端完整闭环，传入自然语言提问，顺序流转模式索引剪枝 $\to$ 初次生成 $\to$ AST 防火墙 $\to$ 沙箱执行（含自愈容错）$\to$ 图表自适应推荐，最终输出完整的 `QueryResultVO`（包含执行 SQL、耗时、自愈轮次、表格数据与图表规格配置）。

---

## 七、落地实施文件清单 (File Action List)

```text
[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/model/SchemaCard.java
      - 表结构元数据与样本值卡片领域对象 (tableName, comment, columns, foreignKeys, sampleValues, embedding)

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/model/QueryResultVO.java
      - 结构化智能数据分析响应视图对象 (finalQuery, status, executionTimeMs, healingRounds, columns, rows, chartType, echartsOption)

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/model/ChartSpecVO.java
      - ECharts 5.5 单色钛金毛玻璃规格配置对象

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/security/AstSecurityException.java
      - AST 安全防火墙拦截强类型异常

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/security/SqlAstSecurityFilter.java
      - 基于 JSqlParser 与 Druid AST 的只读安全防火墙与 LIMIT 1000 强制注入重写器

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/security/CypherAstSecurityFilter.java
      - Cypher 图查询语言只读安全防火墙与子句白名单校验器

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/service/SchemaCatalogService.java
      - 动态模式索引、千问 1536 维超球面向量粗筛与外键传递闭包剪枝服务

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/service/ChartRecommenderService.java
      - 结果集维度度量分析与 Wilkinson/Mackinlay 准则自适应图表推荐服务

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/agent/QuerySelfHealingAgent.java
      - 基于 Reflexion 范式与 DeepSeek-R1 链式推理的数据库错误闭环自愈引擎

[NEW] backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/dataagent/service/DataAgentService.java
      - 智能数据分析总协调器服务

[NEW] backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase35DataAgentAndSqlSecurityContractTest.java
      - Phase 35 专属 10 项严苛契约测试套件
```

---

## 八、实验与验证计划 (F. 实验与实现计划)

### 1. 契约测试验证命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest=Phase35DataAgentAndSqlSecurityContractTest
```
- **通过判据**：10/10 项契约测试 100% 绿灯，0 失败，0 错误。

### 2. 后端全量防退化回归命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests
```
- **通过判据**：全量测试套件（882 项基础 + 10 项 Phase 35 契约 = 892 项）100% 绿灯，0 失败，0 错误。

### 3. 前端构建校验命令
```bash
cd frontend && npm run build:prod
```
- **通过判据**：TypeScript 校验与 Vite 生产资源打包 100% 成功，0 错误退出。

---

## 九、风险、停止条件和后续授权边界 (G. 风险、停止条件和后续授权边界)

1. **残余风险**：
   - 特殊数据库专有方言自定义函数（如特异的 GIS 空间函数或复杂 JSONB 语法）在 JSqlParser 中可能被误判为语法异常。对此我们在 `SqlAstSecurityFilter` 中支持针对安全内置函数的白名单动态扩展；
   - 超高并发下短时间内大量触发 DeepSeek-R1 自愈重试可能逼近 API 速率上限。我们在 `QuerySelfHealingAgent` 中内置了硬上限（最多 3 轮）与指数抖动退避，并支持在超时不可恢复时秒级熔断。
2. **停止条件 (Halt Conditions)**：
   - 若 JSqlParser 无法有效阻断注入了恶意注释的分号堆叠多语句，立即停止编码并重构访问者遍历逻辑；
   - 若 Schema 剪枝算法在百表场景下未能将上下文控制在 4KB 以内，或契约测试未全部通过，严禁进入后续阶段。
3. **后续授权边界**：
   - 现阶段严格锁定在只读数据分析（Text-to-SQL / Cypher SELECT 查询、Schema 剪枝、AST 安全防护、自愈闭环与图表推荐）；
   - 严禁擅自开放任何数据库写入、修改、DDL 变更权限；
   - 任何涉及生产数据库只读账号配置与在线启用的操作需独立审批授权。
