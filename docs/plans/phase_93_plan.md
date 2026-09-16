# Phase 93 实施详案：复杂业务 Agent 分层多模态意图反思理解、歧义主动消解与确定性状态机交互中枢

## 一、战略定位与任务概述

- **所属战略支柱**：严格遵循《业务定位与领域边界铁律（铁律九）》之两大支柱：
  - **支柱一：复杂业务 Agent 认知与编排 (Cognitive Orchestration)**：深度重构与增强 Hermes 内核、多模态意图理解与歧义主动反思消解；
  - **支柱四：前端工作流交互与开发者体验 (Interactive Canvas & HITL Experience)**：确定性状态机交互、主动澄清回路与极低延迟人机协作体验。
- **唯一核心待验证假设**：`H-PHASE93-001`
  - 分层多模态意图解析引擎结合阿里千问 1536 维超球面投影，单步求解耗时严格 $\le 50\mu\text{s}$，意图分解准确率 $\ge 99.0\%$；
  - 反事实歧义反思探测门禁识别歧义耗时严格 $\le 30\mu\text{s}$，高危动作逃逸率恒为 $0.0\%$，主动澄清触发率 $\ge 98.0\%$；
  - 确定性状态机交互控制器驱动 5 态转移单步耗时 $\le 10\mu\text{s}$，至多 3 轮澄清槽位收敛率 $\ge 95.0\%$，非法乱序转移阻断率 100.0%；
  - 1000Hz 4096 槽位 Disruptor 无锁总线非阻塞写入 $\le 50\text{ns}$，JitterGuard 连续 3 帧抖动（>2ms）瞬切缓冲降级，存证凭单 SHA-256 自签名验真 100% 通过。

---

## 二、代码包路径与核心类规划

全部代码独立封装于 `backend/qknow-hermes/qknow-hermes-core` 模块（包路径：`tech.qiantong.qknow.hermes.intent.*`）：

### 2.1 契约 DTO 模块 (`tech.qiantong.qknow.hermes.intent.dto`)
1. `HierarchicalIntentState.java`：
   - Java 21 Record 格式封装分层意图状态：`intentId`, `sessionId`, `macroIntent`, `subIntentPath`, `slots`, `sphericalEmbedding`, `confidenceScore`, `timestamp`；内置阿里千问 1536 维超球面单位向量范数强校验 `isValidEmbedding()`（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）。
2. `AmbiguityDetectionResult.java`：
   - Java 21 Record 格式封装歧义探测结果：`isAmbiguous`, `conditionalEntropy`, `geodesicMargin`, `competingIntents`, `missingRequiredSlots`, `isDestructiveAction`, `clarificationPrompt`。
3. `IntentFsmTransitionFrame.java`：
   - Java 21 Record 格式封装 5 态有限状态机跃迁事件：`sessionId`, `fromState`, `toState`, `triggerEvent`, `roundCounter`, `payload`, `timestamp`。
4. `IntentInteractionEventFrame.java`：
   - Java 21 Record 格式封装 1000Hz 交互事件单帧：`eventId`, `sessionId`, `eventType` (`INTENT_RESOLVE`/`AMBIGUITY_DETECTED`/`CLARIFICATION_PROMPT`/`SLOT_FILLED`/`EXECUTION_CONFIRMED`), `payload`, `healthy`, `timestamp`。
5. `IntentDisambiguationReceipt.java`：
   - 不可变密码学存证凭单 Java 21 Record：`receiptId`, `sessionId`, `finalMacroIntent`, `resolvedSlotCount`, `clarificationRounds`, `isDestructiveIntercepted`, `elapsedMicros`, `busStatus`, `timestamp`, `signature`；内置 SHA-256 密码学自签名与 `verifySignature()` 验真方法。

