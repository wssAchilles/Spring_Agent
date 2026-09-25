# Phase 131 实施计划：超高保真多模态 GraphRAG 层次化图嵌入、Steiner 树因果骨架提取与 DeepSeek 参数化思考对齐中枢

> **课题编号**：Phase 131  
> **战略支柱**：支柱三：高保真 RAG 知识引擎与多模态图谱 (Advanced RAG & Multimodal Knowledge)  
> **学术理论报告**：`docs/plans/phase_131_academic_report.md` (准入状态: `RESEARCH_GATE_PASSED`)  
> **工业实践报告**：`docs/plans/phase_131_industrial_report.md` (准入状态: `RESEARCH_GATE_PASSED`)  
> **唯一模型基线**：唯一生成模型为 DeepSeek API（主干模型参数化思考 `thinking: {"type": "enabled"}`）；唯一向量模型为阿里千问 (Qwen) Embedding 1536 维超球面（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）；全系统绝无本地大模型与 OpenAI API。  
> **编译运行环境**：统一使用 Java 21 隔离虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。  
> **业务领域铁律**：100% 聚焦于 Agent 业务核心主战场与企业 RAG，彻底封存具身力学与空间课题。

---

## 一、唯一待验证假设与核心指标契约

### 1.1 核心假设 (H-PHASE131-001)
在面向复杂多模态企业知识库检索中，通过构建**具有跨页表头继承与双向指针的四级多模态层次化流形树（Hierarchical Multimodal Chunk Tree）**、在阿里千问 1536 维超球面测地线内积赋权图上运行**基于度量闭包与 Kruskal MST 的 2-近似 Steiner 树因果子图紧致抽取算子**，并转化为结构化有向无环图（DAG）因果拓扑命题链注入 DeepSeek 参数化思考前置提示词（`thinking: {"type": "enabled"}`）：
1. **子假设 1（Steiner 树因果子图抽取纯内存毫秒级耗时）**：在候选图规模 $|V| \le 100, |E| \le 300$、端点种子集合 $|S| \le 6$ 的图谱拓扑中，度量闭包构建与 Steiner 树抽取纯 Java 21 堆内耗时严格有界于 $\le 15\text{ms}$，且零外部图数据库 RPC 依赖；
2. **子假设 2（因果骨架节点严格有界与 Token 深度压缩）**：抽取出的因果骨架节点数严格有界于 $N \le 16$，相比于未剪枝的 2-跳邻域子图，上下文 Token 压缩率 $\ge 75\%$，彻底消除超级节点爆炸；
3. **子假设 3（DeepSeek 事实接地置信度突破）**：在 DeepSeek-Chat 启用链式思考的生成过程中，基于因果拓扑命题约束的事实接地置信度评分 $\text{GroundingScore} \ge 0.90$（依据 FActScore 原子事实评测协议）；
4. **子假设 4（大模型事实幻觉指数级抑制）**：相较于传统定长切片与离散三元组平铺 baseline，复杂多跳问答中非事实性断言（幻觉发生率）降低 $\ge 60\%$（$P(\text{Hallucination}) \le 0.08$）。

---

## 二、四级工业防线核心组件设计

### 2.1 第一道防线：AST 语法树感知父子分块与跨页表格完整性保护 (`HierarchicalAstChunker.java`)
- **功能目标**：将 Markdown / HTML 文本解析为包含 `Document -> Section -> Paragraph / Table` 的层次化流形树；
- **跨页表格保护**：将表格视为原子块（`ATOMIC_TABLE`），严禁在行内撕裂。若表格跨切片拆分，强制将第一页的 Column Headers（表头 Schema）复制并注入后续所有数据切片，同时将每行序列化为键值对描述；
- **双向指针**：各切片保存 `parentId` 与 `childrenIds`，命中叶子节点时通过父指针向上无损恢复上下文大纲。

### 2.2 第二道防线：千问 1536 维超球面度量与 Steiner 树紧凑因果剪枝 (`SteinerCausalSubgraphPruner.java`)
- **度量闭包计算**：以种子实体 $S$（$|S| \le 6$）为源点，执行堆优化 Dijkstra 最短路，构建完全度量闭包图 $G_M = (S, E_M, w_M)$；
- **Kruskal MST 求解**：在 $G_M$ 上求解最小生成树 $T_M$；
- **路径重构展开与叶子剪枝**：将 $T_M$ 的边映射回原图最短路径并展开为 $G_S$，自底向上剪除非端点度为 1 的叶子节点，得到严格满足 2-近似比的 Steiner 树；
- **硬性边界**：节点数严格限制在 $N \le 16$，边数 $M \le 15$，执行时间 $\le 15\text{ms}$。

