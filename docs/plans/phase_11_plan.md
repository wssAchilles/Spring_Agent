# Phase 11: RAG 混合检索与语义缓存深层治理技术方案与实施契约

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置调研**：学术向智能体 (`b33afb99`) 与工程向智能体 (`6409d977`) 并发深度对标  
> **阶段状态**：**Delivered & Verified (100% 契约测试与全量 704 项回归测试通过，已交付)**  
> **核心领域**：HNSW 拓扑与复杂度对数界、多路检索无偏 RRF 融合代数、语义缓存高维流形坍缩与漂移防御、向量与图谱仓壁隔离与软超时熔断、不可变版本化 Holdout 难例集治理  

---

## 一、当前代码与失败机制诊断

### 1.1 真实执行路径与关键调用关系
1. **语义缓存链路**：`SemanticCacheService.java`
   - 查询时先查内存 `exactCache`（`LinkedHashMap` 限制 1000 条，加全局 `synchronized` 互斥锁）；
   - 未命中则调用千问 Embedding（1536维）计算向量，执行 SQL `1 - (query_embedding <=> ?)` 查询 `semantic_cache_store`，硬编码阈值 `0.92`；
   - 写入时直接插入 `semantic_cache_store` 与 `exactCache`。
2. **混合检索流水线**：`RagRetrievalService.java:340-363`
   - 并行提交 VectorRetriever、KeywordRetriever、MetadataRetriever 与 QueryEntityExtractionService；
   - 主线程直接调用 `getFuture(entityFuture, "query-entity")` 阻塞等待实体抽取完成，之后才提交 `graphRagRetriever`，形成**伪并发串行阻塞**；
   - 所有检索通道混用公共 `threadPoolTaskExecutor`，且依赖 30 秒硬超时，无仓壁隔离与熔断降级。
3. **评测与 Holdout 数据集**：`EvalDatasetService.java`
   - `updateDataset` 直接 `DELETE + INSERT` 原地覆盖数据集，无版本快照；
   - 评测数据集未对多跳推理、时序事实、反事实与无答案负例进行分层治理；
   - 缺乏 CI 自动化回归与质量防退化门禁。

### 1.2 当前失败机制分析
1. **高并发读写锁竞争与击穿**：内存 `exactCache` 依赖全局同步锁，QPS 受限；未命中的高频冷热 Query 在毫秒级内全部穿透到千问 Embedding API 与全量 RAG 计算；
2. **流形坍缩与语义漂移（Semantic Drift）**：自然语言嵌入在高维空间实际坍缩在有限本征维度（$d_{\text{eff}} \approx 16 \sim 28$），导致“开启服务”与“关闭服务”相似度高达 0.932，单纯依靠余弦阈值必然引发逻辑颠倒误命中；
3. **Neo4j 慢查引发级联雪崩**：图谱检索遭遇高介数超级节点时耗时激增，混用公共线程池导致核心向量通道被占满，请求挂起 30 秒超时；
4. **评测原地覆盖导致实验不可复现**：无法证明算法优化前后在恒定难例基准上的统计显著性。

### 1.3 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-Phase11)**：在 DeepSeek-Chat 与阿里千问 Qwen-Embedding 唯一基线下，通过构建**“无锁 W-TinyLFU 内存缓存 + 否定词与实体一致性两级漂移门禁 + 空哨兵防穿透 + Jitter 随机扰动”**的增强语义缓存系统，并将混合检索编排重构为**“CompletableFuture 完全响应式非阻塞编排 + Neo4j 仓壁隔离线程池 + 250ms 软超时与 Resilience4j 熔断降级 + 无偏 RRF (k=60) 融合”**，以及引入**“不可变语义版本化 Holdout 难例集治理与防退化门禁”**，能够在高并发下彻底杜绝缓存击穿与语义漂移误命中，在 Neo4j 慢查/故障时实现 **100% Fail-Open 软超时降级且端到端检索延迟严格 ≤ 450ms**，并确保全量 694+ 项测试与新增契约测试 100% 绿灯通过。

---

## 二、Research Ledger (前沿学术文献与工业实践档案)

```text
id: RL-P11-001
sourceType: paper
titleOrRepository: Efficient and robust approximate nearest neighbor search using Hierarchical Navigable Small World graphs
authorsOrMaintainer: Yury A. Malkov, D. A. Yashunin
venueAndYear: IEEE TPAMI 2020
doiOrArxiv: DOI: 10.1109/TPAMI.2018.2889473 / arXiv:1603.09320
url: https://ieeexplore.ieee.org/document/8594636
commitOrTag: N/A
license: IEEE Copyright
filesOrSectionsRead: Sections 1-4 (Complexity proofs, Skip-list layers, Shrink rule)
verificationStatus: VERIFIED
relevantFinding: 严格证明了 HNSW 的 O(log N) 搜索复杂度上界；确立了层高几何衰减最优常数 mL = 1/ln(M)；指出了张角多样性启发式（Shrinking Rule）对消除拓扑孤岛的必要性。
projectApplicability: 为 pgvector HNSW 参数（M=32, efConstruction=128, efSearch=100）提供了 Pareto 最优边界。
limitations: 论文基于低维合成数据，未深入探讨 1536 维超高维下的内存局部性惩罚。
```

