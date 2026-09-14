# Phase 34 决策完备实施方案：神经符号可解释性拓扑、因果归因与密码学存证前端可视化大屏 (Generative Explainability, Causal Attribution & Merkle Proof Frontend Experience)

> **遵循规范**：`AGENTS.md` Research-to-Implementation Gate 强制准入规范  
> **学术依据**：`docs/plans/phase_34_academic_report.md`（75.7KB，Sugiyama $k$-分图分层映射、贪心无环循环消除定理、中位数启发式交叉最小化 3-近似比上界推导、定理 1.1 拓扑无碰撞紧凑布局不变量证明、因果沙普利值 4 大经典公理严格证明与唯一性定理、定理 2.1 蒙特卡洛随机采样有界归因误差收敛界证明、RFC 6962 单字节域分离抗第二原像碰撞证明、定理 3.1 密码学验真完备性与可靠性定理、zk-SNARK 零知识合规证明边界分析）  
> **工程依据**：`docs/plans/phase_34_industrial_report.md`（80.8KB，Langfuse Trace Tree、Arize Phoenix、Dify Visual Workflow、Vue Flow 视口裁剪虚拟化、RFC 6962 W3C WebCrypto API 原生硬件加速、Apache ECharts 5.5.1 钛金单色流光雷达图、大厂 3 大生产级事故复盘与规避指南）  
> **设计系统依据**：`.shared/ui-ux-pro-max` 检索输出之“Security Audit Dashboard”规范与 Phase 12/19 固化之 Monochromatic Glassmorphism 单色钛金毛玻璃分层规范（L0~L3）  
> **核心假设**：唯一核心待验证假设 H-PHASE34-001（基于 Vue Flow 视口虚拟化与 Dagre 分层算法构建全链路 8 阶段拓扑画板、基于原生 WebCrypto 硬件加速实现 RFC 6962 免密离线验真卡片、基于 ECharts 与钛金毛玻璃构建安全态势大屏：实现 50~500 节点 58~60 FPS 平滑交互、纯客户端离线验真与 Java 后端 100% 密码学等价且耗时 $< 1.5\text{ms}$、打字机流式动效与因果回溯 0 阻塞、单比特篡改拦截率 100%）  
> **架构模型基线**：唯一生成模型为 DeepSeek API；唯一向量模型为阿里千问 (Qwen) Embedding（1536 维超球面归一化）；后端全量统一 Java 21 隔离环境 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、当前代码与失败机制诊断 (A. 当前代码与失败机制)

### 1.1 现存代码审查与前后端断层分析
在 Phase 32 与 Phase 33 交付后，后端已具备完整的密码学与因果数据基底：
- `MerkleTreeEngine.java`：严格按照 RFC 6962 构建平衡二叉 Merkle 树，前置单字节域分离前缀（$0x00$ 叶子, $0x01$ 内部节点），导出对数级包含性证明 `MerkleProof`；
- `CausalAttributionGraph.java`：形式化记录 `QUERY -> INTENT -> KNOWLEDGE -> BFT -> OUTPUT` 因果拓扑图，支持逆向溯源回溯；
- `AuditVerificationController.java`：提供后端免密核验端点；
- `AiPipelineEngine.java`：在微阶段流转中透明沉淀安全判定、耗时与归因数据。

然而，在前端工程（`frontend/src`）中，存在三大致命缺陷：
1. **因果拓扑呈现缺失与边交叉混乱**：现有前端缺少全链路因果流转大屏。若采用传统无约束力导向算法，由于缺乏 Sugiyama $k$-分图分层与中位数交叉极小化约束，在大规模多切片与共识辩论场景下连线交错如“毛球”，用户无法看清推理因果链；
2. **密码学验真黑盒化与离线信任断层**：前端缺乏纯客户端硬件级验真组件，依赖向服务器发送请求回验。未利用现代浏览器原生 WebCrypto API，无法实现零服务器依赖的离线免密核验；
3. **安全护栏态势感知割裂**：PII 脱敏量、对抗越狱拦截、事实忠实度走势散落于后端日志，缺少符合 `.shared/ui-ux-pro-max` 钛金毛玻璃规范的综合监控大屏。

