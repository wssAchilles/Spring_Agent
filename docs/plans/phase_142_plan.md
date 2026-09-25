# Phase 142 实施方案与契约设计：多智能体自适应分层记忆巩固、海量情节图谱遗忘修剪与多模态反思推演中枢
> **经三方 ESWA 顶刊专业智能体（理论专审员、实证专审员、工业管理专审员）法医级交叉审计与裁决重构后版本**

---

## 一、方案核心目标与唯一算法假设

### 1.1 唯一待验证假设 (Hypothesis H-142)
在多智能体长程认知协作与演化场景下，构建“基于阿里千问 1536 维超球面的增量质心分层情节记忆巩固引擎 (`HierarchicalEpisodicMemoryConsolidator`) + 基于对数阻尼艾宾浩斯衰减、DAG因果拓扑豁免与三级冷备隔离的情节图谱自适应修剪器 (`AdaptiveEpisodicForgettingPruner`) + 纯 Java 21 Record 格式记忆巩固存证凭单 (`EpisodicMemoryConsolidationReceipt`)”，能够实现：
1. **语义抽象保真与高效压缩**：微观情节单点重构保真度满足测地约束 $\min_i \langle \mathbf{v}_i, \mathbf{c}_k \rangle \ge 0.80$（严格满足 Lemma 142.1 信息保真引理，单点失真 $\le 0.20$），文本与向量空间聚类压缩率 $\ge 80.0\%$，单次增量质心巩固耗时 $\le 3.0\text{ms}$；
2. **因果拓扑保全与三级冷备**：基于对数阻尼与双曲有界拓扑度的保测度保留函数，对陈旧记忆实施软淘汰与三级冷备隔离，端到端决策因果路径保持率（Causal Path Reachability Ratio, CRR）保持 $\ge 95.0\%$，单次修剪耗时 $\le 2.0\text{ms}$；
3. **关键判例显式永久免疫**：关键业务判例（如 HikariCP 调优、安全审计）享有显式永久免疫钉扎（`isPinned == true`）与两跳拓扑辐射保护，彻底杜绝循环辩论死循环伪免死与内存无界泄漏（严格满足 Lemma 142.2 核心因果拓扑可达性不灭引理）；
4. **硬隔离与防泄漏契约**：严格实现租户间物理集合隔离与智能体角色私有知情权边界，跨租户跨角色数据泄漏率为 0.0%；
5. **密码学不可变存证**：签发纯 Java 21 Record 格式凭单，规范化存证巩固主题哈希与修剪摘要，SHA-256 常量时间自验真率 100.0%（支持反向篡改检测）。

---

## 二、理论引理与形式化数学模型 (Formally Verified Lemmas)

### 2.1 引理 142.1：记忆巩固信息保真引理 (Episodic Consolidation Fidelity Lemma)
设微观情节向量集合 $\mathcal{V}_k = \{\mathbf{v}_1, \dots, \mathbf{v}_{n_k}\} \subset \mathbb{S}^{1535}$，满足超球面测地半径聚类约束，即对质心 $\mathbf{c}_k = \frac{\sum_{i=1}^{n_k} \mathbf{v}_i}{\|\sum_{i=1}^{n_k} \mathbf{v}_i\|_2}$，满足单点测地余弦相似度下界 $\min_{i \in [n_k]} \langle \mathbf{v}_i, \mathbf{c}_k \rangle \ge \alpha$（$\alpha = 0.80$）。则：
1. 簇内平均余弦保真度 $\bar{F}_k = \frac{1}{n_k} \sum_{i=1}^{n_k} \langle \mathbf{v}_i, \mathbf{c}_k \rangle = \sqrt{\rho_k} \ge \alpha$；
2. 任意微观情节在宏观元规则质心投影下的单点信息重构损失严格有界：
   $$\forall \mathbf{v}_i \in \mathcal{V}_k, \quad \mathcal{L}_{recon}(\mathbf{v}_i; \mathbf{c}_k) \triangleq 1 - \langle \mathbf{v}_i, \mathbf{c}_k \rangle \le 1 - \alpha = 0.20$$
3. 杜绝离群孤立样本发生语义假阴性漏检。

