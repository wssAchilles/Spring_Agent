# Phase 137 实施方案与契约设计：超长多模态异构知识图谱金字塔切分、子图因果路径抽取与 GraphRAG 极低延迟流式认知增强中枢

## 一、方案核心目标与唯一算法假设

### 1.1 唯一待验证假设 (Hypothesis H-137)
在企业级超长多模态异构文档检索与深度推理场景下，通过构建“四级多模态金字塔切分（L1~L4）+ 基于 2-近似度量闭包 Steiner 树拓扑因果路径抽取与重排序 + 面向 DeepSeek 长思考双轨流式认知增强中枢”，能够实现：
1. 超长多模态文档（含跨页表格与图表）的层次化实体召回率相比传统单层扁平分块提升 $\ge 25\%$，多模态实体均精确对齐至阿里千问 1536 维超球面空间（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）；
2. 基于拓扑可达矩阵门控将虚假相关非因果噪声节点剔除 $\ge 70\%$，包含度量闭包与最小生成树剪枝的因果推演最短路径抽取端到端耗时严格 $\le 8.5\text{ms}$，子图节点数有界紧凑于 $|\mathcal{V}^*| \le 16$；
3. 将因果子图无损转化为结构化上下文并精准注入 DeepSeek 长思考（Thinking）模式，签发纯 Java 21 Record 格式的不可变密码学存证凭单（`GraphRagCognitiveAugmentationReceipt`），SHA-256 签名常量时间验真成功率 100.0%。

---

## 二、架构设计与核心组件规划

### 2.1 后端核心组件设计 (`qknow-hermes-core`)

#### 1. `MultimodalPyramidChunker.java`
- **定位**：企业级超长多模态异构文档四级金字塔切分与超球面向量投影引擎；
- **四级模型**：
  * **L1 (Document Summary)**：文档全局概要骨架，提供宏观语义锚点；
  * **L2 (Section Topics)**：章节与子主题逻辑块，形成树状目录超边；
  * **L3 (Micro Entities & Triples)**：微观实体、关系与属性三元组；
  * **L4 (Multimodal & Tables Markdown)**：跨页表格与图表结构化 Markdown 线性化表达，包含表头元数据与行列语义对齐；
- **超球面归一化**：
  * 强制将各层节点嵌入向量投影至阿里千问 1536 维单位超球面 $\mathbb{S}^{1535}$，满足 $\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$；
  * 保持父子包含关系的测地线内积单调性：$\mathbb{E}_{v \in Children(u)} [\arccos(\langle \mathbf{e}_u, \mathbf{e}_v \rangle)] \le \sigma_{intra}$。

#### 2. `SteinerCausalPathReranker.java`
- **定位**：基于 2-近似度量闭包 Steiner 最小树与拓扑可达矩阵门控的高性能因果路径抽取与重排序引擎；
- **算法核心流程**：
  1. **全源最短路径度量闭包 (Metric Closure)**：针对目标查询端点集合 $\mathcal{S}$，在包含因果边权重的诱导图上构建完全距离图；
  2. **拓扑可达性门控 (Causal Reachability Gate)**：对非因果有向连接（$\mathcal{R}_{topo}(u, v) = 0$）施加重度惩罚边权，排挤虚假相关边；
  3. **Kruskal 最小生成树与冗余叶子节点剪枝 (Leaf Pruning)**：计算度量生成树并投影回原图，递归剪除度为 1 的非端点冗余节点，确保 $|\mathcal{V}^*| \le 16, |\mathcal{E}^*| \le 15$；
  4. **拓扑因果逆序重排**：沿因果有向流进行拓扑排序，生成最短因果推演链（Causal Deductive Chain），耗时严格 $\le 8.5\text{ms}$。

#### 3. `GraphRagCognitiveAugmentationReceipt.java`
- **定位**：纯 Java 21 Record 格式不可变密码学认知增强审计存证凭单；
- **核心字段**：
  * `receiptId` (String): 全局唯一存证编号 (`RCP-COGNITIVE-...`)；
  * `query` (String): 检索推理输入问题；
  * `pyramidLevelCoverage` (Map<String, Integer>): 金字塔 L1~L4 各层命中节点数统计；
  * `causalPath` (List<String>): 提取的最优因果推演路径节点序列；
  * `selectedNodeCount` (int): 最终因果子图保留节点数；
  * `filteredNoiseRatio` (double): 虚假噪声节点剔除百分比；
  * `deepSeekThinkingPrompt` (String): 格式化生成的 DeepSeek 长思考双轨认知增强 Prompt；
  * `latencyMs` (double): 因果抽取与对齐端到端耗时；
  * `timestamp` (long): 毫秒时间戳；
  * `sha256Signature` (String): 全字段规范化 SHA-256 哈希防篡改签名；
- **特性**：提供常量时间防篡改自验真方法 `verifySignature()`。

---

### 2.2 前端可视化扩展 (`frontend`)

#### `GraphRagCausalPathwayWidget.vue`
- **路径**：`frontend/src/views/kb/bot/build/components/rag/GraphRagCausalPathwayWidget.vue`；
- **视觉风格与规范**：
  * 严格遵循 Rule 2 UI/UX Pro Max 规范与单色现代暗黑钛金毛玻璃设计 Token；
  * 材质底色 `#0a0a0c`、毛玻璃 `backdrop-filter: blur(20px)`、发丝边框 `rgba(255,255,255,0.08)`、微光脉冲 `rgba(255,255,255,0.15)`；
