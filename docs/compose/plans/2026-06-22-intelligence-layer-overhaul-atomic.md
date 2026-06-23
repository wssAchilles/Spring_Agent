# 智能层升级 — Agent 执行手册

> **Tech Stack:** Spring Boot 3.5 / Spring AI 2.0 / Spring AI Alibaba 2.0 / PgVector / gRPC / JUnit 5 + Mockito
> **测试规范:** 所有测试统一存放 `backend/tests/src/test/java/tech/qiantong/qknow/{hermes,ai,kmc,kb}/`
> **依赖链:** Phase 0 → Phase 1 → Phase 2（阻塞）→ Phase 3/4/5/6 可并行 → Phase 7 → Phase 8

---

## Phase 0：基础设施

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 0.1 | 创建 `backend/tests/` Maven 模块，父 POM 追加 `<module>tests</module>`，引入被测模块 + `spring-boot-starter-test` + `junit-jupiter` + `mockito` + `h2` | `backend/tests/pom.xml` + `application-test.yml` | `mvn install -pl backend/tests -DskipTests` 通过 |
| 0.2 | 编写 SmokeTest（`@SpringBootTest` 加载验证 + JUnit5/Mockito 基础验证） | `SmokeTest.java` | `mvn test -pl backend/tests` 通过 |
| 0.3 | 创建 GitHub Actions CI/CD：PR→编译→测试→覆盖率≥60%阻止合并；main→Docker 构建→推送 | `.github/workflows/ci.yml` + `deploy.yml` | PR 自动触发 CI，测试失败阻止合并 |

**转场检查：** `mvn test -pl backend/tests` 全绿 + CI 流水线可触发

---

## Phase 1：P0 安全修复

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 1.0 | 修复 `GeneralSplitter.setOverlap()` 越界：`chunkList.size()<2` 时跳过 | 修改 `GeneralSplitter.java:85-86` + `GeneralSplitterBugTest.java` | 单 chunk 不抛异常 |
| 1.1 | `AiJudgeService` catch 块 `passed`→`failed`（:60-62 + :136-138） | 修改 `AiJudgeService.java` + `AiJudgeServiceTest.java` | 评分失败时 `isPassed()=false` |
| 1.2 | 新增 `JudgeConfig`（`@ConfigurationProperties("hermes.judge")`），注入 `AiJudgeService`，替代硬编码 DeepSeek | `JudgeConfig.java` + 修改 `AiJudgeService.java` + `AiJudgeConfigTest.java` | yml 可配评分模型 |
| 1.3 | `VectorStoreServiceImpl.getVectorStore()` 双重检查锁缓存 JdbcTemplate + PgVectorStore | 修改 `VectorStoreServiceImpl.java` + `VectorStoreServiceImplTest.java` | 100 次调用仅 1 个实例 |

**转场检查：** 4 个测试全绿 + 无安全漏洞残留

---

## Phase 2：Spring AI 2.0 迁移（阻塞后续）

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 2.1 | 父 POM 统一声明 `springAi.version=2.0.0`，子模块移除本地版本，处理 `FunctionToolCallback→ToolCallback` 等 API 变更 | 修改 3 个 `pom.xml` | `mvn install -DskipTests` 全模块通过 |
| 2.2 | 适配 4 平台 ChatModel builder API（OpenAI/DeepSeek/DashScope/Ollama） | 修改 `ChatModelFactory.java` + `ChatModelServiceImpl.java` + `ChatModelFactoryTest.java` | 4 平台创建不抛异常 |
| 2.3 | 搜索所有 `FunctionToolCallback.builder` → 替换为 2.0 ToolCallback API | 修改 `AgentOrchestrator.java` + `KbAgentConfigServiceImpl.java` + `ToolCallbackMigrationTest.java` | 无 `FunctionToolCallback` 残留 |
| 2.4 | 适配 `PgVectorStore.builder()` / `SearchRequest.builder()` / `Document.getScore()` API 变更 | 修改 `VectorStoreServiceImpl.java` + `KmcKnowledgeBaseServiceImpl.java` + `VectorStoreApiTest.java` | 向量检索正常 |
| 2.5 | Hermes `ChatModelFactory` 删除，引用 qknow-ai 实现；`WebClient.Builder` 每次新建避免线程安全；ChatModel 以 `platform+model+baseUrl` 为 key 缓存 | `ChatModelCacheConfig.java` + 修改 2 文件 + `ChatModelCacheTest.java` | 并发 100 次仅 1 实例 |

**转场检查：** `mvn install -DskipTests` 全绿 + 5 个测试全绿

---

