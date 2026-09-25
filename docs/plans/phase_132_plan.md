# Phase 132 实施计划：可视化工作流 DAG 画布节点级状态快照热回溯、流式 Token 实时因果拓扑高亮与人机协同 (HITL) 动态干预中枢

> **课题编号**：Phase 132  
> **战略所属支柱**：**支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)** —— 演化第七阶段压轴收官课题  
> **学术理论报告**：`docs/plans/phase_132_academic_report.md` (准入状态: `RESEARCH_GATE_PASSED`)  
> **工业实践报告**：`docs/plans/phase_132_industrial_report.md` (准入状态: `RESEARCH_GATE_PASSED`)  
> **唯一模型基线**：唯一生成模型为 DeepSeek API（主干模型参数化思考 `thinking: {"type": "enabled"}`）；唯一向量模型为阿里千问 (Qwen) Embedding 1536 维超球面（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）；全系统绝无本地大模型与 OpenAI API。  
> **编译运行环境**：统一使用 Java 21 隔离虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。  
> **前端设计系统**：严格遵守 UI/UX Pro Max 规范检索的单色钛金毛玻璃系统（`#1C1917`, `#0A0A0C`, `rgba(255,255,255,0.08)`, `backdrop-filter: blur(16px)`）。  
> **业务领域铁律**：100% 聚焦于 Agent 业务核心主战场与工作流编排，彻底封存具身力学与空间课题。

---

## 一、唯一待验证假设与核心指标契约

### 1.1 核心假设 (H-PHASE132-001)
在企业级 AI-Native 工作流编排平台中，通过构建**基于不可变哈希数组映射树 (HAMT) / 路径复制树的持久化结构共享状态快照引擎**、**基于双缓冲事件环与一阶指数衰减能量滤波的流式 Token 实时因果拓扑高亮同步中枢 (满足 60FPS 垂直同步)**、以及**基于 Petri 网可达性分析、因果租约看门狗与 Fail-Close 超时熔断机制的人机协同 (HITL) 异步挂起-恢复治理中枢**：
1. **子假设 1（快照增量内存有界性与空间节约率）**：单步状态快照增量内存开销严格有界于 $\mathcal{O}(\Delta_V)$（其中 $\Delta_V$ 为单步实际发生变动的变量数），在连续 50 步以上的工作流执行中，相较于朴素全量深拷贝实现 $\ge 85\%$ 的内存空间节约率；
2. **子假设 2（历史时光旅行 $O(1)$ 常数时间检索与切换耗时）**：在任意历史时刻 $\tau \in [0, T]$，基于持久化不可变根引用的状态重构时间复杂度严格为 $\mathcal{O}(1)$，画布历史状态切换耗时严格 $\le 5\text{ms}$，且热调优分叉执行树（Forked Tree）提供 100% 因果隔离，完全消除幽灵覆盖污染；
3. **子假设 3（流式 Token 因果高亮端到端同步延迟）**：在 DeepSeek 高速流式吐字（50-100 tokens/s）场景下，双缓冲聚合与 rAF 垂直同步调度保障流式 Token 到 DAG 节点能量脉冲因果高亮的端到端渲染延迟严格 $\le 16\text{ms}$，视口 AABB 空间裁剪下稳态渲染帧率保持 60FPS（单帧耗时 $\le 3.5\text{ms}$）；
4. **子假设 4（HITL 异步挂起-恢复无死锁与概率 1.0 有限步收敛）**：在因果租约看门狗保护下，工作流发生死锁的概率严格等于 $0.0\%$，任意处于挂起或干预状态的工作流在有限步内以概率 $1.0$ 确定性收敛至终态（完成或熔断终止），且全流程通过纯 Java 21 Record 签署 HMAC-SHA256 不可变存证凭单，拦截单比特篡改。

---

## 二、四级工业防线核心组件设计

### 2.1 第一道防线：单色钛金毛玻璃高性能渲染防线
- **前端规范**：对齐 UI/UX Pro Max 规范（`#1C1917`, `#0A0A0C`, `rgba(255,255,255,0.08)`, `backdrop-filter: blur(16px)`）；
- **节点状态机**：`PENDING`, `RUNNING_ACTIVE`, `BREAKPOINT_PAUSED`, `HITL_SUSPENDED`, `SUCCESS_COMMITTED`, `COMPENSATED_FAILED`；
- **视口裁剪**：AABB 空间相交测试，视口外节点直接剔除（剔除率 $\ge 70\%$），保障 60FPS 丝滑交互。

### 2.2 第二道防线：节点级不可变状态快照与时间旅行分叉热回溯防线 (`TimeTravelSnapshotBranchGovernor.java`)
- **不可变状态快照**：封装 `SnapshotNode`，记录输入、输出、增量变动变量集 $\Delta_V$ 与状态基线哈希；
- **持久化结构共享**：单步增量内存仅为 $\mathcal{O}(\Delta_V)$，实现 $\ge 85\%$ 的空间节约；
- **常数时间时光旅行**：在任意历史时刻 $\tau \in [0, T]$ 实现 $\mathcal{O}(1)$ 寻址切换，耗时 $\le 5\text{ms}$；
- **安全分叉派生**：支持 `forkBranchWithHotPatch` 注入热补丁并派生全新执行分支线，原主线历史只读冻结，因果绝对隔离，杜绝幽灵覆盖。

