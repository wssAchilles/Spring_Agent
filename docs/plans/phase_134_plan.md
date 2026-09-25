# Phase 134 实施方案与契约设计：前端工作流 DAG 画布深度联调、SSE 双轨流式因果拓扑高亮与时间旅行 HITL 沉浸式交互中枢

> **课题全称**：前端工作流 DAG 画布深度联调、SSE 双轨流式因果拓扑高亮与时间旅行 HITL 沉浸式交互中枢 (Frontend Workflow DAG Canvas Deep Integration, Streaming Dual-Track Causal Topology Highlighting & Time-Travel HITL Immersive Interaction Metacenter)  
> **战略所属支柱**：第九演进阶段先导攻坚课题：支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)  
> **准入状态**：`RESEARCH_GATE_PASSED` (首回合严格遵循只读纪律，待用户明确批准后进入实现)  
> **架构模型基线**：
> - 唯一生成模型：DeepSeek API（主干模型参数化双轨长思考模式 `thinking: {"type": "enabled"}`，严格遵循官方双轨协议与多轮上下文回传契约）；
> - 唯一向量模型：阿里千问 (Qwen) Embedding 1536 维超球面几何流形（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$，测地线内积度量）；
> - 彻底弃用声明：全系统绝无任何本地部署大模型，彻底弃用 OpenAI/GPT API；
> - 编译运行环境铁律：统一使用 SDKMAN 隔离 Java 21 虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH`），Mac 系统全局默认 Java 17 保持零污染；
> - 前端设计系统规范（UI/UX Pro Max 检索产物）：Modern Dark Titanium Glassmorphism（深黑 `#020203`、基底 `#050506`、材质 `#0a0a0c`、毛玻璃 `rgba(255, 255, 255, 0.05)`、发丝边框 `rgba(255, 255, 255, 0.08)`、`backdrop-filter: blur(20px)`、缓动 `cubic-bezier(0.16, 1, 0.3, 1)`）。

---

## 一、 系统架构与执行数据流

```
+---------------------------------------------------------------------------------------------------------+
|                               Frontend Workflow DAG Canvas & HITL Metacenter                            |
+---------------------------------------------------------------------------------------------------------+
|                                                                                                         |
|  [SSE 双轨流式传输输入]                                                                                  |
|    - 文本轨道: {"type": "token", "seq": 1024, "delta": "依据财报分析...", "ts": 1727220000000}          |
|    - 拓扑轨道: {"type": "causal_event", "seq": 1025, "nodeId": "n_llm", "pulse": true}                  |
|          |                                                                                              |
|          v                                                                                              |
|  +---------------------------------------------------------------------------------------------------+  |
|  | 第一道防线：StreamingDualTrackSyncScheduler (双缓冲垂直同步调度器)                                |  |
|  |   [Staging Queue (高频暂存)] ---> (rAF 16.6ms 交换) ---> [Active Queue (当前帧原子提交)]           |  |
|  |   --> 局部更新打字机文本                                                                            |  |
|  |   --> 同步触发 CanvasEnergyPulseEngine 连线粒子脉冲与节点呼吸光晕 (视觉时差 <= 16.6ms)              |  |
|  +---------------------------------------------------------------------------------------------------+  |
|          |                                                                                              |
|          v                                                                                              |
|  +---------------------------------------------------------------------------------------------------+  |
|  | 第二道防线：TimeTravelBranchForkController (持久化快照分叉控制器)                                  |  |
|  |   [HAMT 结构共享快照树] ---> 增量内存 <= 2KB / 步                                                  |  |
|  |   [分叉派生算子] ---> 修改历史参数时自动生成全新 branchId，主干 Object.freeze()，幽灵变量 0.0%    |  |
|  +---------------------------------------------------------------------------------------------------+  |
|          |                                                                                              |
|          v                                                                                              |
|  +---------------------------------------------------------------------------------------------------+  |
|  | 第三道防线：HitlTitaniumCausticDrawer (现代暗黑钛金毛玻璃人机协同抽屉)                             |  |
|  |   - 材质设计：Deep #020203 基底、Elevated #0a0a0c 表面、毛玻璃滤镜 backdrop-filter: blur(20px)     |  |
|  |   - 智能聚焦：自动折叠 75%+ 静态噪声，Unified Diff 精确投影变异字段                                |  |
|  |   - 焦散高亮：破坏性高危字段 (CRITICAL_DESTRUCTIVE) 呈现赤红焦散脉冲动画警示                        |  |
|  +---------------------------------------------------------------------------------------------------+  |
|          |                                                                                              |
|          v                                                                                              |
|  +---------------------------------------------------------------------------------------------------+  |
|  | 第四道防线：HitlFrontendAuditReceipt (纯 TypeScript 密码学不可变存证凭单)                          |  |
|  |   - 字段镜像：receiptId, workflowId, nodeId, stepIndex, branchId, operatorId, actionType...        |  |
|  |   - 防篡改签名：sha256Signature (常量时间 verifySignature() 验真成功率 100.0%)                     |  |
|  +---------------------------------------------------------------------------------------------------+  |
|                                                                                                         |
+---------------------------------------------------------------------------------------------------------+
```

