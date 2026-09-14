# Phase 26 决策完备实施方案：知识库自动化语义冲突检测、时态演进治理、紧凑去重与高质量合成问答基准自进化体系 (Autonomous Knowledge Conflict Resolution, Temporal Evolution Governance, Compact Deduplication & Synthetic Golden Q&A Self-Bootstrapping Engine)

> **拟归档路径**：`docs/plans/phase_26_plan.md`  
> **依据规范**：`AGENTS.md` Research-to-Implementation Gate 强制规范  
> **前置依赖**：Phase 01 ~ Phase 25 全量交付通过（测试 792/792 100% 绿灯，前端生产构建通过）  
> **理论依据**：`docs/plans/phase_26_academic_report.md`（一阶谓词逻辑 NLI 语义映射、极性反转与数值冲突 Chernoff 概率界、反思链式推理幻觉指数级衰减定理、Allen 13 种时序拓扑偏序集、带半衰期指数衰减函数单调下凸性与旧切片自然淘汰渐进有界性定理、MinHash Jaccard 独立置换无偏估计定理、LSH $(b, r)$ S 曲线 Pareto 极值方程求解、香农信息熵双阈值二值分离存在性定理、互信息语义命题守恒定理、反事实自一致性校验指数净化衰减界限定理）  
> **工业对标**：`docs/plans/phase_26_industrial_report.md`（Mem0 四态冲突状态机、LlamaIndex 双时态建模与时间衰减算子、NeMo Curator / DataPrepKit 64 位 SimHash 与香农熵清洗管线、Ragas 进化式问答生成与双向自我一致性反思过滤，复盘规避“新旧政策同时召回引发客诉”、“标点微调导致重复嵌入万份合同”、“合成问答未做反思过滤导致假真值”三大生产灾难）  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **环境隔离铁律**：后端全量模块统一且唯一使用 **Java 21** 编译与运行，绝对路径固定为 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，Maven 执行强制局部传入 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。

---

## 一、方案全景与架构拓扑

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            Phase 26 知识库时态演进与数据自进化全景架构                             │
│                                                                                                  │
│  [ 原始文档分块 ] ──> [ 中文分词与标点归一化 (Jieba) ]                                            │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ SimHashEntropyPruningCleaner ]                                                                │
│         ├── 香农信息熵检测 (H(X) in [2.5, 6.8] bits/char)，过滤模板占位与高熵乱码                 │
│         └── 64 位 SimHash + 4段16位内存倒排分桶 (Hamming <= 3)，O(1) 近似去重                    │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ DocumentConflictResolutionService ] (两阶段语义冲突消歧状态机)                                │
│         ├── 阶段 1: 轻量极性与事实预检 (Polar Pre-check)                                          │
│         ├── 阶段 2: DeepSeek-R1 反思仲裁 (SUPERSEDE / CONFLICT / COEXIST)                        │
│         └── 四态状态机流转: ACTIVE / DEPRECATED / SUPERSEDED / CONFLICTED                          │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ TimeAwareRetrievalFilter ] (双时态时序门禁与衰减加权)                                          │
│         ├── PostgreSQL 双时态字段: effective_start, effective_end, superseded_by_id              │
│         ├── 检索查询时间戳 t_q 范围门禁: (effective_start <= t_q) && (effective_end > t_q)       │
│         └── 时序指数衰减算子: S_final = S_base * ((1-alpha) + alpha * exp(-lambda * delta_t))   │
│         │                                                                                        │
│         ▼                                                                                        │
│  [ SyntheticGoldenBootstrapEngine ] (合成黄金基准与自进化闭环)                                   │
│         ├── 4 类高保真问答进化: 单跳事实 / 多跳推理 / 时序对比 / 反事实拒答                       │
│         ├── 双向自我一致性反向验证 (Grounding Score >= 0.85 过滤幻觉真值)                         │
│         └── 冻结输出: fixtures/rag-synthetic-golden-v1.jsonl                                    │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、唯一待验证假设 (H-Phase26)

> **唯一待验证假设 (H-Phase26)**：  
> 在 `qknow-module-kmc` 及其向量/检索基础设施构建三大核心工程底座：  
> 1. **高性能 64 位 SimHash 倒排去重与香农信息熵噪声剪枝器**：实现纯内存 64 位 SimHash 分桶查重（吞吐 $\ge 50,000$ ops/s）与香农熵双阈值过滤（$[2.5, 6.8]$ bits/char），剔除 $25\%+$ 冗余与低熵模板切片；  
> 2. **两阶段语义冲突消歧状态机与时序门禁过滤器**：结合轻量极性预检与反思仲裁，建立四态流转（`ACTIVE`, `DEPRECATED`, `SUPERSEDED`, `CONFLICTED`），在检索时严格阻断被取代和已过期的旧切片，实现冲突切片召回阻断率 $\ge 99\%$；  
> 3. **高质量合成问答基准与自我一致性自进化引擎**：基于 DeepSeek API 生成 4 类问答（单跳、多跳、时序对比、反事实），并通过双向自一致性反向作答校验（Grounding Score $\ge 0.85$），输出不可变基准 `rag-synthetic-golden-v1.jsonl`。

