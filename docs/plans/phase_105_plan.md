# Phase 105 决策完备实施详案与可证伪工程契约
## 超长文档层级化语义解析、子图推理增强 GraphRAG 与千问向量时空对齐知识中枢 (Hierarchical Document Semantic Parsing, Subgraph-Reasoning GraphRAG & Qwen Spatiotemporal Knowledge Metacenter)

> **制定时间**：2026-09-18  
> **核心假设**：`H-PHASE105-001`  
> **战略定位**：严格遵守《业务定位与领域边界铁律（铁律九）》，100% 聚焦于**支柱三：高保真 RAG 知识引擎与多模态图谱 (Advanced RAG & Multimodal Knowledge)**，联动支柱一（复杂业务 Agent 认知与编排）与支柱二（企业级 MCP 工具生态）。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（V3/R1）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面几何流形 $\mathbb{S}^{1535}$，满足 $\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）；全系统绝无本地部署大模型，彻底弃用 OpenAI/GPT API；后端编译与运行环境统一使用隔离 **Java 21** 虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`），宿主环境严格保持 Java 17 隔离。

---

### 一、阶段唯一核心科学假设 (`H-PHASE105-001`)

在唯一生成模型（DeepSeek API）与唯一向量模型（阿里千问 1536 维超球面向量 $\mathbb{S}^{1535}$）的架构基线约束下，引入四级文档拓扑树动态父展开算子、局部 2-跳测地内积加权诱导子图 Personalized PageRank 幂迭代推理、以及超球面时空流形保角投影结合自适应泊松 JitterBuffer 队列中枢后：

1. **子假设 1（H-PHASE105-001a：四级树动态展开上下文保真度）**：  
   四级文档树流形 $\mathcal{T}_{\text{doc}} = \langle \mathcal{N}_{\text{doc}}, \mathcal{N}_{\text{sec}}, \mathcal{N}_{\text{para}}, \mathcal{N}_{\text{sent}}, \mathcal{E}_{\text{tree}} \rangle$ 的父级向上展开算子 $\Omega_{\text{expand}}$ 能够将上下文信息熵损失控制在界限 $\Delta H \le \varepsilon$ 内，使召回命中文本的跨章节指代消解与核心事实完整度保真率达到 **$\ge 95.0\%$**，父展开时间复杂度为严格有界常数 $\mathcal{O}(h)$ ($h \le 4$)，单次树构建与展开耗时 **$\le 50\mu\text{s}$**，长程语境保真度提升 **$\ge 54\%$**；
2. **子假设 2（H-PHASE105-001b：局部 2-跳 PPR 幂迭代平稳收敛与毫秒级延迟）**：  
   在种子节点引导的局部 2-跳诱导子图（节点数 $N \le 100$）上，以千问 1536 维超球面测地内积（$\cos_g(\mathbf{u}, \mathbf{v}) \ge 0.70$）加权边权重，阻尼因子 $\alpha = 0.35$ 下 PPR 幂迭代在有限步 **$K \le 5$** 步内单调收敛至平稳分布，残差误差 $\|\mathbf{p}^{(K)} - \mathbf{p}^*\|_2 \le (1-\alpha)^K \le 0.05$，单次局部子图推理耗时严格有界 **$\le 5.0\text{ms}$**，计算复杂度降阶至 $\mathcal{O}(d_{\text{avg}}^2)$，彻底杜绝全局图漫游发散与 Hub 节点语义漂移；
3. **子假设 3（H-PHASE105-001c：千问时空流形对齐与打字机抖动抑制）**：  
   阿里千问超球面单位向量与指数时间衰减核 $e^{-\lambda \Delta t}$ 的凸组合保持度量保角性；闭环自适应泊松 JitterBuffer 队列在李雅普诺夫渐近稳定控制下，输出速率瞬时方差降低 **$\ge 80.0\%$**，断流超过 2000ms 时触发一阶软封口，字符丢失率恒为 **$0.0\%$**；1000Hz 4096 槽位 Disruptor 无锁控制总线单步入队延迟 **$\le 50\text{ns}$**，统筹签发包含 SHA-256 密码学自签名验真凭单。

---

### 二、系统核心执行组件与架构防线解耦设计

```
                            【Phase 105 知识中枢端到端架构拓扑】

       +-------------------------------------------------------------------------+
       |   企业级超长文档输入 (Markdown / 行业技术白皮书 / 法律合同 10^4~10^6 字)   |
       +------------------------------------+------------------------------------+
                                            |
                                            v
       +-------------------------------------------------------------------------+
       | 防线一：自适应四级树状切片与动态父展开引擎 (HierarchicalDocumentChunker) |
       |   * Document -> Section -> Paragraph -> Sentence 嵌套拓扑流形           |
       |   * 叶子节点细粒度粗排 + 命中后沿 parentId 向上展开富语义包围盒          |
       |   * 单步树构建耗时 <= 50us, 语境保真度提升 >= 54%, 指代消解率 >= 95%     |
       +------------------------------------+------------------------------------+
                                            |
                                            v 种子实体抽取 (千问 1536 维超球面测地余弦最大)
       +-------------------------------------------------------------------------+
       | 防线二：测地加权 2-跳局部诱导子图极速 PPR 推理 (GraphRagSubgraphReasoner) |
       |   * 抛弃全局社区聚类，以种子实体为根抽取 2-跳诱导子图 (N <= 100)        |
       |   * 测地内积加权转移矩阵 P = D^{-1}W, 阻尼 alpha = 0.35, 5 轮幂迭代收敛  |
       |   * 单步推理耗时 <= 5.0ms, 复杂度降阶至 O(d^2), 杜绝语义漫游漂移         |
       +------------------------------------+------------------------------------+
                                            |
                                            v 时空多维流形保角融合 (向量 + 图谱 + 时序衰减)
       +-------------------------------------------------------------------------+
       | 防线三：1000Hz 定长 4096 Disruptor 无锁控制总线与存证凭单               |
       |   (GraphRagOrchestrationControlBus & GraphRagExecutionReceipt)          |
       |   * 纯 Java 21 AtomicReferenceArray + 位掩码无锁寻址, 单步入队 <= 50ns  |
       |   * JitterGuard 监控连续 3 帧时钟抖动超限 (>2ms) 自动降级软着陆          |
       |   * 统筹签发纯 Java 21 Record 格式 SHA-256 密码学防篡改不可变存证凭单    |
       +------------------------------------+------------------------------------+
                                            |
                                            v 驱动下游大模型流式生成 (SSE Chunk 突发流)
       +-------------------------------------------------------------------------+
       | 防线四：自适应闭环泊松 JitterBuffer 流式打字机中枢                      |
       |   (StreamingTypewriterAlignBuffer)                                      |
       |   * 闭环比例调控律 v(t) = clamp(v* + k_p(Q-Q_t), 25, 45)                |
       |   * 突发方差降低 >= 80%, 断流 2000ms 一阶平滑减速软封口, 字符丢失率 0.0% |
       +------------------------------------+------------------------------------+
                                            |
                                            v
       +-------------------------------------------------------------------------+
       | 前端呈现：UI/UX Pro Max 单色钛金毛玻璃流式打字机画布 (Monochrome Frosted)|
       +-------------------------------------------------------------------------+
