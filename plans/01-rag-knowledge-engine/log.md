# 01-rag-knowledge-engine RAG 知识引擎核心

> 状态: completed | 开始: 2026-06-20T00:00:00+08:00 | 完成: 2026-06-20T02:00:00+08:00

## 目标清单

- [x] 1.1 PostgreSQL + PgVector 数据库迁移脚本
- [x] 1.2 后端配置从 MySQL 切换到 PostgreSQL
- [x] 1.3 VectorStore 从 Weaviate 重构为 PgVector

## 关键决策

### 决策 1: 使用 PgVector 而非独立的 Milvus/Qdrant
- **原因**: 减少运维复杂度，PgVector 作为 PostgreSQL 扩展无需额外服务
- **否决方案**: Milvus、Qdrant、保留 Weaviate

### 决策 2: IVectorStoreService 返回通用 VectorStore 接口
- **原因**: 解耦向量存储实现，后续可轻松切换
- **否决方案**: 保留 WeaviateVectorStore 具体类型

### 决策 3: 新增 conversation、chat_message 表
- **原因**: 原 kb 模块缺少独立的对话和消息表
- **否决方案**: 复用 kb_runtime 表

## 遇到的问题

### 问题 1: MySQL CONCAT 函数兼容性
- **状态**: 已解决
- **解决方案**: PostgreSQL 也支持 CONCAT 函数，无需修改 Mapper XML

### 问题 2: MySQL TINYINT(1) 类型映射
- **状态**: 已解决
- **解决方案**: PostgreSQL DDL 中统一使用 BOOLEAN 类型

## 修改的文件

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| deploy/sql/postgresql/00-init-extensions.sql | 新建 | PostgreSQL 扩展初始化 |
| deploy/sql/postgresql/01-schema.sql | 新建 | 完整 DDL 建表脚本 (50+ 表) |
| deploy/sql/postgresql/02-init-data.sql | 新建 | 初始数据 |
| backend/pom.xml | 修改 | 新增 PostgreSQL JDBC 驱动 |
| backend/qknow-framework/qknow-ai/pom.xml | 修改 | weaviate-store → pgvector-store |
| backend/qknow-framework/qknow-ai/.../IVectorStoreService.java | 修改 | 返回类型改为 VectorStore |
| backend/qknow-framework/qknow-ai/.../VectorStoreServiceImpl.java | 修改 | 重写为 PgVectorStore 实现 |
| backend/qknow-framework/qknow-ai/.../WeaviateConfig.java | 修改 | 移除 WeaviateClient Bean |
| backend/qknow-framework/qknow-ai/.../application-ai-dev.yml | 修改 | 移除 Weaviate 配置 |
| backend/qknow-framework/qknow-ai/.../application-ai-prod.yml | 修改 | 移除 Weaviate 配置 |
| backend/qknow-module-kmc/.../KmcKnowledgeBaseServiceImpl.java | 修改 | WeaviateVectorStore → VectorStore |
| backend/qknow-module-kmc/.../KmcDocumentSegmentServiceImpl.java | 修改 | WeaviateVectorStore → VectorStore |
| backend/qknow-module-kmc/.../KmcSyncServiceImpl.java | 修改 | WeaviateVectorStore → VectorStore |
| backend/qknow-server/.../application-dev.yml | 修改 | MySQL → PostgreSQL 数据源 |
| backend/qknow-server/.../application.yml | 修改 | PageHelper dialect → postgresql |

## 技术笔记

1. **PgVectorStore 自动建表**: Spring AI 的 PgVectorStore 会自动创建 `vector_store` 表，但我们也在 DDL 中显式创建了该表并添加了 IVFFlat 索引
2. **向量维度**: 默认 1536 维 (OpenAI text-embedding-ada-002)，如使用其他模型需调整
3. **Filter 表达式**: PgVectorStore 使用 Spring AI 的通用 Filter.Expression API，与 Weaviate 实现兼容
4. **JdbcTemplate**: PgVectorStore 需要 JdbcTemplate 实例，通过注入 DataSource 创建

## 风险提示

1. Spring AI PgVectorStore 的 metadata filter 语法可能与 Weaviate 有细微差异，需在集成测试中验证
2. PgVector IVFFlat 索引需要在有数据后执行 `CREATE INDEX`，空表时索引无意义
3. 大数据量下的向量检索性能需验证 (建议 >10000 条时测试)

## 下一阶段预研

- [ ] Agent 配置前端 (创建/编辑表单、知识库绑定选择器)
- [ ] Agent 对话引擎 (会话管理、多轮对话上下文)
- [ ] 多模型支持 (DeepSeek + Gemini Flash)
- [ ] 前置条件检查: PostgreSQL 已部署、DeepSeek API Key 已配置