### 2.2 核心执行引擎模块 (`tech.qiantong.qknow.hermes.intent.engine`)
1. `HierarchicalIntentResolver.java`：
   - 分层多模态意图解析引擎：实现定理 1.1 纤维丛树状投影分解，阿里千问 1536 维超球面大圆弧测地内积（$d_g = \arccos(\langle \mathbf{q}, \mathbf{c}_j \rangle)$），单步耗时严格 $\le 50\mu\text{s}$，分解准确率 $\ge 99.0\%$。
2. `AmbiguityReflectiveDetector.java`：
   - 反事实歧义反思探测门禁：实现定理 1.2 条件信息熵与测地角裕度判据，对多语义竞争或关键参数缺失的查询瞬时判定歧义（$\le 30\mu\text{s}$），高危动作分支 100% 物理硬拦截，主动澄清触发率 $\ge 98.0\%$。
3. `DeterministicIntentFsmController.java`：
   - 确定性状态机交互控制器：实现定理 1.3，维护 `INITIAL_PARSING` -> `AMBIGUITY_DETECTED` -> `ACTIVE_CLARIFYING` -> `SLOT_CONVERGED` -> `CONFIRMED_EXECUTION` 5 态确定性转移，单步转移耗时 $\le 10\mu\text{s}$，至多 3 轮澄清槽位收敛率 $\ge 95.0\%$，非法乱序跃迁 100% 拦截。
4. `IntentInteractionControlBus.java`：
   - 1000Hz 定长 4096 槽位 Disruptor 无锁意图总线：无锁并发环形队列，非阻塞写入 $\le 50\text{ns}$；JitterGuard 滑动窗口检测连续 3 帧抖动（>2ms）自动切入 `STATUS_DEGRADED_BUFFERED` 缓冲软着陆，统筹签发密码学存证凭单。

### 2.3 专属契约测试模块 (`backend/tests/src/test/java/tech/qiantong/qknow/hermes/intent`)
- `Phase93IntentDisambiguationContractTest.java`：覆盖 8 项核心契约：
  1. `testHierarchicalResolver_DecompositionWithin50Micros`：分层意图超球面树状分解单步耗时 $\le 50\mu\text{s}$ 且准确率 $\ge 99.0\%$（定理 1.1）；
  2. `testHierarchicalResolver_Enforces1536DimensionValidation`：千问 1536 维超球面单位向量强校验（命题 2.1）；
  3. `testAmbiguityDetector_EntropyThresholdAndInterceptionWithin30Micros`：条件信息熵测度与高危歧义识别耗时 $\le 30\mu\text{s}$（定理 1.2）；
  4. `testAmbiguityDetector_ZeroEscapeForDestructiveOperations`：高危破坏性动作在歧义状态下 100% 物理硬拦截（定理 1.2）；
  5. `testFsmController_StateTransitionsWithin10Micros`：5 态确定性状态机单步跃迁耗时 $\le 10\mu\text{s}$（定理 1.3）；
  6. `testFsmController_ConvergenceWithinThreeRounds`：至多 3 轮澄清槽位强收敛且阻断非法乱序跃迁（定理 1.3）；
  7. `testControlBus_DisruptorThroughputAndJitterGuard`：1000Hz 定长 4096 槽位 Disruptor 无锁推帧写入 $\le 50\text{ns}$，JitterGuard 监控；
  8. `testImmutableReceipt_Sha256SelfSignatureVerification`：不可变意图存证凭单 SHA-256 密码学防篡改自签名与验真 100% 通过。

---

## 三、验证与回归计划

1. **局部编译**：在隔离 Java 21 环境下编译 `qknow-hermes-core` 模块；
2. **契约单测验证**：运行 `Phase93IntentDisambiguationContractTest` 确保 8/8 全绿；
3. **全库防退化回归**：运行全库回归测试，确保在 Phase 92 的 1392 项基础上，顺利突破至 **1400 项历史大关**（1400/1400 100% 全绿）；
4. **前端生产打包验证**：运行 `npm --prefix frontend run build:prod` 确保 0 错误纯净通过。
