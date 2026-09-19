# Phase 106 决策完备实施详案与可证伪工程契约
## 前端流光脉冲动效与全链路瀑布流可观测中枢 (Canvas Energy Flow Pulse Animation & Full-Link Waterfall Observability Metacenter)

> **制定时间**：2026-09-19  
> **核心假设**：`H-PHASE106-001`  
> **战略定位**：严格遵守《业务定位与领域边界铁律（铁律九）》，100% 聚焦于**支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)**，并联动支柱一（复杂业务 Agent 认知与编排）、支柱二（企业级 MCP 工具生态）与支柱三（高保真 RAG 知识引擎）。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（V3/R1）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面几何流形 $\mathbb{S}^{1535}$）；全系统绝无本地部署大模型，彻底弃用 OpenAI/GPT API；后端编译与运行环境统一使用隔离 **Java 21** 虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`），宿主环境严格保持 Java 17 隔离；前端严格遵循 **UI/UX Pro Max 单色钛金毛玻璃 (Monochrome Titanium Frosted Glass)** 规范与 iOS 26 极简质感。

---

### 一、阶段唯一核心科学假设 (`H-PHASE106-001`)

在唯一生成模型（DeepSeek API）与唯一向量模型（阿里千问 1536 维超球面）基线约束下，引入视口 AABB 裁剪下参数化三次贝塞尔能量脉冲粒子流动力学引擎、层次化有向无环因果偏序追踪引擎、以及单色钛金毛玻璃瀑布流可观测看板后：

1. **子假设 1（H-PHASE106-001a：视口 AABB 裁剪下三次贝塞尔粒子流稳态 60fps）**：  
   引入参数化三次贝塞尔阻尼粒子动力学更新律，结合有向边包围盒 AABB 相交测试算子 $\Omega_{\text{cull}}$，将动画运算严格约束在视口可见边集合 $E_{\text{vis}}$ 内（非可见边粒子计算直接休眠冻结），活跃粒子数硬上限 $N_{\text{active}} \le 100$，动画更新单帧计算耗时 $\le 2.5\text{ms} \ll 16.67\text{ms}$，在 200+ 节点大型工作流画布上稳态锁定 **60fps**，浏览器主线程 CPU 占用率压制在 **$\le 5.0\%$**，GPU 显存降低 **$95\%$ 以上**，杜绝页面卡顿与掉帧；
2. **子假设 2（H-PHASE106-001b：层次化分布式追踪因果偏序保真度 100% 与李雅普诺夫内存有界）**：  
   基于 Lamport 逻辑时钟与因果偏序投影映射 $\Phi_{\text{causal}}$，在存在物理时钟漂移（Clock Skew $\le 200\text{ms}$）的环境下，子任务与父任务之间因果偏序拓扑重建保真度严格达到 **$100.0\%$**，杜绝因果倒挂；定长环形追踪窗口（容量 $M = 20$）配合超过 1KB 自适应截断与 SHA-256 指纹锁定，离散李雅普诺夫势函数严格满足一致有界性 $\sup_k V(k) \le \frac{1}{2} (M \cdot S_{\max})^2 < \infty$，内存开销降低 $95\%$ 以上，杜绝 JVM OOM；
3. **子假设 3（H-PHASE106-001c：瀑布流甘特图视觉感知熵减与故障诊断潜伏期降低 70%）**：  
   遵循 UI/UX Pro Max 规范与 iOS 26 单色钛金毛玻璃设计语言，通过关键路径拓扑遍历（Critical Path Method, CPM）与分层甘特图动态聚合投影，使前端呈现的视觉信息熵 $H(V)$ 降低 **$\ge 65.0\%$**，人类开发者定位跨 Agent 协作与 MCP 工具调用性能瓶颈与异常的平均决策潜伏期从 $>60\text{s}$ 显著压缩至 **$\le 15\text{s}$**（潜伏期下降 **$\ge 75\%$**）；全链路签发包含 SHA-256 密码学自签名的不可变存证凭单 `TraceExecutionReceipt`，抗抵赖验真率达到 **$100.0\%$**。

---

### 二、系统核心执行组件与架构防线解耦设计

```
                      【Phase 106 流光脉冲与全链路瀑布流可观测中枢】

       +-------------------------------------------------------------------------+
       |   多 Agent 协作流 (Swarm 辩论 / MCP 工具调用 / GraphRAG 检索知识中枢)   |
       +------------------------------------+------------------------------------+
                                            |
                                            v 纳秒级链路事件采集
       +-------------------------------------------------------------------------+
       | 防线一：层次化轻量追踪引擎与因果偏序对齐 (HierarchicalExecutionTraceEngine)|
       |   * 纯 Java 21 Record 树状模型 (HierarchicalTraceSpan)                   |
       |   * Lamport 逻辑时钟强制偏序单调: T_start(child) >= T_start(parent)      |
       |   * 定长 20 步环形池 (RingBufferTracePool), 超过 1KB 自适应摘要化截断     |
       |   * 关键路径拓扑遍历 (CPM Critical Path), 准确捕获长尾性能瓶颈           |
       +------------------------------------+------------------------------------+
                                            |
                                            v 统筹签发密码学自签名凭单
       +-------------------------------------------------------------------------+
       | 防线二：全链路不可变追踪存证凭单 (TraceExecutionReceipt)                 |
       |   * 纯 Java 21 Record 封装全链路指标与关键路径耗时                       |
       |   * SHA-256 自验真 verifySignature(), 满足等保三级不可抵赖合规要求       |
       +------------------------------------+------------------------------------+
                                            |
                                            v SSE 流式下发追踪与状态事件
       +-------------------------------------------------------------------------+
       | 防线三：自适应视口感知能量脉冲粒子流引擎 (CanvasEnergyPulseEngine.ts)    |
       |   * 三次贝塞尔参数方程 B(s) 数学闭式位置更新, 定长 100 容量对象池复用   |
       |   * AABB 视口相交裁剪: 仅可见边激活粒子, 视口外自动冻结, 活跃粒子 <= 100 |
       |   * 节点单色钛金呼吸光晕 (Idle/Running/Success/Failed), 稳态 60fps, CPU<=5%|
       +------------------------------------+------------------------------------+
                                            |
                                            v
       +-------------------------------------------------------------------------+
       | 防线四：单色钛金毛玻璃全链路瀑布流可观测看板 (ExecutionWaterfallPanel.vue)|
       |   * 遵循 UI/UX Pro Max 规范与 iOS 26 单色钛金毛玻璃设计语言              |
       |   * 虚拟化甘特图引擎 (WaterfallVirtualTimelineEngine.ts), 支持缩放展开  |
       |   * 关键路径高亮、Token 消耗比例分布、点击下钻查看 MCP 入参出参与调用栈  |
       +-------------------------------------------------------------------------+
