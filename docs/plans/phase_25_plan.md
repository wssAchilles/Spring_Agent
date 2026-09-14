# Phase 25 决策完备实施方案：面向百万级超长知识库的高吞吐异步分块流、混合检索重排小模型蒸馏加速与双活平滑索引热切换体系 (Ultra-Scale Chunking & Embedding Streaming Pipeline, Distilled Rerank Serving & Zero-Downtime Index Hot-Swapping)

> **拟归档路径**：`docs/plans/phase_25_plan.md`  
> **依据规范**：`AGENTS.md` Research-to-Implementation Gate 强制规范  
> **前置依赖**：Phase 01 ~ Phase 24 全量交付通过（测试 100% 绿灯，前端构建通过）  
> **理论依据**：`docs/plans/phase_25_academic_report.md`（离散时间排队状态方程、李雅普诺夫漂移加惩罚负漂移条件与强稳定性严格证明、滑动窗口缓冲区零溢出概率证明、断点增量分块信息恢复上界证明、两阶段级联重排期望时延与 Pareto 最优前沿方程、NDCG@10 截断损失下界定理、教师-学生蒸馏梯度解析与 SGD/Adam 收敛性证明、MVCC 快照读隔离性、CAS 原子指针翻转零空窗期不变性与 WAL/CDC 增量追平零数据丢失严格证明）  
> **工业对标**：`docs/plans/phase_25_industrial_report.md`（Project Reactor / Java Flow API 响应式背压整形、Apache Flink 异步屏障快照 ABS 断点游标、ColBERTv2 迟交互两阶段级联架构、Elasticsearch Index Aliases 零停机重索引、HuggingFace TEI 动态 Token 微批切分、PostgreSQL pgvector HNSW 并发索引构建与生产避坑）  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **环境隔离铁律**：后端全量模块统一且唯一使用 **Java 21** 编译与运行，绝对路径固定为 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，Maven 执行强制局部传入 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。

---

## 一、方案全景与架构拓扑

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                             超长长文本 / 百万级切片异步流式导入子系统                              │
│                                                                                                  │
│  物理长文档 (PDF/MD/TXT)                                                                         │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ StreamingDocumentReader ]  ──>  Java NIO 按段读取，常数级 O(1) 堆内存                             │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ Reactive Chunking Flux ]   ──>  父子切片划分与结构感知分块                                         │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ DynamicMicroBatcher ]      ──>  双阈值动态微批截断 (Tokens <= 8192 || Count <= 25)              │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ AdaptiveBackpressureSmoother ] ->  李雅普诺夫强稳定队列 (Q(t) <= Q_high)，千问 1536维批量 Embedding   │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ DocumentCheckpointManager ]──>  (doc_id, line_offset, seq_id) 两阶段提交，崩溃秒级精准断点恢复    │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            双活平滑索引热切换器 (Zero-Downtime Hot-Swapper)                       │
│                                                                                                  │
│   在线读请求 (Query)  ──>  [ PostgreSQL View: vector_store_active ] ──> [ Active: vector_store_v1 ] │
│                                         ▲                                                        │
│                                  CAS 原子翻转 (<=1ms)                                             │
│                                         │                                                        │
│   离线重构 / 批量同步 ──>  [ Shadow Table: vector_store_v2 ]  ──> [ CREATE INDEX CONCURRENTLY HNSW ]│
│                                         │                                                        │
│                                  故障一键瞬时回滚通道                                            │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                             两阶段级联精排引擎 (Two-Stage Cascade Reranking)                      │
│                                                                                                  │
│   多路召回候选集 (Top-100/200)                                                                   │
│         │                                                                                        │
│         ├──[ 命中 Phase 07 动态门控 ] ──> 直接短路旁路返回 Top-K (耗时 < 1ms)                     │
│         │                                                                                        │
│         └──[ 未命中门控 ] ──> [ Fast-Pass Scorer ] ──> 向量内积+CJK Bi-gram混合保真 (耗时 <= 5ms)│
│                                     │                                                            │
│                               快速截断降维 (Top-20)                                              │
│                                     │                                                            │
│                                     ▼                                                            │
│                               [ Precise-Pass ] ──> Cross-Encoder 精排模型 (耗时削减 75%)          │
│                                     │                                                            │
│                                     ▼                                                            │
│                               最终 Top-K 高置信度切片结果                                         │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、唯一待验证假设 (H-Phase25)