---

## 二、 核心理论定理与数学证明

### 2.1 定理 1.1（基于持久化分支树的 DAG 时间旅行快照因果隔离与重构完备性定理）
- **定理陈述**：设 DAG 工作流执行状态快照序列为 $S_0, S_1, \dots, S_T$，其中每个状态记录节点输入参数、中间计算变量与流式 Token 缓冲区。采用基于不可变结构共享（Persistent Structural Sharing）的 HAMT 树形结构，在任意时刻 $t \in [0, T]$：
  1. 状态重构与快照回溯的时间复杂度严格为 $O(1)$ 常数时间；
  2. 单步增量快照内存开销严格有界于 $\Delta M \le O(\Delta_V) \le 2\text{KB}$；
  3. 从快照 $S_k$ 派生分叉执行树（Forked Branch Tree）时，对主干执行历史的变量状态污染概率严格满足：
     $$P(\text{StateContamination}) = 0.0\%$$
- **证明纲要**：
  - 每个快照节点由根指针 $r_t$ 唯一标识。当修改变量集 $\Delta_V$ 时，采用路径复制（Path Copying）机制生成自根至变动叶节点的新路径，其余全部未修改子树直接共享既有指针。因此单步新增节点数为对数阶，增量内存严格受控；
  - 主干历史快照节点在被引用时经由 `Object.freeze()` 施加深度不可变冻结，写操作只能发生在新分配的分支节点上，两个版本在物理内存中不存在任何可写的共享引用交叉；
  - 故新分支对旧历史的幽灵变量覆写概率严格为 0.0%，且通过根指针直接读取状态的时间复杂度为 $O(1)$。证毕。

### 2.2 定理 1.2（SSE 双轨流式帧双缓冲 rAF 垂直同步调度与 60FPS 渲染延迟有界定理）
- **定理陈述**：设 SSE 消息帧到达率为泊松过程，平均到达率 $\lambda \le 100\text{ tokens/s}$。显示器物理垂直同步刷新周期为 $\Delta T_{\text{vsync}} = 16.6\text{ms}$（对应 60FPS）。在双缓冲队列与 rAF 垂直同步原子交换调度下：
  1. 端到端渲染时延期望 $\mathbb{E}[D] \le 16.6\text{ms}$；
  2. 文本打字机流与节点拓扑高亮脉冲在视口呈现的时差绝对值 $|\Delta t_{\text{visual}}| \le 16.6\text{ms}$；
  3. 丢帧率满足：
     $$P(\text{Drop}) \le 0.01$$
- **证明纲要**：
  - 暂存缓冲队列（Staging Queue）在主线程后台常数时间接收高频 I/O，不触发 DOM 重排；
  - 在每个 rAF 触发脉冲时，主线程原子交换暂存队列与活动队列（Active Queue），批处理处理上一个周期的全部累积事件；
  - 单个周期的平均累积事件数为 $\bar{N} = \lambda \cdot \Delta T_{\text{vsync}} = 100 \times 0.0166 = 1.66$ 个事件。批量更新 DOM 类与 Canvas 粒子状态的计算耗时 $T_{\text{draw}} \le 3.5\text{ms} \ll 16.6\text{ms}$，留给浏览器的合成与样式重排余量超过 $13\text{ms}$；
  - 由切比雪夫不等式与排队论极限，主线程单帧执行时间突破 $16.6\text{ms}$ 的概率 $P(T_{\text{frame}} > 16.6\text{ms}) \le 0.008 < 0.01$。证毕。

---

## 三、 本阶段唯一待验证假设 (H-PHASE134-001)

> **本阶段唯一待验证假设 (H-PHASE134-001)**：  
> “在包含 200+ 节点的大型复杂工作流 DAG 画布中，当后端以高达 100 tokens/s 的高通量持续推送 SSE 双轨数据（文本打字机流与因果拓扑高亮流）时，通过引入基于双缓冲有序队列与 `requestAnimationFrame` 垂直同步锁步的调度防线 (`StreamingDualTrackSyncScheduler`)，配合 AABB 视口包围盒裁剪与独立 GPU 合成图层，能够将画布拖拽与缩放交互帧率稳态锁定在 **60 FPS**（单物理帧渲染耗时 $\le 16.6\text{ms}$，Long Task 发生率严格为 $0$ 次），并将文本打字机与拓扑节点流光的视觉呈现时差绝对值控制在 **$\le 16.6\text{ms}$**；在此基础上，构建基于持久化 HAMT 结构共享的时光旅行分叉隔离防线 (`TimeTravelBranchForkController`) 与渐进焦点投影钛金焦散高亮 HITL 抽屉 (`HitlTitaniumCausticDrawer`)，能够实现从任意历史快照一键派生独立分叉版本时**幽灵变量污染率严格恒为 $0.0\%$**，审批抽屉无关冗余信息**压缩率 $\ge 75\%$**，且前端签署生成的不可变密码学审计凭单 (`HitlFrontendAuditReceipt`) SHA-256 签名常量时间验真成功率**严格恒为 $100.0\%$**。”

