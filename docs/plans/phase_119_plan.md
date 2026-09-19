# Phase 119 实施详案与工程契约文件

## 课题：支柱三：高保真 RAG 知识引擎与多模态图谱 —— 层次化多模态文档切分与图谱子图推理对齐中枢 (Hierarchical Multimodal Document Chunking & Subgraph Reasoning Alignment Metacenter)

> **归档路径**：`docs/plans/phase_119_plan.md`  
> **基线环境约束**：
> - 唯一生成模型：DeepSeek API（主干模型，参数化思考模式 `thinking: {"type": "enabled"}`, `reasoning_effort: "high"`, 绝无 r1 称呼）
> - 唯一向量模型：阿里千问 (Qwen) Embedding (1536 维超球面空间，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$)
> - 隔离环境：Java 21 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)
> - 业务边界：100% 聚焦企业级知识库与智能体编排，严禁力学发散

---

### 一、唯一待验证假设 (Hypothesis H-PHASE119-001)

> 在保持 Java 21 隔离环境、DeepSeek API 参数化思考模式、阿里千问 1536 维超球面向量基线不变的前提下：
> 1. 构建**AST 语法树感知层次化切分防线 (`HierarchicalMultimodalChunker`)**，对 Markdown/HTML 实施动态父子块切分（Parent-Child Chunking），并在切分过程中对表格施加结构完整性保护（保留完整父级标题链，且当大表格跨越切片时强制逐块复制 Markdown 表头与列定义），能够**100% 消除表格断头导致的财务/数字歧义**，跨页复杂表格的数值召回准确率由传统定长切分的 $41.5\%$ 跃升至 **$\ge 98.0\%$**；
> 2. 构建**阿里千问 1536 维超球面引导的堆内子图启发式剪枝防线 (`InMemoryHeuristicSubgraphPruner`)**，结合测地距离 $S_{\text{geo}} = \max(0, \mathbf{u} \cdot \mathbf{v})$ 与关系权重，对局部子图展开施加硬性约束（最大跳数 $K \le 2$、单节点最大扩展度数 $B_{\max} \le 16$、总节点上限 $N_{\max} \le 32$），算法时间复杂度严格有界于 $\mathcal{O}(|V_k| + |E_k| \log |V_k|)$，**纯内存堆内推理耗时严格 $\le 10\text{ms}$（零外部图数据库 RPC 依赖）**，将 Prompt 中图谱相关 Token 消耗**降低 $\ge 75\%$**，并彻底根除上下文窗口击穿与噪声淹没；
> 3. 构建**DeepSeek 参数化思考因果注入防线 (`GraphRagAlignmentMetacenter`)**，将剪枝后的因果三元组拓扑骨架注入 Prompt，并严格遵循 DeepSeek 官方多轮思考回传协议（带 `tools` 则完整回传 `reasoning_content`，不带 `tools` 则安全剥离），使多跳复杂因果推理的幻觉率**降低 $\ge 50\%$**；
> 4. 构建**纯 Java 21 Record 格式的不可变存证凭单 (`SubgraphReasoningReceipt`)**，内嵌 SHA-256 密码学自签名与运行时自验真，单次凭单生成与验真耗时 **$\le 50\mu\text{s}$**，实现 100% 防篡改可追溯。

---

### 二、拟实施文件清单与代码架构

#### 模块：`backend/qknow-framework/qknow-ai`

1. **[NEW] `tech.qiantong.qknow.ai.rag.hierarchical.model.MultimodalDocumentChunk.java`**
   - 纯 Java 21 Record 不可变实体；
   - 包含 `chunkId`, `parentChunkId`, `chunkType` (TEXT_PARAGRAPH, TABLE, IMAGE_ANCHORED, SECTION_PARENT), `content`, `breadcrumbPath`, `metadata`, `isPartialTable`, `tableRowStart`, `tableRowEnd`, `imageUri`, `imageCaption`, `chunkSha256`；
   - 构造时自动校验并计算 SHA-256 签名。