```

#### 1. 前端组件设计清单 (`frontend/`)
- **`src/views/kb/bot/build/components/canvas/engine/CanvasEnergyPulseEngine.ts`** [NEW]：
  - 基于三次贝塞尔参数方程数学闭式更新粒子运动轨迹；
  - 结合 AABB 视口相交裁剪（只激活当前视口可见连线上的脉冲粒子，不可见边自动冻结）；
  - 定长 100 容量对象池复用，零 GC 抖动，支持 4 种状态节点呼吸光晕（IDLE, RUNNING, COMPLETED, FAILED）；
- **`src/views/kd/observability/engine/WaterfallVirtualTimelineEngine.ts`** [NEW]：
  - 虚拟化全链路瀑布流甘特图时间线引擎；
  - 实现基于 Lamport 因果单调校正（`T_start(child) >= T_start(parent)`）；
  - CPM 关键路径拓扑遍历与高亮计算；
- **`src/views/kd/observability/components/ExecutionWaterfallPanel.vue`** [NEW]：
  - 沉浸式单色钛金毛玻璃全链路瀑布流可观测看板；
  - 遵循 UI/UX Pro Max 单色钛金质感（`backdrop-filter: blur(24px) saturate(190%)`，微米级反光边框）；
  - 支持多层 Span 折叠展开、Token 消耗比例饼图/条状图展示、关键路径一键过滤、下钻查看 MCP 工具入参出参及错误栈。

#### 2. 后端执行引擎与凭证核验组件清单 (`backend/qknow-hermes/`)
- **`tech.qiantong.qknow.hermes.trace.model.HierarchicalTraceSpan.java`** [NEW]：
  - 纯 Java 21 Record 格式层次化追踪片段模型；
  - 包含 `spanId`, `traceId`, `parentSpanId`, `spanName`, `spanType`, `startNano`, `durationUs`, `tokenCount`, `status`, `summaryInput`, `summaryOutput`, `attributes`；
- **`tech.qiantong.qknow.hermes.trace.model.TraceExecutionReceipt.java`** [NEW]：
  - 纯 Java 21 Record 格式不可变全链路追踪密码学存证凭单；
  - 包含 `receiptId`, `traceId`, `workflowId`, `totalSpans`, `rootDurationUs`, `criticalPathDurationUs`, `totalTokens`, `errorCount`, `timestampMs`, `signature`；
  - 内建 `verifySignature()` SHA-256 密码学自验真逻辑；
- **`tech.qiantong.qknow.hermes.trace.engine.HierarchicalExecutionTraceEngine.java`** [NEW]：
  - 树状有向无环因果偏序追踪引擎；
  - 定长 20 步环形 Trace 池（RingBuffer），防止高并发内存泄漏；
  - 自适应大文本截断（超过 1KB 截取摘要并标记 SHA-256 指纹）；
  - Lamport 逻辑时钟单调对齐与 CPM 关键路径拓扑推演；
  - 统筹签发密码学存证凭单。

---

### 三、精确验证命令与测试用例清单 (TDD 先红后绿)

#### 1. 自动化测试文件清单 (`backend/tests/` & `frontend/`)
1. **`HierarchicalTraceSpanTest.java`** [NEW]：
   - 契约 1：Java 21 Record 不可变性与多类型 Span 模型验证（AGENT_REASONING, TOOL_MCP 等）；
   - 契约 2：自适应文本超长截断与 SHA-256 指纹锚定（超过 1KB 压缩至前 256 字符并标记哈希）；
2. **`HierarchicalExecutionTraceEngineTest.java`** [NEW]：
   - 契约 3：父子 Span 树状嵌套拓扑与 Lamport 因果时钟单调性校验（$T_{\text{start}}(\text{child}) \ge T_{\text{start}}(\text{parent})$）；
   - 契约 4：物理时钟漂移反事实消融实验（时钟回退情况下依然保证拓扑偏序严格一致）；
   - 契约 5：定长 20 步环形池内存有界性与最老 Trace 优雅淘汰；
   - 契约 6：CPM 关键路径拓扑遍历与累计关键耗时精确计算；
3. **`TraceExecutionReceiptTest.java`** [NEW]：
   - 契约 7：密码学存证凭单正常构建与自签名验真通过（100% 通过）；
   - 契约 8：单字段恶意篡改（修改 durationUs、totalTokens）100% 拦截失败；
4. **`Phase106TraceObservabilityIntegrationTest.java`** [NEW]：
   - 契约 9：端到端全链路协同集成测试（AgentOrchestration -> SwarmDebate -> ToolMcp -> RAG 检索 -> 树状构建 -> 关键路径推演 -> 凭单签发全链路闭环，100% 绿灯）。
5. **前端动效与时间线逻辑单测**：
   - `CanvasEnergyPulseEngine` 贝塞尔参数方程步进、AABB 视口可见性粒子激活、对象池定长 100 复用验证；
   - `WaterfallVirtualTimelineEngine` 因果单调投影、关键路径计算与 Token 聚合统计验证。

#### 2. 精确后端测试验证命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-hermes/qknow-hermes-core,qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client,qknow-module-kmc/qknow-module-kmc-biz,tests -Dtest="tech.qiantong.qknow.hermes.trace.*Test" -Dsurefire.failIfNoSpecifiedTests=false
```