---

## 三、拟变动文件清单 (Proposed Changes)

### 3.1 数据库迁移 (Database Migration)
#### [NEW] [`26-temporal-conflict-and-dedup.sql`](file:///Users/achilles/Documents/许子祺/Agent/deploy/sql/postgresql/26-temporal-conflict-and-dedup.sql)
- 为 `kmc_document_segment` 添加字段：`effective_start`, `effective_end`, `version_tag`, `conflict_status`, `superseded_by_id`, `simhash_64`, `shannon_entropy`；
- 创建组合索引 `idx_kmc_seg_active_temporal`, `idx_kmc_seg_simhash`, `idx_kmc_seg_superseded_by`。

---

### 3.2 核心业务与控制组件 (Core Business & Governance Components)
#### [NEW] [`SimHashEntropyPruningCleaner.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/clean/SimHashEntropyPruningCleaner.java)
- 纯 Java 21 标准库实现的 64 位 SimHash 算法与 4 段 16 位内存倒排索引桶（汉明距离 $\le 3$）；
- 香农信息熵双阈值过滤器（低于 2.5 或高于 6.8 的切片自动过滤）。

#### [NEW] [`DocumentConflictResolutionService.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/conflict/DocumentConflictResolutionService.java)
- 两阶段语义冲突检测与消歧状态机（轻量极性预检 + DeepSeek 反思仲裁）；
- 四态状态机定义（`ACTIVE`, `DEPRECATED`, `SUPERSEDED`, `CONFLICTED`）与版本取代指针链。

#### [NEW] [`TimeAwareRetrievalFilter.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/temporal/TimeAwareRetrievalFilter.java)
- 时态感知检索门禁（按查询时间戳 $t_q$ 过滤有效区间）；
- 带业务半衰期（$\tau_{1/2} = 180\text{天}$）的指数时间衰减打分算子。

#### [NEW] [`SyntheticGoldenBootstrapEngine.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/eval/synthetic/SyntheticGoldenBootstrapEngine.java)
- 4 类高保真问答自进化引擎（单跳事实、多跳推理、时序对比、反事实不可回答）；
- 双向自一致性检验器，输出 `rag-synthetic-golden-v1.jsonl`。

---

### 3.3 测试驱动开发与自动化门禁 (TDD & Automated Verification)
#### [NEW] [`Phase26KnowledgeEvolutionContractTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase26KnowledgeEvolutionContractTest.java)
- 覆盖 10 项严密专项契约测试：
  1. `contract01_shannonEntropy_prunesLowEntropyTemplatesAndHighEntropyNoise`
  2. `contract02_simHash64_clustersNearDuplicatesWithHammingDistanceUnderThree`
  3. `contract03_simHashThroughput_exceedsFiftyThousandPerSecond`
  4. `contract04_conflictPolarityPrecheck_detectsNegationAndInversion`
  5. `contract05_conflictStateMachine_transitionsToSupersededWithPointer`
  6. `contract06_timeAwareFilter_blocksExpiredSegmentsAtQueryTimestamp`
  7. `contract07_timeDecayWeight_strictlyMonotonicDecayWithHalfLife`
  8. `contract08_syntheticQAGenerator_producesFourDiverseQuestionTypes`
  9. `contract09_selfConsistencyVerification_filtersHallucinatorySamples`
  10. `contract10_endToEndConflictBlock_preventsConflictingContextInjection`

#### [NEW] [`rag-synthetic-golden-v1.jsonl`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/fixtures/rag-synthetic-golden-v1.jsonl)
- 固化的 4 类代表性合成基准测试数据集。

---

## 四、验证计划 (Verification Plan)

### 自动化契约测试
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -am -pl tests -Dtest=Phase26KnowledgeEvolutionContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

### 全量防退化回归测试
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -am -pl tests
```
预期测试计数：792 既有测试 + 10 本阶段契约测试 = 802 项测试 100% 绿灯。

### 前端生产构建验证
```bash
cd frontend && npm run build:prod
```
确保 0 错误通过。