> **唯一待验证假设 (H-Phase25)**：  
> 在 `qknow-module-kmc` 落地反应式流分块与微批嵌入流水线、两阶段级联精排算子与双活索引热切换器后：  
> 1. 超长文档分块过程保持 $O(1)$ 堆内存开销，突发导入时队列长度严格稳定，无 OOM 与缓冲区溢出，断点恢复实现零重复处理；  
> 2. Fast-Pass 粗排在 100 条候选下打分延迟 $\le 5\text{ms}$，粗筛至 Top-20 时高相关文档召回率 $\ge 90\%$，端到端 P99 重排耗时降低 $\ge 70\%$；  
> 3. 双活索引热切换指针翻转耗时 $\le 1\text{ms}$，期间并发读查询零空窗期（100% 成功率），支持故障一键瞬时回滚。

---

## 三、实施范围与修改边界

### 3.1 新增核心业务与控制组件 (最小修改集)
1. **`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/pipeline/DocumentCheckpoint.java`** [NEW]：
   断点游标值对象（记录文档ID、字节/行偏移、已入库切片数、状态与时间戳）。
2. **`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/pipeline/DocumentCheckpointManager.java`** [NEW]：
   断点游标持久化管理（两阶段提交、状态流转与断点恢复加载）。
3. **`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/pipeline/ReactiveChunkingEmbeddingPipeline.java`** [NEW]：
   基于 Project Reactor 的高吞吐流式分块、双阈值动态微批（8192 Token / 25 切片）与自适应背压嵌入控制器。
4. **`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/FastPassHybridScorer.java`** [NEW]：
   Fast-Pass 轻量混合保真打分算子（千问向量余弦 + CJK Bi-gram 词重合度 + 实体加权，耗时 $\le 5\text{ms}$）。
5. **`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/TwoStageCascadeRerankEngine.java`** [NEW]：
   两阶段级联精排引擎（Fast-Pass 粗筛降维至 Top-20，Precise-Pass 高精度精排与 Phase 07 门控短路联动）。
6. **`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/sync/hotswap/IndexHotSwapperService.java`** [NEW]：
   双活平滑索引热切换器（影子表生命周期管理、CONCURRENTLY HNSW 构建、PostgreSQL View CAS 指针原子翻转与瞬时回滚）。
7. **`deploy/sql/postgresql/25-streaming-and-hotswap.sql`** [NEW]：
   PostgreSQL 迁移脚本（创建 `kmc_document_checkpoint`、`kmc_index_metadata` 表与 `vector_store_active` 视图别名）。

### 3.2 契约测试组件
8. **`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase25UltraScaleContractTest.java`** [NEW]：
   覆盖 10 大核心契约测试用例，验证背压稳定性、动态微批、断点精准续传、Fast-Pass 延迟与召回、级联门控、影子索引无锁构建、CAS 原子翻转与一键回滚。

---

## 四、验证命令与精确测试计数

```bash
# 局部显式指定 Java 21 环境编译并运行 Phase 25 契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -am -pl tests -Dtest=Phase25UltraScaleContractTest -Dsurefire.failIfNoSpecifiedTests=false

# 全量防退化回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=*Test -Dsurefire.failIfNoSpecifiedTests=false
```
精确契约测试计数：10 / 10 必须全量通过。
全量后端回归测试：792 / 792 必须 100% 绿灯。