### 1.2 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-PHASE34-001)**：  
> 构建全链路神经符号可解释性拓扑画板（`ExplainabilityTopologyCanvas.vue`，基于 Vue Flow 视口虚拟化与 Dagre 分层排版）、纯客户端 RFC 6962 密码学免密验真卡片（`MerkleProofValidator.vue`，基于 WebCrypto API 原生硬件加速与 TypedArray 零拷贝）、以及合规安全态势感知仪表盘（`GuardrailDashboard.vue`，基于 ECharts 钛金单色流光动效与 Monochromatic Glassmorphism 规范），并对外扩展后端拓扑导出与指标聚合接口：  
> 1. 画布在 50~500 节点下平移缩放保持稳定 **58~60 FPS**，内存占用较全量渲染方案压降 $\ge 65\%$；  
> 2. 纯客户端离线验真算法与 Java 后端 `MerkleTreeEngine` 计算结果 **100% 密码学等价（零误报、零漏报）**，对数级步骤验真耗时 $< 1.5\text{ms}$；  
> 3. 单比特篡改（Bit-Flip Tampering）检出率 **100%**，并能精准定位篡改层级；  
> 4. 逆向因果溯源链路高亮与流式打字机在主线程并发运行时实现 **0 帧丢失与 0 阻塞**。

---

## 二、Research Ledger 索引与理论/工程依据 (B. Research Ledger)

本方案严格建立在以下 12 篇顶级学术文献与工业级开源实现的实证证据链之上：

### 2.1 学术理论来源（详见 `docs/plans/phase_34_academic_report.md`）
1. **Sugiyama et al. 1981 (IEEE SMC)**：Methods for Visual Understanding of Hierarchical Systems. 确立 7 层 $k$-分图正规化分层映射与虚节点技术；
2. **Eades & Wormald 1994 (Algorithmica)**：Edge Crossing Minimization in Bipartite Graphs. 证明中位数法在单侧两层交叉极小化中具备 3-近似比上界（$C_{med} \le 3C^*$）；
3. **Brandes & Köpf 2001 (Graph Drawing)**：Fast and Simple Horizontal Coordinate Assignment. 确立拓扑顺序保持约束与水平坐标紧凑压缩，证明定理 1.1 拓扑无碰撞紧凑布局不变量；
4. **Shapley 1953 / Lundberg & Lee 2017 (NeurIPS)**：A Unified Approach to Interpreting Model Predictions (SHAP). 确立因果沙普利值公理化分配模型，证明完备效率性、对称性、虚设因子性与可加性 4 大经典公理；
5. **Hoeffding 1963 (JASA)**：Probability Inequalities for Sums of Bounded Random Variables. 导出蒙特卡洛随机采样有界归因误差界，证明定理 2.1（浏览器 128~256 次采样即可达到 $\epsilon < 0.08$ 精度）；
6. **RFC 6962 / Crosby & Wallach 2009 (USENIX Security)**：Certificate Transparency & Efficient Data Structures for Tamper-Evident Logging. 确立单字节域分离前缀（$0x00 / 0x01$）抗第二原像碰撞，证明定理 3.1 客户端离线验真完备性与可靠性。

### 2.2 工业实现对标（详见 `docs/plans/phase_34_industrial_report.md`）
1. **Langfuse (MIT / FSL)**：Trace Tree 时间线与 Agent Graph 有向无环图双重视图设计，侧边抽屉展示深层 Payload；
2. **Arize Phoenix (Apache-2.0)**：OpenInference 规范 XAI 指标卡，事实忠实度与因果图联动；
3. **Dify (Apache-2.0)**：SVG 虚线流光动态连线（Animated Stroke Dasharray），自定义节点状态指示灯；
4. **Vue Flow (MIT)**：Vue 3 组合式 API 画布，原生 `onlyRenderVisibleElements` 视口虚拟化，Dagre 分层对齐；
5. **W3C Web Cryptography API**：`window.crypto.subtle.digest('SHA-256')` 原生硬件加速，彻底移除第三方库依赖；
6. **Apache ECharts (Apache-2.0)**：Canvas 高性能时序面积图与脉冲雷达图，单色钛金属灰阶滤镜。

---

## 三、可迁移与不可迁移结论 (C. 可迁移与不可迁移结论)

