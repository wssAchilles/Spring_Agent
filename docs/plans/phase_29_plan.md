# Phase 29 实施方案：知识图谱深度语义推理与子图神经符号混合图 RAG (GraphRAG 2.0 / Neuro-Symbolic Hybrid Graph RAG)

> **文档状态**：Designed / Decision-Complete（待用户明确授权后启动 TDD 编码）  
> **依据规范**：`AGENTS.md` Research-to-Implementation Gate | `docs/plans/00_master_index.md` | `plans/RAG长期优化链路-v2.md`  
> **前置调研**：[`docs/plans/phase_29_academic_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_29_academic_report.md) (学术理论与收敛性证明) & [`docs/plans/phase_29_industrial_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_29_industrial_report.md) (工业架构与避坑对标)  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维，单位超球面 $\mathbb{S}^{1535}$ 归一化内积余弦度量）；绝无本地大模型与 OpenAI API；唯一编译运行环境为 Java 21 隔离虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

## 一、目标与唯一待验证假设 (Sole Falsifiable Hypothesis)

### 1.1 现状与痛点剖析
1. **超级节点（Supernode）引发的查询雪崩与 OOM 风险**：
   当前 `GraphRagRetriever.java` 中的 Cypher 查询直接执行 `OPTIONAL MATCH (e)-[*1..%d]-(n:Entity)` 变长模式，完全没有出入度检查与截断保护。一旦命中通用概念超级节点（如“系统”、“管理”），在图遍历展开时会触发指数级路径爆炸（$O(D^2)$，度数 $D=10^4$ 时路径数达 $10^8$），拖垮 Neo4j 的 Bolt 连接池并引发 JVM 堆内存 OOM 崩溃。
2. **图结构价值损失与孤立切片割裂**：
   当前 `GraphRagRetriever` 遍历图后仅提取 `segmentIds` 反查数据库，图本身蕴含的强因果拓扑谓词（如“导致”、“前置依赖”、“调用异常”等符号路径事实）被彻底丢弃，大模型依然只能看到孤立文本切片，缺乏显式因果推理链。
3. **无约束图扩散伤害事实性记忆（Factual Dilution Paradox）**：
   如 HippoRAG 2 揭示的严谨现象，无约束的随机游走将漫反射到大量拓扑相连但语义弱相关的事实，塞满上下文窗口，严重干扰 DeepSeek 模型的注意力机制，导致事实性幻觉率攀升（基线 $\ge 32.0\%$）。
4. **缺乏层次化社区与增量维护机制**：
   当前 `GraphCommunityService.java` 仅支持单一尺度的扁平社区划分，且每次检测均尝试全图投影，不仅耗时昂贵，更缺乏 L0 宏观领域（3~8个）与 L1 微观实体簇（5~15实体/簇）的分层抽象，缺乏版本哈希增量缓存，极易导致大模型 API 账单失控。

### 1.2 唯一待验证假设 (H-PHASE29-001)
> **核心假设**：在检索与推理链路中引入“限制 1~2 跳且带度数截断（Degree Cutoff $\le 30$）的因果推理链抽取器”，结合“千问 1536 维向量 Seed 定位 + 神经符号逻辑门控自适应 PPR 局部扩散”，并将抽取的结构化因果谓词链与基于版本指纹增量缓存的层次化社区摘要（L0/L1）融合入 20KB 上下文预算中：
> 1. 在遭遇超级节点（度数 $> 1000$）时，Cypher 路径查询耗时稳定在 $\le 15\text{ ms}$ 内，遍历节点数严格受限在 $\le 50$，彻底杜绝 Neo4j OOM；
> 2. 神经符号逻辑门控（关系类型硬过滤 + 余弦阈值截断）使得多跳事实噪声呈指数衰减（定理 2.1 闭环），将跨实体多跳推理的事实性幻觉率从基线 $\ge 32.0\%$ 压制至 $\le 4.5\%$；
> 3. 端到端多跳因果问答准确率（F1 / Exact Match）提升 $\ge 25.0\%$，且当 Neo4j 或 GDS 不可用时具备毫秒级无感 Fail-Open 平滑降级能力。

---

## 二、架构基线与设计原则 (Architecture Baseline & Guiding Principles)

1. **唯一生成模型**：DeepSeek API（`deepseek-chat` / `deepseek-reasoner`，流式 SSE 输出）。
2. **唯一向量模型**：阿里千问 (Qwen) Embedding（1536 维，单位超球面 $\mathbb{S}^{1535}$，内积余弦度量）。
3. **Java 21 隔离环境**：所有构建与测试命令统一显式前缀 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
4. **严格预算门禁**：全局上下文预算严格锁定 20KB（约 6500 汉字），其中：
   - Section A (因果事实链)：上限 5KB（25%）；
   - Section B (层次社区摘要)：上限 3KB（15%）；
   - Section C (精排正文切片)：上限 12KB（60%），保留 Parent-Child 动态优雅降级。