### 2.2 引理 142.2：核心因果拓扑可达性不灭引理 (Critical Causal Reachability Invariance Lemma)
设多智能体决策因果图为 $\mathcal{G} = (\mathcal{V}, \mathcal{E})$。核心因果骨架子图 $\mathcal{G}_{core}$ 包含所有满足有效决策传递性且 $\text{BaseImportance}(u) \ge \theta_{core} = 0.85$ 或 `isPinned == true` 的节点及其诱导边。对于修剪算子 $\Pi_{\theta_{prune}}$（$\theta_{prune} = 0.35$）：
1. 核心节点全量保留：$\mathcal{V}_{core} \cap \mathcal{V}_{pruned} = \emptyset$；
2. 核心因果连通度恒等：$\forall u, v \in \mathcal{V}_{core}$，若修剪前存在因果可达路径 $u \rightsquigarrow_{\mathcal{G}} v$，则修剪后必有 $u \rightsquigarrow_{\Pi(\mathcal{G})} v$；
3. 决策路径连通保持率下界恒定满足：$\text{CRR} \ge 95.0\%$。

### 2.3 动态记忆保留方程重构（消除无界发散与死循环免死）
$$R(e, t) = \text{BaseImportance}(e) \cdot \exp\left(-\frac{\Delta t}{\tau \cdot (1 + \beta \ln(1 + N_{access}))}\right) \cdot \left(1.0 + \gamma \frac{\text{Deg}_{causal}^{DAG}(e)}{1.0 + \text{Deg}_{causal}^{DAG}(e)}\right)$$
归一化截断：$R^*(e, t) = \min\left(1.0, \; \frac{R(e, t)}{1.0 + \gamma}\right) \in [0, 1.0]$。其中半衰期 $\tau = 86400000\text{ms}$（24小时），$\beta = 0.2$（对数饱和访问阻尼），$\gamma = 0.5$（双曲有界拓扑权重）。

---

## 三、架构设计与核心组件规划

### 3.1 后端核心组件设计 (`qknow-hermes-core`)

#### 1. `HierarchicalEpisodicMemoryConsolidator.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/memory/HierarchicalEpisodicMemoryConsolidator.java`；
- **定位**：基于阿里千问 1536 维超球面的增量质心谱聚类与反思元规则沉淀引擎；
- **核心数据结构**：
  * `EpisodicMemoryNode`：包含 `nodeId`, `tenantId`, `agentId`, `agentRole`, `episodeSummary`, `importanceScore`, `accessCount`, `causalDegree`, `timestamp`, `isPinned`, `visibilityScope`, `qwenEmbedding` (1536维归一化向量)；
  * `ConsolidatedSemanticCluster`：包含 `clusterId`, `tenantId`, `topicLabel`, `sourceEpisodeIds`, `centroidEmbedding` (1536维单位质心), `consolidatedConfidence`, `reflectiveMetaRule`, `consolidatedAt`；
  * `ConsolidationResult`：包含 `tenantId`, `originalCount`, `clusterCount`, `compressionRatioPercent`, `clusters`, `latencyMs`；
- **核心算法**：
  * **增量超球面质心聚类 (Adaptive Streaming Spherical Clustering)**：复杂度 $O(N \cdot K \cdot D)$，维护最多 200 个动态质心池，单批次耗时 $\le 3.0\text{ms}$；
  * **单位质心提取与单点保真度强制约束**：$\mathbf{c}_k = \frac{\sum \mathbf{v}_i}{\|\sum \mathbf{v}_i\|_2}$，强制断言每个簇内样本单点余弦保真度 $\langle \mathbf{v}_i, \mathbf{c}_k \rangle \ge 0.80$；
  * **元认知反思提炼 (Meta-Cognitive Reflection)**：提炼出跨轮次高阶反思元规则；
  * **空间与 Prompt 压缩率**：压缩率 $\ge 80.0\%$。

#### 2. `AdaptiveEpisodicForgettingPruner.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/memory/AdaptiveEpisodicForgettingPruner.java`；
- **定位**：基于对数阻尼艾宾浩斯衰减、DAG因果拓扑豁免与三级冷备隔离的情节图谱修剪器；
- **核心数学机制**：
  * **保测度保留分计算**：严格按照修正后的对数阻尼与双曲有界拓扑方程计算 $R^*(e, t) \in [0, 1.0]$；
  * **DAG 因果拓扑防成环过滤**：因果度仅统计无向环外的有效前向拓扑边，剔除循环辩论/互点回边，彻底防范内存无界泄露；
  * **关键判例永久免疫钉扎**：若 `isPinned == true` 或 $\text{BaseImportance} \ge 0.85$，直接旁路衰减函数；
  * **三级渐进冷备隔离区 (Quarantine Ring Buffer)**：对 $R^* < 0.35$ 且非豁免节点标记为 `TENTATIVE_PRUNED`，移入软淘汰环形缓冲区，支持 72 小时因果断裂自动复活与两阶段回滚；
  * **修剪性能**：端到端因果连通路径保持率（Causal Reachability）保持 $\ge 95.0\%$，单次修剪耗时 $\le 2.0\text{ms}$。

