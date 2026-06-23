# 智能层全量升级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目智能层从 Spring AI 1.1 升级至 2.0，修复所有安全漏洞，统一 Hermes 反思循环与 Agent 推理，补齐 MCP / QueryTransformation / Multi-Agent 等行业顶级能力，建立完整测试体系。

**Architecture:** 控制面（Spring Boot 单体）+ 认知面（Hermes 微服务）双核架构保持不变。升级以渐进式迁移为核心策略：先修复安全漏洞，再升级 Spring AI 版本，最后补齐缺失能力。每个 Phase 独立可交付、可测试。

**Tech Stack:** Spring Boot 3.5 / Spring AI 2.0 / Spring AI Alibaba 2.0 / PgVector / Lucene / gRPC / JUnit 5 + Mockito

**测试规范:** 所有测试文件统一存放于 `backend/tests/` 模块，按 `src/test/java/tech/qiantong/qknow/{hermes,ai,kmc,kb}/` 分包组织，禁止散落在业务模块中。

---

## Phase 0: 测试基础设施搭建

### Task 0.1: 创建统一测试模块

**目标:** 建立 `backend/tests/` Maven 模块，作为所有智能层测试的唯一载体。

**Files:**
- Create: `backend/tests/pom.xml`
- Create: `backend/tests/src/test/resources/application-test.yml`
- Modify: `backend/pom.xml` — 在 `<modules>` 中追加 `<module>tests</module>`

**要求:**
1. `pom.xml` 继承父 POM `qKnow`，引入被测模块依赖（`qknow-hermes-core`、`qknow-ai`、`qknow-module-kmc-biz`、`qknow-module-kb-biz`）和测试依赖（`spring-boot-starter-test`、`junit-jupiter`、`mockito-core`、`mockito-junit-jupiter`、`h2`）
2. Spring AI BOM 版本声明为 `2.0.0`
3. `application-test.yml` 配置 H2 内嵌数据库、禁用 Redis、配置 mock AI 模型
4. 父 POM `<modules>` 中 `<module>tests</module>` 放在最后

**验收:**
- `mvn clean install -pl backend/tests -DskipTests` 编译通过
- 目录结构 `backend/tests/src/test/java/tech/qiantong/qknow/{hermes,ai,kmc,kb}/` 存在

---

### Task 0.2: 测试基础设施 Smoke Test

**目标:** 验证测试模块可运行，确认 Spring Context 加载正常。

**Files:**
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/SmokeTest.java`

**要求:**
1. 一个 `@SpringBootTest` 测试类，仅验证 ApplicationContext 加载成功
2. 一个纯单元测试类，验证 JUnit 5 + Mockito 基础可用

**验收:**
- `mvn test -pl backend/tests` 全部通过

---

### Task 0.3: CI/CD 流水线搭建

**目标:** 建立 GitHub Actions 自动化构建、测试、部署流水线。（来源：战略报告 Critical 技术债务 #1）

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/deploy.yml`

**要求:**
1. `ci.yml`：PR 触发 → 编译 → 单元测试 → 测试覆盖率报告 → 评论到 PR
2. `deploy.yml`：main 分支合并触发 → Docker 构建 → 推送镜像 → 部署到测试环境
3. 测试覆盖率门槛：新增代码 >= 60%，低于门槛阻止合并
4. 使用 `actions/setup-java@v4` (JDK 17) + `actions/setup-node@v4` (Node 18)
5. PostgreSQL + Redis 服务容器化启动（`services:` 配置）

**验收:**
- PR 提交自动触发 CI
- 测试失败阻止合并
- main 合并自动构建 Docker 镜像

---

## Phase 1: P0 安全修复

### Task 1.0: GeneralSplitter overlap bug 修复

**目标:** 修复 `GeneralSplitter.setOverlap()` 中 `chunkList.get(1)` 越界异常。（来源：战略报告 8 项必须修复 AI 缺陷 #7）

**Files:**
- Modify: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/transformer/GeneralSplitter.java:85-86`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/ai/transformer/GeneralSplitterBugTest.java`

**要求:**
1. 修复 `setOverlap()` 方法：当 `chunkList.size() == 1` 时，`chunkList.get(1)` 抛出 `IndexOutOfBoundsException`
2. 增加边界检查：`chunkList.size() < 2` 时跳过重叠逻辑
3. 测试覆盖：单 chunk 输入、双 chunk 输入、多 chunk 输入、空列表输入

**验收:**
- `GeneralSplitterBugTest` 通过
- 单 chunk 文本不再抛异常

### Task 1.1: AiJudgeService 评分失败行为修复

**目标:** 评分服务故障时默认拒绝（而非放行），消除安全漏洞。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/judge/AiJudgeService.java:60-62`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/judge/AiJudgeService.java:136-138`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/judge/AiJudgeServiceTest.java`

**要求:**
1. `judge()` 方法 catch 块中，`JudgeResult.passed(1.0, 1.0, 1.0, ...)` 改为 `JudgeResult.failed(0.0, 0.0, 0.0, "评分服务不可用，默认拒绝")`
2. `parseJudgeResponse()` 方法 catch 块中，`JudgeResult.passed(0.8, 0.8, 0.8, ...)` 改为 `JudgeResult.failed(0.0, 0.0, 0.0, "评分结果解析失败，默认拒绝")`
3. 单元测试覆盖：正常评分通过、评分失败返回 failed、解析失败返回 failed、边界阈值测试

**验收:**
- `AiJudgeServiceTest` 全部通过
- 评分失败时 `judgeResult.isPassed()` 返回 `false`

---

### Task 1.2: AiJudgeService 模型可配置化

**目标:** 评分模型不再硬编码 DeepSeek，改为从配置或参数获取。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/judge/AiJudgeService.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/judge/AiJudgeConfigTest.java`

**要求:**
1. `AiJudgeService` 构造函数注入 `ChatModelFactory` 和 `JudgeConfig`（新增配置类）
2. `JudgeConfig` 包含字段：`platform`、`baseUrl`、`apiKey`、`modelName`、`threshold`，通过 `@ConfigurationProperties(prefix = "hermes.judge")` 绑定
3. `judge()` 方法使用 `chatModelFactory.getChatModel(config.getPlatform(), ...)` 替代硬编码
4. 配置默认值：`platform=deepseek`、`modelName=deepseek-chat`、`threshold=0.7`
5. 测试验证：不同配置创建不同模型实例