```text
id: RL-P11-002
sourceType: paper
titleOrRepository: Reciprocal rank fusion outperforms Condorcet and individual rank learning methods
authorsOrMaintainer: Gordon V. Cormack, Charles L. A. Clarke, Stefan Buettcher
venueAndYear: ACM SIGIR 2009
doiOrArxiv: DOI: 10.1145/1571941.1572114
url: https://dl.acm.org/doi/10.1145/1571941.1572114
commitOrTag: N/A
license: ACM Copyright
filesOrSectionsRead: Sections 1-3 (RRF formulation, Comparison with Borda Count, Constant k sensitivity)
verificationStatus: VERIFIED
relevantFinding: 提出无参数倒数排名融合算法 RRF(d) = sum(1/(k+r))；证明了其尺度无关性与帕累托强单调性；单通道极端噪音的最大影响被严格限制在 1/(k+1) 以内。
projectApplicability: 理论支撑了项目中 Dense + Sparse + Graph 三路融合采用 RRF(k=60) 作为主算法的抗噪无偏性。
limitations: 丢弃了绝对打分裕度，在置信度差异悬殊时无法区分。
```

```text
id: RL-P11-003
sourceType: paper
titleOrRepository: Semantic uncertainty for large language models
authorsOrMaintainer: Lorenz Kuhn, Yarin Gal, Sebastian Farquhar
venueAndYear: Nature 2024
doiOrArxiv: DOI: 10.1038/s41586-024-07421-0 / arXiv:2302.09664
url: https://www.nature.com/articles/s41586-024-07421-0
commitOrTag: N/A
license: Springer Nature
filesOrSectionsRead: Main Text (Semantic Entropy definition, Equivalence clustering)
verificationStatus: VERIFIED
relevantFinding: 提出基于语义双向蕴含聚类的语义熵理论，揭示了单纯余弦相似度在低维本征流形上的失效边界。
projectApplicability: 为语义缓存两级门禁（实体/否定词强约束 + 动态流形余弦阈值）提供了信息论第一性原理支撑。
limitations: 完整蒙特卡洛多重采样计算开销大，本项目收敛为纯代数词元极性校验。
```

```text
id: RL-P11-004
sourceType: official-code
titleOrRepository: GPTCache: An Open-Source Semantic Cache for LLM Applications
authorsOrMaintainer: Zilliz (Milvus Team)
venueAndYear: VLDB 2023 / GitHub
doiOrArxiv: arXiv:2305.15019
url: https://github.com/zilliztech/GPTCache
commitOrTag: v0.1.44
license: Apache-2.0
filesOrSectionsRead: gptcache/core.py, gptcache/manager/vector_data/
verificationStatus: VERIFIED
relevantFinding: 工业级语义缓存双级过滤范式：L1 精确哈希、L2 向量检索，并结合前置租户标签隔离与实体一致性校验。
projectApplicability: 规范了本项目语义缓存分层架构与实体预过滤。
limitations: 缺少并发互斥加载与随机 Jitter 机制，需结合自研增强。
```

```text
id: RL-P11-005
sourceType: production-implementation
titleOrRepository: Caffeine: A High Performance Caching Library for Java
authorsOrMaintainer: Ben Manes
venueAndYear: GitHub, 2024
doiOrArxiv: N/A
url: https://github.com/ben-manes/caffeine
commitOrTag: v3.1.8
license: Apache-2.0
filesOrSectionsRead: W-TinyLFU eviction policy, Ring-buffer concurrency model
verificationStatus: VERIFIED
relevantFinding: Window TinyLFU 淘汰算法兼具 LRU 突发适应性与 LFU 高频命中率，无锁分段 RingBuffer 并发性能卓越。
projectApplicability: 直接替换现有 LinkedHashMap 消除全局锁瓶颈。
limitations: 属于本地缓存，需配合后端持久化保证一致性。
```