### 2.3 第三道防线：DeepSeek 参数化思考因果骨架双轨对齐 (`DeepSeekCausalThinkingAligner.java`)
- **Kahn 拓扑排序**：将因果子图节点与边按因果先后依赖构建为结构化因果拓扑命题链（Causal Proposition Chain）；
- **官方思考协议对齐**：
  * 构建包含 `<thinking_scaffold>` 的 Prompt，开启 `thinking: {"type": "enabled"}`；
  * 双轨协议校验：若请求包含 `tools`，严格保留并回传多轮历史中的 `reasoning_content`；若未包含 `tools`，深度遍历物理剥离 `reasoning_content`，杜绝 400 Bad Request。

### 2.4 第四道防线：纯 Java 21 Record 格式不可变存证凭单 (`GraphRagCausalSteinerReceipt.java`)
- **不可变数据结构**：封装 `receiptId`, `queryText`, `seedEntities`, `steinerNodeCount`, `steinerEdgeCount`, `causalPaths`, `groundingScore`, `latencyMicros`, `sha256Signature`；
- **密码学验真**：提供常量时间自验真方法 `verifySignature()`，杜绝时序攻击。

---

## 三、实施最小文件清单

获批后仅修改或新建以下最小文件集合：
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag/causal/HierarchicalAstChunker.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag/causal/SteinerCausalSubgraphPruner.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag/causal/DeepSeekCausalThinkingAligner.java` (新建)
4. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/rag/causal/GraphRagCausalSteinerReceipt.java` (新建)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/rag/causal/Phase131SteinerCausalAlignmentContractTest.java` (新建契约测试)

明确禁止修改的边界：
- 严禁修改外部生产数据库配置与 SQL 脚本；
- 严禁修改具身力学与机器人仿真已封存代码；
- 严禁修改系统全局 JDK 17 环境。

---

## 四、验证命令与测试用例集

### 4.1 独立验证命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean test -pl backend/tests -Dtest=Phase131SteinerCausalAlignmentContractTest
```

### 4.2 跨阶段全量回归测试命令 (Phase 125 ~ Phase 131)
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean test -pl backend/tests -Dtest=Phase125McpToolExecutionContractTest,Phase126McpVirtualThreadIsolationContractTest,Phase127McpDynamicPipelineContractTest,Phase128McpZeroTrustSecuritySandboxContractTest,Phase129HermesNashDebateConsensusContractTest,Phase130McpSagaDistributedFailoverContractTest,Phase131SteinerCausalAlignmentContractTest
```

### 4.3 8 项硬核契约测试用例定义
1. `test01_QwenHypersphericalDistanceMetricProperties()`: 验证千问 1536 维超球面测地线内积权重满足非负性、对称性与三角不等式；
2. `test02_HierarchicalAstChunkerTableIntegrity()`: 验证跨页大表格切片自动复制继承首行表头 Schema，消除代词悬挂与字段撕裂；
3. `test03_MetricClosureAndKruskalMstCorrectness()`: 验证种子实体集合的完全度量闭包图生成与 Kruskal MST 计算准确性与无环性；
4. `test04_SteinerTreeTwoApproximationRatioBound()`: 验证 Steiner 树 2-近似比严格满足上界 $w(G_{\text{steiner}}) \le 2(1 - 1/|S|) w(\text{OPT})$（定理 1.1）；
5. `test05_SteinerPruningBoundedLatencyAndNodes()`: 验证纯内存 Steiner 树子图抽取耗时 $\le 15\text{ms}$，骨架节点严格钳位在 $N \le 16$；
6. `test06_KahnCausalTopologicalPropositionOrdering()`: 验证 Kahn 算法拓扑排序构建的因果命题链满足前置条件严格先于后继结论；
7. `test07_DeepSeekThinkingProtocolDualTrackAlignment()`: 验证 DeepSeek 思考协议请求体构造严格遵循带 tools 保留、无 tools 剥离 reasoning_content；
8. `test08_ReceiptSha256SelfVerificationAndTamperResistance()`: 验证纯 Java 21 Record 格式凭单 SHA-256 签名常数时间自验真通过，篡改字段立即抛错。

---

## 五、残余风险与立即停止条件

1. **立即停止条件 1（延迟超标）**：纯内存 Steiner 树抽取耗时突破 $25\text{ms}$；
2. **立即停止条件 2（节点失控）**：剪枝后骨架节点数突破 20 个；
3. **立即停止条件 3（协议报错）**：DeepSeek 请求因 `reasoning_content` 回传格式错误触发 400 Bad Request；
4. **立即停止条件 4（测试失败）**：8 项硬核契约测试任一失败。