#### 3. `EpisodicMemoryConsolidationReceipt.java`
- **代码路径**：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/memory/EpisodicMemoryConsolidationReceipt.java`；
- **定位**：纯 Java 21 Record 格式不可变记忆巩固与修剪存证凭单；
- **核心字段**：
  * `receiptId` (String): 全局唯一存证流水号 (`RCP-MEM-CONSOLIDATE-...`)；
  * `tenantId` (String): 租户命名空间标识；
  * `traceId` (String): W3C 32 位全局 TraceId；
  * `originalEpisodes` (int): 原始情节节点数；
  * `consolidatedClusters` (int): 提炼语义主题簇数；
  * `prunedEpisodes` (int): 淘汰软修剪节点数；
  * `compressionRatioPercent` (double): 空间压缩率；
  * `causalReachabilityPercent` (double): 端到端决策路径保持率；
  * `clustersDigest` (String): 巩固结果 SHA-256 摘要；
  * `latencyMs` (double): 执行耗时；
  * `timestamp` (long): 时间戳；
  * `sha256Signature` (String): 规范化全字段 SHA-256 签名；
- **防篡改自验真**：采用 `java.security.MessageDigest.isEqual` 常量时间自验真 `verifySignature()`。

---

### 3.2 前端可视化扩展 (`frontend`)

#### `EpisodicMemoryReflectWidget.vue`
- **路径**：`frontend/src/views/kb/bot/build/components/swarm/EpisodicMemoryReflectWidget.vue`；
- **设计规范（严格遵守 Apple iOS 26 Liquid Glass 规范）**：
  * **无层叠上下文铁律**：组件自身及内部容器**严禁**使用 `transform: translateZ(0)`、悬停位移 `transform: translateY(-2px)`、`isolation: isolate`、`will-change: transform` 或非 1 的 `opacity`，防止破坏 `mix-blend-mode: color-dodge` 提亮；
  * **双层 50px 玻璃材质配方**：每种玻璃材质由底层高光 + $50\text{px}$ 模糊层组成，彻底禁止使用 `saturate()` 滤镜；
  * **Headline 590 字重铁律**：主标题字重锁定为 `590`，正文与数值统一为 `400`，依靠光学微调字距呈现层级；
  * **零阴影系统与纯净信号色**：零大投影，强调色严格使用 Apple 规范：系统蓝 `#0088ff`、系统绿 `#34c759`、系统橙 `#ff8d28`、系统紫 `#6155f5`；
- **人机协同（HITL）交互闭环通道**：
  1. **记忆演化胶囊 Header**：展示输入总数、压缩率、因果路径保持率、自适应修剪按钮与凭单验真按钮；
  2. **艾宾浩斯指数遗忘仪表盘**：动态可视化保留分衰减曲面与对数访问加权效果；
  3. **分层情节语义簇与反思规则库**：展示聚类质心提炼出的高阶反思元规则，支持人工免死钉扎（`isPinned` 切换）与元规则审批；
  4. **密码学凭单在线验真卡片**：展示 SHA-256 签名，点击触发 WebCrypto 常量时间双向验真，以翡翠绿高光呈现验真结果。

---

## 四、8 项严苛契约测试定义 (Contract Tests)

| 测试编号 | 契约方法名 | 核心验证指标与断言标准 |
| :--- | :--- | :--- |
| **TC-142-1** | `testEpisodicMemory_clusteringAndConsolidation()` | 千问 1536 维增量超球面聚类：断言质心单位模长（$\|\mathbf{c}\|_2 = 1.0 \pm 1e-6$）、簇内微观情节单点余弦相似度 $\ge 0.80$（保真度引理 142.1）、压缩率 $\ge 80.0\%$，预热后耗时 $\le 3.0\text{ms}$ |
| **TC-142-2** | `testEbbinghausDecay_retentionScoreCalculation()` | 对数阻尼艾宾浩斯遗忘模型验证：零点无损 $R(t_0) = \text{Imp}$、单调衰减、对数访问加权与双曲因果度有界归一化（值域严控在 $[0, 1.0]$） |
| **TC-142-3** | `testAdaptivePruning_causalCompletenessPreservation()` | 破除同义反复漏洞：构建 10 条端到端因果决策 DAG 链，断言修剪后决策因果路径连通保持率（Causal Reachability）$\ge 95.0\%$，单次修剪耗时 $\le 2.0\text{ms}$ |
| **TC-142-4** | `testReflectiveInsight_metaRuleSynthesis()` | 跨轮次多智能体博弈提炼高阶反思元规则，断言溯源指针 `sourceEpisodeIds` 完备且反思置信度 $\ge 0.85$ |
| **TC-142-5** | `testZeroInformationLeakage_immutablePartition()` | 双租户（Tenant-Alpha 与 Tenant-Beta）与双角色混合输入，断言跨租户跨角色污染数为绝对 0（$0.0\%$ 数据泄漏） |
| **TC-142-6** | `testEpisodicConsolidationReceipt_immutableVerification()` | 纯 Java 21 Record 凭单验真：正向常量时间验真 100.0% 通过；反向注入篡改字段（如压缩率 +0.1%）立即触发验真失败 |
| **TC-142-7** | `testHighScaleEpisodicMemory_throughputAndStability()` | 1000 条长程节点高并发批量聚类与修剪，50 次迭代统计 P99 耗时 $\le 8.0\text{ms}$，平均耗时 $\le 5.0\text{ms}$，零内存泄露 |
| **TC-142-8** | `testEndToEndEpisodicConsolidation_fullPipelineIntegration()` | 端到端全链路闭环：微观情节产生 $\to$ 增量质心巩固 $\to$ 三级冷备自适应修剪 $\to$ 反思规则沉淀 $\to$ 凭单签发与双向验真 |

