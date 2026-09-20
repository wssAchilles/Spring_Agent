# Phase 124 实施详案与工程契约文件

## 课题：支柱四：前端工作流交互与开发者体验 —— 可视化 DAG 画布沉浸式调试、节点级状态回溯与人机协同审批 (HITL) 体验升华中枢

> **归档路径**：`docs/plans/phase_124_plan.md`  
> **基线环境约束**：
> - 核心定位：100% 聚焦企业级 AI-Native 知识库与智能体编排，严禁力学与空间在轨发散
> - 唯一生成模型：**DeepSeek API**（主干模型参数化思考模式 `thinking: {"type": "enabled"}`，严格遵循官方 API 契约与双轨传输规约）
> - 唯一向量模型：**阿里千问 (Qwen) Embedding**（1536 维超球面空间，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）
> - 彻底弃用声明：全系统绝无本地部署大模型，彻底弃用 OpenAI API
> - 隔离环境：**Java 21 虚拟环境**（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，严格禁止污染宿主 Java 17）
> - 前端设计：严格遵循 **UI/UX Pro Max 单色钛金毛玻璃 (Monochrome Titanium Frosted Glass)** 规范

---

### 一、 唯一待验证假设 (Hypothesis 124-H1)

> 在保持 Java 21 隔离环境、DeepSeek API 参数化思考模式、阿里千问 1536 维超球面向量基线不变的前提下：
> 1. 构建 **节点运行时动态参数热调优与局部重放引擎 (`HotTuningExecutionEngine.ts`)**，深度整合 HAMT 持久化结构共享快照树（`PersistentSnapshotTree`），在 50 步长测试集下单步快照增量内存开销严格有界于 $\mathcal{O}(\Delta_V)$，相较于 Naive 全量深拷贝**内存节约率 $\ge 85\%$**；
> 2. 任意历史步长重构寻址时间复杂度严格为 **$\mathcal{O}(1)$**，快照切换延迟 **$\le 5\text{ms}$**；在分叉点现场热修改参数派生独立不可变分支（Forking），原始历史快照保持深度冻结只读，**逆向时间数据污染发生率为 0**（原历史不变性保持率 100%）；
> 3. 构建 **后端反应式异步挂起与状态守恒治理中枢 (`WorkflowHitlReactiveGovernor.java`)** 与前端单色钛金毛玻璃人机协同审批中枢（`HitlApprovalMetacenter.vue`），当工作流触发 HITL 审批时实施非阻塞反应式挂起，挂起区间执行上下文状态哈希严格守恒（$\mathcal{H}(\sigma_t) \equiv \mathcal{H}(\sigma_{\text{barrier}})$）；在注入热补丁放行时严格保持因果偏序一致性，状态转移在有界拓扑下步数有界，**无死锁收敛概率为 1.0**；
> 4. 构建 **端到端不可变存证凭单**，纯 Java 21 Record 格式与前端 TypeScript 契约模型 100% 对齐，具备双向 HMAC-SHA256 密码学自签名与单比特篡改拦截能力（**Fail-Close 拦截率 100%**）；
> 5. 构建 **多智能体动态协同拓扑流向画板 (`SwarmDynamicTopologyCanvas.vue`)**，基于 AABB 视口空间相交测试（`VirtualizedDagCanvasEngine`）与一阶能量流光脉冲动力学衰减（`CanvasEnergyPulseEngine`），高密度节点并发拓扑下视口外图元剔除率 100%，单帧 CPU 主线程计算耗时严格 **$\le 3.5\text{ms}$**，渲染帧率稳定收敛在 **60fps**。

---

### 二、 拟实施文件清单与代码架构

#### 模块一：后端反应式挂起与状态守恒中枢 (`backend/qknow-hermes/qknow-hermes-core`)

