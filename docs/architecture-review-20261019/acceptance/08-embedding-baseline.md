# Embedding 基线说明：语料模型为 text-embedding-v4（1024 维）

> 日期：2026-10-22　|　结论来源：本地 `ai_agent` 数据库 + 阿里云百炼 `/compatible-mode/v1/embeddings` 实测
> 本文用于纠正仓库内“1536 维 Qwen”旧基线描述，明确当前**真实生产/实验语料的切分向量模型**。

## 1. 明确结论

**本项目现有语料的 embedding（切片向量化）模型是 `text-embedding-v4`，输出 1024 维向量。**

- 不是 `qwen3.7-text-embedding`；
- 不是 1536 维；
- 已入库的向量不需要重建（查询侧继续使用 `text-embedding-v4` 即与之同空间）。

## 2. 实测证据

### 2.1 存量向量规模

```sql
SELECT count(*) FROM vector_store;                 -- 4955
SELECT count(*) FROM kmc_document_segment;         -- 31250
```

按知识库分布（`metadata->>'kmc_knowledgeBase_id'`）：

| KB | 向量数 |
|---|---|
| 8（课设报告） | 2317 |
| 1（盛趣实习日志） | 2158 |
| 7（常州工学院总评方案） | 445 |
| 无 KB 元数据 | 35 |
| **合计** | **4955** |

`vector_dims(embedding)` 全部为 **1024**。

### 2.2 模型兼容性对照（同一段原文重新 embedding，与库内向量算余弦）

| 模型 | 样本数 | 平均余弦 | 结论 |
|---|---|---|---|
| `text-embedding-v4`（dimensions=1024） | 16 | **0.999999**（min 0.999999 / max 1.0） | **与存量向量完全一致** |
| `qwen3.7-text-embedding`（dimensions=1024） | 16 | **0.0186**（min -0.036 / max 0.067） | 不同向量空间，不可直接混用 |

### 2.3 真实 RAG 冒烟（v4 + DeepSeek）

- 问题：“鸿蒙移动端图书借阅系统为什么从 Supabase 迁移到 PocketBase？迁移时保留了哪些数据？”
- 查询向量：`text-embedding-v4`，1024 维（26 tokens）；
- pgvector Top-5 余弦：**0.8021–0.8186**，命中文档 `移动应用开发.pdf`；
- DeepSeek `deepseek-chat` 基于检索上下文生成答案，内容有据可查并主动标注资料不足处（801 tokens）。

## 3. 运行时配置要求

### 3.1 知识库映射（本地数据修正建议）

当前 `kmc_knowledge_base` 的部分映射指向了错误的 Key/模型（KB 1 → OpenAI Key 101 且 Key 失效；KB 4/7 → DeepSeek Key 1；KB 8 → text-embedding-v1 + provider 空），应用层检索会失败。应统一为：

```sql
UPDATE kmc_knowledge_base
SET embedding_model = 'text-embedding-v4',
    embedding_model_provider = '103'   -- ai_api_key.id = TongYi（DashScope）
WHERE id IN (1, 4, 7, 8);
```

> 该操作只修正配置，不修改任何向量数值。

### 3.2 维度配置/探测

新增配置项（默认 0 = 自动探测）：

```yaml
qknow:
  embedding:
    dimension: 1024   # 生产语料基线；0 表示启动时从 vector_store 自动探测
```

- `EmbeddingDimensionGuard`：维度优先级为 **显式配置 → 启动探测 `vector_dims(embedding)` → 兼容回退 1536**；配置与库内实际维度不一致时打 ERROR，写入/检索前按生效维度拒绝跨空间向量。
- `McpToolSemanticRetriever`：不再写死 1536；首次索引自动探测实际维度，也可通过 `setEmbeddingDimension` 显式指定；维度不一致的索引向量被拒绝，查询维度不一致时降级为默认工具集，避免跨空间余弦。

## 4. 与旧文档的差异

仓库 `docs/plans/*` 与 `AGENTS.md` 中的“阿里千问 1536 维”属于历史算法基线描述；**当前真实库内数据与实验基线以本文为准：`text-embedding-v4` / 1024 维**。

如需回到 1536 维 Qwen 基线，必须**重建全部语料向量**（4,955 条约 ¥0.6–1.1，或 31,250 条 segment 约 ¥1.6–3.1），不能只换查询模型。

## 5. 本地配置修正与 API 级验证（2026-10-22）

已按上节 SQL 将本地 `kmc_knowledge_base` 的 7 条记录统一修正为：

```
embedding_model = text-embedding-v4
embedding_model_provider = 103   -- ai_api_key.id = TongYi
```

随后以 Java 21 启动后端（独立端口 8098，避免与本地 dev watcher 的 8099 冲突），`admin/admin123` 登录后调用真实 API：

```http
POST /kmc/knowledgeBase/recallTest?debug=true
Authorization: Bearer <token>
{"id":8,"query":"鸿蒙移动端图书借阅系统为什么从 Supabase 迁移到 PocketBase？迁移时保留了哪些数据？","topK":5}
```

返回 `code=200`，命中 `移动应用开发.pdf`，首条 `score=3.015625`、**`scoreType=RRF`**，共 12 条候选；证明“DB 配置 → v4 查询向量化 → pgvector/混合检索 → RRF 排序”的应用链路已真实打通。

启动过程中同时修复了两个阻塞问题：

1. `embedded-redis` 已从生产 fat jar 排除，但 `EmbeddedRedisConfig` 仍被 Spring 内省导致 `NoClassDefFoundError`；
   增加 `@ConditionalOnClass(name = "redis.embedded.RedisServer")` 后，缺失该依赖时整类跳过。
2. `HermesConfiguration` 通过 `@PostConstruct` 注入 `McpToolAdapter` 形成循环依赖；
   改为独立的 `McpSemanticEmbeddingInitializer implements ApplicationRunner`，容器就绪后再装配 MCP 语义 EmbeddingModel。