5. **Fail-Open 高可用原则**：任何图数据库异常、GDS 缺失或查询超时（50ms 熔断），均毫秒级降级为“PostgreSQL 倒排匹配 + 纯向量检索”，保障业务 0 报错。

---

## 三、核心组件与数据流架构设计 (Core Design & Data Flow)

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                用户查询 (Natural Language Query)                                 │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│          第一层：种子实体双路召回 (Seed Localization via Qwen 1536d + Keyword)                   │
│          - 千问 1536 维向量检索 + 关键词匹配快速定位 Top-5 核心 Seed 实体                         │
└────────────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                                 │ Top Seed Nodes
                                                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│          第二层：神经符号逻辑门控与局部 PPR 图扩散 (Adaptive Neuro-Symbolic PPR)                 │
│          - 符号谓词硬过滤（剔除无关虚词边，保留 CAUSES / DEPENDS_ON / BELONGS_TO 等因果依赖）   │
│          - 连续千问向量测地线语义门控：g(e) >= tau_prune，阻断 Hub 节点漫反射 (Theorem 2.1)      │
│          - 局部 Banach 压缩映射不动点迭代，几何收敛率 O((1-alpha)^t) 极速收敛                    │
└───────────────────────┬──────────────────────────────────────────────────┬───────────────────────┘
                        │                                                  │
                        ▼                                                  ▼
┌───────────────────────────────────────────────┐  ┌───────────────────────────────────────────────┐
│ 跨实体多跳因果推理链抽取器                    │  │ 层次化社区发现与增量摘要引擎                  │
│ (MultiHopCausalPathExtractor)                 │  │ (HierarchicalCommunityService)                │
│ - Degree Cutoff <= 30 (彻底消除超级节点 OOM)  │  │ - L0 顶层宏观全局摘要 (3~8 个全局领域)        │
│ - 严格限制 1~2 跳，提取结构化事实链:          │  │ - L1 局部微观实体簇摘要 (5~15 紧密实体簇)     │
│   "[实体A] --[因果/依赖]--> [实体B] --[属于]..." │  │ - 版本拓扑哈希 + LRU 缓存，增量局部更新       │
└───────────────────────┬───────────────────────┘  └───────────────────────┬───────────────────────┘
                        │ 结构化事实链 (<= 5KB)                            │ 宏观概念背景 (<= 3KB)
                        └───────────────────────┬──────────────────────────┘
                                                │
                                                ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│          子图模式 Pareto 双目标神经符号重排器 (NeuroSymbolicGraphRanker)                         │
│          - 对数可加性得分: Phi(P) = sum ln w(r) + sum beta ln cos(e_v, q)                        │
│          - 防环路冗余消除定理 (Loop Redundancy Elimination): Pareto 前沿剪除非简单路径环路       │
└───────────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                                │
                                                ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│          GraphRAG 2.0 统一自适应装配器 (GraphRag2Coordinator)                                    │
│          - 20KB 全局预算硬门禁切分 (5KB Causal + 3KB Community + 12KB Segments)                  │
│          - 毫秒级 Fail-Open 降级保护 (Neo4j GDS 缺失或超时自动安全回退)                          │
└───────────────────────────────────────────────┬──────────────────────────────────────────────────┘
                                                │
                                                ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│             DeepSeek API (deepseek-chat / deepseek-reasoner 流式输出高可信最终回答)              │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、核心代码组件落地规划 (Component Specifications)

### 1. 层次化社区数据模型与服务 (`HierarchicalCommunityService.java`)
- 位于：`backend/qknow-module-kg/qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/`
- **两级分层设计**：
  - `L0_MACRO_GLOBAL`：全局顶层领域（$\gamma_0 = 0.05$），3~8 个宏观社区；
  - `L1_MICRO_CLUSTER`：微观实体簇（$\gamma_1 = 1.0$），每个包含 5~15 个紧密实体。
- **增量版本指纹与 LRU 缓存**：
  - 社区摘要以 `Hash(workspaceId + communityId + entityFingerprints)` 为 Key 缓存；
  - 仅对发生边变更的局部受影响实体更新 L1 归属并使缓存失效，避免全图重跑 Leiden 与 DeepSeek API 账单爆炸；
  - 提供 `isGdsAvailable()` 心跳探针与无 GDS 时的内存模拟图降级。

