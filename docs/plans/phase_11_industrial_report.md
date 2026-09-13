# Phase 11 核心工程落地课题：工业级架构调研与落地契约报告

**架构模型基线**：唯一生成模型为 **DeepSeek API**，唯一向量模型为**阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型。

---

## 一、当前代码基线与系统失败机制诊断

### 1. 语义缓存模块 (SemanticCacheService.java)
- L1 内存缓存使用 LinkedHashMap(1000)，使用全局同步锁 synchronized(exactCacheLock) 造成高并发排队；
- 冷热突发 Query 未命中时缺少互斥加载，导致并发穿透 Qwen Embedding API 与后置 RAG 全链路；
- 对空结果不缓存哨兵，恶意或无解问题永远穿透；
- TTL 静态固定无 Jitter 抖动，存在雪崩隐患；
- 0.92 固定余弦阈值无法区分否定意图与核心实体对立，存在严重语义漂移。

### 2. 向量与知识图谱混合检索编排 (RagRetrievalService.java)
- 存在串行阻塞：主线程提前调用 entityFuture.get()，导致图谱检索被推迟 100~300ms 启动；
- 缺乏软超时预算控制，硬超时 30s 极易拖垮请求；
- 图谱检索与核心向量检索混用公共线程池，Neo4j 慢查引发仓壁击穿与级联耗尽；
- 缺少针对 Neo4j 慢调用和异常率的熔断降级。

### 3. 离线评测与 Holdout 数据集治理
- 数据集缺乏不可变语义版本快照，原地覆盖破坏可复现性；
- 难例未分层（多跳、反事实、时序事实、无答案负例）；
- 缺少指标自动化回归门禁。

---

## 二、Research Ledger

- **RL-P11-001**: GPTCache (VLDB 2023 / GitHub), VERIFIED.
- **RL-P11-002**: Redis Semantic Cache Architecture & Vector Search (2024), VERIFIED.
- **RL-P11-003**: Caffeine: W-TinyLFU High Performance Cache (GitHub 2024), VERIFIED.
- **RL-P11-004**: HippoRAG (NeurIPS 2024), VERIFIED.
- **RL-P11-005**: Ragas: Automated Evaluation of RAG (EACL 2024), VERIFIED.
- **RL-P11-006**: Resilience4j: Fault Tolerance Library for Java (GitHub 2024), VERIFIED.

---

## 三、工业级设计模式与核心代码骨架

1. **L1 Caffeine (W-TinyLFU) + 实体与否定词漂移门禁 + Jitter 防雪崩**；
2. **CompletableFuture 响应式编排 + 仓壁隔离线程池 + 250ms 软超时与 Resilience4j 熔断降级**；
3. **不可变语义版本化 Holdout 难例集治理与自动化回归门禁**。