**验收:**
- 通过 `application.yml` 可配置评分模型
- 默认行为不变（DeepSeek）

---

### Task 1.3: VectorStoreServiceImpl 单例缓存修复

**目标:** 消除每次调用创建新 JdbcTemplate 的资源泄漏。

**Files:**
- Modify: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/VectorStoreServiceImpl.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/ai/service/VectorStoreServiceImplTest.java`

**要求:**
1. 使用 double-checked locking 或 `Supplier<VectorStore>` 懒加载缓存
2. `getVectorStore(EmbeddingModel)` 首次调用创建 JdbcTemplate 和 PgVectorStore 并缓存，后续调用返回缓存实例
3. 同一 EmbeddingModel 实例返回同一 VectorStore
4. 不同 EmbeddingModel 实例创建不同 VectorStore（以 modelName 为 key）
5. 线程安全测试：多线程并发调用返回同一实例

**验收:**
- `VectorStoreServiceImplTest` 通过
- 连续调用 100 次 `getVectorStore()` 仅创建 1 个 JdbcTemplate 实例

---

## Phase 2: Spring AI 2.0 版本迁移

### Task 2.1: BOM 版本升级

**目标:** 将 Spring AI BOM 从 1.1.0 升级至 2.0.0，处理依赖冲突。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/pom.xml` — `springAi.version` 改为 `2.0.0`
- Modify: `backend/qknow-framework/qknow-ai/pom.xml` — `springAi.version` 改为 `2.0.0`
- Modify: `backend/pom.xml` — 父 POM `<properties>` 中统一声明 `springAi.version`

**要求:**
1. 父 POM `<properties>` 新增 `<springAi.version>2.0.0</springAi.version>` 和 `<springAiAlibaba.version>2.0.0</springAiAlibaba.version>`（需确认 Alibaba 对应版本）
2. 子模块 pom.xml 移除本地 `<properties>` 中的版本声明，引用父 POM
3. 处理 API 变更：
   - `FunctionToolCallback` → `ToolCallback` 迁移（Spring AI 2.0 工具 API 变更）
   - `ChatModel.call(Prompt)` 返回值类型检查
   - `VectorStore` 接口方法签名检查
4. 删除注释掉的 Swagger 依赖（pom.xml:141-151）

**验收:**
- `mvn clean install -DskipTests` 全模块编译通过
- 无 `ClassNotFoundException` 或 `NoSuchMethodError`

---

### Task 2.2: ChatModel API 适配

**目标:** 适配 Spring AI 2.0 的 ChatModel API 变更。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/ChatModelFactory.java`
- Modify: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/ChatModelServiceImpl.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/config/ChatModelFactoryTest.java`

**要求:**
1. 检查 Spring AI 2.0 中 `OpenAiChatModel`、`DeepSeekChatModel`、`DashScopeChatModel`、`OllamaChatModel` 的 builder API 是否有变更
2. 适配 `OpenAiApi.builder()` 的 HTTP client 配置方式（2.0 可能变更 connector 配置）
3. `ChatModelFactory` 新增 `getChatModel(JudgeConfig config)` 重载方法
4. 测试覆盖：4 个平台的 ChatModel 创建（mock 底层 HTTP）

**验收:**
- `ChatModelFactoryTest` 通过
- 4 个平台 ChatModel 创建均不抛异常

---

### Task 2.3: ToolCallback API 迁移

**目标:** 将所有 `FunctionToolCallback` 迁移至 Spring AI 2.0 的 `ToolCallback` API。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java:90-101`
- Modify: `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java` 中的工具注册代码
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/ToolCallbackMigrationTest.java`

**要求:**
1. 搜索所有 `FunctionToolCallback.builder(...)` 调用，按 Spring AI 2.0 迁移指南替换
2. `SearchKnowledgeTool` 的 `BiFunction<knowledgeQuery, ToolContext, String>` 接口适配 2.0 签名
3. `ToolCallbackResolver` 使用方式检查（2.0 可能变更解析器接口）
4. 测试验证工具注册和调用链路完整

**验收:**
- 无 `FunctionToolCallback` 引用残留
- Agent 对话工具调用正常

---

### Task 2.4: VectorStore API 适配

**目标:** 适配 Spring AI 2.0 的 VectorStore 和 SearchRequest API 变更。

**Files:**
- Modify: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/VectorStoreServiceImpl.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java:446-477`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/ai/service/VectorStoreApiTest.java`

**要求:**
1. 检查 `PgVectorStore.builder()` API 在 2.0 中是否变更
2. 检查 `SearchRequest.builder()` 的 `filterExpression`、`similarityThreshold`、`topK` 方法签名
3. 检查 `FilterExpressionBuilder` 是否仍可用
4. 检查 `Document.getScore()` 返回类型（2.0 可能改为 `Double`）

**验收:**
- 向量检索功能正常
- `VectorStoreApiTest` 通过

---

### Task 2.5: ChatModelFactory 去重与线程安全

