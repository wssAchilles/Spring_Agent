# 企业级 RAG 知识引擎与图谱检索审计报告（task-3）

- 任务：task-3（RAG 知识引擎审计员）
- 审计日期：2026-10-19（Asia/Shanghai）
- 代码基线：`backend/` 工作区当前未提交状态（只读，未执行 mvn/gradle，未启动服务，未运行测试）
- 交付约束：本文件是本次审计唯一写入的文件；未修改任何源码、配置、fixture、qrel、SQL
- 证据分级：**已验证**（源码行/证据文件直接证明）、**推断**（多行源码逻辑串联但未运行验证）、**未知**（现有材料无法判定）

## 0 阅读范围与方法

### 0.1 阅读范围与工作量（已验证）

| 范围 | 文件数 | 行数 | 读法 |
|---|---|---|---|
| `qknow-hermes-core/.../hermes/rag/**`（causal、cognitive） | 12 | 2,793 | 全量文件清单；核心类逐段精读 |
| `qknow-hermes-core/.../hermes/search/**`（graphrag、graphrag/causal、hybrid、speculative、ColBERT） | 17 | 2,165 | 全量文件清单；核心类精读 + 全仓引用计数 |
| `qknow-hermes-core/.../hermes/flow/rag/**`（RagRetrievalService、governor、agentic） | 4 | 570 | 逐行精读 |
| `qknow-module-kmc-biz/.../service/rag/**`（全部 99 个类：检索/融合/重排/缓存/图谱/评测/演进） | 99 | 15,202 | 清单全量；40+ 个核心类逐行或分段精读 |
| `qknow-framework/qknow-ai`（分块器、Embedding/VectorStore 实现） | 10+ | 约 2,260 | 分块器全量、Embedding/VectorStore 实现逐行 |
| `qknow-module-kb`（检索编排、内部 API、Agent 配置、DAG 检索节点） | 15+ | — | 调用链逐行核对 |
| `qknow-module-kg`（图谱社区与图谱控制器） | 10+ | — | 关键类逐行核对 |
| `backend/tests` RAG 相关测试 | 153（`rag` 目录 106） | — | 目录结构、ShadowBaseline/候选/数据集/A-B 证据抽样精读 |
| `backend/tests/evidence/**` 评测证据 | 30+ 个目录/文件 | — | 汇总表、统计报告、完整性审计、E2E 记录、A/B 报告读取与交叉核对 |
| Spring AI PgVectorStore 1.1.0 二进制 | 本地 m2 jar | — | `javap` 核对默认距离类型与 builder 默认值 |

### 0.2 方法

1. 先建立**真实调用边**：从控制面聊天入口与内部 API 反向追踪到 kmc 检索服务，再到 Hermes gRPC/Agent；对每个"高级算法类"统计全仓生产引用（排除 `src/test`、测试目录与自己）。
2. 再评估**实现语义**：核对 SQL/公式/常量/降级分支，而不是相信类名与注释。
3. 最后核对**证据可信度**：读取 `tests/evidence` 的汇总、统计报告、完整性审计与 E2E 原始记录，确认每个数字对应的是"真实生产管线"还是"离线代理仿真"。
4. 全文每条结论附 `路径:行号`；凡"生产默认值"均核对 `application*.yml` 基线配置。

### 0.3 路径缩写（全文统一）

