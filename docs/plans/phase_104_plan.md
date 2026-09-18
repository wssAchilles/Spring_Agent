# Phase 104 决策完备实施详案与可证伪工程契约
## 前端可视化 DAG 工作流交互画布、节点级状态快照回溯与人机协同审批 (HITL) 交互调试中枢 (Interactive Workflow Canvas, Node-Level State Travel & HITL Debugger Metacenter)

> **制定时间**：2026-09-18  
> **核心假设**：`H-PHASE104-001`  
> **战略定位**：严格遵守《业务定位与领域边界铁律（铁律九）》，100% 聚焦于**支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)**，深度联动支柱一（复杂业务 Agent 编排）与支柱二（生产级企业 MCP 工具生态）。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（V3/R1）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面几何流形 $\mathbb{S}^{1535}$）；全系统绝无本地部署大模型，彻底弃用 OpenAI API；后端编译与运行环境统一使用隔离 **Java 21** 虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）；前端统一采用 **Vue 3.4 + TypeScript + Pinia + Vite + @vue-flow/core**，严格遵循 **UI/UX Pro Max 单色钛金毛玻璃 (Monochrome Titanium Frosted Glass)** 规范。

---

### 一、阶段唯一核心科学假设 (`H-PHASE104-001`)

在唯一生成模型（DeepSeek API）与唯一向量模型（阿里千问 1536 维超球面）基线约束下，引入 DAG 拓扑分层弹性阻尼力导向松弛机制、定长环形快照窗口 COW 增量时空回溯引擎、以及渐进式认知投影人机审批状态机后：
1. **假设分支一（DAG 弹性阻尼松弛有限步收敛）**：将 Sugiyama 拓扑分层与带粘性阻尼（$\gamma > 0$）的非定常力学势能松弛结合，能够保证系统相轨迹在有限迭代步 **$K \le 50$** 内单调收敛至能量极小驻点（$\|\nabla E\| < 10^{-4}$），边交叉数下降 **70% 以上**，节点重叠率严格为 **0.0%**，前端排布计算复杂度受控于 $\mathcal{O}(|V| \log |V| + |E|)$，单帧布局耗时 $\le 16\text{ms}$（稳态 60fps）；
2. **假设分支二（时空快照李雅普诺夫一致有界与零时间污染）**：定长环形快照窗口（容量 $M = 20$）配合 COW 增量结构共享，离散李雅普诺夫内存势函数严格满足一致有界性 $\sup_t V(t) \le M \cdot C_{\max} < \infty$，杜绝内存泄漏；时光旅行回溯算子在因果偏序约束下，历史状态恢复偏序一致性达到 **100.0%**，彻底杜绝反向时间污染；
3. **假设分支三（认知负荷渐进投影与零死锁恢复）**：通过渐进式风险参数投影算子 $\Pi_{\text{focus}}(C)$ 将审批上下文有效 token 压缩 $75\%$ 以上，使人类审批决策潜伏期均值下降 **$\ge 60\%$**（从基线 $>45\text{s}$ 压制至 $\le 18\text{s}$）；在双向心跳与看门狗状态机驱动下，审批通道死锁概率恒等于 **$0.0\%$**，超时触发 Fail-Close 事务原子补偿，实现遍历确定性恢复。

---

### 二、系统核心执行组件解耦设计

#### 1. 前端可视化与交互调试组件清单 (`frontend/`)
- **`src/views/kb/bot/build/components/canvas/engine/VirtualizedDagCanvasEngine.ts`** [NEW]：
  - 高性能虚拟化 DAG 画布渲染引擎；
  - 核心能力：视口包围盒 AABB 相交测试（外扩 200px 缓冲垫）、三级 LOD 平滑切换（LOD 0 全量表单、LOD 1 精简卡片、LOD 2 纯色单色钛金几何胶囊）、自适应三次贝塞尔平滑连线方程计算与微秒级 Handle 磁吸检测；
- **`src/views/kb/bot/build/components/debug/engine/NodeLevelTimeTravelDebugger.ts`** [NEW]：
  - 节点级时空快照时光旅行调试器；
  - 核心能力：定长 20 步环形内存快照池（Ring Buffer）、递归不可变深度冻结（`deepFreeze`）、步过 (Step Over)、步入 (Step Into)、回退 (Step Back)、断点悬停 (Breakpoint Pause) 与分叉派生隔离；
