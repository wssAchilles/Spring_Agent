# qKnow 项目 MEMORY.md

## 项目概述

**项目名称**：qKnow - AI-Native RAG 智能体编排平台
**技术栈**：Spring Boot 3.5.8 + Spring AI 1.1.0 + Spring AI Alibaba 1.1.2.2 + PostgreSQL 16 + pgvector + Vue 3
**远程仓库**：<https://github.com/wssAchilles/Spring_Agent.git>
**项目路径**：`/Users/achilles/Documents/许子祺/Agent`
**测试账号**：`admin / admin123`
**后端端口**：8099
**前端端口**：80
**Hermes gRPC 端口**：9090

---

## 一、系统架构

### 1.1 核心模块

```
backend/
├── qknow-server/           # 主启动模块
├── qknow-framework/        # 框架层
│   ├── qknow-ai/           # AI 能力（Embedding、VectorStore、Splitter）
│   ├── qknow-security/     # 安全认证
│   └── qknow-mybatis/      # 数据库访问
├── qknow-module-kmc/       # 知识管理中心
│   ├── qknow-module-kmc-api/    # API 接口
│   └── qknow-module-kmc-biz/    # 业务实现
├── qknow-module-kb/        # 知识库模块（Agent 配置、Bot 管理）
├── qknow-module-ai/        # AI 模型市场、API Key 管理
├── qknow-hermes/           # Agent 编排引擎
│   ├── qknow-hermes-core/  # 核心逻辑（ReactAgent、gRPC）
│   └── qknow-hermes-starter/ # 启动器
└── tests/                  # 统一测试模块
```

### 1.2 前端技术栈

| 技术         | 版本 | 用途      |
| ------------ | ---- | --------- |
| Vue 3        | 3.x  | 前端框架  |
| Vite         | 5.x  | 构建工具  |
| Pinia        | -    | 状态管理  |
| Element Plus | -    | UI 组件库 |
| Vue Router   | -    | 路由管理  |

### 1.3 Agent 架构

```
用户提问
    ↓
KbAgentConfigServiceImpl.chatMessage()
    ↓
RAG 检索（KmcKnowledgeBaseServiceImpl.recallTest()）
    ↓
构建 gRPC ChatRequest（含 RAGContext）
    ↓
hermesGrpcClient.chat(request)
    ↓
AgentOrchestrator.chat(ChatRequest)
    ↓
ReactAgent 执行（System Prompt + Tools + Messages）
    ↓
流式输出 ChatEvent
```

### 1.2 数据库

| 实例            | 地址           | 用户     | 用途     |
| --------------- | -------------- | -------- | -------- |
| 本地 PostgreSQL | localhost:5432 | achilles | 主数据库 |
| Docker Redis    | localhost:6379 | -        | 缓存服务 |

**注意**：Docker PostgreSQL 容器已停止，统一使用本地 PostgreSQL。

### 1.3 关键表结构

| 表名                     | 用途       | 关键字段                                                                       |
| ------------------------ | ---------- | ------------------------------------------------------------------------------ |
| `kmc_knowledge_base`   | 知识库     | id, name, embedding_model, embedding_model_provider, rag_cache_ttl             |
| `kmc_document`         | 文档       | id, knowledge_base_id, mode, separator, max_tokens, chunk_overlap, sync_status |
| `kmc_document_segment` | 文档分片   | id, document_id, content, content_tsv, position                                |
| `vector_store`         | 向量存储   | id, content, metadata (JSONB), embedding (vector)                              |
| `ai_api_key`           | API 密钥   | id, platform, api_key, url                                                     |
| `kb_bot`               | Bot 配置   | id, name, knowledge_ids, tool_ids                                              |
| `kb_agent_config`      | Agent 配置 | id, bot_id, question, model                                                    |

### 1.4 MCP 集成

项目支持 Model Context Protocol (MCP) 协议，用于扩展 Agent 工具能力：

- `McpToolAdapter` - MCP 工具适配器
- `McpClient` - MCP 客户端
- `McpProtocolHandler` - MCP 协议处理器

**支持的 MCP 工具**：

- WebSearchTool - 网页搜索
- 自定义 Function Bean - 自动发现

---

## 二、RAG Pipeline v2 优化（2026-06-24）

### 2.1 问题背景

