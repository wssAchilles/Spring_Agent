# Phase 128 实施计划：状态图 (StateGraph) 可视化交互、微秒级状态一致性与全链路因果调试中枢
## (StateGraph Dynamic Visual Interaction, Microsecond State Consistency & End-to-End Causal Debugging Metacenter)

> **归档路径**：`docs/plans/phase_128_plan.md` 及当前实施工件  
> **制定时间**：2026-09-24  
> **所属阶段**：第六演进阶段 (Phase 125 ~ Phase 128) 第四步骤（压轴收官）  
> **所属核心支柱**：支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)  
> **学术科研对齐**：ESWA 顶刊论文 Section 4.3 与 Section 9；彻底抹平循环图动态可视化交互、微秒级状态一致性与全链路因果调试缺口；对齐 Leslie Lamport 状态快照偏序一致性、Okasaki 纯函数式持久化数据结构 (Persistent Data Structures) 与李雅普诺夫渲染稳定性理论  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（主干模型参数化思考 `thinking: {"type": "enabled"}`）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面几何流形，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）；全系统绝无本地部署大模型，彻底弃用 OpenAI API；编译与运行统一使用隔离 **Java 21** 虚拟环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）；前端严格遵循 **UI/UX Pro Max 单色钛金毛玻璃** 规范。

---

## 目标陈述 (Goal Description)

在系统既有实现中，状态图调度器 `StateGraphScheduler` 驱动基于 Google Pregel 的循环超步推进，包含收敛门禁与自愈路由。然而，在**前端可视化交互与全链路因果调试**维度存在以下三大缺口：
1. **循环超步事件风暴引发渲染阻塞**：状态图在包含循环回退边时，超步推进可能在数十毫秒内产生数十次状态突变，若未加版本纪元控制与防抖合并，前端将频繁触发重绘卡顿；
2. **时空调试时光旅行（Time-Travel Debugging）缺乏持久化结构共享**：在前端暂停或回溯历史超步并热修改局部变量时，直接在当前 `sharedState` 上修改会导致旧分支与新分支数据混合，产生严重的幽灵状态污染；
3. **前端状态回显缺乏双向密码学防篡改存证**：前端画布与后端调度器之间缺乏轻量级自签名凭单自验真。

**Phase 128 目标**：构建生产级不可变时光旅行调试器（`StateGraphTimeTravelDebugger`）与微秒级前后端状态同步引擎（`StateGraphVisualSyncEngine`），配套不可变自签名存证凭单（`StateGraphCausalDebugReceipt`），在数学上严格证明结构共享状态完备性（定理 1.1）与李雅普诺夫稳态收敛性（定理 1.2），在工程上实现 0 幽灵污染、0 事件风暴与微秒级极速响应。

---

## 用户审查重点 (User Review Required)

> [!IMPORTANT]
> **结构共享与分支隔离无幽灵污染保证 (Zero Phantom State Pollution)**：
> 调试器采用持久化结构共享技术，在某个历史超步 $k$ 热补丁变量并分叉出分支 `forkBranchId` 时，底层仅克隆包含补丁的增量视图并绑定至派生分支，绝不覆写主分支任何历史快照。原分支在回退或继续推进时读到的状态 100% 保持历史真实性。

> [!TIP]
> **版本纪元与视口 AABB 裁剪推流**：
> 同步引擎引入单调递增版本纪元 `epoch`，过期的陈旧到达事件被 100% 物理拦截；结合 16ms（60FPS 垂直同步周期）防抖合并窗口，将事件流控至稳态；同时根据前端视口轴对齐包围盒（AABB）裁剪屏幕外节点状态，推流开销降低 80% 以上。

---

## 科研门禁规范详案 (AGENTS.md Compliance)

### A. 当前代码与失败机制剖析

#### 1. 真实执行路径追踪
系统当前状态图调度路径为：
`tech.qiantong.qknow.hermes.flow.stategraph.engine.StateGraphScheduler.java`。
调度器在每个同步超步通过 `context.incrementSuperstep()` 推进，并由 `ConvergenceLoopGuard` 与 `NodeSelfHealingRouter` 判定出边与自愈。

#### 2. 三大核心生产失败模式 (Failure Modes)
1. **模式一：双向数据流循环事件风暴打垮前端主线程**：
   超步循环产生的密集事件在推流给前端时，导致 Vue 响应式依赖收集每秒数百次触发，主线程掉帧卡死；
2. **模式二：时间旅行回溯热修改导致幽灵变量污染**：
   开发者在前端回退至超步 2 修改输入并重新试跑，若原地修改共享 Map，导致后续未重新执行节点的依赖读到了脏数据，原主干执行轨迹被污染；
3. **模式三：视口外无用渲染与网络带宽浪费**：
   大型状态图包含 50+ 节点时，用户仅聚焦局部 5 个节点，后端全量广播每个节点的状态变更，造成严重无谓序列化与渲染损耗。

#### 3. 本阶段唯一待验证假设 (H-PHASE128-001)
> **假设陈述**：通过在状态图调度中引入基于持久化结构共享的不可变超步快照管理器（`StateGraphTimeTravelDebugger`）与具备单调纪元防抖及视口 AABB 裁剪的同步引擎（`StateGraphVisualSyncEngine`），系统能够实现：
> 1. 超步快照捕获与任意历史时刻时间旅行寻址时间复杂度严格为 $\mathcal{O}(1)$，增量内存开销 $\mathcal{O}(\Delta V)$；
> 2. 从任意历史超步派生热调试分支，主分支与分叉分支之间因果隔离，幽灵变量污染率为 0.0%；
> 3. 在 16ms 周期内针对高频超步循环事件实现幂等防抖合并，主线程稳态达到 60FPS 零卡顿；
> 4. 视口 AABB 裁剪将屏幕外节点变更 100% 过滤，网络推流载荷削减 $\ge 75\%$；
> 5. 单步调试操作纯内存计算耗时 $\le 20\mu\text{s}$。