**目标:** 消除 Hermes 和 qknow-ai 中 ChatModelFactory 的代码重复，修复 WebClient.Builder 非线程安全问题。（来源：战略报告 ChatModelFactory 评估）

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/ChatModelFactory.java`
- Modify: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/ChatModelServiceImpl.java`
- Create: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/config/ChatModelCacheConfig.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/ai/config/ChatModelCacheTest.java`

**要求:**
1. 统一 ChatModelFactory：Hermes 模块的 `ChatModelFactory` 改为引用 qknow-ai 模块的实现，消除重复
2. 修复 `WebClient.Builder` 线程安全：`getOpenAiChatModel()` 中 `webClientBuilderProvider.getIfAvailable()` 返回的 Builder 被就地修改（`.clientConnector(...)`），多线程调用会互相覆盖。改为每次创建新的 Builder 或使用不可变配置
3. ChatModel 实例缓存：以 `platform + modelName + baseUrl` 为 key 缓存 ChatModel 实例，避免每次调用新建 HTTP client
4. 测试覆盖：并发创建 ChatModel、缓存命中验证、不同平台实例隔离

**验收:**
- `ChatModelCacheTest` 通过
- Hermes 模块不再有独立的 ChatModelFactory
- 并发 100 次 `getChatModel()` 仅创建 1 个实例

---

## Phase 3: RAG 引擎升级

### Task 3.1: 引入 Query Transformation

**目标:** 在 RAG 检索前增加查询重写/压缩能力，提升多轮对话检索效果。

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryTransformService.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java` — `recallTest()` 方法
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/QueryTransformServiceTest.java`

**要求:**
1. `QueryTransformService` 封装 Spring AI 2.0 的 `CompressionQueryTransformer` 和 `RewriteQueryTransformer`
2. 接口定义：
   - `String compressQuery(String currentQuery, List<Message> history)` — 将多轮对话压缩为独立查询
   - `String rewriteQuery(String query)` — 重写模糊查询为更精确的检索查询
3. `recallTest()` 方法在检索前调用 `QueryTransformService.rewriteQuery()` 进行查询重写
4. 当 `history` 不为空时，调用 `compressQuery()` 进行上下文压缩
5. 可通过知识库配置项 `queryTransformEnabled` 开关
6. 测试覆盖：压缩场景、重写场景、开关关闭场景

**验收:**
- `QueryTransformServiceTest` 通过
- 多轮对话 "它的第二大城市呢？" 能正确压缩为独立查询

---

### Task 3.1b: HyDE 与 Multi-Query 查询增强

**目标:** 在 Query Transformation 基础上增加 HyDE（假设文档嵌入）和 Multi-Query 扩展能力。（来源：战略报告查询理解评分 1.5/5）

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryTransformService.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/HyDEMultiQueryTest.java`

**要求:**
1. HyDE（Hypothetical Document Embedding）：
   - 接口：`String generateHypotheticalDocument(String query)` — 调用 LLM 生成假设性答案文档
   - 使用假设文档的 Embedding 进行检索（而非原始查询的 Embedding）
   - 适用于：用户查询简短或模糊时，假设文档能提供更丰富的语义信号
2. Multi-Query 扩展：
   - 接口：`List<String> expandQueries(String query, int count)` — 调用 LLM 生成 N 个语义多样化的查询变体
   - 每个变体独立检索，结果合并去重
   - 适用于：单一查询可能遗漏相关文档时
3. 通过知识库配置项选择增强策略：`none` / `hyde` / `multi_query` / `both`
4. 测试覆盖：HyDE 生成质量、Multi-Query 多样性、合并去重正确性

**验收:**
- `HyDEMultiQueryTest` 通过
- HyDE 模式下检索召回率提升（对比基线）

---

### Task 3.2: 文本切块策略升级

**目标:** 替换纯正则切块为递归字符切块 + 语义感知切块。

**Files:**
- Modify: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/transformer/GeneralSplitter.java`
- Create: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/transformer/RecursiveSplitter.java`
- Create: `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/transformer/SemanticSplitter.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/ai/transformer/SplitterTest.java`

**要求:**
1. `RecursiveSplitter` 继承 `TextSplitter`，实现递归字符切块：
   - 分隔符优先级：`\n\n` → `\n` → `.` → ` ` → `""`
   - 当前分隔符切块超长时，递归使用下一级分隔符
   - 支持 `chunkOverlap` 参数
2. `SemanticSplitter` 继承 `TextSplitter`，实现语义切块：
   - 按句子切分后，计算相邻句子的 Embedding 余弦相似度
   - 在相似度低于阈值处切分
   - 依赖 `EmbeddingModel` 注入
3. `GeneralSplitter` 保留但标记 `@Deprecated`
4. 知识库配置项支持选择切块策略：`general` / `recursive` / `semantic`
5. 中文分词支持（来源：战略报告 8 项必须修复 #8）：
   - `RecursiveSplitter` 分隔符增加中文标点（`。`、`；`、`！`、`？`）
   - 语义切块的句子分割需支持中文句号、问号、感叹号
   - 测试用例必须包含纯中文长文档（>5000 字）
6. 测试覆盖：中文文档切块、英文文档切块、重叠验证、边界情况、中文标点切分

**验收:**
- `SplitterTest` 通过
- 递归切块在段落边界处切分，不在句子中间切分

---

### Task 3.3: 检索结果后处理（Document Post-Processor）

**目标:** 在检索结果返回前增加去重、压缩、上下文窗口裁剪能力。

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/DocumentPostProcessor.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java` — `recallTest()` 方法
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/DocumentPostProcessorTest.java`

**要求:**
1. `DocumentPostProcessor` 接口定义：
   - `List<Document> deduplicate(List<Document> docs)` — 按内容哈希去重
   - `List<Document> compress(List<Document> docs, String query)` — 提取与 query 相关的片段，去除冗余
   - `List<Document> fitToContextWindow(List<Document> docs, int maxTokens)` — 裁剪到上下文窗口限制
2. `recallTest()` 在 rerank 后调用后处理链：去重 → 裁剪
3. 测试覆盖：去重正确性、裁剪后不超限、空列表处理

**验收:**
- `DocumentPostProcessorTest` 通过
- 检索结果无重复内容

---

### Task 3.4: Lucene 全文检索迁移到 PostgreSQL

**目标:** 将 Lucene 文件系统索引替换为 PostgreSQL `tsvector` + `pg_trgm` 全文检索，消除容器化部署的持久卷依赖。