- **`src/views/kb/bot/build/components/hitl/HitlApprovalModal.vue`** [NEW]：
  - 沉浸式单色钛金高保真毛玻璃人机二次审批弹窗；
  - 遵循 UI/UX Pro Max 规范与 iOS 26 单色钛金质感（`backdrop-filter: blur(24px) saturate(190%)`，微米级反光边框）；展示敏感参数 Unified Diff、爆炸半径评估、双重滑块确认解锁与 15s 心跳保活；

#### 2. 后端执行引擎与凭证核验组件清单 (`backend/qknow-hermes/`)
- **`tech.qiantong.qknow.hermes.flow.hitl.model.WorkflowDebugReceipt.java`** [NEW]：
  - 纯 Java 21 Record 格式不可变工作流调试存证凭单；
  - 包含 `receiptId`, `executionBatchId`, `workflowId`, `totalExecutedSteps`, `breakpointsHitCount`, `timeTravelStepCount`, `hitlTicketsHandledCount`, `operatorUserId`, `startTimestampMicros`, `endTimestampMicros`, `finalStatus`, `signature`；
  - 内建 `verifySignature()` SHA-256 密码学验真方法；
- **`tech.qiantong.qknow.hermes.flow.hitl.engine.TimeTravelSnapshotRingBuffer.java`** [NEW]：
  - 后端线程安全定长 20 步环形快照池；
  - 严格保持 $\sup_t V(t) \le M \cdot C_{\max}$，自动淘汰最老快照，防止长时间运行 OOM；
  - 提供不可变镜像切片与快速因果偏序校验；
- **`tech.qiantong.qknow.hermes.flow.hitl.engine.CognitiveProjectionFilter.java`** [NEW]：
  - 渐进式风险上下文投影算子 $\Pi_{\text{focus}}(C)$；
  - 差分提炼增量变量、过滤只读冗余常量，压缩比 $\ge 75\%$，直击高危阻断根因。

---

### 三、精确验证命令与测试用例清单

#### 1. 自动化测试文件清单 (`backend/tests/` & `frontend/`)
- **`backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/hitl/WorkflowDebugReceiptTest.java`** [NEW]：
  - 验证正常创建签名凭证自验真通过（100% 通过率）；
  - 反事实消融：单字段篡改自验真失败拦截；
  - 边界参数与空值防御测试；
- **`backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/hitl/TimeTravelSnapshotRingBufferTest.java`** [NEW]：
  - 验证环形缓冲区固定 20 步容量淘汰机制；
  - 验证时空快照回溯偏序一致性（100% 一致）；
  - 验证修改回溯快照时触发 COW 分叉隔离，杜绝反向时间污染；
- **`backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/hitl/CognitiveProjectionFilterTest.java`** [NEW]：
  - 验证渐进式投影算子 $\Pi_{\text{focus}}$ 对庞大上下文变量的精炼压缩率 $\ge 75\%$；
  - 验证高危破坏性参数（`DROP`, `DELETE`）100% 精确保留；
- **`backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/hitl/Phase104HitlCanvasIntegrationTest.java`** [NEW]：
  - 全链路协同集成测试：DAG 拓扑初始化 -> 节点断点触发暂停 -> 时光旅行向后回退 -> 命中 Phase 103 高危工具触发 HITL 挂起 -> 模拟前端审批放行 -> 流程恢复 -> 签发双向 SHA-256 审计凭单；
- **前端单元测试**：
  - 编写前端逻辑单元验证脚本或测试用例，验证 `VirtualizedDagCanvasEngine` 视口裁剪、`RenderLodLevel` 三级判定、三次贝塞尔连线吸附与 `NodeLevelTimeTravelDebugger` 深度冻结机制。

#### 2. 精确后端测试验证命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-hermes/qknow-hermes-core,qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client,tests -Dtest="tech.qiantong.qknow.hermes.flow.hitl.*Test" -Dsurefire.failIfNoSpecifiedTests=false
```

---

### 四、实施纪律与严禁修改边界

1. **严格禁止修改边界**：
   - 严禁修改 Phase 101 的 `tech.qiantong.qknow.hermes.flow.stategraph.*` 状态图调度核心；
   - 严禁修改 Phase 102 的 `tech.qiantong.qknow.hermes.agent.swarm.*` 辩论与交接核心；
   - 严禁修改 Phase 103 的 `tech.qiantong.qknow.mcp.client.*` MCP 协议栈与语义路由核心；
   - 严禁修改 `tech.qiantong.qknow.ai.embodied.*` 封存力学资产；
   - 严禁修改父 POM Java 21 版本定义。
2. **唯一模型与环境铁律**：
   - 唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 1536 维超球面，绝无本地大模型；
   - 严格使用 SDKMAN 隔离环境 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`。