## Phase 3：RAG 引擎升级

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 3.1 | 新建 `QueryTransformService`：`compressQuery(history)` + `rewriteQuery(query)`，接入 `recallTest()` 前置 | `QueryTransformService.java` + 修改 `KmcKnowledgeBaseServiceImpl.java` + 测试 | "它的第二大城市呢？"正确压缩 |
| 3.1b | `QueryTransformService` 扩展：`generateHypotheticalDocument(query)` + `expandQueries(query, n)`，配置项 `strategy: none/hyde/multi_query/both` | 修改 `QueryTransformService.java` + `HyDEMultiQueryTest.java` | HyDE 召回率提升 |
| 3.2 | 新建 `RecursiveSplitter`（分隔符 `\n\n→\n→。→.→空格`，递归+overlap+中文标点）+ `SemanticSplitter`（Embedding 相似度切分），`GeneralSplitter` 标记 `@Deprecated` | 2 新文件 + 修改 1 文件 + `SplitterTest.java` | 段落边界切分，中文>5000字通过 |
| 3.3 | 新建 `DocumentPostProcessor`：`deduplicate(哈希去重)` + `fitToContextWindow(裁剪)`，接入 rerank 后 | `DocumentPostProcessor.java` + 修改 1 文件 + 测试 | 无重复内容 |
| 3.4 | SQL 迁移：`content_tsv tsvector` + GIN 索引 + 触发器；`fullTextSearch()` 改用 `WHERE content_tsv @@ plainto_tsquery` | `03-fulltext-migration.sql` + 修改 2 文件 + `FullTextSearchTest.java` | 不依赖 `lucene.indexPath` |
| 3.4b | `LuceneServiceImpl` IndexWriter 单例化 + `@PreDestroy` 关闭 hook + `ReentrantLock` 并发保护 | 修改 `LuceneServiceImpl.java:46-61` + `LuceneWriterTest.java` | 100 次写入仅 1 实例 |
| 3.5 | 新建 `RagCacheService`：Redis 缓存 `kbId+queryHash+method`，TTL 300s，文档更新时 `@CacheEvict` | `RagCacheService.java` + 修改 1 文件 + 测试 | 相同查询第二次 <10ms |
| 3.6 | 新建 `PermissionFilter.buildPermissionFilter(userId)`，查询 `kmc_knowledge_role` 构建 `kb_id in (...)` 过滤，注入 `semanticSearch()` + `fullTextSearch()` | `PermissionFilter.java` + 修改 1 文件 + `PermissionAwareSearchTest.java` | 普通用户无法检索未授权库 |

**转场检查：** 8 个测试全绿 + RAG 管线端到端可运行

---

## Phase 4：Agent 编排统一

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 4.1 | 新建 `ReflectiveAgent`（组合 AgentOrchestrator + HermesKernel）：推理→Judge→通过返回/未通过反馈注入→重推理（maxRetries 轮）。新增 `ChatEvent` 类型：`ReflectionStart/Score/Complete` | `ReflectiveAgent.java` + 修改 2 文件 + `ReflectiveAgentTest.java` | 反思事件流式推送 |
| 4.2 | 新建三层记忆：`ShortTermMemory`（20 轮窗口+自动摘要）+ `LongTermMemory`（PgVector 存储+按会话隔离）+ `WorkingMemory`（JSONObject 临时状态）+ `MemoryManager` 统一注入 | 4 新文件 + `MemoryManagerTest.java` | 20 轮后自动摘要不丢信息 |
| 4.3 | 新建 `BaseAgent`→`WorkerAgent`/`SupervisorAgent` + `AgentRegistry`。预置 RAG/Search/Code 三 Worker。Supervisor：LLM 拆任务→分发→聚合 | 4 新文件 + `MultiAgentTest.java` | Supervisor 正确拆分+协调 |

**转场检查：** 3 个测试全绿 + 反思循环可流式输出

---

## Phase 5：Tool Calling + MCP

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 5.1 | 用 `spring-ai-mcp-client-spring-boot-starter` 替换 `McpToolAdapter` 占位逻辑；`registerServer()` 自动 `tools/list`；`createMcpToolCallback()` 通过 McpClient 实际调用 | `McpClient.java` + `McpProtocolHandler.java` + 修改 `McpToolAdapter.java` + 测试 | 可连接外部 MCP Server |
| 5.2 | 新建 `ToolResilienceDecorator`（超时 30s + 指数退避重试 3 次 + 降级消息）+ `ToolCircuitBreaker`（5 次熔断→60s 半开→恢复），包装所有 ToolCallback | 2 新文件 + 修改 `AgentOrchestrator.java` + `ToolResilienceTest.java` | 连续失败 5 次自动熔断 |
| 5.3 | 新建 `ToolDiscoveryService`：扫描 `@Component` Function Bean + MCP Server 工具，`discoverAll()` 返回分类列表 | `ToolDiscoveryService.java` + 修改 1 文件 + 测试 | 新增 Bean 自动发现 |
| 5.4 | `AgentOrchestrator` 工具调用前插入 `ToolPermissionEnforcer.checkPermission()`，权限不足返回友好消息 | 修改 `AgentOrchestrator.java` + `ToolPermissionIntegrationTest.java` | 低权限无法调 DANGEROUS 工具 |
| 5.5 | `WebSearchToolFunction` 改为可配置提供商（`serper/bing/duckduckgo`），统一 `SearchProvider.search()` 接口，`WEBSEARCH_API_KEY` 环境变量注入 | 修改 `WebSearchToolFunction.java` + `WebSearchToolTest.java` | 配置切换提供商 |

