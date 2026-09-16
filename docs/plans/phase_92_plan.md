# Phase 92 实施详案：复杂业务 Agent 分布式流式推理拓扑自愈、零拷贝上下文路由与极低延迟人机交互中枢

## 一、战略定位与任务概述

- **所属战略支柱**：严格遵循《业务定位与领域边界铁律（铁律九）》之两大支柱：
  - **支柱一：复杂业务 Agent 认知与编排 (Cognitive Orchestration)**：深度增强 Hermes 内核、分布式多智能体协同推理拓扑与容错自愈；
  - **支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)**：极低延迟流式打字机、人机协同审批 (HITL) 零拷贝断点热恢复。
- **唯一核心待验证假设**：`H-PHASE92-001`
  - 反应式流式拓扑单节点断流/超时（>200ms）下，自愈求解耗时严格 $\le 50\mu\text{s}$，极大流恢复率 $\ge 95\%$，断流自愈恢复率 $\ge 99.5\%$；
  - 基于阿里千问 1536 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）索引切片，零拷贝切片路由耗时严格 $\le 50\mu\text{s}$，跨 Agent 内存对象深度拷贝削减 $\ge 80\%$；
  - HITL 审批挂起中间 Token 偏移与推理快照冻结耗时 $\le 10\mu\text{s}$，审批通过后零拷贝断点恢复耗时严格 $\le 10\text{ms}$，首 Token 生成延迟 TTFT $\le 100\text{ms}$，状态恢复一致性 100.0%；
  - 1000Hz 4096 槽位 Disruptor 无锁流式总线非阻塞写入耗时 $\le 50\text{ns}$，JitterGuard 监控连续 3 帧抖动（>2ms）自动切入缓冲软着陆，不可变存证凭单 SHA-256 自签名验真 100% 通过。

---

## 二、代码包路径与核心类规划

全部后端代码落盘于 `qknow-hermes-core` 模块（包路径：`tech.qiantong.qknow.hermes.streaming.*`）：

### 2.1 契约 DTO 模块 (`tech.qiantong.qknow.hermes.streaming.dto`)
1. `StreamingTopologyNodeState.java`：
   - Java 21 Record 格式封装拓扑节点状态：`nodeId`, `nodeRole`, `status` (`HEALTHY`/`DEGRADED`/`FAILED`), `rttMillis`, `throughputTokensPerSec`, `lastHeartbeatTimestamp`；提供健康判断与衰减辅助方法。
2. `ZeroCopyContextSlice.java`：
   - Java 21 Record 格式封装零拷贝上下文切片：`sliceId`, `sessionId`, `rawContentRef`, `offset`, `length`, `sphericalEmbedding`, `topicLabel`, `createdTimestamp`；内置千问 1536 维超球面单位向量范数强校验 `isValidEmbedding()`（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）与只读文本提取方法 `getSliceContent()`。
3. `HitlStreamingCheckpoint.java`：
   - Java 21 Record 格式封装 HITL 挂起断点快照：`checkpointId`, `sessionId`, `suspendedTokenOffset`, `emittedText`, `partialCoTThought`, `pendingToolName`, `pendingToolArguments`, `createdTimestamp`。
4. `StreamingInteractionEventFrame.java`：
   - Java 21 Record 格式封装 1000Hz 交互事件单帧：`eventId`, `sessionId`, `eventType` (`TOKEN_CHUNK`/`TOPOLOGY_HEAL`/`HITL_SUSPEND`/`HITL_RESUME`/`SLICE_ROUTED`), `payload`, `healthy`, `timestamp`。
5. `StreamingInteractionReceipt.java`：
   - 不可变密码学存证凭单 Java 21 Record：`receiptId`, `sessionId`, `topologyNodeCount`, `healedNodeCount`, `routedSliceCount`, `hitlResumeElapsedMicros`, `busStatus`, `timestamp`, `signature`；内置 SHA-256 密码学自签名与 `verifySignature()` 验真。

