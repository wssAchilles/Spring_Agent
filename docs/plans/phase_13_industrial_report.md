# Phase 13 核心工程落地课题调研与工业级自愈设计报告：数据运维底座、Embedding 维度漂移防御与索引健康自愈体系

> **报告归档位置**：`docs/plans/phase_13_industrial_report.md`  
> **报告性质**：Phase 13 工业级工程落地调研与避坑指南（遵循 `AGENTS.md` 规范）  
> **状态**：**RESEARCH_GATE_PASSED**

---

## 一、Research-to-Implementation Gate 核心对标

### 1.1 真实执行路径与存储边界
- **分段与元数据存储**：关系表 `kmc_document_segment` 存储分段内容、状态及统计信息（`status`, `sync_status`, `document_id`, `qm_segment_id`）。
- **向量物理存储**：`vector_store` 表由 Spring AI 驱动，结构为 `(id UUID, content TEXT, metadata JSONB, embedding vector(1536))`。
- **写入路径**：`KmcSyncServiceImpl.save2VectorStore` 按 5 条一批调用 `VectorStore.add(partition)`，在写入前先基于 `metadata->>'kmc_document_id'` 执行 `vectorStore.delete`。
- **召回路径**：`VectorRetriever.retrieve` 基于 `metadata->>'kmc_knowledgeBase_id'` 过滤并检索，再由 `VecSimNative` 批次重排。

### 1.2 工业级设计模式
1. **Embedding 模型版本化与维度漂移防御 (EmbeddingDriftGuard)**：
   - 元数据绑定规范：在 `vector_store.metadata` 注入标准指纹（`model_provider`, `model_name`, `embedding_dim`）；
   - 三道强类型防线：内存校验（必须为 1536 维，杜绝 NaN/Inf）、物理列类型 `vector(1536)`、启动期自检与存量分布审计。
2. **向量存储与 RDBMS 双向对齐自愈管道 (VectorReconciliationEngine)**：
   - 方向 1：缺失向量补偿（Forward Healing）——扫描 `kmc_document_segment` 中已完成但在 `vector_store` 中缺失的切片，限流批量补齐；
   - 方向 2：孤儿向量物理清理（Reverse Purge）——扫描 `vector_store` 中对应分段/知识库已删除的残留向量，按批安全物理删除，释放内存与磁盘；
   - 基于主键游标滑动（Keyset Pagination），避免大事务与全表锁。
3. **冷启动与空库/短库容错契约 (Cold Start & Zero-State Fallback)**：
   - 前置门禁嗅探知识库文档数与向量就绪度；
   - 对未就绪/空库/零命中返回结构化 `RagZeroState`（`EMPTY_KNOWLEDGE_BASE`, `INDEXING_IN_PROGRESS`, `ZERO_SIMILARITY_HIT`），短路昂贵的 LLM 调用，杜绝拼接 `"## 知识库\nnull"` 产生幻觉。
4. **蓝绿平滑迁移与生产无停机重索引**：
   - 生产环境严禁直接运行同步 `CREATE INDEX`，必须使用 `CREATE INDEX CONCURRENTLY`；
   - 模型版本变更时通过影子表（Shadow Table）全量双写并对齐后，通过原子重命名完成零停机秒级切换。