### 3.1 可直接采纳的结论
- **Vue Flow 视口虚拟化**：开启 `onlyRenderVisibleElements: true`，在 500 个节点下仅渲染视野内的数十个节点，从根本上杜绝 DOM 爆炸；
- **RFC 6962 位级对齐**：纯前端使用原生 `Uint8Array` 拼接 `0x00`（叶子）与 `0x01`（内部双亲节点），左右兄弟采用 32 字节原始二进制拼接，与后端 Java `MessageDigest` 100% 位级对齐；
- **单色钛金毛玻璃分层规范**：全量复用 Phase 12/19 的 L0~L3 材质层级，背景为深邃微暗（`#0A0A0C`），卡片为半透明高斯模糊（`rgba(36, 36, 42, 0.85)` + `blur(8px)`），边框为微光（`rgba(255, 255, 255, 0.12)`）。

### 3.2 必须改造与增强的结论
- **8 大神经符号专属节点类型**：扩展通用开源节点为：`QueryNode`, `GuardrailNode`, `SlaNode`, `KnowledgeChunkNode`, `GraphReasoningNode`, `ConsensusNode`, `ModelExecutionNode`, `MerkleAnchorNode`；
- **反向因果脉冲高亮**：结合后端 `CausalAttributionGraph` 导出的逆向祖先拓扑，将因果链路上节点的连线赋予高亮钛金光晕，其余节点降权变暗（Opacity 0.15）；
- **动态步骤折叠动画**：在客户端验真卡片中，通过对数级循环阶梯式呈现当前哈希与兄弟哈希合成双亲哈希的微动效，验真成功呈现绿色印章。

### 3.3 坚决拒绝的技术陷阱
- **坚决拒绝使用 `crypto-js`**：纯 JS 数组运算性能极慢且容易产生内存碎片，强制使用原生 `window.crypto.subtle`；
- **坚决拒绝将 64 字符 Hex 字符串作为内部节点哈希的直接输入**：必须先转为 32 字节二进制再拼接，否则与 Java 后端产生严重歧义；
- **坚决拒绝在流式 Token 阶段触发表盘整图全量重绘**：必须采用局部 DOM 节点与 RAF 调度隔离。

---

## 四、候选方案比较 (D. 候选方案比较)

| 比较维度 | Baseline (现状) | 最小诊断方案 (仅修Bug) | 候选方案 (本方案：VueFlow+WebCrypto+ECharts) | 拒绝方案 (D3.js手写/纯服务端验真) |
| :--- | :--- | :--- | :--- | :--- |
| **正确性与可靠性** | 弱（无图，依赖服务端校验） | 低（仅能展示静态哈希列表） | **极高（RFC 6962 位级等价，定理 3.1 完备可靠）** | 中（手写易引入字节序与矩阵变换缺陷） |
| **大图交互性能** | 无图 | 无图 | **58~60 FPS（内置视口虚拟化裁剪，内存压降65%）**| 30~45 FPS（无底层虚拟化需大量手写优化） |
| **离线验真能力** | 0%（必须联网发请求） | 0% | **100%（纯客户端硬件加速，耗时 < 1.5ms）** | 依赖后端验证端点，破坏脱机信任 |
| **视觉一致性** | 风格分散 | 简易表格 | **100% 契合 Monochromatic Glassmorphism 规范** | 风格粗糙或高度不协调 |
| **可维护性与代码量** | 低 | 低 | **极高（Vue 3 SFC 插槽解耦，TypeScript 强类型）** | 极低（数千行底层 SVG DOM 胶水代码） |
| **生产风险与回滚** | 无 | 低 | **极低（纯增量视图组件，不破坏现有任何业务接口）** | 高（重构成本过高） |

**决策结论**：坚定采纳**候选方案**，以最小侵入增量构建生产级可视化大屏。

---