---

## 五、最小实现文件集合与禁止修改边界

### 5.1 最小修改文件集合
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/memory/HierarchicalEpisodicMemoryConsolidator.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/memory/AdaptiveEpisodicForgettingPruner.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/swarm/memory/EpisodicMemoryConsolidationReceipt.java` (新建)
4. `frontend/src/views/kb/bot/build/components/swarm/EpisodicMemoryReflectWidget.vue` (新建)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase142EpisodicMemoryConsolidationContractTest.java` (新建)

### 5.2 严格禁止修改的边界
- 严禁修改已归档封存的力学物理沙箱目录：`tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改全局系统默认 JDK 17，所有编译与测试必须严格使用 Java 21 隔离环境变量：
  `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH`；
- 严禁引入任何未经许可的外部第三方内存数据库或图中间件，必须使用纯原生 Java 21 内存超球面向量算子；
- 严禁在测试中修改断言期望值以掩盖失败；
- 严禁使用 OpenAI API 或本地小模型，唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 1536 维超球面空间。

---

## 六、完整复现与全量回归验证命令

```bash
# 1. 编译 Hermes 核心子模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn clean install -pl qknow-hermes/qknow-hermes-core -DskipTests

# 2. 执行 Phase 142 专属契约基准测试 (8/8 绿灯)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase142EpisodicMemoryConsolidationContractTest

# 3. 执行全量 18 个阶段跨阶段回归套件 (80/80 绿灯)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase125DynamicGraphPartitionContractTest,Phase126DecoupledRouterContractTest,Phase127DifferentiableSearchContractTest,Phase128RealtimeSublinearRankerContractTest,Phase129GraphHypergraphRerankerContractTest,Phase130StreamingRagTypewriterContractTest,Phase131MultimodalLayoutRagContractTest,Phase132HierarchicalContextEngineContractTest,Phase133ActiveLearningFeedbackContractTest,Phase134StreamingSemanticFlowContractTest,Phase135EnterpriseMcpSecurityContractTest,Phase136AdaptiveMultiAgentDebateContractTest,Phase137SteinerCausalPyramidContractTest,Phase138MultiAgentDeadlockWfgContractTest,Phase139DatabaseMcpSandboxContractTest,Phase140SwarmConsensusTraceContractTest,Phase141CognitiveStateReplayContractTest,Phase142EpisodicMemoryConsolidationContractTest

# 4. 执行前端生产打包构建
cd frontend && npm run build:prod
```

---

## 七、风险评估、停止条件与后续授权边界

### 7.1 残余风险评估
1. **千问向量超球面聚类增量质心偏移**：通过质心归一化与单位模长断言（$\|\mathbf{c}\|_2 = 1.0 \pm 1e-6$）控制几何漂移；
2. **长尾低频记忆因果割边误删**：通过端到端连通路径保持率（$\text{CRR} \ge 95.0\%$）与永久免疫标签（`isPinned`）提供拓扑保护。

### 7.2 立即停止条件 (Abort Criteria)
- 若压缩巩固耗时在基准测试中超过 $3.0\text{ms}$，立即停止；
- 若核心因果连通路径保持率低于 $95.0\%$，立即停止；
- 若 SHA-256 签名自验真失败率 $> 0.0\%$，立即停止；
- 若出现任何跨租户、跨角色数据泄漏，立即停止。

### 7.3 后续授权边界
本实施方案经三方智能体法医级交叉审计获批后，授权仅限于上述最小修改文件集合的代码编写与测试验证。任何参数阈值与生产环境配置变更，均须单独报批。