**转场检查：** 5 个测试全绿 + MCP 可调用外部工具

---

## Phase 6：DAG 工作流增强

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 6.1 | 新建 `FlowExecutor`（加载流程定义→构建节点/边→DagExecutor.execute→映射 FlowEvent），`HermesGrpcService.executeFlow()` 实际接入 | `FlowExecutor.java` + 修改 `HermesGrpcService.java:60-72` + `FlowExecutorTest.java` | gRPC executeFlow 返回 FlowEvent |
| 6.2 | 新建 `HttpNodeBO` + `KnowledgeNodeBO` + `AggregatorNodeBO`，`FlowNodeTypeEnums` 新增枚举，`NodeFactory` 注册 | 3 新文件 + 修改 2 文件 + `NodeTypesTest.java` | NodeFactory 可创建 7 种节点 |
| 6.3 | 新建 `ExpressionEngine`（SpEL 白名单模式），支持 `==/!=/>/</>=/<=/&&/\|\|/!/contains/startsWith`，`ConditionNodeBO` 委托调用 | `ExpressionEngine.java` + 修改 1 文件 + `ExpressionEngineTest.java` | `{{x}}>=0.8 && {{y}}=='tech'` 正确评估 |

**转场检查：** 3 个测试全绿 + DAG 端到端可执行

---

## Phase 7：可观测性 + 评估

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 7.1 | 新建 `TraceSpan` + `TraceContext` + `TraceCollector`（startSpan/endSpan/getTrace，内存缓存+异步写 DB），AgentOrchestrator 关键节点挂载 trace | 3 新文件 + 修改 1 文件 + `TraceCollectorTest.java` + SQL 建表 | 完整 trace 树可导出 |
| 7.2 | 新建 `TokenCounter`（从 Usage 提取）+ `CostEstimator`（预置 DeepSeek/GPT-4o/Gemini 价格），写入 `agent_trace` | 2 新文件 + `CostEstimatorTest.java` | 对话后可查 token+成本 |
| 7.3 | 新建 `RagasEvaluator`（LLM-as-Judge 实现 Faithfulness/Answer Relevance/Context Precision/Context Recall 四指标）+ `EvaluationDataset`（JSON 导入导出）+ `EvaluationReport`（JSON+Markdown） | 3 新文件 + `RagasEvaluatorTest.java` + SQL 建表 | 10 条数据生成完整报告 |
| 7.4 | 新建 `EvalDatasetService`（CRUD+导入导出）+ `EvalController`（POST /api/eval/run, GET /report/{runId}, GET /datasets），异步评估管线 | 2 新文件 + `EvalDatasetServiceTest.java` | API 触发评估返回报告 |

**转场检查：** 4 个测试全绿 + 评估报告可生成

---

## Phase 8：集成验收

| # | 动作 | 交付物 | 验收 |
|---|------|--------|------|
| 8.1 | RAG E2E：创建知识库→上传文档→切块→向量化→混合检索→Rerank→生成回答（H2 + mock EmbeddingModel） | `RagE2ETest.java` | 三种检索模式全通过 |
| 8.2 | Agent E2E：创建 Agent→配置知识库/工具→对话→反思循环→工具调用→记忆存储（mock ChatModel + mock 工具） | `AgentE2ETest.java` | 单轮/多轮/降级全通过 |
| 8.3 | DAG E2E：定义流程（START→LLM→CONDITION→LLM/REPLY）→执行→验证（串行/并行/条件/失败） | `DagE2ETest.java` | 全场景通过 |
| 8.4 | 性能基准：1000 文档切块耗时、向量检索 P95、混合检索 P95、Agent 端到端延迟、并发 10 用户吞吐 | `RagPerformanceTest.java` + `AgentPerformanceTest.java` | 生成 `target/performance-report.json` |

**转场检查：** 全部 E2E 测试通过 + 性能报告生成 + `mvn test -pl backend/tests` 全绿

---

## 附录

### A. 变更统计

