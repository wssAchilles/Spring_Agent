# Phase 41: 全局混沌工程自治、故障自愈与多活机房裂脑防御 实施方案

## 一、方案背景与目标

前 40 个 Phase 的建设使全系统在功能完整性、多模态全双工吞吐、知识图谱推理与安全护栏上达到了极高成熟度。进入 **Phase 41**，系统正式迈向**超高可用自治防御与混沌工程自治**阶段。

本阶段的核心目标在于：
1. **彻底消除多活机房裂脑风险**：实现基于多数派 Raft 租约与分布式单调递增 Fencing Token 的硬仲裁机制，在注入 100% 跨机房网络分区时，双主脑裂写入发生率绝对为 0（定理 1.1）；
2. **毫秒级亚健康（灰度故障）自愈隔离**：基于自适应 EWMA 滑动方差异常检验，对卡顿延迟激增的软死节点在 $250\text{ms}$ 内完成定性并触发物理隔离，杜绝线程池耗尽级联雪崩；
3. **受控自治混沌工程注入与爆炸半径绝对收敛**：支持模拟跨机房专线中断、高并发延迟注入、突发丢包与节点硬下线，内置全局安全熔断器（Emergency Kill-Switch）与自动超时回收机制，系统整体平均故障自愈时间 MTTR 严格控制在 $\le 1000\text{ms}$（定理 2.1）；
4. **10 项严苛契约测试 100% 绿灯**，后端全量防退化回归测试保持 100% 通过（0 失败 0 错误），前端生产打包 0 错误。

---

## 二、唯一待验证假设与契约测试

**唯一待验证假设 (H-PHASE41-001)**：
> 在多活机房网络分区、跨域专线中断与亚健康节点并发注入下，通过多数派 Quorum 租约硬仲裁与单调 Fencing Token 约束，双主并发写入发生率为 0；结合自适应 EWMA 亚健康探测，亚健康节点隔离率 $\ge 99\%$，且系统在故障注入后的自愈恢复时间满足 MTTR $\le 1000\text{ms}$。

**10 项严苛契约测试设计 (`Phase41ChaosAndSplitBrainDefenseContractTest.java`)**：
1. **多活机房多数派 Quorum 租约裁决与主节点选举契约**（定理 1.1，5 节点集群必须汇聚 $\ge 3$ 票赞成才允许生成有效 Lease）；
2. **跨机房网络完全分区模拟与少数派机房自动降级只读契约**（定理 1.1，分区后少数派 2 节点机房租约失效，拒绝一切写操作并返回只读状态）；
3. **分布式单调递增 Fencing Token 屏障防双写与过期写入拦截契约**（旧 Leader 携带旧 Token 企图写入时，被原子 CAS 校验 100% 拒绝）；
4. **跨机房专线单向连通（Asymmetric Partition）偏序仲裁契约**（单向连通下维持唯一全局 Leader，消除脑裂假阳性）；
5. **亚健康节点（Limping Node）EWMA 延迟离群与自适应方差检出契约**（定理 2.1，节点 P99 延迟偏离 $3\sigma$ 立即检出）；
6. **亚健康节点毫秒级路由隔离与流量零分发契约**（隔离后路由权重瞬间置 0，杜绝请求积压与线程池耗尽）；
7. **隔离节点连续探活恢复与慢启动（Slow Start）自愈解除契约**（连续正常探活后恢复健康状态，恢复时间 $\le 1000\text{ms}$）；
8. **受控混沌注入器网络延迟与丢包模拟契约**（按指定概率与延迟范围受控注入故障，未受影响节点保持正常）；
9. **混沌实验全局爆炸半径超限触发 Emergency Kill-Switch 瞬时止血契约**（故障率超标或收到紧急停止指令，50ms 内 100% 清理全部注入）；
10. **端到端多活容灾混沌演练全链路自愈闭环契约**（全链路注入网络断连 + 节点故障，系统完成仲裁切换、降级、故障隔离与恢复，MTTR $\le 1000\text{ms}$）。

---

## 三、代码组件落地清单

代码落盘路径：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/chaos`

### 1. 数据传输与状态对象 (DTO/VO)
- `dto/ChaosExperimentDTO.java`: 混沌实验元数据（ID、类型、机房、目标节点、延迟、丢包率、持续时间、状态）；
- `dto/NodeHealthSnapshotVO.java`: 节点健康快照（节点标识、Region、状态 HEALTHY/LIMPING/ISOLATED/DEAD、EWMA 延迟、离群度 Z-Score、权重）；
- `dto/RegionLeaseDTO.java`: 机房租约对象（Region、LeaderNodeId、Term/FencingToken、生效时间、过期时间、Quorum 签名集合）；

### 2. 核心业务引擎组件
- `engine/AutonomousChaosGovernor.java`: 自治受控混沌注入引擎（单机/跨机房延迟、丢包、网络分区模拟、超时自动注销与紧急 Kill-Switch）；
- `engine/MultiRegionSplitBrainArbiter.java`: 多活机房防脑裂硬仲裁器（Quorum 租约管理、Fencing Token 原子递增发放与过期写入 CAS 校验）；
- `engine/LimpingNodeDetector.java`: 亚健康节点自适应 EWMA 方差检验与自愈隔离器（基于滑动窗口离群分析、节点自动降权隔离与慢启动探活复位）；
- `engine/SelfHealingOrchestrator.java`: 全局故障自愈总协调器（端到端统筹租约仲裁、亚健康隔离与混沌演练自愈生命周期）。\n