## 五、推荐的最小架构设计 (E. 推荐的最小架构)

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                      Phase 34 前端可视化大屏与后端数据服务协同架构全景图                                    │
│                                                                                                         │
│  [ 前端视图层 (frontend/src/views/audit) ]                                                              │
│  ├─ ExplainabilityDashboard.vue (主控制台大屏容器，Tab 联动与单色钛金毛玻璃布局)                           │
│  │                                                                                                      │
│  ├─ 模块 1: ExplainabilityTopologyCanvas.vue (神经符号因果拓扑画板)                                     │
│  │   ├─ Vue Flow 引擎 (开启 only-render-visible-elements 视口虚拟化)                                    │
│  │   ├─ Dagre 分层布局算法 (dagreLayout.ts，k=7 拓扑排序对齐，边垂直拉直)                                 │
│  │   ├─ 8 大自定义阶段节点 (Query, Guardrail, Sla, Chunk, Graph, Consensus, Model, Merkle)               │
│  │   ├─ 连线流光脉冲动画 (CSS Stroke Dasharray 硬件加速)                                                 │
│  │   ├─ 反向因果回溯链路高亮 (Causal Backpropagation Path，非关联节点降权 Opacity 0.15)                  │
│  │   └─ NodeDetailDrawer.vue (侧滑审计抽屉，展示 Latency / Tokens / 归因权重 / Payload)                   │
│  │                                                                                                      │
│  ├─ 模块 2: MerkleProofValidator.vue (RFC 6962 客户端免密验真器卡片)                                    │
│  │   ├─ JSON 存证证书上传解析 (拖拽或本地导入)                                                           │
│  │   ├─ 原生 WebCrypto 零拷贝计算 (webCryptoRfc6962.ts，单字节域分离 0x00/0x01，Uint8Array 缓冲区复用)     │
│  │   ├─ 对数级兄弟路径逐层折叠动画 (L1 -> L2 -> ... -> Root 计算进度)                                   │
│  │   ├─ 数字绿色防伪印章展示 (RFC 6962 VERIFIED vs TAMPER DETECTED)                                     │
│  │   └─ 导出结构化审计报告 (JSON / 打印友好型)                                                          │
│  │                                                                                                      │
│  └─ 模块 3: GuardrailDashboard.vue (安全护栏态势感知大屏)                                                │
│      ├─ 4 大核心指标微卡片 (脱敏率、PII 实体拦截数、对抗提示词阻断率、事实忠实度均值)                     │
│      ├─ ECharts 24小时风险拦截态势面积走势图 (钛金单色流光渐变)                                          │
│      └─ ECharts 多维防御能力雷达图 (PII, 越狱, 红线敏感词, 幽灵引用自愈, 事实支撑)                       │
│                                                                                                         │
│  [ 后端服务扩展层 (backend/qknow-framework/qknow-ai - AuditVerificationController) ]                     │
│  ├─ GET /api/v1/audit/topology/{traceId} -> 导出 8 阶段拓扑节点与边集合 (CausalTopologyExportVO)        │
│  ├─ GET /api/v1/audit/topology/{traceId}/attribution -> 导出逆向因果回溯节点 ID 列表                     │
│  └─ GET /api/v1/audit/guardrail/metrics -> 导出安全护栏态势感知聚合指标 (GuardrailMetricsVO)             │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 六、实验与实现计划 (F. 实验与实现计划)

### 6.1 实施文件集合清单（最小修改范围原则）

#### 1. 前端新建文件 (`frontend/src/views/audit`)
- `frontend/src/views/audit/ExplainabilityDashboard.vue`：大屏主容器与 Tab 联动控制台；
- `frontend/src/views/audit/components/ExplainabilityTopologyCanvas.vue`：Vue Flow 拓扑画板；
- `frontend/src/views/audit/components/MerkleProofValidator.vue`：RFC 6962 纯前端 WebCrypto 验真器；
- `frontend/src/views/audit/components/GuardrailDashboard.vue`：安全护栏态势感知大屏；
- `frontend/src/views/audit/components/NodeDetailDrawer.vue`：节点审计抽屉组件；
- `frontend/src/views/audit/utils/webCryptoRfc6962.ts`：纯前端原生 WebCrypto 零拷贝密码学验真工具库；
- `frontend/src/views/audit/utils/dagreLayout.ts`：Dagre 分层排版工具；
- `frontend/src/views/audit/custom-nodes/`（8 大自定义节点组件：`QueryNode.vue`, `GuardrailNode.vue`, `SlaNode.vue`, `KnowledgeChunkNode.vue`, `GraphReasoningNode.vue`, `ConsensusNode.vue`, `ModelExecutionNode.vue`, `MerkleAnchorNode.vue`）；
- `frontend/src/api/audit.ts`：前端拓扑与态势感知接口请求封装。