| Phase | 新增 | 修改 | 测试 |
|-------|------|------|------|
| 0 | 4 | 1 | 1 |
| 1 | 0 | 4 | 4 |
| 2 | 1 | 6 | 5 |
| 3 | 8 | 5 | 8 |
| 4 | 8 | 2 | 3 |
| 5 | 6 | 4 | 5 |
| 6 | 5 | 3 | 3 |
| 7 | 9 | 1 | 4 |
| 8 | 4 | 0 | 0 |
| **合计** | **45** | **26** | **33** |

### B. 配置清单

```yaml
hermes:
  judge: { platform: deepseek, modelName: deepseek-chat, apiKey: ${JUDGE_API_KEY}, threshold: 0.7 }
  reflection: { enabled: true, maxRetries: 2 }
  rag:
    cache: { enabled: true, ttl: 300 }
    queryTransform: { enabled: true, strategy: rewrite }
    splitter: { strategy: recursive }
  tool:
    timeout: 30000
    retry: { maxAttempts: 3 }
    circuitBreaker: { threshold: 5, recoveryTimeout: 60000 }
    webSearch: { provider: serper, apiKey: ${WEBSEARCH_API_KEY} }
  memory:
    shortTerm: { windowSize: 20 }
    longTerm: { enabled: true }
```

### C. 新增表

```sql
-- Trace
CREATE TABLE agent_trace (id BIGSERIAL PK, trace_id VARCHAR(64) UNIQUE, request_id VARCHAR(64), bot_id BIGINT, user_id BIGINT, total_duration_ms BIGINT, total_tokens INT, total_cost DECIMAL(10,6), status VARCHAR(20), created_at TIMESTAMP DEFAULT NOW());
CREATE TABLE agent_trace_span (id BIGSERIAL PK, trace_id VARCHAR(64), span_id VARCHAR(64) UNIQUE, parent_span_id VARCHAR(64), name VARCHAR(128), input TEXT, output TEXT, status VARCHAR(20), duration_ms BIGINT, token_count INT, cost DECIMAL(10,6), created_at TIMESTAMP DEFAULT NOW());
-- 全文检索
ALTER TABLE kmc_document_segment ADD COLUMN content_tsv tsvector;
CREATE INDEX idx_segment_content_tsv ON kmc_document_segment USING GIN(content_tsv);
CREATE TRIGGER trg_segment_content_tsv BEFORE INSERT OR UPDATE ON kmc_document_segment FOR EACH ROW EXECUTE FUNCTION tsvector_update_trigger(content_tsv, 'simple', content);
-- 评估
CREATE TABLE eval_dataset (id BIGSERIAL PK, name VARCHAR(128), knowledge_base_id BIGINT, description TEXT, created_at TIMESTAMP DEFAULT NOW());
CREATE TABLE eval_dataset_item (id BIGSERIAL PK, dataset_id BIGINT REFERENCES eval_dataset(id), query TEXT, expected_answer TEXT, ground_truth_contexts JSONB, created_at TIMESTAMP DEFAULT NOW());
CREATE TABLE eval_result (id BIGSERIAL PK, run_id VARCHAR(64) UNIQUE, dataset_id BIGINT REFERENCES eval_dataset(id), faithfulness DECIMAL(5,4), answer_relevance DECIMAL(5,4), context_precision DECIMAL(5,4), context_recall DECIMAL(5,4), report_json JSONB, status VARCHAR(20), created_at TIMESTAMP DEFAULT NOW());
```

### D. 执行时序

```
Day 1-3   Phase 0  基础设施
Day 3-5   Phase 1  安全修复
Day 6-10  Phase 2  Spring AI 2.0 迁移
Day 11-22 Phase 3  RAG 升级        ─┐
Day 11-28 Phase 4  Agent 统一       ├─ 并行
Day 17-25 Phase 5  Tool + MCP       │
Day 17-30 Phase 6  DAG 增强        ─┘
Day 28-38 Phase 7  可观测性 + 评估
Day 39-45 Phase 8  集成验收
```

**总工期：7 周（45 工作日）**

### E. 债务覆盖映射

| 债务 | Task | 缺陷 | Task |
|------|------|------|------|
| #1 无 CI/CD | 0.3 | #1 Judge 静默失败 | 1.1 |
| #2 无测试 | 0.1-0.2 | #2 Kernel 未接入 | 4.1 |
| #4 Judge 默认通过 | 1.1 | #3 DAG 501 | 6.1 |
| #5 MCP 桩 | 5.1 | #4 MCP 桩 | 5.1 |
| #6 DAG 501 | 6.1 | #5 Permission 未用 | 5.4 |
| #13 IndexWriter | 3.4b | #6 IndexWriter | 3.4b |
| — | — | #7 Splitter 越界 | 1.0 |
| — | — | #8 中文分词 | 3.2 |