### 2. 跨实体多跳因果推理链抽取器 (`MultiHopCausalPathExtractor.java` & `MultiHopCausalPathExtractorImpl.java`)
- 位于：`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/`
- **超级节点防御与度数截断**：
  - 严格限制 1~2 跳遍历（`MAX_HOPS = 2`）；
  - 单节点出入度截断 `DEGREE_CUTOFF = 30`，Cypher 模板中硬编码 `WHERE size((s)--()) <= 30 AND size((m)--()) <= 30`；
  - 候选子节点单点扩展数量通过 `LIMIT 30` 截断，从源头杜绝路径指数爆炸。
- **结构化谓词格式化**：
  - 输出 `CausalPathFact` 对象，格式化为大模型高可读的事实文本：
    `"[实体A] --[因果/依赖]--> [实体B] --[部署于]--> [实体C] (置信度: 0.92)"`。

### 3. 神经符号逻辑门控图扩散与重排器 (`NeuroSymbolicGraphRanker.java` & `NeuroSymbolicGraphRankerImpl.java`)
- 位于：`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/`
- **神经符号逻辑门控（Theorem 2.1 落地）**：
  - 关系类型硬过滤：只保留因果（CAUSES, TRIGGERS）、依赖（DEPENDS_ON, CALLS）、从属（BELONGS_TO, LOCATED_IN）等谓词；
  - 阿里千问 1536 维超球面语义门控：$g(e) \ge \tau_{\text{prune}} = 0.60$，非相关边权重清零，彻底斩断漫反射通道；
- **局部 Banach 压缩映射 PPR 求解**：
  - 阻尼因子 $1 - \alpha = 0.85$，迭代 15~20 轮达到收敛残差 $< 10^{-4}$；
- **防环路冗余消除（Loop Redundancy Elimination）**：
  - 识别路径中的重复节点，在 Pareto 双目标（语义相关性 + 符号紧凑度）前沿上执行剪环优化，保留最简非支配路径。

### 4. GraphRAG 2.0 统一编排与自适应装配器 (`GraphRag2Coordinator.java`)
- 位于：`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/`
- **20KB 动态预算切分**：
  - Section A (因果事实链): 5KB (25%);
  - Section B (层次社区摘要): 3KB (15%);
  - Section C (正文切片): 12KB (60%)。
- **毫秒级 Fail-Open 降级**：
  - 捕获任何图遍历超时或异常，自动清空图提示段，将完整预算退还给切片正文与 Parent-Child 扩展，保障系统 99.99% 高可用。

---

## 五、TDD 契约测试用例设计 (`Phase29GraphRag2ContractTest.java`)

在 `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase29GraphRag2ContractTest.java` 中构建 10 项专属契约测试：

1. `contract01_supernodeDegreeCutoff_preventsCombinatorialExplosion`：
   验证当种子实体或中介节点度数 $> 1000$（超级节点）时，抽取器在 `DEGREE_CUTOFF = 30` 约束下严格限制扩展度数，查询耗时 $\le 15\text{ms}$，返回路径数不超过设定阈值，彻底阻断组合爆炸。
2. `contract02_causalPathExtraction_formatsStructuredReasoningChains`：
   验证 1~2 跳因果路径被正确抽取并转换为标准结构化事实链文本（包含源实体、谓词、中介实体、目标实体与综合置信度），无空指针与非法格式。
3. `contract03_hierarchicalCommunity_buildsL0AndL1TreePartitions`：
   验证层次化社区服务在给定拓扑下成功生成 L0 宏观领域（大颗粒度）与 L1 微观实体簇（细粒度）两级社区结构，且两级社区的实体覆盖率满足包含关系。
4. `contract04_communityIncrementalCache_invalidatesOnlyDirtyClustersOnUpdate`：
   验证局部实体新增或边变更时，仅影响对应 L1 微观社区的拓扑哈希与缓存脏位，其余 90% 以上无关社区摘要缓存严格命中，单次更新大模型 API 消耗下降 90% 以上。
5. `contract05_neuroSymbolicGating_suppressesSemanticDiffusionAndHallucination`：
   验证弱语义或无因果关系的通用边被神经符号门控（$\tau_{\text{prune}}$ + 关系谓词过滤）硬剪枝，PPR 概率质量集中于因果紧密实体，杜绝漫反射。
6. `contract06_pprConvergence_reachesStationaryDistributionGeometrically`：
   验证列随机归一化矩阵在 Banach 压缩映射迭代下，残差单调几何级数衰减（$L = 1 - \alpha = 0.85$），在 $\le 20$ 轮内达到稳定分布。
7. `contract07_loopRedundancyElimination_prunesCyclicPathsOnParetoFrontier`：
   验证存在回路的推理路径（如 A $\to$ B $\to$ C $\to$ B $\to$ D）被防环定理正确剪除为无环路径（A $\to$ B $\to$ D），在语义信息密度等价的前提下符号紧凑度显著提升。