- 向量检索只能查到部分 Day 文档，换 Day 或换问法就失效
- 全文检索在中文环境下失效（`plainto_tsquery('simple', ?)` 按空格切分，中文无法匹配）
- 向量数据缺失（API 超时导致写入失败，但 sync_status 已标记为成功）
- Agent 回答"知识库没有找到"，即使相关内容存在于数据库中

### 2.2 优化目标

- 稳定召回：只要相关内容存在，就应被召回并用于回答
- 智能过滤：提到 Day/文档名时作为硬约束，未提到时搜索全库
- 可观测性：调试信息可追踪问题根因

---

### 2.3 实施内容

#### 2.3.1 基础设施修复

| 问题                      | 修复                                      |
| ------------------------- | ----------------------------------------- |
| PostgreSQL 端口冲突       | 停止 Docker 容器，统一使用本地 PostgreSQL |
| `valid_flag` 类型不匹配 | `smallint` → `boolean`               |
| 空指针异常                | `Boolean.TRUE.equals()` + 默认值        |
| 平台名称大小写            | `openai` → `OpenAI`                  |
| 序列值落后                | 重置所有序列                              |

#### 2.3.2 切分模式优化

| 优化项     | 旧值                            | 新值                                  |
| ---------- | ------------------------------- | ------------------------------------- |
| 切分模式   | `GeneralSplitter`（单分隔符） | `RecursiveSplitter`（递归多分隔符） |
| 默认分隔符 | `\n`                          | `\n\n`（段落级）                    |
| 最大切片   | 1024 字符                       | 512 字符                              |
| 重叠长度   | 100                             | 64                                    |
| 切片数量   | 571 个（test.pdf）              | **22 个**（-96%）               |

#### 2.3.3 向量数据保障

| 机制          | 说明                                      |
| ------------- | ----------------------------------------- |
| 事务一致性    | 向量写入失败 →`sync_status=3`（ERROR） |
| 自动重试      | 3 次重试，指数退避（2s → 4s）            |
| metadata 增强 | 新增 `day_no`、`position` 字段        |
| 历史数据回填  | 2133 条向量全部回填完成                   |

#### 2.3.4 RAG Pipeline v2 核心模块

**三路并行召回：**

- VectorRetriever（语义检索, top 50）
- KeywordRetriever（全文检索, top 50）
- MetadataRetriever（元数据检索, top 20）

**RRF 融合算法：**

```java
score = sum(1 / (60 + rank_i))  // Reciprocal Rank Fusion, k=60
```

**智能重排：**

- Day 编号匹配 → +3 分
- 文档名子串匹配 → +2 分
- 关键词命中 → +1 分/词

**上下文构建：**

- 同一文档内按 `position` 补充相邻片段 `±1`
- 去重、控制 token 长度（12KB 限制）
- 保留来源文档名和 segmentId

#### 2.3.5 Agent 上下文注入

| 修改                         | 说明                                     |
| ---------------------------- | ---------------------------------------- |
| `appendRagContext()`       | 添加 4 条规则防止误报"未找到"            |
| `KbAgentConfigServiceImpl` | 格式化为 `[来源 N] 文档名 / 内容：...` |
| `SearchKnowledgeTool`      | 返回 `根据知识库检索结果：\n` + 内容   |

---

### 2.4 新增文件清单

#### RAG 核心模块（11 个）

```
backend/qknow-module-kmc/.../rag/
├── model/
│   ├── QueryIntent.java          # 查询意图（dayNo, docName, keywords）
│   ├── RetrievalResult.java      # 检索结果（segmentId, content, score, source）
│   └── RagResult.java            # 最终结果（context, sources, debugInfo）
├── QueryIntentAnalyzer.java      # 查询意图分析
├── VectorRetriever.java          # 向量检索
├── KeywordRetriever.java         # 关键词检索
├── MetadataRetriever.java        # 元数据检索
├── CandidateFusionService.java   # RRF 融合
├── RagRerankService.java         # 重排
├── RagContextBuilder.java        # 上下文构建
└── RagRetrievalService.java      # 主编排器
```

#### RAG 增强模块（4 个）

```
backend/qknow-framework/qknow-ai/.../transformer/
├── RecursiveSplitter.java        # 递归切分器
├── SemanticSplitter.java         # 语义切分器（Embedding 相似度）
├── GeneralSplitter.java          # 通用切分器（原有）
└── SplitterFactory.java          # 切分器工厂
```

