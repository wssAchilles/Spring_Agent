# Phase 120 实施详案与工程契约文件

## 课题：支柱四：前端工作流交互与开发者体验 —— 可视化 DAG 画布沉浸式调试与 HITL 审批体验升华 (Visual DAG Canvas Immersive Debugging & HITL Human-in-the-Loop Approval Metacenter)

> **归档路径**：`docs/plans/phase_120_plan.md`  
> **基线环境约束**：
> - 唯一生成模型：DeepSeek API（主干模型，参数化思考模式 `thinking: {"type": "enabled"}`, `reasoning_effort: "high"`, 绝无 r1 称呼）
> - 唯一向量模型：阿里千问 (Qwen) Embedding (1536 维超球面空间，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$)
> - 隔离环境：Java 21 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)；前端 Vue 3 + Vite + TypeScript
> - 业务边界：100% 聚焦企业级知识库与智能体编排，严禁力学发散
> - 界面设计规范：严格遵循 `.shared/ui-ux-pro-max` 单色钛金毛玻璃设计 Token

---

### 一、唯一待验证假设 (Hypothesis 120-H1)

> 在保持 Java 21 隔离环境、DeepSeek API 参数化思考模式、阿里千问 1536 维超球面向量基线不变的前提下：
> 1. 构建**轻量持久化结构共享状态快照树 (`PersistentSnapshotTree`)**，以变量哈希路径实施路径复制（Path Copying），单步快照增量内存开销严格有界于 $\mathcal{O}(\Delta_V)$，相较于 Naive 深拷贝在 50 步长测试集下内存开销**节约 $\ge 85\%$**；
> 2. 增强**时空分叉与断点执行引擎 (`TimeTravelForkEngine`)**，接入结构共享快照树，历史时刻状态重构寻址时间复杂度严格为 **$\mathcal{O}(1)$**，任意历史步长切换延迟 **$\le 5\text{ms}$**；在分叉点热修改变量派生独立不可变 Branch，彻底**杜绝逆向时间数据污染**；
> 3. 构建**单色钛金毛玻璃人机协同审批中枢 (`HitlApprovalMetacenter.vue`)**，当工作流挂起时激活全屏毛玻璃抽屉，展示前驱数据流切片与变量差异对比，支持在线热补丁（Hot Patching），且与后端不可变分叉凭单（`WorkflowForkReceipt`）及调试存证凭单（`WorkflowDebugReceipt`）SHA-256 自签名验真 100% 对齐，在注入热修改时严格保持因果偏序一致性，**无死锁收敛概率为 $1.0$**；
> 4. 增强**工作流调试运行面板 (`WorkflowDebugRunPanel.vue`)**，集成 Whyline 风格因果分析探针与时间旅行滑块，单色钛金工业设计使开发者定位关键决策因果链路的认知负荷**降低 $\ge 40\%$**。

---

### 二、拟实施文件清单与代码架构

#### 模块：`frontend/src/views/kb/bot/build`

1. **[NEW] `components/debug/engine/PersistentSnapshotTree.ts`**
   - 纯 TypeScript 实现的不可变持久化字典树（HAMT 结构共享模型）；
   - 分支因子 $B=32$（5-bit 掩码），树高 $H \le 4$；
   - 路径复制算子 `set(key, value)`：仅复制自根至叶路径上的节点，其余子树全量结构共享；
   - 提供 `get(key)`、`toObject()`、`deltaKeys()` 与 `computeDigest()`；
   - 维护 `snapshotRoots: ReadonlyArray<SnapshotRoot>`，提供 $\mathcal{O}(1)$ 时间的历史时刻检索。

2. **[MODIFY] `components/debug/engine/TimeTravelForkEngine.ts`**
   - 接入 `PersistentSnapshotTree` 作为底层状态存储，废弃全量 `Object.freeze` 浅/深拷贝；
   - 支持在历史任意步长 $t$ 进行分叉派生 `forkFromStep()`，生成 `ForkExecutionBranch`；
   - 在分叉点注入热补丁并计算 SHA-256 哈希 `mutatedVariablesHash`；
   - 严格保证原始分支与历史快照的只读不可变性，杜绝跨时空反向污染。

3. **[NEW] `components/debug/HitlApprovalMetacenter.vue`**
   - 基于 UI/UX Pro Max 规范的单色钛金毛玻璃悬浮审批中枢；
   - 视觉规范：`#020203`（Canvas Base）、`#0a0a0c`（Card Elevated）、`rgba(255,255,255,0.08)`（Border Subtle）；
   - 顶部展示工单编号、危险级别与审批节点；
   - 中部展示原始入参与热补丁参数只读/编辑对比面板；
   - 底部提供“拒绝终止”与“批准并热修改放行”操作，一键签署生成防篡改凭单哈希。

4. **[MODIFY] `components/WorkflowDebugRunPanel.vue`**
   - 引入 Whyline 风格因果溯源探针（`Whyline Causal Inspector`）：点击节点展示直接前驱与变量依赖；
   - 引入时间旅行时间轴滑块（`Time Travel Timeline Slider`）：绑定 `TimeTravelForkEngine`，支持历史步长瞬时拖拽预览；
   - 整合 `HitlApprovalMetacenter` 弹窗联动，实现挂起时的沉浸式审批交互。

#### 模块：`frontend/tests`

5. **[NEW] `tests/phase120_canvas_hitl_contract_test.ts`**
   - Phase 120 完备契约测试，精确覆盖 5 大核心契约：
     1. 契约 1：定理 1.1 持久化结构共享快照树内存有界性 $\mathcal{O}(\Delta_V)$ 与节约率 $\ge 85\%$（对比 Naive 深拷贝）；
     2. 契约 2：定理 1.1 历史快照 $\mathcal{O}(1)$ 寻址重构与切换延迟 $\le 5\text{ms}$；
     3. 契约 3：定理 1.2 现场热补丁因果分支隔离与绝对防逆向时空污染（历史只读冻结）；
     4. 契约 4：定理 1.2 HITL 异步挂起-热补丁恢复因果一致性与确定性收敛（无死锁概率 1.0）；
     5. 契约 5：端到端密码学存证凭单 SHA-256 自签名与单比特篡改拦截（与后端 Java 21 Record 算法 100% 对齐）。

---

### 三、验证命令与测试计数

#### 前端契约测试：
```bash
npx tsx tests/phase120_canvas_hitl_contract_test.ts
```
预期测试计数：**5 大核心契约测试 100% 绿灯通过**。

#### 后端跨模块全量回归验证：
在隔离虚拟环境 Java 21 下执行：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-framework/qknow-ai,qknow-hermes/qknow-hermes-core,tests -Dtest="Phase119HierarchicalGraphRagContractTest,Phase118McpSagaPipelineContractTest,Phase117NashDebateConsensusContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```
预期测试计数：**22 项跨模块历史测试 100% 绿灯全量通过**。