### 2.3 第三道防线：SSE 双轨单调时序事件总线与因果拓扑高亮同步防线 (`StreamingCausalTopologySyncBus.java`)
- **双轨帧同步**：在 SSE 分发 Token 文本流时，同步携带激活节点 ID (`activeNodeId`)、因果命题锚点 (`causalAnchor`) 与单调递增逻辑序号 (`logicalSeq`)；
- **双缓冲环形聚合**：将高频流式帧汇聚于后台缓冲，按垂直同步周期（16.6ms）批量提交渲染；
- **端到端延迟控制**：流式 Token 到画布因果高亮的渲染延迟严格 $\le 16\text{ms}$，打字与高亮视觉零脱节。

### 2.4 第四道防线：HITL 异步挂起、动态热补丁与密码学存证凭单防线 (`WorkflowHitlAuditReceipt.java`)
- **纯 Java 21 Record 格式**：记录 `ticketId`, `workflowId`, `branchId`, `nodeId`, `decisionAction`, `originalInputs`, `patchedInputs`, `approverId`, `leaseDurationMs`, `latencyMicros`, `timestamp`, `sha256Signature`；
- **看门狗超时自愈**：租约到期时自动触发 `TIMEOUT_FAILSAFE` 降级，杜绝分布式长事务死锁；
- **防时序攻击验真**：内置微秒级常量时间自验真方法 `verifySignature()`。

---

## 三、实施最小文件清单

获批后仅修改或新建以下最小文件集合：
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/hitl/dto/WorkflowHitlAuditReceipt.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/hitl/engine/TimeTravelSnapshotBranchGovernor.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/hitl/engine/StreamingCausalTopologySyncBus.java` (新建)
4. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/flow/hitl/Phase132WorkflowHitlTimeTravelContractTest.java` (新建契约测试套件)

明确禁止修改的边界：
- 严禁修改具身力学已封存沙箱代码；
- 严禁修改系统全局 JDK 17 环境；
- 严禁原地覆写历史快照对象状态。

---

## 四、验证命令与测试用例集

### 4.1 独立验证命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core,tests -Dtest=Phase132WorkflowHitlTimeTravelContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

### 4.2 跨阶段全量回归测试命令 (Phase 125 ~ Phase 132)
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core,tests -Dtest=Phase125McpToolExecutionContractTest,Phase126McpVirtualThreadIsolationContractTest,Phase127McpDynamicPipelineContractTest,Phase128McpZeroTrustSecuritySandboxContractTest,Phase129HermesNashDebateConsensusContractTest,Phase130McpSagaDistributedFailoverContractTest,Phase131SteinerCausalAlignmentContractTest,Phase132WorkflowHitlTimeTravelContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

### 4.3 8 大核心契约测试用例定义
1. `test01_PersistentStructuralSharingMemoryBound()`: 验证连续 50 步执行快照增量开销有界在 $O(\Delta_V)$，相比全量克隆实现 $\ge 85\%$ 的空间节约；
2. `test02_TimeTravelConstantTimeRestoration()`: 验证任意历史快照恢复寻址时间复杂度严格为 $O(1)$，纯内存耗时 $\le 5\text{ms}$；
3. `test03_ForkBranchCausalIsolationAndZeroPhantomOverwrite()`: 验证时光旅行回溯注入热补丁后派生新分支，主线快照状态 100% 保持只读因果守恒，零幽灵覆盖；
4. `test04_StreamingCausalTopologySyncBoundedLatency()`: 验证 SSE 流式 Token 与 DAG 节点因果高亮事件双轨对齐，端到端延迟严格 $\le 16\text{ms}$；
5. `test05_AabbViewportCullingOptimization()`: 验证视口 AABB 边界相交裁剪算法在 200+ 节点拓扑中剔除率 $\ge 70\%$，单帧剔除计算耗时 $\le 1\text{ms}$；
6. `test06_HitlAsynchronousSuspensionStateConservation()`: 验证 HITL 挂起期间基线状态哈希严格守恒，放行、驳回与热补丁流转严格符合 Petri 网可达性；
7. `test07_HitlWatchdogTimeoutFailCloseDeadlockFreedom()`: 验证审批人离线时租约看门狗超时自动熔断并释放事务锁，死锁概率严格为 0.0%；
8. `test08_HitlAuditReceiptSha256TamperResistance()`: 验证纯 Java 21 Record 格式存证凭单 SHA-256 签名自验真通过，篡改任意入参或审批字段立即抛错。

---

## 五、残余风险与立即停止条件

1. **立即停止条件 1（内存膨胀）**：快照增量内存节约率低于 $75\%$；
2. **立即停止条件 2（延迟失控）**：时光旅行历史切换耗时突破 $15\text{ms}$，或流式双轨同步延迟突破 $25\text{ms}$；
3. **立即停止条件 3（幽灵覆盖）**：分叉分支写操作污染了主线已有历史快照的任何字段；
4. **立即停止条件 4（测试失败）**：8 项硬核契约测试任一失败。