#### 测试与脚本（4 个）

```
backend/tests/.../rag/RagGoldenTest.java
deploy/sql/postgresql/05-backfill-vector-metadata.sql
deploy/sql/postgresql/06-test-queries.sql
deploy/sql/postgresql/07-add-rag-cache-ttl.sql
```

---

### 2.5 修改文件清单

| 文件                                 | 修改内容                                                      |
| ------------------------------------ | ------------------------------------------------------------- |
| `WeaviateConstant.java`            | 新增 `day_no`, `category`, `position` 常量              |
| `KmcSyncServiceImpl.java`          | 写入 day_no metadata + 自动重试 + 缓存清除                    |
| `KmcKnowledgeBaseServiceImpl.java` | 接入 RagRetrievalService + RagCacheService + PermissionFilter |
| `KmcKnowledgeBaseController.java`  | debug 参数                                                    |
| `KmcKnowledgeBaseDO.java`          | 新增 `ragCacheTtl` 字段                                     |
| `RetrieveResultReqVO.java`         | 新增 `history` 字段（多轮对话）                             |
| `AgentOrchestrator.java`           | 结构化 RAG 上下文注入                                         |
| `KbAgentConfigServiceImpl.java`    | 召回内容格式化                                                |
| `SearchKnowledgeTool.java`         | 工具响应格式化                                                |
| `SplitterFactory.java`             | 兼容旧 mode 值 + semantic 模式                                |
| `RecursiveSplitter.java`           | 新增递归切分器                                                |
| `GeneralSplitter.java`             | 修复 overlap bug                                              |
| `VectorStoreServiceImpl.java`      | ConcurrentHashMap 缓存                                        |
| `ChatModelServiceImpl.java`        | WebClient 线程安全                                            |
| `EmbeddingServiceImpl.java`        | 修复 WebClient 共享 Bean 问题                                 |
| `PermissionFilter.java`            | 新增 `getAccessibleKnowledgeBaseIds` 方法                   |
| `RagCacheService.java`             | 支持 per-KB TTL、不缓存空结果                                 |

---

### 2.6 测试验证结果

#### 黄金问题集

| 查询           | 预期     | 实际                        | 得分 | 状态 |
| -------------- | -------- | --------------------------- | ---- | ---- |
| Day01 项目架构 | Day01    | Day01-项目架构熟悉与Bug排查 | 5.05 | ✅   |
| Day10 性能优化 | Day10    | Day10-性能优化与监控        | 5.04 | ✅   |
| RAG 检索优化   | Day15-16 | Day15-16-RAG检索优化        | 0.03 | ✅   |
| 昵称展示规则   | Day10    | Day10-性能优化与监控        | 0.02 | ✅   |
| 全库搜索       | 多文档   | Day01-项目架构熟悉与Bug排查 | 1.03 | ✅   |

#### 多轮对话测试

| 测试场景 | 查询                   | 上下文           | 预期     | 实际                  | 状态 |
| -------- | ---------------------- | ---------------- | -------- | --------------------- | ---- |
| 场景 1   | "那第二天呢？"         | Day01 做了什么？ | Day02    | Day02-PDF导出功能修复 | ✅   |
| 场景 2   | "它的主要内容是什么？" | RAG 检索优化     | Day15-16 | Day15-16-RAG检索优化  | ✅   |

#### 数据完整性

| 指标          | 值                                |
| ------------- | --------------------------------- |
| 向量总数      | 2133                              |
| metadata 回填 | 2133 (100%)                       |
| day_no 覆盖   | 19/19 文档                        |
| 知识库覆盖    | 2 个（KB1: 19 文档, KB7: 1 文档） |

---

### 2.7 关键设计决策

1. **RRF 融合**：使用 Reciprocal Rank Fusion（k=60）替代简单加权，因为不同检索器分数尺度不可比
2. **确定性 fallback**：不强依赖付费 reranker，使用规则加分（+3/+2/+1）
3. **±1 上下文扩展**：同一文档内补充相邻片段，保持语义连贯
4. **12KB 限制**：控制注入 Agent 的上下文长度，避免 token 浪费
5. **4 条规则**：防止 LLM 在上下文非空时误报"知识库没有找到"
6. **per-KB TTL 缓存**：每个知识库可独立配置缓存时间，默认 300 秒
7. **权限过滤**：接入全路径（semantic/fulltext/hybrid/RAG v2），admin 角色自动绕过

