# Phase 61 架构实施方案：跨数据中心分布式超级智能体无冲突状态同步 (CRDT)、因果偏序一致性与跨域安全状态机网络

## 一、方案背景与唯一可证伪假设

### 1. 业务背景
在企业级超大规模部署中，超级智能体生态往往跨越多可用区 (Multi-AZ) 与多数据中心（如多云与异地多活机房）。由于广域网传输存在非对称延迟、抖动、偶发网络分区 (Network Partition) 与消息乱序到达，传统的全局同步锁（2PC/分布式锁）会导致极高的 RTT 延迟与线程池耗尽，而基于物理时钟的 LWW 策略极易受 NTP 时钟漂移破坏导致更新被静默吞噬。系统亟需一套基于无冲突复制数据类型 (CRDT)、因果向量时钟与强最终一致性 (SEC) 的跨域状态同步体系。

### 2. 唯一待验证假设 (H-PHASE61-001)
> **假设陈述 (H-PHASE61-001)**：
> 在跨地域多可用区 (Geo-Distributed) 分布式超级智能体网络中，通过引入基于结合半格 (Join-Semilattice) 的状态型 CRDT 寄存器、严格因果偏序向量时钟 (Vector Clock)、具备离线 Hinted Handoff 缓存的异步跨域同步总线与支持网络分区超限自动降级的跨域状态机控制器，相比基于单点物理时间戳 (NTP LWW) 或跨域同步加锁方案，能够将跨域并发状态冲突消除率提升至 100%，在任意网络消息乱序到达或网络分区自愈后状态 100% 同构单调收敛，消除因时钟漂移或墓碑过早回收导致的幽灵数据复活，在网络分区超时时 $\le 50\text{ms}$ 自动切换进入 `DEGRADED` 降级安全态，端到端单次跨域状态机协调调度耗时 $\le 10\text{ms}$ 并签发自签名自校验通过率 100% 的不可变 SHA-256 跨域存证凭单。

---

## 二、六大核心组件与接口契约设计

落地包路径：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/geosync/`

### 1. `GeoStateAuditReceipt.java`（不可变跨域同步存证凭单 Record）
- **字段**：
  - `receiptId` (String / UUID)
  - `syncRoundId` (long)
  - `originRegion` (String，源地域标识)
  - `targetRegion` (String，目标地域标识)
  - `vectorClockSnapshot` (String，向量时钟快照序列化)
  - `convergedStateHash` (String，收敛状态哈希)
  - `conflictResolved` (boolean，是否检测并消解了并发冲突)
  - `degraded` (boolean，是否处于降级状态)
  - `decisionSummary` (String，跨域治理决策摘要)
  - `timestamp` (long)
  - `sha256Signature` (String，密码学 SHA-256 签名)
- **方法**：`verifySignature()` 密码学自验。

### 2. `CausalVectorClock.java`（因果偏序向量时钟）
- **职责**：基于定理 1.2，维护节点维度的单调递增因果时钟，精确判定 Happens-Before 因果序与并发冲突；
- **特性**：
  - `tick(String nodeId)`：本地事件自增；
  - `update(CausalVectorClock remoteClock, String localNodeId)`：接收远程时钟并合并推进；
  - `compare(CausalVectorClock other)`：枚举返回 `BEFORE`, `AFTER`, `CONCURRENT`, `EQUAL`；
  - `isConcurrentWith(CausalVectorClock other)`：判断是否存在因果无占优并发；
  - `clone()`：深拷贝时钟快照。

### 3. `StateBasedCrdtRegister.java`（结合半格强最终一致性状态型 CRDT 寄存器）
- **职责**：基于定理 1.1，实现基于 Join-Semilattice 的单调合并与无冲突收敛；
- **特性**：
  - 维护复合状态：`value`、`versionClock`、`lastWriteEpoch`、`tombstone` 标记；
  - `assign(String agentId, Object newValue, CausalVectorClock clock)`：本地单调赋值；
  - `merge(StateBasedCrdtRegister remoteRegister)`：执行最小上界 LUB 合并 $\sqcup$，具备交换律、结合律与幂等律；
  - 并发冲突打破平局机制（Deterministic Tie-Breaking）：当向量时钟并发时，基于代际号与 NodeId 字典序确定性胜出，绝不依赖易漂移的物理时钟。

### 4. `CrossDomainSyncBus.java`（跨域异步同步总线）
- **职责**：承载跨机房异步状态广播与弱网断点容错；
- **特性**：
  - 具备严格有界队列容量（`MAX_QUEUE_CAPACITY = 1000`）；
  - 离线 Hinted Handoff 缓存：当检测到目标机房处于网络断开状态时，自动暂存更新包并在网络恢复后因果保序回放；
  - `publishSyncMessage(String originRegion, String targetRegion, Object payload)`；
  - 订阅派发与紧急通道排空清空。

### 5. `GeoStateMachineController.java`（跨域安全状态机控制器）
- **职责**：基于定理 1.3，管理跨地域智能体状态生命周期（`INIT` -> `SYNCING` -> `CONVERGED` -> `DEGRADED` -> `QUARANTINED`）；
- **特性**：
  - 监控心跳与网络 RTT 延迟；
  - 当网络超时持续超过阈值（如 $3\tau = 3000\text{ms}$）时，在 $\le 50\text{ms}$ 内原子切入 `DEGRADED` 状态，阻断全局破坏性不可逆变更；
  - 分区恢复后支持探活重同步自愈。

### 6. `GeoDistributedSyncCoordinator.java`（端到端跨域无冲突状态同步总调度中枢）
- **职责**：闭环统筹因果时钟推进 -> CRDT 半格合并 -> 跨域总线分发 -> 状态机安全监控 -> 签发不可变跨域凭单；
- **特性**：
  - 纯 Java 21 高并发无死锁调度；
  - 严密边界防御（非法地域、空参数拒绝并抛出规范异常）；
  - 单次调度在 $\le 10\text{ms}$ 内完成，签发完整不可篡改凭单。

---

## 三、验证矩阵与测试计划

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/geosync/Phase61GeoDistributedSyncContractTest.java` 中建立 8 项严苛契约单测：
1. `testReceiptIntegrityAndSha256Verification`：不可变跨域存证凭单 SHA-256 自签名与防篡改测试；
2. `testCausalVectorClockOrderingAndConcurrency`：向量时钟 Happens-Before 偏序判断与并发并发冲突精确检出测试 (定理 1.2)；
3. `testStateBasedCrdtSemilatticeIdempotenceAndMonotonicity`：状态型 CRDT 结合半格算子交换律、结合律、幂等律与单调收敛性测试 (定理 1.1)；
4. `testCrdtDeterministicTieBreakingWithoutNtpDrift`：并发冲突场景下确定性打破平局与抗物理时钟漂移测试；
5. `testCrossDomainSyncBusHintedHandoff`：跨域同步总线弱网断开缓存 (Hinted Handoff) 与自愈回放测试；
6. `testGeoStateMachineControllerNetworkPartitionDegradation`：跨域状态机控制器网络分区超时 <= 50ms 快速降级隔离测试 (定理 1.3)；
7. `testEndToEndGeoDistributedSyncSuccess`：端到端跨数据中心多节点无冲突状态收敛与凭据签发测试；
8. `testCoordinatorRejectsInvalidInputs`：调度器边界防御与非法参数拦截测试。

全量验证：
- `mvn test -pl tests` 回归验证全库单测（冲刺突破 **1130/1130 项 100% 全绿**）；
- 前端 `npm run build:prod` 验证生产打包 0 错误通过。