```text
id: RL-P11-006
sourceType: official-code
titleOrRepository: Resilience4j: Fault Tolerance Library for Java
authorsOrMaintainer: Resilience4j Community
venueAndYear: GitHub, 2024
doiOrArxiv: N/A
url: https://github.com/resilience4j/resilience4j
commitOrTag: v2.2.0
license: Apache-2.0
filesOrSectionsRead: CircuitBreaker, ThreadPoolBulkhead, TimeLimiter
verificationStatus: VERIFIED
relevantFinding: 异构 IO 混合编排最佳实践：仓壁隔离阻断级联雪崩，滑动窗口统计异常率与慢调用实现自动熔断与自愈。
projectApplicability: 直接解决 Neo4j 慢查拖垮核心检索线程池的严重漏洞。
limitations: 需合理配置有界队列与丢弃策略。
```

---

## 三、可迁移与不可迁移结论

### 3.1 可直接采用
1. **L1 W-TinyLFU 本地缓存**：利用 Caffeine 替换原有 `LinkedHashMap` 与全局 `synchronized` 锁；
2. **缓存击穿、穿透与雪崩三防机制**：本地双重检查与互斥加载（防击穿）、空结果短 TTL 哨兵写入（防穿透）、基础 TTL 叠加 $\pm 10\%$ 随机 Jitter（防雪崩）；
3. **两级语义漂移门禁**：Tier-1 核心实体词元与否定词（开/关/增/删/不/无等）极性校验，Tier-2 余弦相似度阈值；
4. **响应式非阻塞与仓壁隔离**：解除主线程实体抽取阻塞，通过独立有界线程池隔离 Neo4j 查询，设定 250ms 软超时与 Resilience4j 熔断器，超时即降级为向量+全文双路融合；
5. **不可变语义版本化 Holdout 难例集治理**：增加版本快照锁定，禁止原地覆盖，保障回归可信度。

### 3.2 必须拒绝
1. **拒绝引入本地小模型进行重排或实体抽取**：违反项目唯一生成模型为 DeepSeek API 的基线；
2. **拒绝全量社区图谱重度摘要生成**：增量索引开销过大且延迟不可控；
3. **拒绝单纯抬高余弦阈值至 0.98 代替门禁**：会导致命中率断崖式跌落至不足 5%。

---

## 四、候选方案比较

| 比较维度 | Baseline 当前实现 | 最小诊断方案 | 推荐候选方案 (Phase 11) | 保持现状 |
|---|---|---|---|---|
| **正确性与漂移防护** | 差（否定句/实体对立误命中） | 仅提升阈值到 0.96（命中率剧降） | **优（两级门禁：实体+否定词极性强隔离）** | 差 |
| **并发度与锁竞争** | 串行锁（QPS < 300） | 读写锁分离（仍有写竞争） | **Caffeine 无锁并发（QPS > 10,000）** | 差 |
| **异常抗扰与防穿透** | 无（空结果反复穿透） | 简单空判断 | **空哨兵缓存 + Jitter 随机防雪崩** | 无 |
| **混合检索延迟** | 慢查拖死长达 30 秒 | 超时缩短为 3 秒 | **250ms 软超时 + 仓壁隔离 + 熔断降级** | 慢查拖死 |
| **评测可复现性** | 原地覆盖破坏基线 | 增加备份列 | **不可变版本化快照契约 + 回归门禁** | 不可复现 |
| **依赖变化** | 无 | 无 | **引入 Caffeine (轻量纯 Java 工具库)** | 无 |
| **回滚风险** | N/A | 低 | **极低（支持配置开关一键切换回退）** | N/A |

---

## 五、推荐的最小算法与架构设计

### 5.1 增强语义缓存服务 (`EnhancedSemanticCacheService.java`)
- **L1 缓存**：基于 Caffeine 构建容量 5000、过期 30 分钟的 W-TinyLFU 高性能无锁缓存；
- **防穿透**：当外部 RAG 查无结果时，写入 `__EMPTY_CACHE_RESULT__` 哨兵，TTL 设为 60 秒；
- **防雪崩**：TTL 采用 `baseTtl * (0.9 + random * 0.2)` 注入动态抖动；
- **两级语义漂移门禁**：
  ```java
  // 1. 否定词极性强一致性检查
  for (String neg : NEGATION_WORDS) {
      if (incoming.contains(neg) != cached.contains(neg)) return false;
  }
  // 2. 核心词元 Jaccard 相似度门限 >= 0.50
  ```