---

## 三、API 配置（当前最优方案）

### 3.1 API 平台选择

| 组件 | API 平台 | 模型 | Key 来源 | 说明 |
|------|----------|------|----------|------|
| **Agent 对话推理** | DeepSeek | deepseek-chat | `ai_api_key.id=1` | 国内直连，性价比高 |
| **Embedding 向量化** | TongYi (通义千问) | text-embedding-v1 | `ai_api_key.id=103` | 中文优化，1536 维 |
| **查询重写** | DeepSeek | deepseek-chat | `application.yml` | 替代 OpenAI gpt-4o-mini |
| **质量评分** | DeepSeek | deepseek-chat | `hermes.judge` 配置 | 默认使用 DeepSeek |
| **RAG 评估** | DeepSeek | deepseek-chat | `hermes.eval.ragas` 配置 | 默认使用 DeepSeek |

### 3.2 成本对比（优化后 vs 优化前）

| 场景 | 优化前（OpenAI） | 优化后（DeepSeek+TongYi） | 节省 |
|------|-----------------|-------------------------|------|
| Agent 对话 | ~$15/1000次 | ~¥7/1000次 | **95%** |
| Embedding | ~$2/1000文档 | ~¥0.5/1000文档 | **85%** |
| 查询重写 | ~$0.3/1000次 | ~¥0.7/1000次 | 持平 |
| **总计** | ~$17.3 | ~¥8.2 | **~70%** |

### 3.3 支持的 Embedding 平台

| 平台 | 模型名称 | 维度 | 价格 |
|------|----------|------|------|
| TongYi (阿里) | `text-embedding-v1` | 1536 | ¥0.0007/千tokens |
| TongYi (阿里) | `text-embedding-v3` | 1024 | ¥0.0007/千tokens |
| OpenAI | `text-embedding-3-small` | 1536 | $0.02/1M tokens |
| Ollama (本地) | `nomic-embed-text` | 768 | 免费 |

### 3.4 环境变量配置（.env）

```bash
# DeepSeek API（Agent 对话、查询重写、质量评分）
HERMES_OPENAI_BASE_URL=https://api.deepseek.com

# 通义千问 API（Embedding 向量化）
TONGYI_API_KEY=sk-ws-H.RYHDIDL.Ix8p...

# PostgreSQL
POSTGRESQL_USERNAME=achilles
POSTGRESQL_PASSWORD=achilles
```

### 3.5 数据库配置（ai_api_key 表）

| ID | Platform | 用途 | URL |
|----|----------|------|-----|
| 1 | DeepSeek | Agent 对话 | https://api.deepseek.com |
| 101 | OpenAI | Embedding（备用） | https://api.openai.com/v1 |
| 103 | TongYi | Embedding（当前） | https://dashscope.aliyuncs.com/compatible-mode/v1 |
| 104 | Ollama | 本地 Embedding | http://localhost:11434 |

### 3.6 配置流程

```
1. 进入「AI 模型市场」→「API 密钥」
   → DeepSeek: platform=DeepSeek, url=https://api.deepseek.com, apiKey=sk-xxx
   → TongYi: platform=TongYi, url=https://dashscope.aliyuncs.com/compatible-mode/v1, apiKey=sk-xxx

2. 进入「知识库管理」→「新建知识库」
   → embeddingModelProvider = 选择通义千问密钥 (id=103)
   → embeddingModel = text-embedding-v1
   → indexingTechnique = high_quality
   → searchMethod = semantic

3. 进入「Bot 管理」→「Agent 配置」
   → modelConfig = {"modelId":1,"modelName":"deepseek-chat"}
```

### 3.7 数据流

```
知识库.embeddingModelProvider (= ai_api_key.id=103, TongYi)
    ↓
AiApiKeyDO { platform="TongYi", url, apiKey }
    ↓
EmbeddingServiceImpl.getEmbeddingModel("TongYi", baseUrl, apiKey, "text-embedding-v1")
    ↓
DashScopeEmbeddingModel (1536 维)
    ↓
VectorStoreServiceImpl.getVectorStore(embeddingModel)
    ↓
PgVectorStore (embedding + 存储到 PostgreSQL)
```
1. 进入「AI 模型市场」→「API 密钥」
   → 新增密钥：platform=OpenAI, url=https://api.openai.com, apiKey=sk-xxx