2. **[NEW] `tech.qiantong.qknow.ai.rag.hierarchical.model.SubgraphReasoningReceipt.java`**
   - 纯 Java 21 Record 不可变存证凭单；
   - 包含 `receiptId`, `sessionId`, `query`, `seedEntities`, `prunedNodes`, `causalChains`, `tokenBudgetConsumed`, `executionTimeMs`, `generatedAt`, `receiptSignature`；
   - 内置 `PrunedGraphNode` 与 `CausalProposition` 子 Record；
   - 提供内置 SHA-256 自签名与 `verifySignature()` 运行时验真。

3. **[NEW] `tech.qiantong.qknow.ai.rag.hierarchical.HierarchicalMultimodalChunker.java`**
   - AST 语法树感知层次化切分器；
   - 解析 Markdown 标题层级（H1~H6）维护 Breadcrumb 面包屑路径；
   - 识别表格并实施原子完整性保护；若表格超长则按行拆分并逐块强制复制表头；
   - 多模态图文强空间锚定（图片绑定父级路径、图注与上下文说明）。

4. **[NEW] `tech.qiantong.qknow.ai.rag.hierarchical.InMemoryHeuristicSubgraphPruner.java`**
   - 千问 1536 维超球面测地引导的堆内轻量级子图启发式剪枝算子；
   - 结合测地内积与关系权重，计算 $H(v \mid u, \mathbf{q})$ 启发式势能；
   - 实施 $K \le 2, B_{\max} \le 16, N_{\max} \le 32$ 严格剪枝约束；
   - 纯内存堆内执行耗时 $\le 10\text{ms}$，零外部图数据库依赖。

5. **[NEW] `tech.qiantong.qknow.ai.rag.hierarchical.GraphRagAlignmentMetacenter.java`**
   - 层次化切片展开与因果子图推理对齐中枢；
   - 组装因果拓扑骨架（Causal Scaffold）注入 Prompt；
   - 遵循 DeepSeek 官方多轮思考回传协议（带 `tools` 保留 `reasoning_content`，不带 `tools` 剥离）；
   - 签发不可变存证凭单 `SubgraphReasoningReceipt`。

#### 模块：`backend/tests`

6. **[NEW] `tech.qiantong.qknow.ai.rag.hierarchical.Phase119HierarchicalGraphRagContractTest.java`**
   - 综合契约测试，精确覆盖 6 大契约：
     1. 契约 1：定理 1.1 层次化树状切分信息熵损失有界与跨页表格表头完整性保护验证（100% 消除断头表）；
     2. 契约 2：定理 1.2 超球面测地启发式剪枝 $\mathcal{O}(|V_k| + |E_k| \log |V_k|)$ 复杂度与耗时 $\le 10\text{ms}$；
     3. 契约 3：超级节点度数截断（$B_{\max} \le 16, N_{\max} \le 32$）与 Token 预算削减 $\ge 75\%$；
     4. 契约 4：多模态图文空间锚定与标题上下文完整性保真；
     5. 契约 5：DeepSeek 官方思考模式与多轮回传协议对齐（带/不带 tools 正确处理）；
     6. 契约 6：纯 Java 21 Record 存证凭单 SHA-256 自签名与单比特篡改拦截。

---

### 三、验证命令与测试计数

在严格隔离的 Java 21 环境下执行：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-framework/qknow-ai,tests -Dtest="Phase119HierarchicalGraphRagContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```
预期测试计数：**6 项契约测试 100% 绿灯通过**。

全量回归验证：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-framework/qknow-ai,qknow-hermes/qknow-hermes-core,tests -Dtest="Phase119HierarchicalGraphRagContractTest,Phase118McpSagaPipelineContractTest,Phase117NashDebateConsensusContractTest,CrossBorderProcurementEndToEndTest" -Dsurefire.failIfNoSpecifiedTests=false
```
预期测试计数：**23 项跨模块测试 100% 绿灯全量通过**。