1. **[NEW] `tech.qiantong.qknow.hermes.flow.hitl.engine.WorkflowHitlReactiveGovernor.java`**
   - 反应式异步挂起与状态守恒治理总控中枢；
   - 维护待决工单映射表 `ConcurrentHashMap<String, HitlApprovalTicket>` 与响应式挂起 CompletableFuture；
   - 实施状态守恒校验：挂起前计算上下文状态哈希 $\mathcal{H}(\sigma_{\text{barrier}})$，恢复时验真一致性；
   - 支持三种决策流转：
     - `APPROVE`：直接放行恢复；
     - `REJECT`：优雅终止工作流；
     - `PATCH_AND_APPROVE`：注入热补丁参数，计算热补丁 SHA-256 指纹，派生因果分支；
   - 签发并自动验真不可变存证凭单 `WorkflowDebugReceipt`；
   - 超时自动安全熔断降级（Fail-Close）。

#### 模块二：前端工作流画板与动态热调优中枢 (`frontend/src/views/kb/bot/build`)

2. **[NEW] `components/debug/engine/HotTuningExecutionEngine.ts`**
   - 节点运行时动态参数热调优与局部重放引擎；
   - 接入 `PersistentSnapshotTree` 与 `TimeTravelForkEngine`；
   - 提供 `applyHotPatchAndFork(sourceStepIndex, mutatedVariables)` 接口；
   - 计算变更变量哈希 `mutatedVariablesHash`，派生独立快照分支并深度冻结原历史快照；
   - 对齐后端 Java 21 Record 的密码学自签名计算 `computeReceiptSignature`。

3. **[NEW] `components/swarm/SwarmDynamicTopologyCanvas.vue`**
   - 多智能体动态协同拓扑流向画板；
   - 基于 UI/UX Pro Max 单色钛金毛玻璃质感规范（`#020203` Base, `rgba(10, 10, 12, 0.75)` Glass, `blur(24px) saturate(190%)`）；
   - 深度整合 `VirtualizedDagCanvasEngine` 视口空间相交测试（AABB Culling），自动剔除不可见节点；
   - 整合 `CanvasEnergyPulseEngine` 一阶能量阻尼脉冲流光与粒子轨迹动画；
   - 动态呈现 Phase 121 Swarm 委托网络、Phase 122 MCP 工具调用沙箱与 Phase 123 GraphRAG 因果子图。

4. **[MODIFY] `components/debug/HitlApprovalMetacenter.vue`**
   - 升华单色钛金毛玻璃人机协同审批中枢；
   - 接入 `WorkflowHitlReactiveGovernor` 状态守恒检验；
   - 增强热补丁差异高亮面板（Diff Inspector）与密码学存证卡片展示。

#### 模块三：测试套件与全量回归 (`backend/tests` & `frontend/tests`)

5. **[NEW] `backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/hitl/Phase124WorkflowHitlGovernorContractTest.java`**
   - 遵循 Java 21 隔离环境规范与 JUnit 5 体系；
   - 覆盖后端 8 大严苛契约测试：
     1. 反应式异步挂起与工单注册瞬时性（耗时 $\le 2\text{ms}$）；
     2. 状态守恒不变量 $\mathcal{H}(\sigma_t) \equiv \mathcal{H}(\sigma_{\text{barrier}})$ 严格保持；
     3. 现场热补丁注入与 SHA-256 变更指纹计算准确性；
     4. 恢复执行与因果分支派生隔离性；
     5. 拒绝终止状态流转与资源清理安全性；
     6. 超时熔断保护与安全降级不变性；
     7. 双向不可变存证凭单 HMAC-SHA256 自签名验真；
     8. 单比特恶意篡改立即阻断（Fail-Close）。

6. **[NEW] `frontend/tests/phase124_canvas_hitl_contract_test.ts`**
   - 遵循 Node / TSX 纯净断言体系；
   - 覆盖前端 8 大严苛契约测试：
     1. 定理 1.1 HAMT 持久化结构共享快照增量内存有界性 $\mathcal{O}(\Delta_V)$ 与 $\ge 85\%$ 节约率；
     2. 定理 1.1 历史快照 $\mathcal{O}(1)$ 寻址重构与切换延迟 $\le 5\text{ms}$；
     3. 定理 1.1 节点运行时热调优分支隔离与原历史只读冻结（0 逆向污染）；
     4. 定理 1.2 HITL 异步挂起-热补丁恢复因果一致性与状态哈希守恒；
     5. 定理 1.2 反应式挂起有限步收敛界与无死锁确定性收敛（收敛概率 1.0）；
     6. 定理 1.3 视口 AABB 空间相交几何裁剪剔除率与单帧计算耗时 $\le 3.5\text{ms}$（稳态 60fps）；
     7. 定理 1.3 一阶能量流光脉冲动力学指数衰减与活跃通道自收敛；
     8. 端到端不可变存证凭单 HMAC-SHA256 自签名与单比特篡改拦截（与 Java 21 Record 对齐）。