**Files:**
- Create: `deploy/sql/postgresql/03-fulltext-migration.sql` — tsvector 列 + GIN 索引
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java` — `fullTextSearch()` 方法
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/ILuceneService.java` — 标记 `@Deprecated`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/FullTextSearchTest.java`

**要求:**
1. SQL 迁移脚本：
   - `kmc_document_segment` 表新增 `content_tsv tsvector` 列
   - 创建 GIN 索引 `idx_segment_content_tsv`
   - 创建触发器自动更新 `tsvector`（`to_tsvector('simple', content)`）
   - 支持中文分词（使用 `zhparser` 或 `pg_jieba` 扩展，降级方案使用 `simple` 配置）
2. `fullTextSearch()` 方法改用 `JdbcTemplate` 执行 `SELECT ... WHERE content_tsv @@ plainto_tsquery('simple', ?)` 替代 Lucene
3. `ILuceneService` 保留接口但标记废弃，实现类切换为 PostgreSQL
4. 迁移期间双写：同时写 Lucene 和 PostgreSQL，读取优先 PostgreSQL
5. 测试覆盖：中文全文检索、英文全文检索、知识库隔离过滤

**验收:**
- `FullTextSearchTest` 通过
- `fullTextSearch()` 不再依赖 `lucene.indexPath` 配置
- Docker 部署不再需要挂载 Lucene 索引卷

---

### Task 3.4b: LuceneServiceImpl IndexWriter 单例化

**目标:** 修复 `LuceneServiceImpl` 每次操作创建新 IndexWriter 的性能瓶颈和文件锁问题。（来源：战略报告技术债务 #13）

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/LuceneServiceImpl.java:46-61`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/LuceneWriterTest.java`

**要求:**
1. IndexWriter 改为单例：应用启动时创建，注入为 Spring Bean
2. 添加 `@PreDestroy` 关闭 hook：应用停止时调用 `writer.close()`
3. 并发写入使用 `synchronized` 或 `ReentrantLock` 保护
4. 测试覆盖：并发写入不抛异常、写入后可检索到、应用关闭无资源泄漏

**验收:**
- `LuceneWriterTest` 通过
- 连续 100 次写入操作仅创建 1 个 IndexWriter 实例

---

### Task 3.5: RAG 检索结果缓存

**目标:** 对相同查询的 RAG 检索结果进行缓存，减少重复检索的延迟和成本。（来源：战略报告 Phase B 最佳实践）

**Files:**
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagCacheService.java`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java` — `recallTest()` 方法
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/RagCacheServiceTest.java`

**要求:**
1. `RagCacheService` 接口：
   - `List<RetrieveResultRespVO> getOrRetrieve(String cacheKey, Supplier<List<RetrieveResultRespVO>> retriever)`
   - 缓存 key = `knowledgeBaseId + queryHash + searchMethod`
   - 缓存策略：Redis 缓存，TTL 5 分钟
2. `recallTest()` 在检索前查询缓存，命中则直接返回
3. 文档更新时自动失效对应知识库的缓存（`@CacheEvict`）
4. 配置项：`hermes.rag.cache.enabled`（默认 true）、`hermes.rag.cache.ttl`（默认 300s）
5. 测试覆盖：缓存命中、缓存失效、TTL 过期

**验收:**
- `RagCacheServiceTest` 通过
- 相同查询第二次检索延迟 < 10ms

---

### Task 3.6: 权限感知检索

**目标:** 在 RAG 检索层实施 RBAC 权限过滤，确保用户只能检索到其有权限访问的知识库内容。（来源：战略报告 Glean 最佳实践 #3）

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java` — `semanticSearch()` 和 `fullTextSearch()` 方法
- Create: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/PermissionFilter.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kmc/rag/PermissionAwareSearchTest.java`

**要求:**
1. `PermissionFilter` 接口：
   - `Filter.Expression buildPermissionFilter(Long userId)` — 根据用户角色构建 VectorStore 过滤表达式
   - 查询 `kmc_knowledge_role` 表获取用户可访问的知识库 ID 列表
   - 构建 `knowledge_base_id in (...)` 过滤条件
2. `semanticSearch()` 和 `fullTextSearch()` 在检索时注入权限过滤条件
3. 管理员角色（admin/system/sales）跳过权限过滤
4. 测试覆盖：普通用户只能检索授权知识库、管理员可检索全部、无权限用户返回空结果

**验收:**
- `PermissionAwareSearchTest` 通过
- 普通用户无法检索到未授权知识库的内容

---

## Phase 4: Agent 编排统一

### Task 4.1: HermesKernel 与 AgentOrchestrator 统一

**目标:** 将 Hermes 反思循环集成到 Agent 推理流程中，形成统一的推理-评估-修正循环。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/hermes/HermesKernel.java`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/ReflectiveAgent.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/ReflectiveAgentTest.java`

**要求:**
1. `ReflectiveAgent` 组合 `AgentOrchestrator` + `HermesKernel`，流程：
   - Agent 推理（ReactAgent）→ 生成回答
   - AI Judge 评分 → 如果通过，返回最终回答
   - 未通过 → 将评分反馈注入系统提示词 → 重新推理（最多 N 轮）
2. `ReflectiveAgent.chat(ChatRequest)` 返回 `Flux<ChatEvent>`，流式输出包含反思过程事件
3. 新增 `ChatEvent` 类型：`ReflectionStart`、`ReflectionScore`、`ReflectionComplete`
4. `HermesKernel.reflect()` 改为支持流式：每轮尝试的中间结果实时推送给前端
5. 配置项 `hermes.reflection.maxRetries`（默认 2）和 `hermes.reflection.enabled`（默认 true）
6. 测试覆盖：反思通过、反思达到上限、反思禁用直接返回、流式事件顺序

**验收:**
- `ReflectiveAgentTest` 通过
- Agent 对话时前端可收到反思过程事件

---

### Task 4.2: Agent Memory 系统

**目标:** 实现短期记忆（对话上下文）、长期记忆（向量化跨会话）、工作记忆（当前任务状态）三层记忆体系。

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/ShortTermMemory.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/LongTermMemory.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/WorkingMemory.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/MemoryManager.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/memory/MemoryManagerTest.java`

**要求:**
1. `ShortTermMemory`：
   - 管理对话上下文窗口（默认 20 轮）
   - 超出窗口时自动摘要压缩（调用 LLM 生成摘要）
   - 接口：`addMessage(Message)`、`getContext(int windowSize)`、`summarize()`
2. `LongTermMemory`：
   - 将重要信息向量化存储到 PgVector
   - 按会话 ID 和用户 ID 隔离
   - 接口：`store(String content, Map<String, Object> metadata)`、`recall(String query, int topK)`
3. `WorkingMemory`：
   - 当前任务的临时状态（JSONObject）
   - 接口：`set(String key, Object value)`、`get(String key)`、`clear()`
4. `MemoryManager` 统一管理三层记忆，注入到 `ReflectiveAgent`
5. 测试覆盖：上下文窗口截断、摘要生成、长期记忆存储和召回、工作记忆读写

**验收:**
- `MemoryManagerTest` 通过
- 20 轮对话后自动摘要，不丢失关键信息

---

### Task 4.3: Multi-Agent 协作框架

**目标:** 实现 Supervisor Agent 协调多个专业子 Agent 的协作模式。

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/BaseAgent.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/SupervisorAgent.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/WorkerAgent.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentRegistry.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/agent/MultiAgentTest.java`