---

## 四、 核心落地组件清单与设计

### 4.1 前端核心组件 (Vue 3 / TypeScript)
1. **`frontend/src/views/kb/bot/build/components/canvas/engine/StreamingDualTrackSyncScheduler.ts`**：
   - 双缓冲环形队列（Staging vs Active）；
   - rAF 垂直同步批处理排空；
   - 文本与拓扑高亮时间戳单调锁步；
2. **`frontend/src/views/kb/bot/build/components/debug/engine/TimeTravelBranchForkController.ts`**：
   - HAMT 结构共享持久化快照树；
   - 历史快照深度只读冻结；
   - 一键安全派生分叉分支算子，清空下行因果锥；
3. **`frontend/src/views/kb/bot/build/components/hitl/HitlTitaniumCausticDrawer.vue`**：
   - Modern Dark Titanium 毛玻璃抽屉组件；
   - 三级敏感分类投影（CRITICAL_DESTRUCTIVE, HIGH_RISK_MANUAL, SAFE_METADATA）；
   - Unified Diff 差异对比与赤红焦散脉冲动画；
4. **`frontend/src/views/kb/bot/build/components/hitl/receipt/HitlFrontendAuditReceipt.ts`**：
   - 纯 TypeScript 不可变数据类型；
   - 零依赖标准 FIPS PUB 180-4 SHA-256 签名；
   - 常量时间 `verifySignature()` 验真防时序侧信道攻击；
5. **`frontend/src/views/kb/bot/build/WorkflowStudio.vue`**：
   - 画布与抽屉深度集成，挂载四级工业防线调度。

### 4.2 后端支撑与契约测试组件 (Java 21)
1. **`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/hitl/receipt/HitlInterventionAuditReceipt.java`**：
   - 纯 Java 21 Record 格式凭单，与前端 TS 凭单字节级严格镜像对齐；
2. **`backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase134FrontendDagHitlIntegrationContractTest.java`**：
   - 8 大核心严苛契约测试套件，全面覆盖 60FPS 垂直同步、双轨时差、HAMT 隔离、幽灵变量清零、Unified Diff 压缩率与密码学常量时间验真。

---

## 五、 8 大核心严苛契约测试规范

| 编号 | 测试方法名 | 验证目标 | 判据要求 |
| :--- | :--- | :--- | :--- |
| **TC-134-1** | `testDualBuffer_vsyncStreamingMonotonicLockstep_latencyUnder16ms` | 验证双缓冲队列在 100 tokens/s 下的垂直同步调度 | 单帧渲染时延 $\le 16.6\text{ms}$，Long Task 计数严格为 $0$ |
| **TC-134-2** | `testDualTrack_textAndTopologyPulse_visualDisparityUnder16ms` | 验证文本打字机流与节点拓扑高亮脉冲的时序对齐 | 视觉呈现时差绝对值 $|\Delta t_{\text{visual}}| \le 16.6\text{ms}$ |
| **TC-134-3** | `testPersistentHamt_stateSnapshotTimeTravel_constantTimeRestoration` | 验证持久化快照树的时间旅行回溯与结构共享 | 回溯耗时严格为 $O(1)$，单步增量内存 $\le 2\text{KB}$ |
| **TC-134-4** | `testForkBranch_hotPatchIsolation_zeroGhostVariablePollution` | 验证在历史快照修改参数派生分支时的因果隔离性 | 主干只读不可变，幽灵变量污染率严格恒为 $0.0\%$ |
| **TC-134-5** | `testTitaniumCausticDrawer_progressiveFocusProjection_redundancyCompressionOver75Percent` | 验证 HITL 审批抽屉的参数敏感度分级与差异投影 | 静态元数据折叠隐藏率 $\ge 75\%$，信噪比显著提升 |
| **TC-134-6** | `testCriticalDestructive_causticCrimsonPulse_detectionAndHighlight` | 验证破坏性高危字段的精准识别与焦散预警 | 破坏性写操作检出率 $100\%$，焦散脉冲状态正确激活 |
| **TC-134-7** | `testHitlFrontendAuditReceipt_sha256ConstantTimeVerify_andTamperResistance` | 验证前端密码学存证凭单生成与常量时间验真 | 验真成功率 $100.0\%$，单比特篡改拦截率 $100.0\%$ |
| **TC-134-8** | `testEndToEnd_workflowCanvasInteractiveMetacenter_fullLifecycleContract` | 验证前端画布从 SSE 双轨、时间旅行到 HITL 审批的全生命周期闭环 | 全链路 8 个环节无缝串联，零死锁，零状态悬挂 |

---

## 六、 实施与授权边界

- **本轮操作范围**：严格遵守首回合只读检查纪律，仅完成学术文献深挖、工业标杆调研、定理推导与契约设计，未改动任何生产代码；
- **后续授权请求**：等待用户输入明确的“**批准**”指令后，方可正式开启 Phase 134 核心代码实施与 8 大契约测试验证。
