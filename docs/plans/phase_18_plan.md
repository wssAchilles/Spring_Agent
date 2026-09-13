# Phase 18: 原生加速深水区 (N1+N2) —— Rust SIMD 批量向量核与 Tantivy 中文 BM25 原生检索引擎闭环实施计划

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置调研**：学术向智能体 (`127c51d7`) 与工程向智能体 (`df569816`) 双向并发深度科研已闭环完成  
> **学术报告**：`docs/plans/phase_18_academic_report.md` (6 篇顶会文献与国际规范，涵盖 Roofline 模型、1536 维 SIMD 代数整除性、FMA 逆向误差界 $\le 1.16 \times 10^{-5}$、保序单调性定理、Block-Max WAND 渐进复杂度定理、JNI 盈亏平衡解析方程)  
> **工程报告**：`docs/plans/phase_18_industrial_report.md` (Faiss/Qdrant/Milvus SIMD 实践、Tantivy gRPC + JiebaTokenizer + IndexWriterActor 节流提交、Java Netty gRPC 客户端 250ms 软超时 Fail-Open 降级、三大生产级灾难避坑指南)  
> **方案文档**：`docs/plans/phase_18_plan.md`  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **当前状态**：**DESIGNED / PENDING_USER_APPROVAL**（第一回合只读检查与方案设计完成，待用户明确批准后进入 TDD 实施）

---

## User Review Required

> [!IMPORTANT]
> **本阶段核心技术突破与工程治理价值**：
> 1. **Rust SIMD 跨平台批量向量计算核 (`vecsim-jni`) 极致重构 (N1 候选)**：
>    - 彻底根治现存朴素标量双层循环的性能瓶颈，利用 1536 维对 8 (AVX2)、16 (AVX-512)、4 (NEON) 的严格整除性，落地 **8 路累加器并行展开 (Loop Unrolling 8x)**，消除流水线气泡；
>    - 基于 Rayon 引入多线程数据切片并行，设置 $N \ge 128$ 自适应分块门槛；
>    - 引入堆外直接内存（DirectByteBuffer）零拷贝接口，将数组跨界传递耗时与 GC 停顿彻底归零，单机千万次点积/秒级吞吐，计算误差严格 $\le 10^{-5}$；
> 2. **Tantivy 中文 BM25 检索引擎服务化与协议纠偏 (`tantivy-server`) (N2 候选)**：
>    - 彻底修复 `tantivy-server` 未初始化目录空指针 Bug，建立目录磁盘持久化；
>    - 彻底解决 IndexWriter 滥用与 Segment 爆炸灾难，构建长驻单例 `IndexWriterActor`，通过异步 MPSC 队列配合“满 500 篇或每隔 1000ms”双阈值节流 Commit；
>    - 集成 `jieba-rs` 实现精准中文分词器（`JiebaTokenizer`），并在搜索时按 `knowledge_base_id` 进行多租户硬隔离；
> 3. **Java 端 TantivyClient 协议对齐与 KeywordRetriever 双轨容灾降级闭环**：
>    - 彻底解决 Java 客户端发送 HTTP POST 与 Rust 暴露 gRPC 的致命协议错配，重构 `TantivyClient` 为高性能 Netty gRPC 客户端；
>    - 设立 **250ms 软超时与心跳探活**，在 `KeywordRetriever` 落地优先检索 Tantivy BM25、超时或故障时平滑降级回 PostgreSQL `pg_trgm` 的双轨高可用闭环；
>    - 在打分排序时引入 `segmentId ASC` 作为二级裁决键（Secondary Key），杜绝浮点微观 ULP 舍入差异引发的排序颠倒（Flapping Ranking）。

---

## 唯一待验证算法假设 (Sole Verifiable Hypothesis)