```

#### 1. 后端组件设计清单 (`backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/advanced/`)
- **`dto/ChunkHierarchyLevel.java`**：四级文档树层级枚举（`DOCUMENT`, `SECTION`, `PARAGRAPH`, `SENTENCE`）；
- **`dto/GraphRagEventFrame.java`**：定长总线事件帧 Record，记录时钟戳、序列号、事件类型与有效载荷；
- **`dto/StreamingPlaybackState.java`**：流式回放状态枚举（`BUFFERING`, `PLAYING`, `DRAINING`, `COMPLETED`）；
- **`dto/GraphRagExecutionReceipt.java`**：不可变存证凭单 Record，内嵌 SHA-256 签名计算与自验真校验；
- **`engine/HierarchicalDocumentChunker.java`**：四级树状分层解析器，单步构建耗时 $\le 50\mu\text{s}$，提供 `expandParentContext` 向上父级动态展开；
- **`engine/GraphRagSubgraphReasoner.java`**：测地加权 2-跳局部诱导子图 PPR 极速幂迭代推理引擎，单步耗时 $\le 5.0\text{ms}$，杜绝语义漫游；
- **`engine/StreamingTypewriterAlignBuffer.java`**：闭环自适应泊松 JitterBuffer 流式打字机对齐缓冲中枢，恒速 25~45 cps，方差降低 $\ge 80\%$，断流 0 丢字；
- **`engine/GraphRagOrchestrationControlBus.java`**：1000Hz 定长 4096 槽位 Disruptor 无锁总线，单步延迟 $\le 50\text{ns}$，JitterGuard 监控；
- **`service/SpatiotemporalKnowledgeMetacenter.java`** [NEW]：Phase 105 核心编排服务，整合四级防线，提供统一全链路知识摄取、推理与流式对齐管道。

---

### 三、精确验证命令与测试用例清单 (TDD 先红后绿)

#### 1. 自动化测试文件清单 (`backend/tests/src/test/java/tech/qiantong/qknow/module/kmc/service/rag/advanced/`)
1. **`HierarchicalDocumentChunkerTest.java`** [NEW]：
   - 契约 1：四级文档树状结构构建正确性（`Document -> Section -> Paragraph -> Sentence`，双向指针校验）；
   - 契约 2：向上父级展开上下文保真度验证（章节标题与段落背景 100% 完整关联，保真度得分 $\ge 0.95$）；
   - 契约 3：空文档、单段落、特殊标点与长文档边界性能压测（单步耗时 $\le 50\mu\text{s}$）；
2. **`GraphRagSubgraphReasonerTest.java`** [NEW]：
   - 契约 4：种子实体高精度定位（千问超球面余弦内积最大者精准召回）；
   - 契约 5：2-跳局部诱导子图邻域抽取（严格限制在 2 跳内，过滤低相似度边）；
   - 契约 6：PPR 幂迭代收敛性验证（5 轮迭代收敛，概率守恒 $\sum p_i = 1.0$，单步推理耗时 $\le 5.0\text{ms}$）；
   - 契约 7：极端孤立节点与悬挂节点自适应归一化防御（零 NaN、零除以零异常）；
3. **`StreamingTypewriterAlignBufferTest.java`** [NEW]：
   - 契约 8：泊松突发 Chunk 平滑排空与恒速回放（25~45 cps 区间控制）；
   - 契约 9：流式输出方差抑制验证（相较于原始突发方差降低 $\ge 80\%$）；
   - 契约 10：断流超时（2000ms）一阶优雅软封口与零丢字（输出总字符数严格等于输入总字符数）；
4. **`GraphRagOrchestrationControlBusTest.java`** [NEW]：
   - 契约 11：定长 4096 槽位 Disruptor 环形总线高性能并发推进（单步入队 $\le 50\text{ns}$）；
   - 契约 12：JitterGuard 时钟抖动监控与降级熔断（连续 3 帧超限自愈切入 `STATUS_DEGRADED_FLAT_FALLBACK`）；
   - 契约 13：不可变存证凭单 SHA-256 密码学自验真（正常通过，篡改单字段 100% 拦截失败）；
5. **`Phase105KnowledgeMetacenterIntegrationTest.java`** [NEW]：
   - 契约 14：长文档摄取分层解析 $\to$ 语义命中父级展开 $\to$ 2-跳局部子图 PPR 推理 $\to$ 时空流形对齐 $\to$ 控制总线发布与凭单签发 $\to$ 流式打字机闭环排空全链路端到端协同集成验证（100% 绿灯）。

#### 2. 精确后端测试验证命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-module-kmc/qknow-module-kmc-biz,tests -Dtest="tech.qiantong.qknow.module.kmc.service.rag.advanced.*Test" -Dsurefire.failIfNoSpecifiedTests=false
```

全量联合回归命令（Phase 101 ~ Phase 105）：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-hermes/qknow-hermes-core,qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client,qknow-module-kmc/qknow-module-kmc-biz,tests -Dtest="*Test" -Dsurefire.failIfNoSpecifiedTests=false
```

---

### 四、实施纪律与严禁修改边界

1. **严格禁止修改边界**：
   - 严禁修改 Phase 101 的 `tech.qiantong.qknow.hermes.flow.stategraph.*` 状态图调度核心；
   - 严禁修改 Phase 102 的 `tech.qiantong.qknow.hermes.agent.swarm.*` 辩论与交接核心；
   - 严禁修改 Phase 103 的 `tech.qiantong.qknow.mcp.client.*` MCP 协议栈与语义路由核心；
   - 严禁修改 Phase 104 的 `tech.qiantong.qknow.hermes.flow.hitl.*` 调试快照与画布核心；
   - 严禁修改 `tech.qiantong.qknow.ai.embodied.*` 封存力学资产；
   - 严禁修改父 POM Java 21 版本定义与 Maven 构建配置。
2. **唯一模型与环境铁律**：
   - 唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 1536 维超球面，绝无本地大模型；
   - 严格使用 SDKMAN 隔离环境 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