| 缩写 | 实际路径 |
|---|---|
| `HA` | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent` |
| `HRAG` | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag` |
| `HS` | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/search` |
| `HFR` | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/rag` |
| `KRAG` | `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag` |
| `KMCBASE` | `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java` |
| `KBCFG` | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/impl/KbAgentConfigServiceImpl.java` |
| `AICORE` | `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai` |
| `TESTS` | `backend/tests/src/test/java/tech/qiantong/qknow` |
| `EVID` | `backend/tests/evidence` |

---

## 1 真实检索执行路径

### 1.1 端到端主链路（已验证）

一次 Web 端机器人问答的实际链路如下（以 `KbAgentConfigServiceImpl.chatMessage` 为入口）：

1. **语义缓存前置**：`KBCFG:226` 进入 `chatMessage`，`:258` 调 `findSemanticCache`（命中则 `:259-261` 直接返回缓存 Flux，不再检索、不再调用 Hermes）。
2. **控制面预检索（同步阻塞于返回 Flux 之前）**：`:263-365`。`@Resource` 注入的 `multiKbCoordinator` / `crossKbCalibrator`（`KBCFG:75-79`）在应用上下文中恒非空，因此实际**总是**走协调器分支：`:278` `coordinateRetrieval(...)`，`:279` `calibrateAndAssemble(...)`。协调器内部对每个知识库在 `threadPoolTaskExecutor` 上并发调用 `kmcApiService.recallTest(kbId, question, turns)`（`backend/qknow-module-kb/.../agent/retrieval/MultiKbRetrievalCoordinator.java:100-138`）。
3. **kmc 侧检索**：`recallTest`（`KMCBASE:324`）先做权限检查（异常吞掉，`:325-333`）、查询改写（`:365`）、多轮压缩（`:377-380`）、`RagCacheService` 缓存（`:389-393`），再调 `ragRetrievalService.retrieve(...)`（`:394`）。
4. **RAG v2 流水线**（`RagRetrievalService.retrieve`，`KRAG/RagRetrievalService.java`）：
   - 路由分类 `queryRouter.classify`（`:202`）；命中 SIMPLE 且 `simpleLightRetrieval`（默认 true）时走**纯关键词轻检索**（`:209-211`，实现 `:124-179`），零 LLM、零向量；
   - 权限过滤 `permissionFilter.getAccessibleKnowledgeBaseIds(SecurityUtils.getUserId())`（`:226`）；
   - CRAG 评估 `cragRetrievalEvaluator.evaluate`（`:250`），INCORRECT 时二次检索（`:277-298`）或 Web 搜索兜底（`:299`、`:734-758`）；
   - `retrieveOnce`（`:322`）四路并行：向量 `retrieveVectorVariants`（`:353-356`）、关键词（`:356-357`）、元数据（`:365`）、图谱（`:368`）；分别受 5s 超时约束（`:713`）；
   - RRF 融合 `candidateFusionService.fuseWithDiagnostics`（`:434`）；
   - 重排 `ragRerankService.rerank`（`:451`）；
   - 上下文装配 `safeBuildContext`（`:476`）。
5. **跨库二次融合**：`CrossKbScoreCalibrator.calibrateAndAssemble` 对每库结果做全局 RRF（k=60）并 20KB 截断（`backend/qknow-module-kb/.../retrieval/CrossKbScoreCalibrator.java:69-190`），随后 `KBCFG:294-313` 按库重新拼 `[来源 N]` 文本写入 `RAGContext.preRetrievedContent`。
6. **gRPC 到 Hermes**：`KBCFG:380-410` 构造 `ChatRequest`（含 `ragContexts`、history、modelConfig 明文 apiKey），`:438-439` `hermesGrpcClient.chat(request)`（客户端 deadline 120s，明文 channel，`backend/qknow-module-kb/.../agent/HermesGrpcClient.java:41-49`、`:35-36`）。
7. **Hermes 内**：`HermesGrpcService.chat`（`backend/qknow-hermes/.../grpc/HermesGrpcService.java:32-40`）→ `AgentOrchestrator.chat` → `executeAgent`（`HA/AgentOrchestrator.java:244`）：
   - `:272-276` 调 `retrievalEvaluator.evaluate(...)` 做**又一次 CRAG 评估**；
   - `:283-310` 把预检索内容包装为伪工具 `knowledgeBase{id}` 与 `knowledgeQuery`（`SearchKnowledgeTool` 仅回放文本，不发起检索：`HA/../tool/function/SearchKnowledgeTool.java:21-33`）；
   - `:392` 将 RAG 上下文拼进**本轮 User 消息前缀**（`:391-396`），而不是 system prompt，以减少上下文缓存污染；
   - 之后进入 ReAct 流式生成，DeepSeek 唯一生成侧。

### 1.2 Hermes 侧是否还有第二套检索？（已验证）

- **没有独立的 Hermes 向量检索**：Hermes 的 `flow/bo/KnowledgeNodeBO` 走 HTTP 回调控制面 `/internal/api/rag/recall`（`backend/qknow-hermes/.../flow/bo/KnowledgeNodeBO.java:55`；`HFR/RagRetrievalService.java:54-87`），服务端是 `KbInternalApiController`（`backend/qknow-module-kb/.../controller/admin/KbInternalApiController.java:33-45`），最终仍调用 `kmcApiService.recallTest`——**算法同一套，但多一次网络往返，且丢失调用方用户身份/权限上下文**（见 1.3）。
- **Hermes 的 `search/**`、`rag/**` 高级检索算法没有进入生产主链**：`HierarchicalGraphRagEngine`、`ResilientHybridRetrievalFunnel`、`ReciprocalRankFusionEngine`、`EmbeddedInvertedIndexEngine`、`SpeculativeRagOrchestrator` 在生产 main 代码中仅被 `hermes/benchmark/e2e/E2EFourMetacenterPipelineBus`（benchmark 目录）与测试引用；`HypersphericalGraphRagGovernor` 全仓零引用；`HypersphericalHippoPprEngine`、`HermesAgenticCorrectiveRagOperator` 仅自引用；`HierarchicalAstChunker`、`CausalSubgraphPruner`、`GraphStreamLosslessTypewriter`、`SpatiotemporalManifoldAligner` 均无生产引用（全仓 grep，排除 `src/test` 与自身声明）。
- Hermes 的 `RagRetrievalService` 是"HTTP 回调 + 仅解析 `content/text` 字段"的薄客户端（`HFR/RagRetrievalService.java:112-126`），返回的是**字符串列表**，不携带 segmentId/score/出处，下游无法做引用闭环。

### 1.3 双路重复与路径漂移（已验证/推断）

| 编号 | 现象 | 证据 | 判定 |
|---|---|---|---|
| D-1 | 两套 CRAG：控制面采样 10% 调 DeepSeek 评估，Hermes 每轮**无条件**再调一次 DeepSeek 评估 | `KRAG/CragRetrievalEvaluator.java:44-52`（gate=sample, rate=0.10）、`:186-191`；`HA/AgentOrchestrator.java:266-276`；`HA/RetrievalEvaluator.java:41-67` | 已验证（调用点+默认配置） |
| D-2 | 两套上下文预算：kmc 侧 `maxContextBytes` 类默认 20,000，但基线配置 `hermes.rag.context.max-bytes=12000`；跨库侧又用 20,000 | `KRAG/RagContextBuilder.java:31-36`；`backend/qknow-server/src/main/resources/application.yml:142-143`；`CrossKbScoreCalibrator.java:36-37` | 已验证 |
| D-3 | 两次 RRF：kmc 内每库 RRF（k=60），跨库再 RRF（k=60） | `KRAG/CandidateFusionService.java:16`、`:59-114`；`CrossKbScoreCalibrator.java:31`、`:130-145` | 已验证 |
| D-4 | **v2→legacy 静默回退**：v2 检索只要抛异常就切换到旧版 `semanticSearch/fullTextSearch/hybridSearch`；旧版在无用户上下文时**显式跳过权限过滤** | `KMCBASE:388-413`（try v2 / catch fallback）；`KMCBASE:725-735`、`:780-786`（注释"无用户上下文时跳过权限过滤（如 Agent 对话）"） | 已验证（代码路径） |
| D-5 | **异步线程无 SecurityContext**：多库协调器与 DAG 节点在 `threadPoolTaskExecutor`/虚拟线程上执行，`ThreadPoolConfig` 未配置 `TaskDecorator`；`SecurityUtils.getUserId()` 在无认证上下文时抛 `ServiceException` | `backend/qknow-framework/qknow-config/.../ThreadPoolConfig.java:32-43`；`MultiKbRetrievalCoordinator.java:100-110`、`:138`；`backend/qknow-framework/qknow-common/.../SecurityUtils.java:29-39`；`KRAG/RagRetrievalService.java:226`、`:130` | 已验证（静态）；**运行时后果推断**：v2 在异步线程必然抛异常→D-4 回退旧版管线 |
| D-6 | 内部 HTTP 召回接口 `/internal/api/rag/recall` 无独立鉴权，Hermes 默认 token 为空；`knowledgeId/query` 直接 `toString()`，缺失即 NPE | `KbInternalApiController.java:33-45`；`HFR/RagRetrievalService.java:39-44`；`application.yml` 无该路由 permit 配置（SecurityConfig 仅对 login/actuator 等放行，`backend/qknow-framework/qknow-security/.../SecurityConfig.java:117-148`） | 已验证（静态）；若 token 配置为静态共享值则等价于"单一身份"，见 3 章 P1 |
| D-7 | SIMPLE 路由在轻检索开启时只查关键词、零向量零 LLM；若轻检索关闭且非 debug，则**直接返回空上下文** | `KRAG/RagRetrievalService.java:209-219`；`application-dev.yml:200-204`（light-retrieval: true, light-top-k: 5） | 已验证 |

**结论（推断，需运行验证）**：机器人聊天路径上，`multiKbCoordinator` 的线程池线程没有用户身份，`recallTest` 内 v2 的 `SecurityUtils.getUserId()` 抛异常，被 `KMCBASE:411-413` 捕获后落入 legacy 检索；legacy 检索又显式跳过权限过滤。也就是说：**生产聊天实际使用的很可能不是被重点宣传的 RAG v2 管线，而是旧的 hybrid/semantic/fulltext 实现 + 跨库 RRF；且 KB 角色权限在该路径上不生效**。

### 1.4 一次问答实际发生几次 embedding / 向量检索 / 重排（已验证 + 推断）

以"N 个知识库（KB）、有历史、缓存未命中、非 SIMPLE 路由、默认 dev 配置"为例：

| 阶段 | 次数 | 证据 |
|---|---|---|
| 语义缓存 L2 查询 embedding | 1 | `KRAG/SemanticCacheService.java:99-137`（`embed(query, embeddingModel)` 后执行 `semantic_cache_store` 余弦查询） |
| 每 KB 向量检索：`PgVectorStore.similaritySearch` 内部 embedding | N | `KRAG/VectorRetriever.java:90-96`；`AICORE/service/impl/VectorStoreServiceImpl.java:33-41`（PgVectorStore 包装 `embeddingModel`，查询文本由 Spring AI 内部 embed） |
| 每 KB VecSim 重打分再次 embedding | N | `KRAG/VectorRetriever.java:117-118`、`:128-195`（`rescoreWithVecSim` 调 `embedQuery`，`:196-203`） |
| 向量 ANN 检索 | N | 同上 `similaritySearch`（pgvector HNSW/ivfflat） |
| 关键词 SQL 检索 | N | `KRAG/KeywordRetriever.java:117-190`（Tantivy 默认关，走 pg_trgm + `ts_rank_cd`） |
| 图谱检索 | 0（默认关） | `KRAG/GraphRagProperties.java:11`（`enabled=false`）、`:15`（`pprEnabled=false`） |
| 重排 HTTP 调用 | 每 KB ≤1 | `KRAG/RagRerankService.java:61-126`；门控可跳过（`:381-432`）；ColBERT 默认被 skip（`:301-311`） |
| LLM 调用 | ≥2 + 附加 | Hermes 检索评估 1 次（`HA/RetrievalEvaluator.java:55-60`）；DeepSeek 生成 1 次；控制面 CRAG 采样 ≈0.1N；生产 profile 下查询改写 1×N（`application.yml:133-134` enabled 默认 true，dev 覆盖 false 见 `application-dev.yml:185-186`）；RAGAS 4 指标（LangFuse 启用时，`HA/AgentOrchestrator.java:200-215`、`backend/qknow-hermes/.../eval/RagasEvalConfig.java:15-16` 默认 enabled=true） |
| 缓存写入 embedding | 1（异步） | `KRAG/SemanticCacheService.java:199-232`；`KBCFG:493-514` |

- 合计：**embedding = 2N+1**（单库=3 次）；**ANN = N**；**重排 HTTP ≤N**；每轮 LLM ≥2 次（+N 次改写 if prod + 0.1N 次 CRAG + 可能 4 次 RAGAS）。
- **结论**：单库问答至少要 3 次 embedding（其中 2 次相同 query 的重复计算）、1 次 ANN、1 次 Hermes LLM 评估、1 次生成；多库线性放大。`VectorRetriever` 的"pgvector 打分后再用 JNI 重算一遍余弦"属于**重复计算同一语义分数**（`:96` 与 `:117-118`），并不引入新信息，只增加一次 Embedding API 往返与全候选 float 重算（`loadCandidateVectors` 还要回表捞向量：`:204-...`）。
- **推断**：N 较大（例如 5-10 个 KB）时，嵌入调用与 SQL 数线性增长，且每个 KB 独立走 CRAG/路由/重排，端到端 P99 风险高；而 `CrossKbScoreCalibrator` 的融合又发生在所有子管线之后，无法提前剪枝。

### 1.5 延迟预算现状（已验证）

| 环节 | 预算/超时 | 证据 |
|---|---|---|
| 单库并发召回软超时 | 2500ms | `MultiKbRetrievalCoordinator.java:44-48` |
| 多库全局硬超时 | 3000ms | `MultiKbRetrievalCoordinator.java:50-58` |
| 四路检索 Future | 5000ms | `KRAG/RagRetrievalService.java:713` |
| DashScope Rerank | connect 10s / read 30s | `KRAG/rerank/DashScopeRerankClient.java:46-48`、`:71-77` |
| CRAG Web 搜索 | 10,000ms | `application.yml:19-22`；`KRAG/CragWebSearchClient.java:42-45` |
| Hermes gRPC deadline | 120s | `HermesGrpcClient.java:35-36` |
| 端到端 RAG 预算 | **无** | 全仓未发现统一 deadline/budget 配置 |

---

## 2 架构与实现评估

### 2.1 Embedding 契约：1536 维 + 单位归一化是否被强制？（问题 2）

**结论：没有在代码层被强制。**"1536 维 + 单位向量 + 余弦"目前主要由 ①DDL 列类型、②部分算法的局部假设、③注释与文档三处"约定"维持，而不是由任何统一门禁保证。

- **数据库层（已验证）**：`deploy/sql/postgresql/01-schema.sql:1410-1421` 定义 `vector_store.embedding vector(1536)`，索引为 `ivfflat (embedding vector_cosine_ops)`；后续迁移 `10-hnsw-index-migration.sql:13-14` 改为 HNSW cosine。pgvector 会拒绝维度不符的写入/查询，这是当前**唯一真实生效的维度约束**。
- **Spring AI 距离类型（已验证，二进制核对）**：本地 `spring-ai-pgvector-store-1.1.0.jar` 的 `PgVectorStore$PgVectorStoreBuilder` 构造器默认 `distanceType = COSINE_DISTANCE`（`javap -c` 字节码），`VectorStoreServiceImpl` 未覆盖该默认值（`AICORE/service/impl/VectorStoreServiceImpl.java:36-40`），因此检索使用 `<=>` 余弦距离，并把 `1 - distance` 作为相似度——几何上是正确的余弦路径。
- **维度参数未显式声明（已验证）**：`EmbeddingServiceImpl` 构造 `OpenAiEmbeddingModel` 时只传 `model`，没有传 `dimensions`（`AICORE/service/impl/EmbeddingServiceImpl.java:76`、`:97`）。也就是说 1536 是"服务端默认恰好如此"的假设，不是客户端契约。
- **归一化无强制校验（已验证）**：`EmbeddingDimensionGuard`（`KRAG/guard/EmbeddingDimensionGuard.java:24-107`）只校验维度与 NaN/Inf（`:41-74`），**不校验 L2 范数**，而且**全仓没有任何生产调用方**——唯一使用点是测试 `TESTS/rag/eval/Phase13DataOpsAndHealthGateTest.java:22` 直接 `new EmbeddingDimensionGuard()`。该 guard 的 `onStartupCheck` 只查询 `information_schema.tables` 是否存在 `vector_store` 表（`:91-107`），不做维度/范数审计。
- **唯一真正做 1536+单位范数校验的代码是死代码**：`HRAG/cognitive/MultimodalPyramidChunker.java:28-30`、`:106-150` 定义了 `normalizeToQwen1536Hypersphere` 与 `verifyHypersphereConstraint`，但该类在全仓没有生产引用（仅测试引用）。
- **实测证据与 1536 声明矛盾（已验证，来自项目自己的证据文件）**：
  - `EVID/h6-chunk-ab/README.md:7`：chunk A/B 使用模型 `qwen3.7-text-embedding (1024d)`；
  - `EVID/a1-colbert-fail-closed/a0-a1-live-ann-report.json:439`：候选来自"restored vector_store embeddings (text-embedding-v4, 1024d)"；
  - `EVID/a1-colbert-fail-closed/live-ann-environment.md:12`：恢复后列定义 `embedding vector(1536)`，但 `count(embedding)=0`。
  - 这说明项目曾在 1024 维模型上做实验，而 DDL/文档宣称 1536；二者不可能同时成立，实际库的维度与模型配置**未知**（需 Lead 运行核查）。
- **维度不一致时的行为（已验证，分层不一致）**：
  - 语义缓存：显式跳过并记 fallback，不写不读（`KRAG/SemanticCacheService.java:99-108`、`:199-204`，期望维度来自 `SemanticCacheConfig.embeddingDimension` 默认 1536，`:296-300`、`:376`）；
  - 向量检索：`PgVectorStore` 抛异常 → `VectorRetriever` catch 后返回空列表并记录 fallback（`VectorRetriever.java:119-125`）——**静默降级为空召回**；
  - 文档入库：`save2VectorStore` 写入异常被重试/吞掉（`KmcSyncServiceImpl.java` 的 retry 循环），只有日志；
  - 图谱/重排：不涉及 1536。
  - 因此"维度不一致"不会快速失败，而是**部分功能静默停摆**。
- **余弦/内积实现（已验证）**：
  - pgvector：`<=>` 余弦距离 + `1-distance`（Spring AI 默认模板）；
  - JNI 重打分：`VecSimNative.safeCosineBatch`（`KRAG/sim/VecSimNative.java:80-110`），JNI 库缺失时返回 null，调用方直接放弃重打分（`VectorRetriever.java:160-166`）；
  - 语义缓存：SQL `1 - (query_embedding::vector(1536) <=> ?::vector(1536))`（`SemanticCacheService.java:112-137`），余弦正确；
  - 其它：`MultimodalPyramidChunker.computeGeodesicDistance`（死代码）用 `acos(dot)`，假定输入已归一化；`HypersphericalGraphRagGovernor.computeCosine`（死代码）直接点积，假定单位向量。

**评价**：检索主链的距离度量在数学上正确（余弦），但"1536/单位范数"是**数据库列 + 注释级契约**，不是代码级契约；一旦模型配置改为 1024 维（证据显示曾发生），系统不会在入口处失败，而是表现为"向量检索空召回/缓存绕过/写入失败"，这属于 P0 级可观测性问题。

### 2.2 切块：HierarchicalDocumentChunker / MultimodalPyramidChunker / HierarchicalAstChunker 的真实算法（问题 3）

**先给结论（已验证）：生产入库使用的是 `RecursiveSplitter` / `StructureAwareMarkdownSplitter` / `SemanticSplitter`（`KmcSyncServiceImpl.updateResult`），题目点名的三个"层次化 chunker"全部不在生产链路上。**

1) **生产切块链路（已验证）**：
- 定时任务每 5s 扫描 `sync_status=IN` 的文档（`KmcSyncServiceImpl.java:334-336`），单线程串行处理；
- 分块器选择：`mode=semantic` 用 `SemanticSplitter`（embedding 计算语义断点，`SemanticSplitter.java:119-170`），否则 `SplitterFactory.create(mode, separator, maxTokens, chunkOverlap)`（`KmcSyncServiceImpl.java:350-358`；`SplitterFactory.java:28-49`）；
- 结构感知/递归模式下调用 `splitParentChild(documentList, parentChunkSize, childChunkSize, ...)`：`parentChunkSize=max(maxTokens,1024)`，`childChunkSize=min(maxTokens, childChunkTokens)`，`childChunkTokens` 默认 128（`KmcSyncServiceImpl.java:375-385`、`:88`）；
- 父块只入 `kmc_document_segment` 表，子块才写入向量库（`KmcSyncServiceImpl.java:605-610` 过滤 `chunk_level=parent`）；父块 ID（UUID）写入子块 metadata `parent_segment_id`（`RecursiveSplitter.java:70-90`），上下文构建时再回表展开父块（`KRAG/RagContextBuilder.java:142-221`）；
- `StructureAwareMarkdownSplitter` 在 Markdown 标题层级上切分并注入 breadcrumb（`AICORE/transformer/StructureAwareMarkdownSplitter.java:24-50`、`:93-160`）；
- 入库前叠加：实体/关系抽取（parent 抽取后继承给 child，`KRAG/EntityExtractionService.java:31-58`）、逐 chunk 的 Contextual Enrichment（LLM 生成 50-100 词上下文并**前置到 chunk 正文**，`ContextualEnrichmentService.java:18-45`、`:63-90`）、HyPE 假设问题（`KmcSyncServiceImpl.java:427-437`、`:691-713`）。
- 表格/代码：只有"结构化 Markdown 按标题/段落"处理，没有单元格级表头继承、没有 AST 语法树（代码块只作为普通文本被递归切分）。`TableArtifact` 表格线性化只存在于死代码 `MultimodalPyramidChunker.linearizeTableToMarkdown`（`:157-184`）。

2) **HierarchicalDocumentChunker（已验证：死代码，且算法存在缺陷）**：
- 位置：`KRAG/advanced/engine/HierarchicalDocumentChunker.java`，196 行；唯一引用是 `SpatiotemporalKnowledgeMetacenter`（`:137`），而后者又无生产引用。
- 算法：`buildHierarchyTree` 用 `fullContent.split("\n\s*\n")` 切"章节"（`:78`），再按单换行切段落（`:100`），按中文句号分句（`:112`），构建 Document→Section→Paragraph→Sentence 四级内存树；`expandParentContext` 向上取一层父标题/段落（`:142-181`）。
- 缺陷：① Java 15+ 中 `"\n\s*\n"` 的 `\s` 是"空格"转义，实际正则等价于 `\n *\n`，对 `\r\n\r\n`（Windows/PDF 提取常见）**完全不匹配**，整篇会退化为单 section；② 没有 chunk 大小上限，单个 section 可无限大；③ 树只存在于 `ConcurrentHashMap`，不持久化，节点无淘汰（内存泄漏面）；④ `ChunkNode.embeddingVector` 永远为 null，不参与向量化；⑤ 句子切分只认中文标点，英文句号/分号不切。
- 结论：**不能用它来宣称"层次化切块已生产化"**；若接入，需先修 CRLF 与大小上限。

3) **MultimodalPyramidChunker（已验证：死代码，且"金字塔"不切文本）**：
- 位置：`HRAG/cognitive/MultimodalPyramidChunker.java`，341 行，无生产引用（仅测试）。
- 所谓"切分"只是**接收已切好的输入**（`sectionData: Map<章节, List<事实>>`、`tableData`），组装 L1 摘要/L2 章节/L3 实体/L4 表格节点（`:188-315`）；它不做文本分块、不做表格识别、不做图片/版式解析。
- 更严重的是**向量是合成的**：`generatePerturbedVector` 在 seed 向量上加 `sin(i*0.17+delta)*0.05` 噪声再归一化（`:317-330`），不是真实 embedding；`normalizeToQwen1536Hypersphere` 对任意维度输入**先截断/补零到 1536 再归一化**（`:106-140`），若真接入 1024 维模型会静默产生"伪 1536 向量"。
- 结论：它是"结构装配 + 向量玩具"，不是多模态切块器；不能作为能力证据。

4) **HierarchicalAstChunker（已验证：死代码，算法相对最实，但有边界缺陷）**：
- 位置：`HRAG/causal/HierarchicalAstChunker.java`，358 行；无生产引用（仅测试）。
- 算法：按行扫描 Markdown，`#{1,6}` 标题入栈构建 Section 层级（`:78-135`）；表格行检测 + 表头/分隔行识别，按 `maxTableRows` 把跨页表格切成多块，**每块复制表头并线性化"列=值"**（`:200-290`）；`expandContext` 生成面包屑与继承表头（`:294-330`）。
- 缺陷：① 表格用 `split("\\|")` 解析（`:280-292`），不处理单元格内的 `\|` 转义与含管道符内容，列会错位；② 段落节点无最大长度（只切表格），长段落不切；③ `nodeStore` 为进程内 `ConcurrentHashMap` 且无清理（`:50-51`、`:319-322`），按文档累积会 OOM；④ 不是真正 AST（不含代码/HTML/PDF 结构解析）。
- 结论：可作为"表格语义保真"的最小可用原型，但必须先修解析与内存，且需要与生产 splitter 二选一，不能并存。

5) **与 metadata 的耦合（已验证）**：
- 子块 metadata 携带 `parent_segment_id`、`parent_content`、`chunk_level`、`breadcrumb`（`RecursiveSplitter.java:22-27`、`:70-92`；`StructureAwareMarkdownSplitter.java:27-31`）；
- 入库时 `parent_segment_id` 落库为 `parent_id`（`KmcSyncServiceImpl.java:530`），`entities/relations` 落 `kmc_segment_entity_metadata`（`:539-560`）并同步 Neo4j（`GraphRagSyncService.syncRows`）；
- **父块内容在入库前被 LLM Contextual Enrichment 改写**：`enrichAsync` 返回 `context + "\n\n" + chunk` 并替换 Document 文本（`KmcSyncServiceImpl.java:398-410` → `ContextualEnrichmentService.java:42-44`），因此父块/子块正文里混入了 LLM 生成文本，`parent_content` 与原文不一致；引用/出处展示会包含生成内容。这是 provenance 混淆（P2，但与"引用可回溯"直接相关）。

### 2.3 混合检索融合与重排（问题 4）

- **融合公式（已验证）**：纯 RRF，无加权、无 per-path 权重。`rrf += 1.0/(rrfK + rank + 1)`，`rrfK=60`（`KRAG/CandidateFusionService.java:16`、`:94-102`），候选上限 `candidateTopK = max(topK*3, 50)`（`KRAG/RagRetrievalService.java:24-25`、`:332`）。
- **归一化只用于门控，不参与打分**：`normalizePathScore` 把 vector 截到 1.0、metadata/10、graph/12、keyword=1.0（`CandidateFusionService.java:190-200`），只用于弱路径过滤（默认阈值 0，等于关闭，`:19-21`）与"高置信度/首位共识"门控（`:132-160`）。
- **去重键是 `segmentId`**：`segmentId == null` 的结果直接跳过（`:79-84`）——这一点对 HyPE/web 结果有真实后果：
  - HyPE 写入的假设问题向量没有 `kmc_segment_id` metadata（`KmcSyncServiceImpl.java:698-703` 只写 kb/document/docName），`VectorRetriever` 映射后 `segmentId=null`（`VectorRetriever.java:98-110`），进而在融合阶段被**丢弃**——先占用 ANN topK 名额、再被静默丢弃，纯浪费（P1）；
  - CRAG Web 结果用了负数 hash 作为 segmentId（`KRAG/CragWebSearchClient.java:99-117`），可以进入融合，但它在 CRAG 之后单独合并（`RagRetrievalService.java:734-758`），**绕过 RRF 与重排**，直接进入上下文；来源为外部网页且 segmentId 为负数，引用无法回溯（P1/P2）。
- **跨库再融合（已验证）**：对每库结果重新按 RRF 计算（`CrossKbScoreCalibrator.java:130-145`），再 `scoreFloor=0.40` 过滤（`:33-34`、`:94-98`）。**但输入 score 已经是 kmc 侧的 RRF 分（量级约 1/61≈0.016/路，VO 原样透传：`KMCBASE:1047-1057`），0.40 的门槛会把几乎所有结果过滤掉**。若 v2 正常返回，则该分支必然得到空 `uniqueSegmentMap`（`:113-119` 返回空上下文）；若 v2 因 1.3/D-5 回退 legacy，则 VO score 变为向量余弦/全文分（0-1 量级），部分结果才能过门槛。这是一个**跨层分数契约不一致**的 P0 缺陷（推断其运行时行为，需日志验证）。
- **重排（已验证）**：
  - 门控默认开启：`rerankGateEnabled=true`，阈值 0.88/0.15（`RagRerankService.java:43-50`），命中 `topConsensus` 或高置信度 margin 时**跳过重排**，直接按 RRF 顺序截断（`:61-78`）；
  - 否则：轻量关键词过滤（默认关闭，`:40-41`、`:205-230`）→ ColBERT 粗排（默认因无 token embedding 配置直接 skip，`:301-311`；`ColbertScorer.java:52-70`、`:349-360`）→ DashScope API 重排（仅当 KB 配置了 `rerankingProviderName+modelName`，`DashScopeRerankerProvider.java:33-36`；HTTP 30s 超时）→ 本地 Cross-Encoder（prod 默认 disabled，`LocalRerankerProvider.java:146-151`；dev 开启，`application-dev.yml:228-229`）→ 规则兜底（Day/文档名/关键词加分，`DeterministicRerankerProvider.java:29-58`）；
  - **重排不调用 LLM**（DashScope rerank 是 cross-encoder 服务，不是 LLM）；但在 prod profile 且 KB 未显式配置远程 reranker 时，最终会退化到"规则加分"，而不是语义重排（P1 质量风险）；
  - 延迟：DashScope 30s read timeout（第 2.5 节表）；本地重排的 `isServiceAlive()` 每次查询发 HEAD 探测（`LocalRerankerProvider.java:132-143`），在服务异常时每问一次额外 2s 级探测。
- **重排后的上下文**：仍会把 `filterIrrelevant` 关闭、gate 跳过时直接 topK 截断——没有"最低相关性阈值"作为最后防线；门控阈值 0.88/0.15 是硬编码经验值，未与 embedding 分数分布校准。

### 2.4 GraphRAG / 社区 / Steiner / PPR 的真实实现（问题 5）

**结论：生产可用的图检索 = `GraphRagRetriever` 的 SQL+Neo4j 双路，但默认关闭；宣称的 GraphRAG 2.0/Steiner/谱剪枝/层次社区在检索主链上均未装配。**

1) **`GraphRagRetriever`（已验证）**：
- 开关默认关闭：`GraphRagProperties.enabled=false`、`pprEnabled=false`（`KRAG/GraphRagProperties.java:11`、`:15`）；`application*.yml` 未找到 `qknow.rag.graph` 覆盖配置（grep 仅命中 `hermes.rag.hype`）。
- 双路：优先 Neo4j `Entity` 名称匹配 + 1-2 跳扩展（`searchNeo4jSegments`，`GraphRagRetriever.java:607-645`；`semanticGuidedRetrieve`，`:413-490`），失败则回退 PostgreSQL `kmc_segment_entity_metadata` 的 jsonb 包含查询（`:548-605`）；所有用户输入通过 `bindAll` 参数绑定（`:445-447`、`:629-631`），`maxHops` 为 int 且被 `min/max` 限制（`:93-96`、`:611`），并经过 `CypherSafetyValidator.isTemplateReadOnly`（`:436-443`、`:622-628`）。
- **Neo4j 注入风险：低**（已验证）：Cypher 模板为静态字符串，仅 `maxHops` 用 int 格式化；实体/上下文全部参数化。`CypherSafetyValidator` 另做只读白名单（禁止 CREATE/MERGE/DELETE/CALL 等，`KRAG/CypherSafetyValidator.java:11-46`）。残余风险是"字符串格式化模板"这一模式本身（当前安全，但改签名即危险）。
- PPR：真实迭代实现（20 次迭代、阻尼 0.85、收敛 0.001、无向邻接、种子偏置，`GraphRagRetriever.java:190-330`）；复杂度 O(iter×E)，有 `pprMaxEdges=50000`/`pprMaxNodes=20000` 上限（`:16-17`），先做 1 跳+2 跳子图装载（`:333-390`）；但**默认关闭**且实体抽取默认关闭（`application-dev.yml:212-213` query-entity.enabled=false），因此默认配置下 PPR 与实体图路径实际不执行。
- 其它图检索是"元数据实体匹配"而非社区摘要/全局问答：`searchByTopics` 用 `content LIKE` 模糊匹配（`:160-188`），`temporalRetrieve` 用 `created_at` 时间衰减（`:490-545`），均不是 GraphRAG 的"社区→摘要→全局答"范式。

2) **真正实现的"社区检测"在 kg 模块而非检索链（已验证）**：`backend/qknow-module-kg/.../service/GraphCommunityService.java:91-165` 用 Neo4j GDS `gds.leiden.stream` 做 Leiden 社区检测，再 `buildSummary`/Map-Reduce（`:12-90`）生成摘要，经 `KgGraphController.detectCommunities/summarizeCommunities/globalSearch`（`:115-200`）作为**管理端点**触发，非聊天检索路径。其 Cypher 用 `%s` 拼接 workspaceId（`GraphCommunityService.java:96-124`），workspaceId 类型为 `Long`（控制器 `:117`）故当前不可注入，但属于潜在风险模式。

3) **"GraphRAG 2.0 / Steiner / 谱剪枝"主体是 mock 或死代码（已验证）**：
- `KRAG/graph/MultiHopCausalPathExtractorImpl.java:15-25、`:85-112` 明确用 `mockPaths`/`mockRegisterPath` 内存数据，**不查任何图数据库**；`NeuroSymbolicGraphRankerImpl.java:25-30、`:51-105` 用 `mockEdgeStore`，其 PPR 求解输入是模拟边；`GraphRag2Coordinator` 无外部引用；`HierarchicalCommunityServiceImpl.java:33-40` 社区只存于内存 Map，`llmSummaryCallCounter` 为模拟计数器。
- `kg/rag/DefaultGraphRagCoordinator.java:15-27` 两个检索方法直接 `return Collections.emptyList()`，是 GraphRAG 3.0 门面的空实现。
- Hermes 侧：`SteinerCausalPathReranker` 有真实 KMB 式 Steiner（`HRAG/cognitive/SteinerCausalPathReranker.java:94-260`，Dijkstra `:151`、`:258`，上限 16 节点 `:24`），但**无生产引用**；`HypersphericalGraphRagGovernor`（拉普拉斯谱剪枝）零引用；`HierarchicalGraphRagEngine`、`TemporalCausalGraphRagEngine` 等仅被 benchmark/测试引用。`HypersphericalGraphRagGovernor.pruneGraph` 的亲和度矩阵为 O(n²) 内存与计算（`HFR/governor/HypersphericalGraphRagGovernor.java:110-160`），若接入需限 n（注释称候选 ≤128，但无代码级断言）。
- 结论：**"社区/层次/Steiner/PPR 已生产化"不成立**；真实生产只有"SQL/Neo4j 实体邻接检索"（默认关）。

### 2.5 语义缓存：键构造与失效风险（问题 6）

- **两级缓存（已验证）**：
  - L1 精确缓存：进程内 `ConcurrentHashMap`（≤5000 条，随机/过期淘汰，`KRAG/cache/EnhancedSemanticCacheService.java:57-59`、`:206-222`）；
  - L2 语义缓存：PostgreSQL `semantic_cache_store`，用 query embedding 与历史 query 做余弦近邻（阈值 0.92，`SemanticCacheService.java:45`、`:110-137`）。
- **缓存键（已验证）**：
  - L2 查询条件 = `workspace_id + bot_id + knowledge_ids_hash + model_name`（`SemanticCacheService.java:116-121`），**没有**：用户/角色/权限哈希、知识库版本/更新时间、embedding 模型签名之外的检索策略、文档 ACL。
  - L1 键 = `workspaceId:permHash:botId:knowledgeIdsHash:modelName:sha256(normalizedQuery)`；`permHash` 参数存在（`EnhancedSemanticCacheService.java:200-213`），但 `findExact/putExactCache` 的便捷重载一律使用 `"default"`（`:104-105`、`:136-139`、`:164-167`），**实际未使用真实权限哈希**；`calculatePermissionHash`（`:222-237`）只被定义未被调用。
  - 答案缓存写入口在控制面：`KBCFG:493-514` → `KmcApiServiceImpl.saveSemanticAnswer`（`KmcApiServiceImpl.java:201-213`），写入字段同样无权限维度。
- **失效策略（已验证）**：TTL 默认 24h（`SemanticCacheService.SemanticCacheConfig` 默认 `Duration.ofHours(24)`，`:370-376`）；`RagCacheService` 另有 300s TTL 的检索结果缓存（`KRAG/RagCacheService.java:44-90`）；失效只在知识库删除/文档同步完成时触发（`KmcKnowledgeBaseController.java:175`、`KmcSyncServiceImpl.java:451-452` 调 `evictByKnowledgeBase`）。
- **风险判定**：
  - **陈旧**：P1——KB 内容在 24h 内更新时，答案缓存不会失效（同步完成才 evict，且若更新走其他入口不一定触发）；`knowledge_ids_hash` 只含 KB id 集合，不含 KB 版本/分段更新时间。
  - **串租户**：P1（推断）——键含 `workspace_id + bot_id`，同 workspace 内不同用户共享 bot 时会命中同一答案缓存；若用户 A 对 KB 有权限、用户 B 无权限，B 仍可能拿到 A 生成的答案（答案内容来自 KB）。真正的 `permissionHash` 未接入，`calculatePermissionHash` 是空转。需要运行验证 bot 是否按用户复制/隔离。
  - **越权泄漏**：P1——即使检索阶段做了 KB 权限过滤，缓存层绕过了检索权限检查（`findSemanticCache` 在权限过滤之前，`KBCFG:258` vs 检索权限在 v2/legacy 内），只要 key 命中就返回答案。
  - **语义误命中**：已有一定防护（否定词极性 + 2-gram Jaccard≥0.40，`EnhancedSemanticCacheService.java:146-198`），但属于词法启发式；0.92 余弦阈值对 1536 维 Qwen 是否合适**未知**（无校准证据）。
  - **NULL 哨兵**：`EMPTY_SENTINEL` 防穿透但有 30min 级短 TTL（`:171-190`、`:57` 常量），可接受。

### 2.6 上下文构建、Token 预算与引用闭环（问题 7）

- **预算（已验证）**：`RagContextBuilder` 类默认 20,000 bytes（`KRAG/RagContextBuilder.java:31-32`），但基线 `application.yml:142-143` 设为 `hermes.rag.context.max-bytes=12000`、`max-tokens=0`（token 预算**关闭**）→ 实际按 12KB 字节硬截断；跨库层又设 20,000（`CrossKbScoreCalibrator.java:36-37`）。两层预算不一致，且都按 UTF-8 字节而非 token，`estimateTokens` 只在 `maxContextTokens>0` 时生效（`RagContextBuilder.java:302-326`）。
- **装配算法（已验证）**：
  - 先按 `parent_segment_id` 回表展开父块并 Max-Pooling 子块最高分（`RagContextBuilder.java:142-221`）；同一父块多次命中只保留一个（`:142-190`）；
  - 无父块且 `expandAdjacent=true` 时，用相关子查询找前后相邻 segment 追加（`:222-281`）；**这些相邻块 score=0.0、未经过重排与相关性过滤**，直接进入上下文（`:248-262`）——质量与引用可信度风险；
  - 去重用 `String.hashCode()`（`:283-300`），理论碰撞低但非内容相等判定；
  - 逐条 `formatEntry` 生成 `[来源 N] docName / segmentId=... / score=... / parentId=...`（`:328-346`），父块超预算时降级为子块（`:78-91`），否则硬 break（`:93-95`）。
  - 上下文块以 `[来源 N]` 编号 + `segmentId` 形式进入 prompt，**理论上可回溯到 chunk**。
- **引用闭环（已验证/缺口）**：
  - 解析器 `CitationExtractor` 是真实实现（正则提取 `[来源 N]`/`[N]`/`【来源 N】`，越界判幻觉，计算 citation precision/recall，`KRAG/citation/CitationExtractor.java:22-111`），但**全仓无生产调用**（仅测试参考）；`EvidenceChainStreamPresenter`（思考链/证据卡片/引文角标核验）同样只有测试引用（`backend/qknow-module-kb/.../agent/retrieval/EvidenceChainStreamPresenter.java:22-90`，grep 仅测试）。
  - 真正生成侧 prompt 在 `HA/AgentOrchestrator.java:1015-1071`：只给 `<document source=...>内容</document>`，**没有要求模型输出 `[来源 N]`/`[^n]` 的指令**，也没有把"来源 N ↔ segmentId"的映射回传给模型；因此模型引用与前端 sourceRefs 之间没有强制闭环。
  - 编号冲突：协调器分支按 KB 循环、每个 KB 都从 1 重新编号（`KBCFG:294-313`）；非协调器分支同样按 KB 重新编号（`:336-352`）。真正的全局编号只在 `CrossKbScoreCalibrator.formatEntry`（`CrossKbScoreCalibrator.java:190-201`）里，但该 `formattedContext` **未被使用**（`:296` 用 `segmentsByKb` 自己拼）。多 KB 时 `[来源 1]` 会重复 N 次，引用语义歧义。
  - 前端 `sourceRefs` 只存 `documentId/documentName/segmentId/knowledgeId`（`KBCFG:286-291`、`:341-348`），没有 citation index；缓存的 `sourcesJson` 同样如此（`:493-514`）。
- **流式打字机对齐（已验证：生产未接）**：`StreamingTypewriterAlignBuffer`（35 字符/秒、JitterBuffer）只被 `SpatiotemporalKnowledgeMetacenter` 使用（`KRAG/advanced/service/SpatiotemporalKnowledgeMetacenter.java:137`），后者无生产引用；生产链路是 gRPC chunk 直通（`HermesGrpcClient.java:125-140`）+ `chatFlux.doOnNext` 聚合（`KBCFG:441-456`），无节流/对齐/断流封口逻辑。**"极低延迟流式打字机与上下文对齐"这一宣称在主链上不成立**。

### 2.7 评测与证据可信度（问题 8）

项目实际有三层评测资产，可信度差异很大：

1) **单元/契约/Shadow 测试（已验证，方法论较扎实）**：
- `backend/tests` 下 RAG 相关测试 153 个（`tests/src/test/java/tech/qiantong/qknow/rag` 106 个），大量 `RagCandidate*/Phase*` 契约测试；
- `RagShadowBaselineTest` 8,071 行，必须显式 `-Drag.eval.shadow=true` 才运行（`TESTS/rag/eval/RagShadowBaselineTest.java:122`），使用 Testcontainers PostgreSQL 起独立库（`:180-190`）、`FeatureHashEmbeddingModel`（确定性 256 维哈希，`TESTS/rag/eval/FeatureHashEmbeddingModel.java:19-60`）替代真实 Qwen、Mockito 替身替代外部服务，并用"哨兵"逐一验证 vector/keyword/metadata/context 四条通道确实工作、以及**禁止访问 `vector_store`**（`RagShadowBaselineTest.java:212-283`）；还断言 DB 调用次数预算（`:1149-1153`、`:1383-1385`）。
- 结论：**该层是可复现的离线仿真/反作弊测试，设计水平高于一般项目；但它不测真实 Qwen、pgvector、DeepSeek、真实权限**。`baseline.json` 记录 n=25 的基线指标与 bootstrap CI（`tests/src/test/resources/rag-eval/baseline.json`），不是硬编码的"通过值"。

2) **300 查询离线检索评测（已验证：代理仿真，不是生产管线）**：
- 数据集：`backend/tests/src/test/resources/eval/benchmark_300.jsonl`（300 题）、`corpus_300.jsonl`（3801 段）、`qrels_300.tsv`（679 对），`evidence_integrity_audit_v2.json` 有 SHA-256、dangling/duplicate/conflicting 检查、3-gram Jaccard 泄漏检查（最大 0.518 < 0.85，判定 VALID）；
- 但评测执行器是 **Python 离线代理**，不是本仓库 Java 管线。`EVID/v1_archive/evidence_integrity_audit.json` 的 `THREE_PATH_PROXY` 明确写道："QuadPath ID invokes BM25, 128-bucket hashed vectors and metadata-family scores, followed by RRF and tie breaking. **No fourth metadata ranking, Qwen embedding, graph traversal or backend call is executed.**"（同文件另注 `SEQUENTIAL_PROXY`：串行循环，不测生产并发）。
- 结果（`EVID/eval_summary_300_v2.csv`）：KnowledgeHub_QuadPath recall@10=0.371、nDCG@10=0.275；**BM25_Only recall@10=0.553、nDCG@10=0.415（明显更好）**；Dense_Only=0.222；Graph_Only=0.140。即：项目自己的离线评测里，"旗舰多路融合"输给了纯 BM25。
- 统计报告 `EVID/statistical_test_report_v2.json` 自身标注 `interpretation=EXPLORATORY_ONLY_NONINDEPENDENT_RECORDS`，并列出四条限制：query 不独立、p 值未校正、NDCG 用冻结证据位置而非人工相关性、Nemenyi 设计不满足。这是诚实的自我限定，但对外叙事若引用这些数字就属于过度解释。

3) **端到端生成评测（已验证：真实 DeepSeek 评测，但检索上下文来自离线仿真）**：
- `EVID/eval_e2e_protocol_100.json`：100 题 × 4 组 = 400 条生成 + 400 条 judge，模型 `deepseek-flash`；generation thinking 关闭，judge thinking 开启（`reasoning_effort=high`）；协议文档化了 `extra_body.thinking` 与官方文档 URL。
- `EVID/eval_e2e_summary_100.csv`（4 组，n=100）：BM25-Only relevance 0.816 / factual_correctness 0.5725；**KnowledgeHub QuadPath relevance 0.590 / factual_correctness 0.317**；Dense-Only 0.4495/0.154；No-RAG 0.238/0.04。faithfulness 四组都 ≥0.956（judge 对"依据不足就拒答"给高分，导致 faithfulness 对系统差异不敏感）。
- 子组（`eval_e2e_subgroup_100.csv`）：`enterprise-core-configuration` 中 QuadPath relevance 0.34 / factual 0.0，BM25 0.64/0.267；`comparison` 中 QuadPath 0.825/0.37，BM25 1.0/0.845。QuadPath 没有稳定优势。
- 可信度问题：① 上下文来自离线代理（结果中的 `contextSegmentIds` 形如 `mh-*/hp-*/src-*`，与 300 离线仿真同源，不是控制面真实召回）；② judge 与生成同属 DeepSeek 家族，存在自偏好/评分漂移，且 judge 能看到参考答案；③ 无人工双标/一致性（κ）报告；④ judge thinking tokens 被完整记录但未做偏差分析（400 条 judge 记录里可见 reasoning_content 很长）。
- `EVID/live-ann-environment.md:1-20` 明确记录：live ANN E2E **ENV_QUALIFICATION_FAILED**——恢复 pgvector 扩展时 `DROP EXTENSION vector CASCADE` 误删了 `vector_store.embedding` 与 `semantic_cache_store.query_embedding` 数据（当前 4955 行、`count(embedding)=0`）。因此**没有任何"真实 Qwen + 真实 pgvector ANN"的端到端证据**。

4) **其它证据（已验证，n 小）**：`h6-chunk-ab`（child 128 vs 256，800/600 chunk，qwen3.7-text-embedding 1024d）、`h10-graph`（24 segment、qwen-plus 抽实体、text-embedding-v4 1024d）、`h12-colbert`（colbertv2 离线 MaxSim，n=12）、`a1-colbert-fail-closed`（n=10 机制消融）。这些是"单变量小样本 A/B"，不能支撑"生产级"结论。

**评测结论**：项目拥有比多数团队更完整的"证据链工程"（checksum、泄漏检查、哨兵、预算断言、限制声明），但当前所有对外可引用的量化结论都建立在**代理仿真 + 合成/仓内语料 + 同族 LLM judge** 之上；且自己的数据表明 QuadPath 不如 BM25。把 300 题离线表当作"生产管线效果"是**不成立**的（P0 级叙事风险）。

---

## 3 缺陷与风险清单（表格）

> 评级口径：P0=会导致主链功能/数据/权限结论性错误或对外声明失真；P1=显著质量/成本/安全风险；P2=工程债与可维护性。

| ID | 级别 | 结论 | 关键证据 | 证据等级 |
|---|---|---|---|---|
| R-P0-1 | P0 | 生产聊天（异步线程池）丢失 SecurityContext，RAG v2 抛异常后静默回退 legacy 管线；legacy 无用户上下文时跳过权限过滤 | `ThreadPoolConfig.java:32-43`；`MultiKbRetrievalCoordinator.java:100-138`；`SecurityUtils.java:29-39`；`RagRetrievalService.java:226`；`KMCBASE:388-413`、`:725-735` | 已验证代码路径；运行时后果**推断**（需日志/断点验证） |
| R-P0-2 | P0 | 跨库融合 `scoreFloor=0.40` 与上游 RRF 分数量纲（≈0.016/路）不匹配，v2 正常返回时上下文被过滤为空；行为随线程上下文在"空"与"legacy 原始分"之间漂移 | `CandidateFusionService.java:94-102`；`KMCBASE:1047-1057`；`CrossKbScoreCalibrator.java:33-34`、`:94-98`、`:113-119` | 已验证（静态）；运行时表现推断 |
| R-P0-3 | P0 | 控制面 CRAG + Hermes 端每轮无条件 CRAG LLM，重复且结果仅用于日志/提示；每轮 RAG 对话额外 +1 次 LLM | `KRAG/CragRetrievalEvaluator.java:44-52`；`HA/AgentOrchestrator.java:266-276`；`HA/RetrievalEvaluator.java:41-70` | 已验证 |
| R-P0-4 | P0 | "Qwen 1536 单位向量"未被代码强制：无 dimensions 参数、归一化 guard 死代码且不校验范数、实测证据使用 1024 维模型；维度漂移时静默空召回/写入失败 | `EmbeddingServiceImpl.java:76`、`:97`；`EmbeddingDimensionGuard.java:41-107`（零生产引用）；`01-schema.sql:1410-1421`；`EVID/h6-chunk-ab/README.md:7`；`EVID/a1-colbert-fail-closed/a0-a1-live-ann-report.json:439` | 已验证（代码+证据文件）；当前运行库维度**未知** |
| R-P0-5 | P0 | "GraphRAG/Steiner/谱剪枝/层次社区/Agentic RAG/推理性检索"均未进入检索主链：死代码、mock 实现或 benchmark/test-only；kg 的 Leiden 社区只挂在管理端点 | `HRAG/cognitive/MultimodalPyramidChunker.java`（无生产引用）；`HS/graphrag/HierarchicalGraphRagEngine.java`（仅 benchmark/test）；`KRAG/graph/MultiHopCausalPathExtractorImpl.java:15-25`；`KRAG/graph/NeuroSymbolicGraphRankerImpl.java:25-30`；`kg/rag/DefaultGraphRagCoordinator.java:15-27` | 已验证（引用计数+grep） |
| R-P0-6 | P0 | 对外可引用的量化结论全部来自代理仿真（128-bucket 哈希向量、无 Qwen/pgvector/图/后端调用），且自身数据显示 QuadPath 不如 BM25；不得表述为"生产管线效果" | `EVID/v1_archive/evidence_integrity_audit.json`（THREE_PATH_PROXY/SEQUENTIAL_PROXY）；`EVID/eval_summary_300_v2.csv`；`EVID/eval_e2e_summary_100.csv` | 已验证 |
| R-P1-1 | P1 | `VectorRetriever` 每次检索重复计算同一 query embedding（pgvector + VecSim rescore），并回表捞全候选向量；单库问答 3 次 embedding、2 次算同一余弦 | `VectorRetriever.java:90-96`、`:117-118`、`:128-203` | 已验证 |
| R-P1-2 | P1 | HyPE 假设问题向量缺 `segment_id` → 进入 ANN 候选后在融合阶段被丢弃：白耗 embedding/向量库容量并稀释 topK | `KmcSyncServiceImpl.java:698-703`；`VectorRetriever.java:98-110`；`CandidateFusionService.java:79-84` | 已验证 |
| R-P1-3 | P1 | 语义缓存键缺权限/版本维度（真实 permissionHash 未接入）；同 workspace+bot 跨用户共享答案；KB 更新 24h 内不失效 | `SemanticCacheService.java:116-121`、`:370-376`；`EnhancedSemanticCacheService.java:104-105`、`:200-213`、`:222-237`（只定义未调用） | 已验证（静态）；跨用户命中需运行验证 |
| R-P1-4 | P1 | 引用闭环未接通：CitationExtractor/EvidenceChainStreamPresenter 无生产调用；生成 prompt 不要求 `[来源 N]`；多 KB `[来源 N]` 重复编号；前端 sourceRefs 无 citation index | `KRAG/citation/CitationExtractor.java:22-111`；`EvidenceChainStreamPresenter.java:22-90`；`HA/AgentOrchestrator.java:1015-1071`；`KBCFG:294-313`、`:336-352` | 已验证 |
| R-P1-5 | P1 | 上下文预算不一致（12KB vs 20KB）且 token 预算关闭；按字节硬截断，无句界；相邻块扩展绕过重排注入 0 分内容 | `application.yml:142-143`；`RagContextBuilder.java:31-36`、`:78-95`、`:222-281`；`CrossKbScoreCalibrator.java:36-37` | 已验证 |
| R-P1-6 | P1 | 内部 HTTP 召回接口无独立鉴权/无用户上下文；若配置静态 token 则所有调用共享一个身份，KB 角色权限不生效；参数缺失 NPE | `KbInternalApiController.java:33-45`；`HFR/RagRetrievalService.java:39-44`；`SecurityConfig.java:117-148` | 已验证（静态） |
| R-P1-7 | P1 | prod profile 下远程重排未配置、本地重排默认关闭、ColBERT 默认 skip → 重排退化为日/文档名/关键词规则加分，不是语义重排 | `LocalRerankerProvider.java:146-151`；`ColbertScorer.java:349-360`；`RagRerankService.java:124-126`、`:381-432`；`application-prod.yml:150-153`（无 rag 段） | 已验证 |
| R-P1-8 | P1 | 图谱检索默认关闭、实体抽取默认关闭 → "GraphRAG"在默认配置下零执行；开启后固定分 12/10 无组内排序 | `GraphRagProperties.java:11`、`:15`；`application-dev.yml:212-213`；`GraphRagRetriever.java:42`、`:20` | 已验证 |
| R-P1-9 | P1 | 入库 Contextual Enrichment 把 LLM 生成文本写入父/子块正文（citation/溯源含生成内容）；每 chunk 1 次 LLM + HyPE 每 chunk 至多 N 次 LLM（n≤20），入库成本高 | `KmcSyncServiceImpl.java:398-410`；`ContextualEnrichmentService.java:42-44`、`:63-90`；`HyPEIndexer.java:138-150` | 已验证 |
| R-P1-10 | P1 | 多 KB 时每库独立跑完整流水线（路由/CRAG/实体/向量×2/重排/上下文），再在控制面二次 RRF；N 库线性放大 embedding/LLM/延迟 | `MultiKbRetrievalCoordinator.java:100-138`；`RagRetrievalService.java:322-500`；`CrossKbScoreCalibrator.java:69-190` | 已验证 |
| R-P2-1 | P2 | `vectorDocuments.indexOf(document)` 在入库循环内 → O(n²) | `KmcSyncServiceImpl.java:624` | 已验证 |
| R-P2-2 | P2 | `position` metadata 从 0 开始（DB position 从 1 开始）；`DayXX` 文档名解析与业务日期强耦合（`DayNo` 从文件名提取） | `KmcSyncServiceImpl.java:610-624`、`:620`；`QueryIntentAnalyzer.java:82-96`；`MetadataRetriever.java:50-55` | 已验证 |
| R-P2-3 | P2 | `isH2()` 每次图查询新建 DB 连接探测数据库类型 | `GraphRagRetriever.java:712-720` | 已验证 |
| R-P2-4 | P2 | 语义缓存做淘汰时仅清过期项；L1 满了仍可能塞到 5000 条上限后才随机清 10%，可能长期不降 | `EnhancedSemanticCacheService.java:206-222` | 已验证 |
| R-P2-5 | P2 | 缓存答案过滤规则过宽（含"抱歉/不确定"即不缓存），部分正常答案无法缓存 | `KBCFG:459-471` | 已验证 |
| R-P2-6 | P2 | 配置分散/重叠：`application.yml` 与 `application-dev.yml` 对同一 `hermes.rag.*` 重复配置且默认值相反，环境间行为差异大（query-transform enabled true vs false；hype/colbert 仅 dev 开启） | `application.yml:130-143`、`application-dev.yml:158-229`、`application-prod.yml:150-153` | 已验证 |
| R-P2-7 | P2 | `RagRetrievalService` 的 4 路检索与多 KB 协调器共用同一个 `threadPoolTaskExecutor`，且拒绝策略 `CallerRunsPolicy`；嵌套提交时可能把慢任务内联到调用线程，放大延迟 | `ThreadPoolConfig.java:32-43`；`RagRetrievalService.java:652-662`；`MultiKbRetrievalCoordinator.java:31-41` | 已验证（静态） |
| R-P2-8 | P2 | 图谱/元数据/关键词检索各有固定分（12/10/关键词混合 0.6/0.4），分数不可比；`normalizePathScore` 用整除常数硬编码归一化 | `GraphRagRetriever.java:42`；`MetadataRetriever.java:20`；`CandidateFusionService.java:190-200`；`KeywordRetriever.java:147-155` | 已验证 |
| R-P2-9 | P2 | 内部召回/兼容路径对缺失参数无防御（`toString()` NPE），外部错误暴露为 500 | `KbInternalApiController.java:35-36`；`HFR/RagRetrievalService.java:57-60` | 已验证 |
| R-P2-10 | P2 | 语义缓存/向量库物理删除未接冷备（AGENTS.md 三级冷备要求）；`cascadeDeleteVectorStore` 直接 `DELETE FROM vector_store` | `KMCBASE:186-205` | 已验证 |

---

## 4 优化建议（最小改动，按优先级）

### 4.1 立即修（P0，最小 diff）

1. **显式传递用户身份，禁止检索链路隐式依赖 SecurityContext**（R-P0-1/D-5）：
   - 最小改动：`IKmcApiService.recallTest/recallTest(..., turns)` 增加 `Long userId` 参数；`MultiKbRetrievalCoordinator.executeSingleRecall` 与 DAG 节点在提交任务前捕获 `SecurityUtils.getUserId()` 并用参数传入；`RagRetrievalService.retrieve` 增加 `userId` 重载（内部不再直接调用 `SecurityUtils`），权限解析失败时 **fail-closed**（返回空 + 明确错误码），而不是回退 legacy。
   - 验证：单测断言"异步线程 + 指定 userId 时能检索到该用户可见 KB"；DAG E2E 断言权限拒绝。
2. **统一分数契约，修 R-P0-2**：
   - 方案 A（推荐，最小）：`CrossKbScoreCalibrator` 的 `scoreFloor` 改为对**原始检索分**判断（在 kmc 侧返回 VO 时保留 `rawScore` 字段），或把默认值改为 `0`（RRF 本身已在排序中体现弱路径）；禁止对 RRF 分做绝对值门槛。
   - 方案 B：把跨库 `scoreFloor` 语义改为"相对 top1 的比例"（如 `<0.1*top1` 才丢弃）。
   - 增加一个跨层契约测试：`RagRetrievalService` 输出 → `retrievalResult2vo` → `CrossKbScoreCalibrator`，断言非空且单调。
3. **去掉重复 CRAG**（R-P0-3）：`RetrievalEvaluator` 默认关闭（配置开关），或改为消费控制面通过 gRPC 传入的 `retrievalEvaluation` 标签（`ChatRequest` 增加 `label/confidence` 字段），Hermes 不再自己调 LLM；保留 judge 仅用于离线评测。
4. **强制 Embedding 契约**（R-P0-4）：
   - 在 `EmbeddingServiceImpl` 外层包一个 `QwenEmbeddingModel` 装饰器：`call()` 返回前校验 `len==1536`、`|‖v‖₂-1|≤1e-4`、无 NaN，失败抛明确异常（fail-close）；`dimensions=1536` 显式传入 `OpenAiEmbeddingOptions`（`EmbeddingServiceImpl.java:97`）。
   - 启动期审计：`EmbeddingDimensionGuard` 接入 `VectorStoreServiceImpl` 写入路径与 `updateResult` 入库路径（现只启动查表）；启动断言 `vector_store` 列维度、抽查 10 条 embedding 的 `vector_dims` 与 L2 范数；不通过则拒绝服务（或只读降级）。
   - 迁移注意：先用 SQL 审计 `SELECT vector_dims(embedding), avg(vector_norm(embedding))`（pgvector 0.8+ 可用）再重嵌入，禁止混写。

### 4.2 短期（P1，1-2 周）

5. **去重 embedding 与向量计算**（R-P1-1）：VecSim rescore 改为"可选诊断开关"（默认关）或直接复用 pgvector 分数；如确需 JNI 精度，将 query embedding 在 `retrieve()` 内缓存传给 rescore（一次调用）。
6. **修 HyPE 元数据与融合**（R-P1-2）：HyPE 文档写入时携带 `kmc_segment_id`（指向生成它的 child segment）与 `doc_type=hypothesis`；`VectorRetriever` 映射时保留该标志；融合阶段不要把 hypothesis 当最终结果（只作为召回证据），或在 ANN 查询 filter 中排除。
7. **语义缓存安全化**（R-P1-3）：key 增加 `permissionHash`（真正调用 `calculatePermissionHash`）+ `kbVersion`（KB/文档 `updated_at` 最大值或 segment 表版本）+ embedding 模型全限定名；写缓存前复核生成时用户的权限快照；TTL 从 24h 降到 5-30 分钟（可配置），并在文档同步完成时 evict（已有入口，确保覆盖所有更新路径）。
8. **引用闭环**（R-P1-4）：控制面统一给全局编号（跨库只编一次）并把 `index→segmentId→documentId` 映射传给 Hermes；生成 prompt 明确"仅可引用 `[来源 n]`"；生成结束后用 `CitationExtractor` 做后置校验（越界引用判失败/降级）；前端把 sourceRefs 与 citation index 对齐。
9. **预算统一**（R-P1-5）：只保留一层预算（建议以 token 为主、bytes 兜底），把 `hermes.rag.context.max-bytes` 与 `CrossKbScoreCalibrator.maxGlobalContextBytes` 统一到同一配置；打开 `max-tokens`（如 3000）并让 `estimateTokens` 参与装配；相邻块扩展改为"仅当父块不存在且块分数高于阈值"。
10. **内部 API 身份化**（R-P1-6）：`/internal/**` 改为 mTLS/HMAC + 带 `userId/kbIds` 的短期凭据（已有 `FlyFlowHmacAuthenticationFilter` 可参考），禁止静态长期 token；`knowledgeId/query` 缺失返回 400 而非 NPE。
11. **重排语义化**（R-P1-7）：prod 明确二选一：启用 DashScope rerank（KB 级配置 + 3-5s 超时 + 失败降级），或启用本地 cross-encoder；不要把"规则加分"作为默认最终层；`filterIrrelevant` 在 gate skip 时也应做最低阈值过滤。
12. **GraphRAG 真实化或收敛宣称**（R-P1-8/R-P0-5）：短期把 `MultiHopCausalPathExtractorImpl`/`NeuroSymbolicGraphRankerImpl`/`HierarchicalCommunityServiceImpl`/`GraphRag2Coordinator` 移入 test 或明确标注 experimental；生产只保留 `GraphRagRetriever` 这一条并给出默认开关矩阵；若要加强 GraphRAG，最小路径是复用 kg 的 GDS Leiden + `kg_node_segment_rel`，而不是新增 mock 层。

### 4.3 中期（P2/治理）

13. 入库流水线：`position` 改批内递增（去掉 `indexOf`）；`DayXX` 解析改为显式 metadata；Contextual Enrichment 产物与原文分列（`content_original` 与 `content_enriched`），引用只回原文。
14. 评测治理：把所有"代理仿真"结果在证据文件与任何对外表格中标注为 `PROXY_SIMULATION`；用真实 Qwen+pgvector 重算 300 题（先修复被误删的 embedding 与 live ANN 资格）；E2E judge 换独立模型/双人标注并报告一致性；将 `eval_e2e_summary_100.csv` 中 BM25 > QuadPath 作为当前 baseline 事实公开。
15. 可观测性：为每次问答记录 `embedding_calls / ann_calls / rerank_calls / llm_calls / context_bytes / cache_hit / fallback_reason`（`RagMetricsService` 已有 stage 计时，可扩展）；加一条端到端 deadline（例如 RAG 预检索 ≤1.2s、总首 token ≤3s），超时按预算降级而不是无限等待。
16. 清理/搬迁：Hermes `search/**`、`rag/**` 的 20+ 个未接线类型、`HypersphericalGraphRagGovernor`（全仓零引用）、`SpatiotemporalKnowledgeMetacenter` 等移入 `src/test` 或 `experimental` 包，避免"类名即能力"的误读。

---

## 5 与顶级 RAG 栈的差距（问题 9）

> 外部框架能力为行业通用认知（本仓库未逐版本核验，标注为"外部参照"）；本项目现状均有本报告 `路径:行号` 证据。

| 能力项 | 本项目现状 | 顶级栈参照（LlamaIndex/Haystack/GraphRAG/Dify/Weaviate） | 差距 |
|---|---|---|---|
| 文档解析与切块 | 递归/结构感知 splitter + parent-child + Contextual Enrichment；无 OCR/表格结构化、无代码 AST | 文档解析器生态（PDF/表格/图片 OCR）、SentenceSplitter/NodeParser、层次化 parent-child、自动 metadata | 中-大：多模态与表格是硬缺口 |
| 向量存储与几何 | pgvector cosine + HNSW/ivfflat；1536 契约未强制；VecSim 双重打分 | 多后端抽象（Weaviate/pgvector/Milvus）+ 距离类型显式配置 + 维度校验 | 中：几何正确，契约工程弱 |
| 混合检索 | RRF(k=60) 融合向量/关键词/元数据/图（图默认关）；无学习权重、无 per-query 权重 | RRF/加权/学习式 fusion、auto-merging、查询路由、多路召回评估 | 大：只有一种融合、无权重校准 |
| 重排 | DashScope/本地 cross-encoder/ColBERT/规则；默认 prod 退化规则；无 LLM 列表重排 | 多 provider cross-encoder、LLM rerank、ColBERT 原生 | 中-大：默认路径不语义 |
| GraphRAG | SQL/Neo4j 实体邻接 + PPR（默认关）；社区 Leiden 仅管理端点；Steiner/谱剪枝为死代码 | Microsoft GraphRAG 的 Leiden 社区→摘要→Map-Reduce 全局问答；LightRAG 双层检索 | 大：社区/全局问答未进主链 |
| 语义缓存 | 两级缓存、0.92 阈值、词法门禁；键缺权限/版本 | 主流框架多为精确/语义缓存插件，权限隔离需自建 | 中：工程有想法但安全语义不足 |
| 引用/溯源 | 上下文含 `[来源 N]`/segmentId；无生成侧强制引用、无后端核验、编号冲突 | 引用回链/自检、citation precision/recall、证据卡片 | 大：闭环缺失 |
| 评测 | 完整证据工程 + 代理仿真 + 小样本 A/B + 同族 judge；无真实线上 A/B；BM25 优于 QuadPath | 标准数据集（BEIR/MTEB/RAGAS）+ 人工标注 + 线上 A/B + 显著性校正 | 大：方法论外壳强、数据与生产证据弱 |
| 多租户/权限 | KB 角色过滤（但异步路径失效）、缓存/内部 API 无身份 | 行级 ACL、tenant-scoped retriever、缓存按 ACL 分区 | 大：安全语义不完整 |
| 流式与 HITL | gRPC 直通流；打字机缓冲/证据链呈现器未接 | 流式 pipeline + 事件协议 + 前端引用联动 | 中 |
| 运维/可观测 | Micrometer 阶段计时、RagFallbackMonitor、LangFuse（可配） | 端到端 trace、token/成本看板、eval 平台 | 中 |

**总体差距判断**：几何与向量检索的"数学正确性"不差，差在**契约强制、默认路径的正确性、生产可观测性和真实评测**；"高级 RAG/GraphRAG"能力在代码量上很可观，但在生产可达性上接近零。

---

## 6 未验证与需 Lead 决策事项

### 6.1 未验证（需运行/环境）

1. 异步线程是否确实无 SecurityContext（是否有 Spring Security 配置或网关注入导致 `getUserId()` 可用）——静态看没有 TaskDecorator/DelegatingSecurityContext；需一次带日志的聊天复现。
2. 生产 profile 实际激活的配置与 `hermes.rag.query-transform.enabled` 的真实取值（base 默认 true、dev 覆盖 false、env 可能覆盖）；以及 `api-key` 是否为空导致 LLM 改写 fail-open。
3. 当前数据库 `vector_store.embedding` 的真实维度与范数分布（证据显示历史为 1024d/被清空）；`model_name` 元数据与 KB 配置是否一致。
4. `scoreFloor` 缺陷在真实链路中的表现：v2 是否真的被回退、跨库上下文是否为空、前端是否收到 `memory_recall` 事件（需抓取一次聊天请求的 debugInfo）。
5. Hermes `flow/KnowledgeNodeBO` 是否在真实工作流中使用；`/internal/api/rag/recall` 在生产环境是否可达、token 配置是否存在。
6. GraphRAG 是否有任何环境显式开启（`qknow.rag.graph.enabled`/`pprEnabled`），以及 Neo4j 数据是否真实同步（当前 `docs` 未见线上开关）。
7. 语义缓存是否在生产启用、`semantic_cache_store` 的实际行数与命中率；跨用户命中是否存在。
8. LangFuse/RAGAS 是否启用（若启用，每问额外 4+ 次 judge LLM，成本需核算）。
9. Neo4j GDS 插件是否安装（`KgGraphController` 对 GDS 缺失有 501 分支，`GraphCommunityService` 依赖 `CALL gds.*`）。
10. `RagShadowBaselineTest`/`tests` 的最近实际执行结果（`tests/target/surefire-reports` 未纳入本次只读审计），无法确认当前是否全绿。

### 6.2 需 Lead 决策

1. **是否承认并立即修 R-P0-1/R-P0-2**（异步身份 + RRF 分数契约）。这两项不修，任何 RAG 质量结论都建立在可能为空的上下文上。
2. **是否把"生产 RAG = legacy fallback"作为当前事实对外披露**，并冻结"RAG v2 已生产化"的表述，直到 E2E 在真实链路复现。
3. **是否接受评测证据降级**：`eval_results_300*` 与 `eval_e2e_*` 需标注 PROXY_SIMULATION；BM25 优于 QuadPath 必须写入基线与后续报告；是否投入真实 Qwen+pgvector 重算（需先恢复被误删的 embedding 数据）。
4. **Embedding 契约治理**：选择 1536 唯一模型并显式传 `dimensions`，还是接受 1024 维并迁移 DDL/算法（涉及"千问 1536 超球面"的全项目几何声明，需高层决策）。
5. **语义缓存 ACL 策略**：同 workspace 内答案缓存是否允许跨用户复用；若允许，需要产品确认"bot 共享即知识共享"。
6. **死代码/实验代码治理**：是否把 Hermes `search/**`、`rag/**` 高级实现与 `KRAG/graph/*` mock 移出生产源码，避免继续产生"已实现"的误读。
7. **内部 API 鉴权**：是否接受 mTLS/HMAC + 用户上下文透传的改造（涉及 Hermes↔控制面协议字段变更）。
8. **冷备合规**：向量库物理删除是否必须接三级冷备（AGENTS.md 要求），涉及上线前必须补的数据保护机制。

---

### 附录 证据索引

| # | 路径:行号 | 说明 | 等级 |
|---|---|---|---|
| E1 | `backend/qknow-module-kb/.../service/agent/impl/KbAgentConfigServiceImpl.java:226`、`:258`、`:263-365`、`:278-279`、`:294-313`、`:391-410`、`:438-456` | 聊天入口/缓存/预检索/跨库装配/gRPC/异步缓存写 | 已验证 |
| E2 | `.../retrieval/MultiKbRetrievalCoordinator.java:44-58`、`:100-138` | 多库并发召回与超时 | 已验证 |
| E3 | `.../retrieval/CrossKbScoreCalibrator.java:31-37`、`:69-190` | 二次 RRF、scoreFloor=0.40、20KB 截断、全局编号 | 已验证 |
| E4 | `backend/qknow-module-kmc/.../service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java:324-413`、`:725-735`、`:780-786`、`:1047-1057` | recallTest/v2→legacy 回退/legacy 跳权限/分数透传 | 已验证 |
| E5 | `backend/qknow-module-kmc/.../service/rag/RagRetrievalService.java:24-25`、`:124-179`、`:197-320`、`:322-500`、`:652-662`、`:713` | RAG v2 主流水线与超时/线程池 | 已验证 |
| E6 | `backend/qknow-framework/qknow-config/.../ThreadPoolConfig.java:32-43` | 无线程池 TaskDecorator | 已验证 |
| E7 | `backend/qknow-framework/qknow-common/.../utils/SecurityUtils.java:29-39` | 无认证上下文即抛 ServiceException | 已验证 |
| E8 | `backend/qknow-module-kb/.../controller/admin/KbInternalApiController.java:33-45` | 内部召回接口无鉴权 | 已验证 |
| E9 | `backend/qknow-hermes/.../flow/rag/RagRetrievalService.java:39-44`、`:54-126` | Hermes 召回 HTTP 客户端/仅取 content | 已验证 |
| E10 | `backend/qknow-hermes/.../agent/AgentOrchestrator.java:244`、`:266-276`、`:283-310`、`:385-396`、`:1015-1071` | Hermes 编排/重复 CRAG/伪工具/RAG prompt | 已验证 |
| E11 | `backend/qknow-hermes/.../agent/RetrievalEvaluator.java:41-70` | 无条件 CRAG LLM | 已验证 |
| E12 | `backend/qknow-module-kmc/.../service/rag/VectorRetriever.java:55`、`:90-118`、`:128-203` | 向量检索 + 重复 embedding/JNI 重打分 | 已验证 |
| E13 | `backend/qknow-framework/qknow-ai/.../service/impl/VectorStoreServiceImpl.java:33-41` | PgVectorStore 包装（默认 cosine） | 已验证（+jar 字节码） |
| E14 | `backend/qknow-framework/qknow-ai/.../service/impl/EmbeddingServiceImpl.java:69-103` | Embedding 构造未传 dimensions | 已验证 |
| E15 | `backend/qknow-module-kmc/.../service/rag/guard/EmbeddingDimensionGuard.java:24-107` | 维度/NaN guard；无范数校验、无生产调用 | 已验证 |
| E16 | `backend/qknow-module-kmc/.../service/rag/SemanticCacheService.java:54-160`、`:186-300`、`:370-376` | L2 语义缓存键/阈值/TTL/维度检查 | 已验证 |
| E17 | `backend/qknow-module-kmc/.../service/rag/cache/EnhancedSemanticCacheService.java:57-59`、`:104-105`、`:146-213`、`:222-237`、`:206-222` | L1 缓存/权限哈希未接线/门禁/淘汰 | 已验证 |
| E18 | `backend/qknow-module-kmc/.../service/rag/RagContextBuilder.java:31-36`、`:52-95`、`:142-281`、`:283-346` | 父级展开/预算/相邻块/去重/格式 | 已验证 |
| E19 | `backend/qknow-module-kmc/.../service/rag/CandidateFusionService.java:16`、`:59-160`、`:190-200` | RRF 公式与门控归一化 | 已验证 |
| E20 | `backend/qknow-module-kmc/.../service/rag/RagRerankService.java:43-50`、`:61-126`、`:205-230`、`:301-311`、`:381-432` | 门控/ColBERT/三阶段降级 | 已验证 |
| E21 | `backend/qknow-module-kmc/.../service/rag/rerank/*.java`（DashScope/Local/Deterministic/Colbert） | 重排 provider 行为与超时 | 已验证 |
| E22 | `backend/qknow-module-kmc/.../service/rag/CragWebSearchClient.java:99-117` | Web 结果负数 segmentId/外部内容 | 已验证 |
| E23 | `backend/qknow-module-kmc/.../service/rag/GraphRagRetriever.java:42`、`:71-120`、`:190-330`、`:413-490`、`:548-645`、`:712-720` | 图检索/PPR/Neo4j 参数化/Cypher 校验 | 已验证 |
| E24 | `backend/qknow-module-kmc/.../service/rag/GraphRagProperties.java:11`、`:15` | GraphRAG/PPR 默认关闭 | 已验证 |
| E25 | `backend/qknow-module-kmc/.../service/rag/CypherSafetyValidator.java:11-46` | 只读模板白名单 | 已验证 |
| E26 | `backend/qknow-module-kmc/.../service/rag/graph/MultiHopCausalPathExtractorImpl.java:15-25`、`:85-112` | mock 因果路径 | 已验证 |
| E27 | `backend/qknow-module-kmc/.../service/rag/graph/NeuroSymbolicGraphRankerImpl.java:25-30`、`:51-105` | mock 图排序 | 已验证 |
| E28 | `backend/qknow-module-kg/.../service/GraphCommunityService.java:91-165`；`.../controller/admin/graph/KgGraphController.java:115-200` | 真实 Leiden/GDS 管理端点 | 已验证 |
| E29 | `backend/qknow-module-kg/.../rag/DefaultGraphRagCoordinator.java:15-27` | GraphRAG 3.0 空实现 | 已验证 |
| E30 | `backend/qknow-module-kmc/.../service/sync/impl/KmcSyncServiceImpl.java:334-442`、`:88`、`:375-385`、`:398-410`、`:507-535`、`:605-630`、`:691-713` | 入库定时/切块/父块/富化/HyPE/O(n²) | 已验证 |
| E31 | `backend/qknow-framework/qknow-ai/.../transformer/RecursiveSplitter.java:22-92`；`StructureAwareMarkdownSplitter.java:27-160` | parent-child metadata 与结构切分 | 已验证 |
| E32 | `backend/qknow-hermes/.../rag/advanced/engine/HierarchicalDocumentChunker.java:78`、`:100-128`、`:142-181` | 死代码 + CRLF/无上限缺陷 | 已验证 |
| E33 | `backend/qknow-hermes/.../rag/cognitive/MultimodalPyramidChunker.java:106-150`、`:157-184`、`:188-330` | 合成向量/表格线性化/无真实切分 | 已验证 |
| E34 | `backend/qknow-hermes/.../rag/causal/HierarchicalAstChunker.java:78-135`、`:200-292`、`:294-330` | Markdown 层级/表格分块/无淘汰 | 已验证 |
| E35 | `backend/qknow-hermes/.../rag/cognitive/SteinerCausalPathReranker.java:94-260` | 真实 Steiner（死代码） | 已验证（实现存在、未接线） |
| E36 | `backend/qknow-hermes/.../flow/rag/governor/*.java` | 谱剪枝/PPR 引擎（零/自引用） | 已验证 |
| E37 | `backend/qknow-hermes/.../benchmark/e2e/E2EFourMetacenterPipelineBus.java:1-120` | 高级 RAG 仅被 benchmark 总线引用 | 已验证 |
| E38 | `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/RagShadowBaselineTest.java:122`、`:180-190`、`:212-283`、`:1149-1153`、`:1383-1385` | Shadow 测试门禁/容器/哨兵/DB 预算 | 已验证 |
| E39 | `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/FeatureHashEmbeddingModel.java:19-60` | 256 维哈希替代 embedding | 已验证 |
| E40 | `backend/tests/src/test/resources/rag-eval/baseline.json` | n=25 bootstrap CI 基线 | 已验证 |
| E41 | `backend/tests/src/test/resources/eval/{benchmark_300.jsonl,corpus_300.jsonl,qrels_300.tsv}` | 300/3801/679 数据集规模 | 已验证 |
| E42 | `backend/tests/evidence/v1_archive/evidence_integrity_audit.json` | THREE_PATH_PROXY/SEQUENTIAL_PROXY 声明 | 已验证 |
| E43 | `backend/tests/evidence/eval_summary_300_v2.csv` | QuadPath 0.371/BM25 0.553 recall@10 | 已验证 |
| E44 | `backend/tests/evidence/statistical_test_report_v2.json` | EXPLORATORY_ONLY + 四条限制 | 已验证 |
| E45 | `backend/tests/evidence/eval_e2e_summary_100.csv`、`eval_e2e_subgroup_100.csv` | E2E 4 组指标（QuadPath 0.590 vs BM25 0.816 relevance） | 已验证 |
| E46 | `backend/tests/evidence/eval_e2e_protocol_100.json` | 400 generation + 400 judge、deepseek-flash 协议 | 已验证 |
| E47 | `backend/tests/evidence/live-ann-environment.md:1-20` | live ANN 资格失败/向量数据被误删 | 已验证 |
| E48 | `backend/tests/evidence/h6-chunk-ab/README.md:7`；`a1-colbert-fail-closed/a0-a1-live-ann-report.json:439` | 实测 1024 维模型 | 已验证 |
| E49 | `backend/qknow-server/src/main/resources/application.yml:15-28`、`:130-143`；`application-dev.yml:158-229`；`application-prod.yml:150-153` | 配置默认值与 profile 差异 | 已验证 |
| E50 | `deploy/sql/postgresql/01-schema.sql:1410-1421`；`10-hnsw-index-migration.sql:13-14` | vector(1536)+cosine 索引 | 已验证 |
| E51 | `backend/qknow-module-kmc/.../service/rag/citation/CitationExtractor.java:22-111`；`backend/qknow-module-kb/.../agent/retrieval/EvidenceChainStreamPresenter.java:22-90` | 引用核验实现存在但无生产调用 | 已验证 |
| E52 | 本地 `~/.m2/.../spring-ai-pgvector-store-1.1.0.jar`（`javap -c`） | PgVectorStore 默认 COSINE_DISTANCE | 已验证（本地二进制） |

> 本报告未运行任何测试/服务；所有"运行时行为"结论均标注为推断或未知，需 Lead 安排一次受控复现（建议：带 debugInfo 的单库/多库问答各一次 + `vector_store` 维度/范数审计 SQL）后闭环。