---

### 三、 8 大严苛契约测试与验证指标矩阵

| 契约编号 | 契约名称 | 验证核心机制 | 对应理论 | 判定通过红线指标 |
| :--- | :--- | :--- | :--- | :--- |
| **契约 1** | **HAMT 增量内存有界性与节约率** | 50 步长下持久化结构共享树与 Naive 全量深拷贝对比 | 定理 1.1 | 增量内存开销 $\mathcal{O}(\Delta_V)$，内存节约率 $\ge 85\%$ |
| **契约 2** | **历史快照 $\mathcal{O}(1)$ 寻址与低延迟** | 100 次随机历史步长检索重构 | 定理 1.1 | 寻址复杂度 $\mathcal{O}(1)$，平均切换延迟 $\le 5\text{ms}$ |
| **契约 3** | **现场热调优分支隔离与防逆向污染** | 在步长 $\tau$ 实施分叉并注入热修改，检查原历史 | 定理 1.1 | 派生独立不可变 Branch，原历史快照 100% 保持只读未受污染 |
| **契约 4** | **HITL 反应式挂起与状态守恒** | 触发审批挂起并在挂起区间检查状态哈希 | 定理 1.2 | 阻塞时间严格为 0，挂起区间 $\mathcal{H}(\sigma_t) \equiv \mathcal{H}(\sigma_{\text{barrier}})$ |
| **契约 5** | **状态机有限步收敛与无死锁** | 审批放行与热补丁注入后因果影响锥推进 | 定理 1.2 | 状态转移步数 $S \le N_{\text{dag}} + K_{\max} |V_{\text{loop}}| < \infty$，无死锁概率 1.0 |
| **契约 6** | **AABB 视口几何裁剪与 60fps** | 模拟超大拓扑视口平移与节点外包围盒判定 | 定理 1.3 | 视口外图元 100% 物理剔除，单帧计算 $\le 3.5\text{ms}$，稳态 60fps |
| **契约 7** | **能量流光脉冲指数衰减收敛** | 消息流动能量脉冲 $\dot{E} = -\lambda E$ 动力学推演 | 定理 1.3 | 能量低于阈值自动解绑动画，动画活跃通道渐近收敛 |
| **契约 8** | **双向密码学存证自验真与防篡改** | 前端 TS 与后端 Java 21 Record 签名互通与单比特篡改 | 全系统防线四 | 合法凭单自验真 100% 通过，单比特篡改立即阻断 (Fail-Close) |

---

### 四、 验证命令与测试计数

#### 1. 前端专属契约测试：
```bash
npx tsx frontend/tests/phase124_canvas_hitl_contract_test.ts
```
- **预期计数**：**8 大严苛契约测试 100% 绿灯全过**。

#### 2. 后端专属契约测试（Java 21 隔离环境）：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-hermes/qknow-hermes-core,tests -Dtest="Phase124WorkflowHitlGovernorContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```
- **预期计数**：**8 项后端契约测试 100% 绿灯全过**。

#### 3. 跨模块全量联合回归测试（Phase 121 + Phase 122 + Phase 123 + Phase 124）：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-framework/qknow-ai,qknow-hermes/qknow-hermes-core,tests -Dtest="Phase121SwarmDelegationContractTest,Phase122McpSecuritySandboxContractTest,Phase123HierarchicalGraphRagContractTest,Phase124WorkflowHitlGovernorContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```
- **预期计数**：**32 项跨模块核心测试 100% 绿灯全过，0 失败 0 错误**。

#### 4. 前端生产构建全量走查：
```bash
cd frontend && npm run build:prod
```
- **预期指标**：Vite 生产打包纯净构建成功，0 错误 0 警告。