- **核心交互特性**：
  1. **四级金字塔多模态层级下钻折叠**：可视折叠展现 L1 (Summary) $\to$ L2 (Section) $\to$ L3 (Entity) $\to$ L4 (Table) 的节点归属关系；
  2. **因果推演路径动态发光连线**：以高亮发光有向脉冲动态呈现 Steiner 因果推演路径；
  3. **DeepSeek 双轨思考链实时预览**：支持流式展开查看因果链映射至 Thinking 过程的认知增强切片；
  4. **流畅度保证**：渲染与状态同步严格在 16.6ms 内完成，实现 60 FPS 锁步体验。

---

## 三、8 项严苛契约测试定义 (Contract Tests)

| 测试用例编号 | 契约方法名 | 核心验证指标与断言标准 |
| :--- | :--- | :--- |
| **TC-137-1** | `testMultimodalPyramidChunking_fourLevelHierarchy()` | 四级金字塔层级包含与表格线性化：验证 L1~L4 层次化切分，跨页表格 Markdown 线性化，实体召回率提升 $\ge 25\%$ |
| **TC-137-2** | `testQwenEmbedding_1536dHypersphereNormalization()` | 阿里千问 1536 维超球面单位向量归一化：验证所有节点嵌入向量 $\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$，父子包含测地线内积单调性成立 |
| **TC-137-3** | `testSteinerCausalPathReranker_metricClosureAndMST()` | 2-近似度量闭包 Steiner 树紧凑性：验证完全距离图构建、Kruskal 最小生成树与度为 1 冗余叶子剪枝，输出点集 $|\mathcal{V}^*| \le 16$ |
| **TC-137-4** | `testCausalReachabilityGate_spuriousCorrelationFiltering()` | 拓扑可达矩阵门控虚假相关剔除：验证非因果可达弱相关边被施加惩罚项排除，噪声节点过滤率 $\ge 70\%$ |
| **TC-137-5** | `testCausalPathExtraction_latencyBudgetEnforcement()` | 微秒级因果路径抽取性能预算：经 10 次循环预热后，单次因果最短路径抽取耗时严格 $\le 8.5\text{ms}$（稳态平均 $\le 1.0\text{ms}$） |
| **TC-137-6** | `testDeepSeekThinkingAlignment_dualTrackPromptGeneration()` | DeepSeek 长思考双轨认知增强对齐：因果推演链转译为标准三元组因果序列，100% 合规匹配官方 Thinking 格式 |
| **TC-137-7** | `testGraphRagCognitiveAugmentationReceipt_immutableSignatureAndVerification()` | 纯 Java 21 Record 凭单签名自验真：验证全字段不可变性与 SHA-256 哈希常量时间验真率 100.0%，篡改任意字段立即抛出校验失败 |
| **TC-137-8** | `testEndToEndGraphRagCognitive_fullPipelineIntegration()` | 端到端全链路闭环集成：多模态金字塔分块 $\to$ 超球面投影 $\to$ Steiner 因果剪枝 $\to$ 拓扑重排 $\to$ DeepSeek 对齐 $\to$ 凭单防篡改签发 |

---

## 四、最小实现文件集合与禁止修改边界

### 4.1 最小修改文件集合
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag/cognitive/MultimodalPyramidChunker.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag/cognitive/SteinerCausalPathReranker.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag/cognitive/GraphRagCognitiveAugmentationReceipt.java` (新建)
4. `frontend/src/views/kb/bot/build/components/rag/GraphRagCausalPathwayWidget.vue` (新建)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase137GraphRagCognitiveContractTest.java` (新建)

### 4.2 严格禁止修改的边界
- 严禁修改已归档封存的力学物理沙箱目录：`tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改全局系统默认 JDK 17，所有编译与测试必须严格使用 Java 21 隔离环境变量；
- 严禁引入任何未获批准的外部三方重型图数据库依赖（如 Neo4j 驱动包等），严格保持零外部基础设施侵入；
- 严禁在测试中修改断言期望值以掩盖失败；
- 严禁使用 OpenAI API 或本地小模型，唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 Embedding。

---

## 五、完整复现与全量回归验证命令

```bash
# 1. 编译安装 qknow-hermes-core 模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn clean install -pl qknow-hermes/qknow-hermes-core -DskipTests

# 2. 运行 Phase 137 专项严苛契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase137GraphRagCognitiveContractTest

# 3. 运行 Phase 125 ~ Phase 137 跨阶段全量基准回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase125SwarmConsensusContractTest,Phase126DistributedSwarmContractTest,Phase127E2ESwarmContractTest,Phase128AutonomousSwarmContractTest,Phase129UnifiedE2ESwarmContractTest,Phase130SwarmGovernanceContractTest,Phase131EnterpriseMcpProductionBenchmarkContractTest,Phase132HierarchicalGraphRagContractTest,Phase133E2EChaosBenchmarkContractTest,Phase134FrontendDagHitlIntegrationContractTest,Phase135SwarmDynamicTopologyContractTest,Phase136McpGatewayContractTest,Phase137GraphRagCognitiveContractTest

# 4. 验证前端 Vite 生产环境全量编译构建
cd frontend && npm run build:prod
```
