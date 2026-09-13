# Phase 13: 数据运维底座、Embedding 维度漂移防御与索引健康自愈体系方案与实施契约

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置调研**：学术向智能体 (`07d2fbd3`) 与工程向智能体 (`523cf974`) 并发深度对标  
> **阶段状态**：**Delivered (全部实施完成，契约单测 4/4 绿灯，全量 712 项测试 100% 绿灯通过)**  
> **核心领域**：Embedding 维度与模型签名防御 (Dimension Guard)、RDBMS 与 VectorStore 双向反熵自愈 (Anti-Entropy Engine)、冷启动与零召回短路防御 (Zero-State Fallback)、批量删除原子性与孤儿向量清理  

---

## 一、当前代码与失败机制诊断

### 1.1 真实执行路径与关键调用关系
1. **向量写入路径**：`KmcSyncServiceImpl.save2VectorStore`
   - 在将分段写入 `vector_store` 时，未对 Embedding 向量维度（1536）进行应用级强校验，亦未在 `metadata` 注入不可变模型指纹（`model_provider`, `model_name`, `embedding_dim`）；若配置漂移或上游 API 变更，直接引发 PostgreSQL 维度错配异常或空间正交污染。
2. **切片批量删除缺陷**：`KmcDocumentSegmentServiceImpl.java:382-394`
   - 源码中 `KmcDocumentSegmentDO segmentDO = segmentDOList.get(0);` 仅取第 0 条执行删除，批量删除其余 $K-1$ 条时产生大量**无主孤儿幽灵向量**，常驻 HNSW 索引导致图拓扑稀疏化与虚假召回。
3. **知识库级联删除缺失**：`KmcKnowledgeBaseServiceImpl.java:removeKmcKnowledgeBase`
   - 仅删除 MySQL 主表记录，未联动物理清理 `vector_store` 中对应的千万级向量，造成磁盘膨胀与多租户数据泄露隐患。
4. **冷启动与未就绪知识库无差别透传**：
   - 知识库为空或无有效切片时，检索返回空，但在 `AgentOrchestrator` 中拼接产生 `"## 知识库名称\nnull"` 脏文本，诱发大模型产生幻觉。

### 1.2 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-Phase13)**：在 DeepSeek API 唯一生成模型与千问 1536 维 Embedding 唯一向量模型基线下，通过构建**“严格维度与模型签名防御门禁 (`EmbeddingDimensionGuard`) + RDBMS/VectorStore 双向对齐反熵自愈引擎 (`VectorReconciliationEngine`) + 切片批量原子清理 + 结构化冷启动短路契约 (`RagZeroState`)”**：
> 1. 能在写入与检索入口以 $O(1)$ 复杂度 **100% 拦截**非 1536 维及含 NaN/Inf 的脏向量；
> 2. 彻底修复批量删除仅删单条的致命 Bug，实现切片物理删除与向量清理 **100% 同步**，幽灵残留为 0；
> 3. 后台基于主键游标的轻量级反熵对账能无锁、无阻塞检测并修复单向悬空记录；
> 4. 冷库检索在 **$\le 5\text{ms}$** 内结构化短路，彻底消除 `"## 知识库\nnull"` 脏上下文，全量单测保持 100% 绿灯。

---

## 二、Research Ledger 核心理论与工业支撑

- **RL-PHASE13-001 (IEEE TKDE 2024 VDBMS)**：揭示了未修补的软/硬删除导致 HNSW 图导航退化与孤儿累积机理。
- **RL-PHASE13-004 (CVPR 2020 BCT)**：证明了跨模型向量空间的不可兑换性，推导了跨空间相似度失真导致假阴性 $P_{\text{FN}} \approx 100\%$ 的断崖式召回崩塌。
- **RL-PHASE13-005 (ACM SOSP 2007 Dynamo)**：指导了基于分片反熵比对的数据一致性收敛模型。
- **RL-P13-001 ~ 004 (PGVector / Milvus / Qdrant / Databricks)**：确立了不可变模型指纹绑定、CONCURRENTLY 零停机重索引以及 Zero-State 结构化短路契约。

---

## 三、架构设计与落地契约

### 3.1 核心组件设计
1. **`EmbeddingDimensionGuard`** (`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/guard/EmbeddingDimensionGuard.java`)：
   - 内存级前置强校验：`embedding.length == 1536`，数值遍历排查 `Float.isNaN` 与 `Float.isInfinite`；
   - 启动期物理对齐探测：检查数据库 `vector_store.embedding` 列物理定义与存量模型分布。
2. **`VectorReconciliationEngine`** (`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/reconcile/VectorReconciliationEngine.java`)：
   - 双向对称差分对账：
     - 方向 1（缺失向量补偿）：基于 Keyset 游标扫描 `kmc_document_segment` 中已完成但在 `vector_store` 缺失的分段并补齐；
     - 方向 2（孤儿向量物理清理）：扫描 `vector_store` 中对应分段/知识库已删除的残留向量并批量清理；
   - 单批 200 条，批次间休眠 50ms，避免占用数据库连接与 IO。
3. **修复批量删除漏洞** (`KmcDocumentSegmentServiceImpl.java`)：
   - 遍历传入的 `segmentDOList`，对所有分段 ID 构建清理表达式或批量物理清除，杜绝孤儿遗留。
4. **知识库级联删除增强** (`KmcKnowledgeBaseServiceImpl.java`)：
   - 删除知识库时，级联清理 `vector_store` 中 `metadata->>'kmc_knowledgeBase_id' = kbId` 的物理数据。
5. **结构化冷启动与零召回短路** (`RagZeroState.java` 与 `RagResult.java`)：
   - 状态枚举：`NORMAL (200)`, `EMPTY_KNOWLEDGE_BASE (4001)`, `INDEXING_IN_PROGRESS (4002)`, `ZERO_SIMILARITY_HIT (4004)`；
   - `RagRetrievalService` 前置快速嗅探，未就绪时直接短路；
   - `AgentOrchestrator` 拦截非 NORMAL 状态，注入防御指示词，绝不拼接 null。

---

## 四、实施计划与最小修改文件集合

### 4.1 新增文件
1. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/guard/EmbeddingDimensionGuard.java`
2. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/reconcile/VectorReconciliationEngine.java`
3. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/model/RagZeroState.java`
4. `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase13DataOpsAndHealthGateTest.java`

### 4.2 修改文件
1. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeSegment/impl/KmcDocumentSegmentServiceImpl.java`
   - 修复 `delete4VectorStore` 批量删除 Bug；
2. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java`
   - 补充级联物理删除 `vector_store` 数据；
3. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/impl/KmcSyncServiceImpl.java`
   - 在 `save2VectorStore` 注入 `EmbeddingDimensionGuard` 校验与模型元数据指纹；
4. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java`
   - 注入冷启动探测与 `RagZeroState` 短路；
5. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java`
   - 处理 `RagZeroState`，杜绝拼接 null 上下文。

---

## 五、验证命令与判定准则

```bash
# 契约测试验证命令
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests -Dtest=tech.qiantong.qknow.rag.eval.Phase13DataOpsAndHealthGateTest test

# 全量防退化回归命令
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests test
```

- 准入条件：Phase 13 契约测试 100% 绿灯，全量 708+ 项单测无一退化！