**要求:**
1. `BaseAgent` 抽象类：定义 `name`、`description`、`tools`、`systemPrompt`、`chat()` 接口
2. `WorkerAgent` 继承 `BaseAgent`，封装单个 ReactAgent 的执行逻辑
3. `SupervisorAgent` 继承 `BaseAgent`：
   - 接收用户任务，分析后拆分为子任务
   - 将子任务分发给对应的 WorkerAgent
   - 收集 Worker 结果并聚合为最终回答
   - 使用 LLM 进行任务拆分和结果聚合
4. `AgentRegistry`：管理所有已注册的 Agent，支持动态注册/注销
5. 预置 Worker 类型：`RAGAgent`（知识库检索）、`SearchAgent`（互联网搜索）、`CodeAgent`（代码生成）
6. 测试覆盖：单 Worker 执行、多 Worker 并行、Supervisor 任务拆分、结果聚合

**验收:**
- `MultiAgentTest` 通过
- Supervisor 能正确拆分任务并协调 Worker 执行

---

## Phase 5: Tool Calling + MCP

### Task 5.1: MCP 协议适配实现

**目标:** 将 `McpToolAdapter` 从占位实现升级为完整的 MCP 协议适配器。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/McpToolAdapter.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/McpClient.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/McpProtocolHandler.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/tool/mcp/McpToolAdapterTest.java`

**要求:**
1. `McpClient`：实现 MCP 协议的 HTTP/SSE 客户端
   - 支持 `initialize`、`tools/list`、`tools/call` 三个核心请求
   - 连接管理：connect、disconnect、reconnect
   - 超时控制和重试机制
2. `McpProtocolHandler`：处理 MCP 协议消息的序列化/反序列化
3. `McpToolAdapter` 改造：
   - `registerServer()` 时自动调用 `tools/list` 获取工具列表
   - `createMcpToolCallback()` 通过 `McpClient` 调用实际 MCP Server
   - 移除 `McpToolFunction.apply()` 中的 TODO 占位逻辑
4. 优先方案：使用 Spring AI 2.0 的 `spring-ai-mcp-client-spring-boot-starter`
5. 测试覆盖：工具发现、工具调用、连接失败处理、超时处理

**验收:**
- `McpToolAdapterTest` 通过
- 可连接外部 MCP Server 并调用其工具

---

### Task 5.2: 工具调用重试与降级

**目标:** 为工具调用增加超时控制、重试、降级机制。

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/resilience/ToolResilienceDecorator.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/resilience/ToolCircuitBreaker.java`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java` — 工具注册处包装 decorator
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/tool/resilience/ToolResilienceTest.java`

**要求:**
1. `ToolResilienceDecorator`：装饰器模式包装 `ToolCallback`
   - 超时控制（默认 30s）
   - 重试策略（指数退避，默认 3 次）
   - 降级策略：工具不可用时返回预设降级消息
2. `ToolCircuitBreaker`：熔断器
   - 连续失败 N 次后熔断（默认 5 次）
   - 熔断后隔 M 秒半开探测（默认 60s）
   - 状态：CLOSED → OPEN → HALF_OPEN → CLOSED
3. 配置项：`hermes.tool.timeout`、`hermes.tool.retry.maxAttempts`、`hermes.tool.circuitBreaker.threshold`
4. 测试覆盖：正常调用、超时重试、熔断触发、半开恢复

**验收:**
- `ToolResilienceTest` 通过
- 工具连续失败 5 次后自动熔断

---

### Task 5.3: 工具动态发现

**目标:** 支持运行时动态发现和注册新工具，无需重启服务。

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/discovery/ToolDiscoveryService.java`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java` — 工具列表从注册中心动态获取
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/tool/discovery/ToolDiscoveryServiceTest.java`

**要求:**
1. `ToolDiscoveryService`：
   - 扫描 Spring 容器中所有 `@Component` 注册的 `Function` Bean
   - 扫描 MCP Server 工具列表
   - 提供 `discoverAll()` 返回所有可用工具
   - 支持按分类过滤：`builtin` / `mcp` / `custom`
2. Agent 编排时从 `ToolDiscoveryService` 动态获取工具列表，替代硬编码
3. 测试覆盖：发现内置工具、发现 MCP 工具、分类过滤

**验收:**
- `ToolDiscoveryServiceTest` 通过
- 新增 `@Component` 的 Function Bean 自动被发现

---

### Task 5.4: ToolPermissionEnforcer 接入 Agent 执行流程

**目标:** 将已实现但未接入的 `ToolPermissionEnforcer` 集成到 Agent 工具调用链路中。（来源：战略报告工具生态评估 — ToolPermissionEnforcer 未使用）

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java` — 工具调用前增加权限检查
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/tool/permission/ToolPermissionIntegrationTest.java`

**要求:**
1. AgentOrchestrator 在执行工具调用前，调用 `ToolPermissionEnforcer.checkPermission(toolCode, sessionId)`
2. 权限不足时返回友好错误消息（而非抛异常），Agent 可据此调整策略
3. 会话权限级别从 gRPC `ChatRequest` 中传入（或从用户角色推导）
4. 测试覆盖：权限允许执行、权限拒绝返回消息、权限级别边界测试

**验收:**
- `ToolPermissionIntegrationTest` 通过
- 低权限会话无法调用 DANGEROUS 级别工具

---

### Task 5.5: WebSearchTool 替换为可靠搜索 API

**目标:** 将使用不可靠 DuckDuckGo 免费 API 的 WebSearchTool 替换为稳定的搜索服务。（来源：战略报告工具生态评估 — WebSearchTool DuckDuckGo 不可靠）

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/function/WebSearchToolFunction.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/tool/function/WebSearchToolTest.java`