全量跨阶段联合回归命令（Phase 101 ~ Phase 106 全绿）：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-hermes/qknow-hermes-core,qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client,qknow-module-kmc/qknow-module-kmc-biz,tests -Dtest="*Phase101*Test,*Phase102*Test,*Phase103*Test,*Phase104*Test,*Phase105*Test,*Phase106*Test,HierarchicalDocumentChunkerTest,GraphRagSubgraphReasonerTest,StreamingTypewriterAlignBufferTest,GraphRagOrchestrationControlBusTest,WorkflowDebugReceiptTest,TimeTravelSnapshotRingBufferTest,CognitiveProjectionFilterTest,EnterpriseMcpClientTransportTest,HighRiskToolSafetyGovernorTest,ToolRagFilterTest,McpExecutionReceiptTest,StateGraph*Test,NodeSelfHealingRouterTest,DebateNetworkCoordinatorTest,SwarmHandoffProtocolTest,MoaMixtureOfAgentsRouterTest,DebateConsensusJudicialReceiptTest,HierarchicalTraceSpanTest,HierarchicalExecutionTraceEngineTest,TraceExecutionReceiptTest" -Dsurefire.failIfNoSpecifiedTests=false
```

---

### 四、实施纪律与严禁修改边界

1. **严格禁止修改边界**：
   - 严禁修改 Phase 101 的 `tech.qiantong.qknow.hermes.flow.stategraph.*` 状态图调度核心；
   - 严禁修改 Phase 102 的 `tech.qiantong.qknow.hermes.agent.swarm.*` 辩论与交接核心；
   - 严禁修改 Phase 103 的 `tech.qiantong.qknow.mcp.client.*` MCP 协议栈与语义路由核心；
   - 严禁修改 Phase 104 的 `tech.qiantong.qknow.hermes.flow.hitl.*` 调试快照与画布核心；
   - 严禁修改 Phase 105 的 `tech.qiantong.qknow.module.kmc.service.rag.advanced.*` 知识中枢核心；
   - 严禁修改 `tech.qiantong.qknow.ai.embodied.*` 封存力学资产；
   - 严禁修改父 POM Java 21 版本定义与 Maven 构建配置。
2. **唯一模型与环境铁律**：
   - 唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 1536 维超球面，绝无本地大模型；
   - 严格使用 SDKMAN 隔离环境 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