#### 2. 后端扩展文件 (`backend/qknow-framework/qknow-ai`)
- `AuditVerificationController.java`：追加拓扑导出接口 `getTopology`、因果回溯接口 `getCausalAttribution` 以及态势指标接口 `getGuardrailMetrics`；
- `model/CausalTopologyExportVO.java`：拓扑数据传输模型（包含节点、边、全局统计）；
- `model/GuardrailMetricsVO.java`：安全护栏态势感知聚合指标模型。

#### 3. 自动化契约测试文件 (`backend/tests`)
- `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase34ExplainabilityAndAuditContractTest.java`：覆盖 10 项严苛契约测试。

### 6.2 契约测试用例矩阵 (10 项核心契约)

```java
public class Phase34ExplainabilityAndAuditContractTest {
    // 契约 1: 拓扑导出接口必须精确包含全链路 8 大核心阶段节点
    void contract01_topologyExportContainsAllEightStages();

    // 契约 2: 节点元数据完整性校验 (耗时、Token、归因权重在非空阶段必须具备)
    void contract02_nodeMetadataHasTimingTokenAndAttributionWeight();

    // 契约 3: 逆向因果回溯接口返回的路径集合必须为拓扑图的严格祖先无环子集
    void contract03_backwardAttributionReturnsValidAcyclicAncestors();

    // 契约 4: 密码学包含性证明导出模型与 RFC 6962 标准格式 100% 契合
    void contract04_merkleProofExportMatchesRfc6962Format();

    // 契约 5: Java 端哈希计算与客户端 WebCrypto 规范 (0x00 叶子 / 0x01 内部) 位级对齐
    void contract05_hashAlgorithmAlignsWithClientWebCryptoSpecification();

    // 契约 6: 证书篡改测试: 单比特篡改 (Bit-Flip) 时验真必须 100% 失败 (Fail-Close)
    void contract06_tamperedProofFailsVerification100Percent();

    // 契约 7: 安全护栏态势感知聚合指标接口包含 PII、越狱、红线及忠实度统计
    void contract07_guardrailMetricsAggregationCompleteness();

    // 契约 8: 拓扑节点时间戳单调非递减不变量与全局时序一致性
    void contract08_topologyTimestampMonotonicallyNonDecreasing();

    // 契约 9: 极值防御: 不存在的 TraceId 或空拓扑安全返回空对象而不是 500 异常
    void contract09_nonExistentTraceReturnsSafeEmptyVo();

    // 契约 10: 跨租户数据隔离: 租户 A 绝对无法读取租户 B 的因果拓扑与审计存证
    void contract10_multiTenantTopologyIsolationEnforced();
}
```

### 6.3 验证命令与测试计数
1. **专属契约测试验证命令**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest=Phase34ExplainabilityAndAuditContractTest
   ```
   **期望测试计数**：`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`。
2. **后端全量防退化回归测试命令**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests
   ```
   **期望测试计数**：`Tests run: 882, Failures: 0, Errors: 0, Skipped: 8`，`BUILD SUCCESS`。
3. **前端生产打包构建校验命令**：
   ```bash
   cd frontend && pnpm run build:prod
   ```
   **期望结果**：TypeScript 编译零错误，Vite 生产构建成功，退出码 0。

---

## 七、风险、停止条件与后续授权边界 (G. 风险与授权边界)

1. **残余风险说明**：
   - 客户端 WebCrypto 要求安全上下文（Secure Context，即 `HTTPS` 或 `localhost`）；在非安全 HTTP 部署下浏览器会禁用 `window.crypto.subtle`。前端代码必须包含降级检测与友好引导提示。
2. **立即停止条件 (Immediate Stop Conditions)**：
   - 若客户端计算的 Merkle Root 与后端 Java 计算结果出现任何单比特分歧，立即停止实施；
   - 若 Vue Flow 画布在 300 节点缩放平移时出现明显顿挫卡死（低于 30 FPS），立即停止上线并重构节点结构；
   - 后端防退化回归出现任何既有测试失败，立即回滚代码。
3. **后续授权边界**：
   - 第一回合严格完成学术/工业研报与本决策完备实施方案编制；
   - **在未获得用户明确指令批准前，绝不修改任何现有生产代码，绝不随意挂载非授权路由**。