**要求:**
1. 搜索 API 可配置化：通过 `hermes.tool.webSearch.provider` 选择搜索提供商
2. 预置提供商：`serper`（Serper.dev）、`bing`（Bing Search API）、`duckduckgo`（降级兜底）
3. 每个提供商实现统一接口：`SearchProvider.search(String query, int maxResults)` → `List<SearchResult>`
4. API Key 通过环境变量注入：`WEBSEARCH_API_KEY`
5. 超时控制（10s）和错误处理（搜索失败返回空结果而非抛异常）
6. 测试覆盖：各提供商 mock 测试、超时处理、降级到 DuckDuckGo

**验收:**
- `WebSearchToolTest` 通过
- 搜索 API 可通过配置切换

---

## Phase 6: DAG 工作流增强

### Task 6.1: DAG gRPC 执行实现

**目标:** 完成 `HermesGrpcService.executeFlow()` 的实际实现（当前返回 501）。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java:60-72`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/FlowExecutor.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/FlowExecutorTest.java`

**要求:**
1. `FlowExecutor`：从数据库加载流程定义 → 构建节点列表和边列表 → 调用 `DagExecutor.execute()` → 返回结果
2. `executeFlow()` 实现：
   - 解析 `FlowRequest` 中的 `flowId`、`variables`、`executeMode`
   - 调用 `FlowExecutor` 执行
   - 将 `NodeRunResultBO` 映射为 `FlowEvent` 流式返回
3. 支持 `BLOCK`（同步）和 `STREAM`（流式）两种执行模式
4. 测试覆盖：单节点流程、多节点串行、并行分支、条件网关

**验收:**
- `FlowExecutorTest` 通过
- gRPC `executeFlow` 返回正确的 `FlowEvent` 流

---

### Task 6.2: 节点类型扩展

**目标:** 扩展 DAG 节点类型，支持 HTTP 请求、知识库检索、变量聚合。

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/bo/HttpNodeBO.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/bo/KnowledgeNodeBO.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/bo/AggregatorNodeBO.java`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/factory/NodeFactory.java` — 注册新节点类型
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/enums/FlowNodeTypeEnums.java` — 新增枚举值
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/node/NodeTypesTest.java`

**要求:**
1. `HttpNodeBO`：执行 HTTP 请求，支持 GET/POST，结果存入上下文变量
2. `KnowledgeNodeBO`：调用 RAG 检索，结果存入上下文变量
3. `AggregatorNodeBO`：将多个输入分支的结果合并为一个输出
4. `FlowNodeTypeEnums` 新增 `HTTP`、`KNOWLEDGE`、`AGGREGATOR`
5. `NodeFactory.createNode()` 新增对应分支
6. 测试覆盖：每种节点类型的独立执行测试

**验收:**
- `NodeTypesTest` 通过
- `NodeFactory` 可创建所有节点类型

---

### Task 6.3: 条件表达式引擎增强

**目标:** 将 `ConditionNodeBO` 的简单比较运算升级为支持复杂表达式的评估引擎。

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/bo/ConditionNodeBO.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/expression/ExpressionEngine.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/expression/ExpressionEngineTest.java`

**要求:**
1. `ExpressionEngine` 接口：
   - `boolean evaluate(String expression, Map<String, Object> context)` — 评估表达式
   - 支持：比较运算（`==`、`!=`、`>`、`<`、`>=`、`<=`）、逻辑运算（`&&`、`||`、`!`）、字符串操作（`contains`、`startsWith`、`matches`）
   - 变量替换：`{{ variableName }}`
2. 优先方案：集成 `Spring Expression Language (SpEL)` 作为表达式引擎
3. 安全：禁止执行任意代码，白名单限制可用函数
4. `ConditionNodeBO.evaluateExpression()` 委托给 `ExpressionEngine`
5. 测试覆盖：简单比较、复合逻辑、字符串操作、变量缺失处理、恶意表达式拦截

**验收:**
- `ExpressionEngineTest` 通过
- `{{ score }} >= 0.8 && {{ category }} == 'tech'` 正确评估

---

## Phase 7: 可观测性

### Task 7.1: Agent 执行追踪（Trace）系统

**目标:** 实现 Agent 执行全链路追踪，记录每个节点的输入/输出/耗时/Token 消耗。

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/trace/TraceContext.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/trace/TraceCollector.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/trace/TraceSpan.java`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java` — 注入 TraceCollector
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/trace/TraceCollectorTest.java`

**要求:**
1. `TraceSpan`：记录单个操作的 span（名称、开始时间、结束时间、输入、输出、状态、token 消耗、子 span 列表）
2. `TraceContext`：管理一次请求的完整 trace（traceId、span 树、总耗时、总 token）
3. `TraceCollector`：
   - `startSpan(String name)` → 开始一个 span
   - `endSpan(String spanId, String output, int tokens)` → 结束一个 span
   - `getTrace(String traceId)` → 获取完整 trace
   - 存储：内存缓存 + 异步写入数据库
4. Agent 执行流程中关键节点挂载 trace：RAG 检索、LLM 推理、工具调用、AI Judge 评分
5. 新增数据库表 `agent_trace` 和 `agent_trace_span`
6. 测试覆盖：span 创建/结束、trace 树结构、并发安全

**验收:**
- `TraceCollectorTest` 通过
- 一次 Agent 对话可导出完整的 trace 树

---

### Task 7.2: Token 消耗与成本追踪