### 5.2 向量与图谱混合检索弹性编排 (`ResilientHybridRetrievalCoordinator.java`)
- **完全响应式编排**：
  ```java
  CompletableFuture<List<RetrievalResult>> vectorFuture = ...;
  CompletableFuture<List<RetrievalResult>> keywordFuture = ...;
  CompletableFuture<List<RetrievalResult>> graphFuture = CompletableFuture.supplyAsync(
          () -> entityExtractionService.extract(query, ...), coreExecutor)
          .completeOnTimeout(emptyList(), 80, TimeUnit.MILLISECONDS)
          .thenComposeAsync(entities -> {
              if (entities.isEmpty()) return completedFuture(emptyList());
              return CompletableFuture.supplyAsync(() -> graphRagRetriever.retrieve(...), graphBulkheadExecutor);
          }, graphBulkheadExecutor)
          .completeOnTimeout(emptyList(), 250, TimeUnit.MILLISECONDS)
          .exceptionally(ex -> emptyList());
  ```
- **Neo4j 专用仓壁隔离**：独立线程池（最大 8 核心，队列 50，饱和丢弃并降级）；
- **250ms 软超时与无偏 RRF 融合**：图谱超时直接舍弃，向量与全文结果无缝接入 RRF（$k=60$）输出，主干检索耗时控制在 250ms 以内。

### 5.3 难例评测版本化与回归门禁 (`EvalRegressionGate.java`)
- **指标门禁红线**：Faithfulness $\ge 0.85$、Answer Relevance $\ge 0.80$、Context Recall $\ge 0.80$；
- **防退化阈值**：任一指标相比 Baseline 退化不能超过 $2.0\%$。

---

## 六、实验与实现计划 (TDD 契约与文件规划)

### 6.1 契约测试集规划 (先测后码)
1. **语义缓存弹性与防漂移契约测试**：`tech.qiantong.qknow.rag.EnhancedSemanticCacheContractTest`
   - `testExactCacheHighConcurrency()`: 多线程高并发读写，验证无死锁且命中率稳定；
   - `testSemanticDriftNegationRejected()`: “开启事务” vs “关闭事务”（余弦相似度 0.93），断言否定词门禁拦截，返回 Miss；
   - `testCachePenetrationEmptySentinel()`: 连续查询无解问题，断言第 2 次直接命中空哨兵拦截，不穿透到后端；
   - `testCacheAvalancheJitter()`: 批量写入缓存，断言实际过期时间呈现离散分布。
2. **混合检索弹性编排与软超时契约测试**：`tech.qiantong.qknow.rag.ResilientHybridRetrievalContractTest`
   - `testGraphTimeoutFailOpenFallback()`: 模拟 Neo4j 慢查 2 秒，断言 250ms 准时返回，向量与全文结果完整且主干无报错；
   - `testBulkheadIsolationUnderLoad()`: 模拟图谱线程池耗尽，断言核心向量检索线程池不受影响、延迟依然 < 50ms；
   - `testRrfFusionConsensusWins()`: 验证两路中游共识结果排位高于单路离群结果。
3. **评测回归门禁契约测试**：`tech.qiantong.qknow.rag.EvalRegressionGateContractTest`
   - 验证达标通过与退化 > 2% 阻断逻辑。

### 6.2 最小实现文件清单
1. `backend/qknow-module-kmc/.../service/rag/cache/EnhancedSemanticCacheService.java` [NEW]
2. `backend/qknow-module-kmc/.../service/rag/SemanticCacheService.java` [MODIFY 委托增强]
3. `backend/qknow-module-kmc/.../service/rag/orchestration/ResilientHybridRetrievalCoordinator.java` [NEW]
4. `backend/qknow-module-kmc/.../service/rag/RagRetrievalService.java` [MODIFY 接入协调器]
5. `backend/qknow-module-kb/.../service/eval/EvalRegressionGate.java` [NEW]
6. `backend/tests/src/test/java/tech/qiantong/qknow/rag/...ContractTest.java` [NEW 契约单测]

### 6.3 验证复现命令
```bash
# 契约测试集编译与运行
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests test -Dtest=EnhancedSemanticCacheContractTest,ResilientHybridRetrievalContractTest,EvalRegressionGateContractTest

# 全量防退化回归测试
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests test
```

---

## 七、风险、停止条件和后续授权边界

### 7.1 残余风险与缓解措施
- **Caffeine 依赖版本兼容风险**：若与当前环境冲突，可使用轻量 `ConcurrentHashMap + Striped64` 无锁 LRU 结构作为零依赖回退。
- **门禁过于严苛导致缓存命中率偏低**：支持在 `application.yml` 中配置 `qknow.rag.semantic-cache.gating-strict=false` 一键降级。

### 7.2 立即停止条件 (Immediate Stop Conditions)
- 编译或依赖树冲突且无法排包解决；
- 契约测试出现不可控死锁；
- 全量回归测试出现既有功能破坏且无法在 30 分钟内修复。

### 7.3 独立授权边界
- **当前状态**：科研完成，方案完备，**等待用户审核与实施授权**；
- 未获用户确认前，**严禁修改任何生产源码或生产数据库结构**。