### 2.2 核心执行引擎模块 (`tech.qiantong.qknow.hermes.streaming.engine`)
1. `ReactiveStreamingTopologySelfHealer.java`：
   - 反应式流式拓扑自愈引擎：维护多 Agent 拓扑 DAG，实时跟踪节点心跳与延迟。当节点异常或超时（>200ms）时，基于定理 1.1 执行增广轨割边旁路路由求解，单步耗时严格 $\le 50\mu\text{s}$，极大流恢复率 $\ge 95\%$。
2. `ZeroCopyContextSliceRouter.java`：
   - 零拷贝上下文切片路由引擎：基于定理 1.2，结合阿里千问 1536 维超球面测地线内积（$d_g = \arccos(\langle \mathbf{q}, \mathbf{v}_i \rangle)$），快速筛选最相关的切片只读引用，单步检索耗时严格 $\le 50\mu\text{s}$，杜绝深拷贝。
3. `HitlStreamingCheckpointGate.java`：
   - 极低延迟 HITL 挂起断点恢复门禁：基于定理 1.3，维护 `STREAMING` -> `SUSPENDED_HITL` -> `APPROVED_RESUMING` / `REJECTED_TERMINATED` 状态转移。冻结快照耗时 $\le 10\mu\text{s}$，审批通过后零拷贝断点热恢复耗时 $\le 10\text{ms}$，首字节续推 TTFT $\le 100\text{ms}$，一致性 100.0%。
4. `StreamingInteractionControlBus.java`：
   - 1000Hz 定长 4096 槽位 Disruptor 无锁流式交互控制总线：无锁并发环形缓冲区，非阻塞写入 $\le 50\text{ns}$；JitterGuard 滑动窗口检测连续 3 帧时钟抖动（>2ms）自动切入 `STATUS_DEGRADED_BUFFERED` 缓冲软着陆，统筹签发存证凭单。

### 2.3 专属契约测试模块 (`backend/tests/src/test/java/tech/qiantong/qknow/hermes/streaming`)
- `Phase92StreamingInteractionContractTest.java`：覆盖 8 项核心契约：
  1. `testTopologyHealer_BypassRoutingWithin50Micros`：单节点故障下拓扑增广轨旁路路由求解耗时 $\le 50\mu\text{s}$，恢复率 $\ge 95\%$（定理 1.1）；
  2. `testTopologyHealer_DetectsTimeoutAndDegradesGracefully`：检测超时节点并平滑降级（定理 1.1）；
  3. `testZeroCopyRouter_OptimalSliceRoutingWithin50Micros`：千问 1536 维超球面测地线切片路由耗时 $\le 50\mu\text{s}$，零深拷贝（定理 1.2）；
  4. `testZeroCopyRouter_Enforces1536DimensionNormValidation`：严格拒绝非 1536 维或非单位范数嵌入（命题 2.1）；
  5. `testHitlGate_CheckpointFreezingWithin10Micros`：流式打字被 HITL 挂起瞬间快照冻结耗时 $\le 10\mu\text{s}$（定理 1.3）；
  6. `testHitlGate_ResumptionWithin10MillisAndZeroLoss`：审批通过后断点热恢复耗时 $\le 10\text{ms}$，Token 零丢失零重复（定理 1.3）；
  7. `testControlBus_DisruptorThroughputAndJitterGuard`：1000Hz 定长 4096 槽位 Disruptor 无锁总线非阻塞推帧写入 $\le 50\text{ns}$，JitterGuard 监控；
  8. `testImmutableReceipt_Sha256SelfSignatureVerification`：不可变流式交互存证凭单 SHA-256 密码学自签名验真 100% 通过。

---

## 三、验证与回归计划

1. **局部编译**：在隔离 Java 21 环境下编译 `qknow-hermes-core` 模块；
2. **契约单测验证**：运行 `Phase92StreamingInteractionContractTest` 确保 8/8 全绿；
3. **全库防退化回归**：运行全库回归测试，确保在 Phase 91 的 1384 项基础上，顺利突破至 **1392 项历史大关**（1392/1392 100% 全绿）；
4. **前端生产打包验证**：运行 `npm --prefix frontend run build:prod` 确保 0 错误纯净通过。