**目标:** 实时统计每次对话的 Token 消耗和估算成本。

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/cost/TokenCounter.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/cost/CostEstimator.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/cost/CostEstimatorTest.java`

**要求:**
1. `TokenCounter`：
   - 从 `ChatResponse` 的 `Usage` 中提取 `promptTokens` 和 `completionTokens`
   - 按模型、按会话、按用户聚合统计
2. `CostEstimator`：
   - 配置各模型的 token 单价（输入/输出分别计价）
   - `estimate(String modelName, int inputTokens, int outputTokens)` → 返回估算成本（元）
   - 预置价格：DeepSeek-Chat、GPT-4o、Gemini Flash
3. 成本数据写入 `agent_trace` 表
4. 测试覆盖：token 计数、成本估算、多模型价格

**验收:**
- `CostEstimatorTest` 通过
- 一次对话后可查询到 token 消耗和成本

---

### Task 7.3: RAGAS 评估框架接入

**目标:** 接入 RAGAS（Retrieval Augmented Generation Assessment）评估框架，实现 RAG 质量的自动化量化评估。（来源：战略报告评估维度评分 0/5，LlamaIndex RAGAS 14.5k Stars）

**Files:**
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RagasEvaluator.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/EvaluationDataset.java`
- Create: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/EvaluationReport.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/hermes/eval/RagasEvaluatorTest.java`

**要求:**
1. `RagasEvaluator` 实现以下 RAGAS 指标（使用 LLM-as-Judge 方式，不依赖 Python）：
   - **Faithfulness**（忠实度）：回答中的声明是否有检索上下文支撑。目标 >= 0.8
   - **Answer Relevance**（答案相关性）：回答是否切题。目标 >= 0.7
   - **Context Precision**（上下文精确度）：检索结果中相关内容的排名是否靠前。目标 >= 0.7
   - **Context Recall**（上下文召回率）：回答所需信息是否被检索到。目标 >= 0.6
2. `EvaluationDataset`：管理评估数据集
   - 存储格式：`query` + `expected_answer` + `ground_truth_contexts`
   - 支持从 JSON 文件导入/导出
   - 按知识库分组
3. `EvaluationReport`：生成评估报告
   - 指标汇总（各维度均值、P50、P90）
   - 逐条明细（每条 query 的评分和详情）
   - 输出格式：JSON + 人类可读 Markdown
4. 数据库表 `eval_dataset` 和 `eval_result` 持久化评估数据
5. 测试覆盖：各指标计算正确性、报告生成、空上下文处理

**验收:**
- `RagasEvaluatorTest` 通过
- 对 10 条测试数据运行评估，生成完整报告

---

### Task 7.4: 评估数据集管理与自动化评估管线

**目标:** 建立评估数据集的管理和自动化运行能力。

**Files:**
- Create: `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/eval/EvalDatasetService.java`
- Create: `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/eval/EvalController.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/kb/eval/EvalDatasetServiceTest.java`

**要求:**
1. `EvalDatasetService`：
   - CRUD 操作：创建/编辑/删除评估数据集
   - 导入：从 JSON 文件批量导入评估用例
   - 导出：导出为 JSON 格式
   - 关联知识库：每个数据集绑定到特定知识库
2. `EvalController`：
   - `POST /api/eval/run` — 触发评估运行（异步执行）
   - `GET /api/eval/report/{runId}` — 获取评估报告
   - `GET /api/eval/datasets` — 列出评估数据集
3. 自动化评估管线：触发后异步执行 → 调用 `RagasEvaluator` → 生成报告 → 存储结果
4. 测试覆盖：CRUD、导入导出、异步评估触发

**验收:**
- `EvalDatasetServiceTest` 通过
- API 可触发评估并返回报告

---

## Phase 8: 集成测试与验收

### Task 8.1: 端到端 RAG 流程测试

**目标:** 验证从文档上传到 RAG 问答的完整链路。

**Files:**
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/integration/RagE2ETest.java`

**要求:**
1. 测试流程：创建知识库 → 上传文档 → 文档切块 → 向量化 → 混合检索 → Rerank → 生成回答
2. 使用 H2 内嵌数据库 + mock EmbeddingModel
3. 验证：检索结果相关性、回答包含知识库内容、无幻觉
4. 覆盖三种检索模式：语义检索、全文检索、混合检索

**验收:**
- `RagE2ETest` 全部通过

---

### Task 8.2: 端到端 Agent 对话测试

**目标:** 验证 Agent 对话完整链路，包括反思循环、工具调用、记忆管理。

**Files:**
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/integration/AgentE2ETest.java`

**要求:**
1. 测试流程：创建 Agent → 配置知识库和工具 → 发起对话 → 验证反思循环 → 验证工具调用 → 验证记忆存储
2. 使用 mock ChatModel（预设响应）和 mock 工具
3. 验证：反思评分事件、工具调用记录、记忆召回
4. 覆盖场景：单轮对话、多轮对话、工具调用失败降级

**验收:**
- `AgentE2ETest` 全部通过

---

### Task 8.3: 端到端 DAG 工作流测试

**目标:** 验证 DAG 工作流从定义到执行的完整链路。

**Files:**
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/integration/DagE2ETest.java`

**要求:**
1. 测试流程：定义流程（START → LLM → CONDITION → LLM/REPLY）→ 执行 → 验证结果
2. 覆盖：串行执行、并行分支、条件网关、节点失败处理
3. 验证：执行结果正确性、执行顺序、并行组执行

**验收:**
- `DagE2ETest` 全部通过

---

### Task 8.4: 性能基准测试

**目标:** 建立智能层核心路径的性能基准。