8. `contract08_adaptiveContextBudget_enforces20KbGlobalHardLimitWithSections`：
   验证全局装配器在极端高并发或大图结果输入下，输出总字符严格控制在 20KB 预算内，且 Section A (因果链)、Section B (社区摘要) 与 Section C (切片) 满足 25% / 15% / 60% 的配额比例。
9. `contract09_failOpenResilience_degradesSeamlesslyWhenNeo4jOrGdsUnavailable`：
   模拟 Neo4j 连接不可用或 GDS 插件缺失场景，验证协调器在 $\le 5\text{ms}$ 内平滑捕获并触发 Fail-Open 降级，返回纯切片上下文并标记 `isDegraded=true`，0 异常漏抛。
10. `contract10_endToEndDualLevelRetrieval_servesMultiHopCausalQuery`：
    端到端验证用户复杂多跳因果查询场景，系统协同召回核心实体、生成结构化因果链、注入宏观社区背景，端到端装配耗时 $\le 50\text{ms}$。

---

## 六、最小实现文件集合与范围约束

### 1. 明确允许新增/修改的文件清单
- **核心接口与 DTO**：
  - `backend/qknow-module-kg/qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/HierarchicalCommunityService.java` [NEW]
  - `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/MultiHopCausalPathExtractor.java` [NEW]
  - `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/NeuroSymbolicGraphRanker.java` [NEW]
  - `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/GraphRag2Coordinator.java` [NEW]
- **核心算法实现与服务**：
  - `backend/qknow-module-kg/qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/HierarchicalCommunityServiceImpl.java` [NEW]
  - `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/MultiHopCausalPathExtractorImpl.java` [NEW]
  - `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/graph/NeuroSymbolicGraphRankerImpl.java` [NEW]
- **契约测试**：
  - `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase29GraphRag2ContractTest.java` [NEW]
- **文档与配置**：
  - `docs/plans/phase_29_plan.md` [NEW]
  - `docs/plans/00_master_index.md` [MODIFY: 推进阶段进度]

### 2. 明确禁止修改的边界
- 严禁修改任何用户主机的全局环境变量（系统全局保持 Java 17）；
- 严禁修改父 POM 或引入与 Python/ONNX/Torch 相关的重型本地依赖；
- 严禁修改现有的 822 项既有通过测试用例及断言逻辑，确保 100% 零退化。

---

## 七、实施步骤与验证命令

### 1. 执行步骤
1. **步骤一**：编写 `Phase29GraphRag2ContractTest.java`，定义 10 项核心契约测试（遵循 TDD，初始状态契约代码编译通过但待实现逻辑）；
2. **步骤二**：实现 `HierarchicalCommunityService.java` 与 `HierarchicalCommunityServiceImpl.java`，构建 L0/L1 社区树与增量指纹缓存；
3. **步骤三**：实现 `MultiHopCausalPathExtractor.java` 与 `MultiHopCausalPathExtractorImpl.java`，固化度数截断 $\le 30$ 与 1~2 跳因果链抽取；
4. **步骤四**：实现 `NeuroSymbolicGraphRanker.java` 与 `NeuroSymbolicGraphRankerImpl.java`，固化神经符号门控与 Banach 压缩映射 PPR；
5. **步骤五**：实现 `GraphRag2Coordinator.java`，实现 20KB 预算切分与 Fail-Open 降级闭环；
6. **步骤六**：运行 Phase 29 专属契约测试，确保 10/10 绿灯通过；
7. **步骤七**：运行全量后端防退化测试（822 + 10 = 832 项用例全绿）及前端生产打包验证（`npm run build:prod` 0 错误）。

### 2. 验证命令
```bash
# 1. 编译相关依赖模块并执行 Phase 29 专属契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -am -pl tests -Dtest=Phase29GraphRag2ContractTest -Dsurefire.failIfNoSpecifiedTests=false

# 2. 运行全量防退化回归测试 (832 项全绿)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -am -pl tests

# 3. 运行前端生产打包构建
cd frontend && npm run build:prod
```

---

## 八、准入与授权确认 (Gate Sign-off)

- [x] 已追踪项目真实图检索路径并锁定唯一可证伪假设（H-PHASE29-001）；
- [x] 学术与工业双路调研报告已完整落盘（`phase_29_academic_report.md` 72KB + `phase_29_industrial_report.md` 40KB），收录 11 篇权威来源且未伪造状态；
- [x] 架构模型基线严格对齐（DeepSeek API 唯一生成 + 阿里千问 1536 维唯一向量 + Java 21 隔离环境）；
- [x] 形成包含 baseline、candidate、反事实/消融、防超级节点、预算门禁、失败码与停止条件的完整实施方案；
- [x] 明确最小修改文件集与严密测试验证命令。

**请等待用户明确授权确认后，方可启动 Phase 29 的 TDD 编码与执行。**