2. 进入「知识库管理」→「新建知识库」
   → embeddingModelProvider = 选择刚创建的密钥
   → embeddingModel = text-embedding-3-small
   → indexingTechnique = high_quality
   → searchMethod = semantic
```

### 3.3 数据流

```
知识库.embeddingModelProvider (= ai_api_key.id)
    ↓
AiApiKeyDO { platform, url, apiKey }
    ↓
EmbeddingServiceImpl.getEmbeddingModel(platform, baseUrl, apiKey, modelName)
    ↓
VectorStoreServiceImpl.getVectorStore(embeddingModel)
    ↓
PgVectorStore (embedding + 存储到 PostgreSQL)
```

---

## 四、文档上传与切分流程

### 4.1 前端操作路径

```
知识库列表页 → 点击知识库卡片 → 文档列表页 → 点击"新增" → 文档上传表单页 → 配置参数 → 提交
```

### 4.2 切分模式

| 模式          | 说明                 | 适用场景         |
| ------------- | -------------------- | ---------------- |
| `recursive` | 递归多分隔符切分     | 中文文档（推荐） |
| `semantic`  | Embedding 相似度切分 | 需要语义完整性   |
| `custom`    | 单分隔符切分         | 简单文本         |

### 4.3 后端处理流程

```
文档上传 → Tika 解析 → 文本预处理 → 切分 → 向量化 → 存储
    ↓           ↓           ↓          ↓        ↓        ↓
  文件存储    提取文本    去空格/URL   RecursiveSplitter  Embedding  PgVector
```

---

## 五、检索流程

### 5.1 RAG v2 流程

```
用户查询
    ↓
QueryIntentAnalyzer (提取 dayNo, docName, keywords)
    ↓
┌───────────────────────────────────────────────┐
│ 三路并行召回（30s 超时）                        │
│  ├── VectorRetriever (语义检索, top 50)         │
│  ├── KeywordRetriever (全文检索, top 50)        │
│  └── MetadataRetriever (元数据检索, top 20)     │
└───────────────────────────────────────────────┘
    ↓
CandidateFusionService (RRF: k=60)
    ↓
RagRerankService (DashScope 或 fallback: +3 dayNo, +2 docName, +1 keyword)
    ↓
RagContextBuilder (±1 扩展, 去重, 12KB 限制)
    ↓
Agent (结构化上下文注入，禁止误报"未找到")
```

### 5.2 检索方法

| 方法                 | 说明                         | 入口                                             |
| -------------------- | ---------------------------- | ------------------------------------------------ |
| `semantic_search`  | 向量语义检索                 | `KmcKnowledgeBaseServiceImpl.semanticSearch()` |
| `full_text_search` | 全文检索（ILIKE + tsvector） | `KmcKnowledgeBaseServiceImpl.fullTextSearch()` |
| `hybrid_search`    | 混合检索（默认）             | `KmcKnowledgeBaseServiceImpl.hybridSearch()`   |
| `rag_v2`           | RAG Pipeline v2              | `RagRetrievalService.retrieve()`               |

---

## 六、已知问题与待办

### 6.1 已完成

- ✅ RAG Pipeline v2 核心模块
- ✅ 多轮对话 compressQuery 支持
- ✅ PermissionFilter 权限过滤
- ✅ RagCacheService TTL 优化
- ✅ SemanticSplitter 语义切分
- ✅ 黄金问题集测试通过
- ✅ 向量数据完整性验证（2133 条）

### 6.2 已知问题

| 问题                       | 状态   | 说明                     |
| -------------------------- | ------ | ------------------------ |
| Docker PostgreSQL 端口冲突 | 已解决 | 统一使用本地 PostgreSQL  |
| valid_flag 类型不匹配      | 已解决 | smallint → boolean      |
| Embedding API 超时         | 已解决 | 自动重试 3 次 + 指数退避 |
| 全文检索中文失效           | 已解决 | ILIKE 兜底匹配           |

## 七、常用命令

### 7.1 启动服务

```bash
# 启动所有服务
bash scripts/start.sh