> **假设 (H-Phase18)**：在 DeepSeek 生成模型与阿里千问 1536 维向量基线下，通过实现：
> 1. **“基于 Rust 跨平台 SIMD (AVX2/NEON) 8路展开与 Rayon 数据分块调度的批量点积内核（`vecsim-jni`，支持堆外直接内存零拷贝）”**；
> 2. **“基于 gRPC + jieba-rs 中文倒排分词的 Tantivy 原生检索引擎（`tantivy-server`，配备磁盘持久化与单例 IndexWriterActor 节流提交）”**；
> 3. **“Java 侧 Netty gRPC 客户端（`TantivyClient`，支持心跳探活与 250ms 软超时 Fail-Open 降级）与 `KeywordRetriever` 双轨回退闭环”**；
> 能够实现：
> 1. 1536 维向量批量点积在 $N \ge 100$ 时相比 Java 标量计算吞吐提升 8~14 倍，相对纯标量计算的浮点误差绝对值 $\le 1.16 \times 10^{-5}$，排序单调性严格保持；
> 2. 关键词中文检索在万级切片下的 P99 延迟由数据库模糊匹配的 300~800ms 降低至 30ms 以内（降幅超 90%）；
> 3. 当 Tantivy 服务人为停止或模拟 3000ms 超时时，系统在 250ms 内触发 Fail-Open 软降级，自动回退至 PostgreSQL `pg_trgm`，`RagFallbackMonitor` 准确记录降级，主业务请求 100% 成功返回，系统 0 异常穿透。

---

## Proposed Changes

### Component 1: Rust SIMD 批量向量计算核重构 (backend/tools/vecsim-jni)

#### [MODIFY] [Cargo.toml](file:///Users/achilles/Documents/许子祺/Agent/backend/tools/vecsim-jni/Cargo.toml)
- 引入 `rayon = "1.10"`；
- 配置 `[profile.release]` 优化项：`opt-level = 3`, `lto = "thin"`, `codegen-units = 1`。

#### [MODIFY] [lib.rs](file:///Users/achilles/Documents/许子祺/Agent/backend/tools/vecsim-jni/src/lib.rs)
- 实现平台专属硬件加速派发：
  - x86_64：`dot_product_1536_avx2_fma`（8 路展开，单指令 8 个 float，24 次循环）；
  - aarch64：`dot_product_1536_neon`（4 路展开，单指令 4 个 float，96 次循环）；
  - 通用标量保底：`dot_product_scalar`；
- 批量分块调度：当 $N \ge 128$ 时启用 Rayon `par_chunks_exact`；
- 新增 JNI 堆外直接内存接口 `Java_tech_qiantong_qknow_module_kmc_service_rag_sim_VecSimNative_cosineDirect`，支持绝对零拷贝；
- 保留原有堆数组接口向后平滑兼容。

---

### Component 2: Tantivy 中文 BM25 检索引擎服务化闭环 (backend/tools/tantivy-server)

#### [MODIFY] [Cargo.toml](file:///Users/achilles/Documents/许子祺/Agent/backend/tools/tantivy-server/Cargo.toml)
- 引入 `jieba-rs = "0.7"`, `lazy_static = "1.4"`。

#### [MODIFY] [proto/search.proto](file:///Users/achilles/Documents/许子祺/Agent/backend/tools/tantivy-server/proto/search.proto)
- 对齐 Java 端与 Rust 端的 Protobuf 契约：
  - 增加 `Ping` 心跳探测 RPC；
  - 增加 `BatchIndex` 批量导入 RPC；
  - 增加 `DeleteDocument` 切片删除 RPC；
  - `SearchRequest` 增加 `knowledge_base_id` 与 `min_score`。

#### [MODIFY] [main.rs](file:///Users/achilles/Documents/许子祺/Agent/backend/tools/tantivy-server/src/main.rs)
- 集成 `JiebaTokenizer`，注册进 Tantivy `TokenizerManager`；
- 目录持久化：默认在 `./data/tantivy_index` 创建或打开物理索引目录；
- 实现 `IndexWriterActor`：利用 `tokio::sync::mpsc` 单例调度写入，双阈值（500 篇或 1000ms）执行 commit，杜绝锁冲突与 Segment 爆炸；
- 实现 `TantivySearch` gRPC 服务接口（`Ping`, `Search`, `IndexDocument`, `BatchIndex`, `DeleteDocument`）。