---

### B. Research Ledger (6 篇顶级学术文献规范调研)

详见 `docs/plans/phase_128_academic_report.md`，覆盖 Chris Okasaki 持久化数据结构、Google Pregel 超步同步、Leslie Lamport 因果时钟、Foster-Lyapunov 队列稳定性、AABB 视口剔除与密码学凭单 6 大权威文献，全部填满 14 项法定字段。

---

### C. 理论基础与严密数学证明

- **定理 1.1（基于结构共享的状态图不可变时间旅行状态完备性与内存有界性定理）**：
  证明回溯至历史任意超步 $k$ 的状态重构复杂度严格为 $\mathcal{O}(1)$，增量内存开销为 $\mathcal{O}(\Delta V)$，派生热调试分支幽灵污染率为 0。
- **定理 1.2（Pregel 超步同步与前端流式脉冲渲染的李雅普诺夫稳态收敛定理）**：
  证明在 16ms 防抖合并与 AABB 视口裁剪下，等效事件到达率被截断，队列长度强渐近稳定，单帧计算耗时 $\le 3.5\text{ms}$，刷新率稳定维持 60FPS。

---

### D. 候选方案比较

| 比较维度 | Baseline (当前现状) | 最小诊断方案 (全量深拷贝) | 本方案 (持久化结构共享 + 纪元防抖 AABB 裁剪) | 保持现状 |
| :--- | :--- | :--- | :--- | :--- |
| **正确性** | 差 (无时间旅行，变量被直接覆盖) | 中 (支持回溯，但内存极度危险) | **极高 (定理 1.1 与 1.2 数学证明完备)** | 极差 |
| **幽灵污染率** | 100% 存在污染 | 0% (全量拷贝) | **0.0% (结构共享 COW 完全隔离)** | 100% |
| **快照内存开销** | $\mathcal{O}(1)$ (仅存当前态) | $\mathcal{O}(S \cdot V)$ (严重膨胀，易 OOM) | **$\mathcal{O}(S \cdot \Delta V)$ (结构共享，节约 90%+)** | $\mathcal{O}(1)$ |
| **历史寻址时延** | 不支持 | $\mathcal{O}(1)$ | **$\mathcal{O}(1)$ 快速寻址** | 不支持 |
| **推流事件防暴** | 无 (高频循环冲垮前端) | 简单粗暴限流 (易丢关键状态) | **单调纪元 + 16ms 防抖合并 (稳态 60FPS)**| 无 |
| **视口优化** | 无 (全量广播) | 无 (全量广播) | **AABB 空间裁剪 (削减 $\ge 75\%$ 载荷)** | 无 |
| **密码学验真** | 无轻量调试凭单 | 无 | **SHA-256 自签名与常数时间验真** | 无 |

---

### E. 推荐的最小架构实现设计

1. **不可变因果调试存证凭单**：
   - 类名：`StateGraphCausalDebugReceipt.java`
   - 路径：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/stategraph/dto/StateGraphCausalDebugReceipt.java`
   - 纯 Java 21 Record 格式，封装快照哈希、分支 ID、超步号与 SHA-256 签名。
2. **不可变结构共享时光旅行调试器**：
   - 类名：`StateGraphTimeTravelDebugger.java`
   - 路径：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/stategraph/engine/StateGraphTimeTravelDebugger.java`
   - 支持定长窗口超步快照捕获、$\mathcal{O}(1)$ 历史寻址、单步步退/步进与热补丁分支派生。
3. **微秒级状态同步与流控引擎**：
   - 类名：`StateGraphVisualSyncEngine.java`
   - 路径：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/stategraph/engine/StateGraphVisualSyncEngine.java`
   - 维护单调版本纪元、16ms 循环防抖合并与视口 AABB 裁剪推流。

---

### F. 契约测试规范 (TDD 8 项硬核指标)

测试文件路径：`backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/stategraph/Phase128StateGraphVisualDebugContractTest.java`

- **契约 1**：超步快照不可变捕获与 $\mathcal{O}(1)$ 时间旅行状态完备性（定理 1.1）
- **契约 2**：历史超步派生分叉分支隔离，幽灵变量污染率为 0.0%（定理 1.1）
- **契约 3**：单步步退（Step Backward）与单步步进（Step Forward）因果偏序一致
- **契约 4**：单调纪元版本控制（Epoch Versioning）与过期事件阻断过滤
- **契约 5**：循环超步 16ms 防抖合并，李雅普诺夫稳态收敛防事件风暴（定理 1.2）
- **契约 6**：视口 AABB 裁剪推流准确率 100%，视口外图元被微秒级剔除
- **契约 7**：纯 Java 21 Record 密码学自签名与常数时间验真防篡改
- **契约 8**：端到端高频调度集成与微秒级极速性能预算（单步调试耗时 $\le 20\mu\text{s}$）

---

### G. 风险、停止条件与边界治理

- **风险 1**：超长循环（如 1000+ 超步）引发快照快照树内存无界消耗；
  - *防御对策*：设置定长滑动窗口（默认 $W=64$），超过窗口自动淘汰最老快照，并支持关键里程碑快照锁定。
- **风险 2**：浮点数坐标在 AABB 视口相交测试中的舍入误差；
  - *防御对策*：引入带外扩缓冲区（Margin $= 50.0\text{px}$）的膨胀 AABB 测试，确保临界边缘节点平滑进出。
- **停止条件**：若任一契约测试未通过、存在幽灵变量污染或单步调试耗时超过 $50\mu\text{s}$，立即停止进入生产交付。