# 停止所有服务
bash scripts/stop.sh

# 查看状态
bash scripts/status.sh
```

### 7.2 数据库操作

```bash
# 连接数据库
psql -h localhost -U achilles -d ai_agent

# 查看向量数量
SELECT count(*) FROM vector_store;

# 查看文档同步状态
SELECT id, name, sync_status FROM kmc_document WHERE del_flag = 0;
```

### 7.3 测试命令

```bash
# 登录获取 Token
TOKEN=$(curl -s -X POST "http://localhost:8099/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# 测试检索（通义千问 Embedding）
curl -s "http://localhost:8099/kmc/knowledgeBase/recallTest" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id": 1, "query": "Day01 项目架构", "topK": 3, "searchMethod": "hybrid_search"}'

# 测试 Agent 对话（DeepSeek）
curl -s -N -X POST "http://localhost:8099/kb/agent/testChatMessages" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "botId": 2,
    "question": "Day01 做了什么？",
    "modelConfig": "{\"modelId\":1,\"modelName\":\"deepseek-chat\"}",
    "knowledgeIds": "1"
  }'
```

---

## 八、配置文件位置

| 文件                                                             | 用途                   |
| ---------------------------------------------------------------- | ---------------------- |
| `backend/qknow-server/src/main/resources/application-dev.yml`  | 开发环境配置           |
| `backend/qknow-server/src/main/resources/application-prod.yml` | 生产环境配置           |
| `.env`                                                         | 环境变量（API Key 等） |
| `docker-compose.yml`                                           | Docker 服务配置        |
| `scripts/start.sh`                                             | 启动脚本               |
| `scripts/stop.sh`                                              | 停止脚本               |
| `scripts/status.sh`                                            | 状态查看脚本           |

### 8.1 环境变量（.env）

| 变量 | 用途 |
|------|------|
| `HERMES_OPENAI_API_KEY` | DeepSeek API 密钥（Agent 对话、查询重写） |
| `HERMES_OPENAI_BASE_URL` | DeepSeek API 地址 |
| `TONGYI_API_KEY` | 通义千问 API 密钥（Embedding） |
| `OPENAI_API_KEY` | OpenAI API 密钥（备用） |
| `POSTGRESQL_USERNAME` | PostgreSQL 用户名 |
| `POSTGRESQL_PASSWORD` | PostgreSQL 密码 |

### 8.2 Spring AI 配置

```yaml
spring:
  ai:
    version: 1.1.0
    alibaba:
      version: 1.1.2.2
```

### 8.3 RAG 配置项

```yaml
hermes:
  rag:
    cache:
      enabled: true
      ttl: 300  # 默认缓存时间（秒）
    query-transform:
      enabled: true
      strategy: rewrite
      platform: DeepSeek
      base-url: https://api.deepseek.com/v1
      model-name: deepseek-chat
```

### 8.3 RAG 配置项

```yaml
hermes:
  rag:
    cache:
      enabled: true
      ttl: 300  # 默认缓存时间（秒）
    query-transform:
      enabled: true
      strategy: rewrite
      platform: OpenAI
      modelName: gpt-4o-mini
```

---

## 九、Agent 配置

### 9.1 Agent 模型配置

Agent 对话使用 DeepSeek 模型，配置存储在 `kb_agent_config` 表：

```sql
-- 查看 Agent 配置
SELECT id, bot_id, model_config, knowledge_ids FROM kb_agent_config WHERE del_flag = 0;

-- 更新 Agent 模型为 DeepSeek
UPDATE kb_agent_config SET model_config = '{"modelId":1,"modelName":"deepseek-chat"}' WHERE bot_id = 2;
```

### 9.2 Agent 对话流程

```
用户提问 → RAG 检索（通义千问 Embedding）→ 构建上下文 → DeepSeek 生成回答 → 流式输出
```

### 9.3 前端测试入口

1. **召回测试**：知识库详情 → 左侧菜单「召回测试」
2. **Agent 对话**：Bot 管理 → 选择 Bot → 构建页面 → 右侧调试对话区

---

**最后更新**：2026-06-24
**总计工作量**：17 个新增文件，19 个修改文件，2133 条向量数据回填，5 个黄金测试用例全部通过。