**Files:**
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/perf/RagPerformanceTest.java`
- Create: `backend/tests/src/test/java/tech/qiantong/qknow/perf/AgentPerformanceTest.java`

**要求:**
1. RAG 性能：1000 文档切块耗时、向量检索 P95 延迟、混合检索 P95 延迟
2. Agent 性能：单轮对话端到端延迟、反思循环额外延迟、并发 10 用户吞吐量
3. 记忆性能：长期记忆写入延迟、召回 P95 延迟
4. 输出性能报告（JSON 格式）

**验收:**
- 性能测试可重复运行
- 生成 `target/performance-report.json`

---

## 附录

### A. 文件变更总览

| Phase | 新增文件 | 修改文件 | 测试文件 | 补强来源 |
|-------|---------|---------|---------|---------|
| 0 | 4 | 1 | 1 | +CI/CD 流水线 |
| 1 | 0 | 4 | 4 | +GeneralSplitter bug |
| 2 | 1 | 6 | 5 | +ChatModelFactory 去重/缓存 |
| 3 | 8 | 5 | 8 | +HyDE/MultiQuery/缓存/权限检索/IndexWriter |
| 4 | 8 | 2 | 3 | — |
| 5 | 6 | 4 | 5 | +权限接入/WebSearch 替换 |
| 6 | 5 | 3 | 3 | — |
| 7 | 9 | 1 | 4 | +RAGAS 评估框架 |
| 8 | 4 | 0 | 0 | — |
| **合计** | **45** | **26** | **33** | |

### B. 关键配置项清单

```yaml
hermes:
  judge:
    platform: deepseek          # 评分模型平台
    modelName: deepseek-chat    # 评分模型名称
    apiKey: ${JUDGE_API_KEY}    # 评分模型 API Key
    threshold: 0.7              # 评分通过阈值
  reflection:
    enabled: true               # 反思循环开关
    maxRetries: 2               # 最大反思次数
  tool:
    timeout: 30000              # 工具调用超时 (ms)
    retry:
      maxAttempts: 3            # 最大重试次数
    circuitBreaker:
      threshold: 5              # 熔断触发阈值
      recoveryTimeout: 60000    # 熔断恢复超时 (ms)
  memory:
    shortTerm:
      windowSize: 20            # 对话上下文窗口大小
    longTerm:
      enabled: true             # 长期记忆开关
  rag:
    cache:
      enabled: true             # RAG 检索缓存开关
      ttl: 300                  # 缓存 TTL (秒)
    queryTransform:
      enabled: true             # 查询变换开关
      strategy: rewrite         # 查询增强策略: none/hyde/multi_query/both
    splitter:
      strategy: recursive       # 切块策略: general/recursive/semantic
  tool:
    webSearch:
      provider: serper          # 搜索提供商: serper/bing/duckduckgo
      apiKey: ${WEBSEARCH_API_KEY}
```

### C. 数据库新增表

```sql
-- Agent 执行追踪
CREATE TABLE agent_trace (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) UNIQUE NOT NULL,
    request_id VARCHAR(64),
    bot_id BIGINT,
    user_id BIGINT,
    total_duration_ms BIGINT,
    total_tokens INT,
    total_cost DECIMAL(10,6),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agent_trace_span (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    span_id VARCHAR(64) UNIQUE NOT NULL,
    parent_span_id VARCHAR(64),
    name VARCHAR(128),
    input TEXT,
    output TEXT,
    status VARCHAR(20),
    duration_ms BIGINT,
    token_count INT,
    cost DECIMAL(10,6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- PostgreSQL 全文检索增强
ALTER TABLE kmc_document_segment ADD COLUMN content_tsv tsvector;
CREATE INDEX idx_segment_content_tsv ON kmc_document_segment USING GIN(content_tsv);
CREATE TRIGGER trg_segment_content_tsv
    BEFORE INSERT OR UPDATE ON kmc_document_segment
    FOR EACH ROW EXECUTE FUNCTION tsvector_update_trigger(content_tsv, 'simple', content);

-- RAGAS 评估数据集
CREATE TABLE eval_dataset (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    knowledge_base_id BIGINT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE eval_dataset_item (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES eval_dataset(id),
    query TEXT NOT NULL,
    expected_answer TEXT,
    ground_truth_contexts JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE eval_result (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) UNIQUE NOT NULL,
    dataset_id BIGINT NOT NULL REFERENCES eval_dataset(id),
    faithfulness DECIMAL(5,4),
    answer_relevance DECIMAL(5,4),
    context_precision DECIMAL(5,4),
    context_recall DECIMAL(5,4),
    report_json JSONB,
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### D. 执行顺序建议

```
Phase 0 (Day 1-3)        → 测试基础设施 + CI/CD 流水线
Phase 1 (Day 3-5)        → P0 安全修复（GeneralSplitter bug + Judge + VectorStore）
Phase 2 (Day 6-10)       → Spring AI 2.0 迁移 + ChatModelFactory 去重（阻塞后续 Phase）
Phase 3 (Day 11-22)      → RAG 引擎升级（QueryTransform/HyDE/切块/缓存/权限检索/PgSQL迁移）
Phase 4 (Day 11-28)      → Agent 编排统一（反思循环/记忆/Multi-Agent，与 Phase 3 并行）
Phase 5 (Day 17-25)      → Tool Calling + MCP + 权限接入 + WebSearch 替换
Phase 6 (Day 17-30)      → DAG 工作流增强（gRPC 接入/节点扩展/表达式引擎）
Phase 7 (Day 28-38)      → 可观测性 + RAGAS 评估框架
Phase 8 (Day 39-45)      → 集成测试与验收
```

总工期预估：**7 周（45 个工作日）**（原 5 周，因补强 RAGAS/CI/CD/权限检索/HyDE 等扩展至 7 周）

### E. 战略报告债务覆盖检查

| 战略报告债务项 | 计划覆盖 Task |
|---------------|--------------|
| #1 无 CI/CD | Task 0.3 |
| #2 无自动化测试 | Phase 0 全部 |
| #3 生产 MySQL vs 开发 PostgreSQL | 不在本计划范围（基础设施） |
| #4 AI Judge 静默失败 | Task 1.1 |
| #5 MCP 协议桩实现 | Task 5.1 |
| #6 DAG ExecuteFlow 未实现 | Task 6.1 |
| #7 Weaviate 仍部署 | 不在本计划范围（基础设施） |
| #8 硬编码外部 IP | 不在本计划范围（配置管理） |
| #9 KAC 模块未完成 | 不在本计划范围（业务功能） |
| #10 3 套代码编辑器 | 不在本计划范围（前端） |
| #11 Neo4j 4.4 EOL | 不在本计划范围（基础设施） |
| #12 ext 模块被禁用 | 不在本计划范围（知识图谱 Phase） |
| #13 Lucene IndexWriter 无复用 | Task 3.4b |

| 战略报告 8 项 AI 缺陷 | 计划覆盖 Task |
|----------------------|--------------|
| #1 AiJudgeService 静默失败 | Task 1.1 |
| #2 HermesKernel 未接入 | Task 4.1 |
| #3 DAG ExecuteFlow 未实现 | Task 6.1 |
| #4 MCP 工具执行桩 | Task 5.1 |
| #5 ToolPermissionEnforcer 未使用 | Task 5.4 |
| #6 LuceneServiceImpl 无连接池 | Task 3.4b |
| #7 GeneralSplitter overlap bug | Task 1.0 |
| #8 中文分词缺失 | Task 3.2 |