---

### Component 3: Java 业务层客户端与双轨容灾降级 (qknow-module-kmc)

#### [MODIFY] [TantivyClient.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/search/TantivyClient.java)
- 彻底废除 `HttpClient` 发送 HTTP POST 的错误实现；
- 引入基于 Netty 的 gRPC Channel（`ManagedChannel`），实现 HTTP/2 复用与长连接 KeepAlive；
- 引入心跳定时探活与健康状态原子标记（`AtomicBoolean isHealthy`）；
- 核心 `search` 方法严格设定 250ms Deadline 软超时：
  `blockingStub.withDeadlineAfter(250, TimeUnit.MILLISECONDS).search(req)`；
- 发生超时或连接故障时，记录 `RagFallbackMonitor` 并返回 `null`（Fail-Open）。

#### [MODIFY] [KeywordRetriever.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/KeywordRetriever.java)
- 注入 `TantivyClient`；
- 检索主流程首行优先进入 Tantivy BM25 分支；
- 若 Tantivy 未开启、不健康或返回 `null`，无感回退至原有 PostgreSQL `pg_trgm` + CJK 模糊匹配；
- 排序比较器引入 Secondary Key（`segmentId ASC`），彻底杜绝微小舍入误差引发的排序抖动。

#### [MODIFY] [VecSimNative.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/sim/VecSimNative.java)
- 新增 `safeCosineDirect` 堆外内存接口，保留原堆内接口平滑兼容。

---

### Component 4: 专属契约门禁测试与零退化回归 (tests)

#### [NEW] [Phase18NativeAccelerationAndTantivyGateTest.java](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase18NativeAccelerationAndTantivyGateTest.java)
- **契约测试 1：SIMD 点积精度与排序单调性验证**
  - 构造包含 1536 维千问特征的多组向量，验证 SIMD 计算与纯 Java 标量计算的绝对误差 $\le 1.16 \times 10^{-5}$；
  - 验证对于相似度差异 $> 10^{-4}$ 的向量对，SIMD 排序结果与理论基准完全保序一致。
- **契约测试 2：SIMD 吞吐性能与加速比验证**
  - 在 $N = 1000$ 批量切片下，验证 SIMD 向量核单次耗时稳定在亚毫秒级，计算加速比显著超越基线。
- **契约测试 3：Tantivy BM25 中文倒排检索有效性**
  - 模拟多篇中文文档索引，使用中文关键词检索，验证基于 Jieba 分词的 BM25 打分准确性与 `knowledge_base_id` 租户硬隔离。
- **契约测试 4：TantivyClient 250ms 软超时 Fail-Open 降级验证**
  - 模拟 gRPC 服务不可用或超时，验证客户端在 250ms 内触发软降级返回 null，并在 `RagFallbackMonitor` 产生降级记录。
- **契约测试 5：KeywordRetriever 端到端双轨容灾验证**
  - 验证 Tantivy 正常时优先命中 BM25 结果；Tantivy 异常时无感降级回 PostgreSQL `pg_trgm`，主业务请求 100% 成功。

---

## Verification Plan

### Automated Tests
1. **专属门禁契约测试**：
   ```bash
   bash run-with-java21.sh ./mvnw test -Dtest=tech.qiantong.qknow.rag.eval.Phase18NativeAccelerationAndTantivyGateTest
   ```
2. **全库全量防退化回归测试（确保 736+ 项单测全部 100% 绿灯通过，0 失败，0 错误）**：
   ```bash
   bash run-with-java21.sh ./mvnw test -pl backend/tests
   ```
3. **Rust 原生单元测试与编译验证**：
   ```bash
   cd backend/tools/vecsim-jni && cargo test --release
   cd backend/tools/tantivy-server && cargo test --release
   ```
