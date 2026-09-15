# Phase 63: 意图投机前置流式执行、端云认知双向同步与轻量符号状态机加速引擎 实施方案

## 一、唯一待验证科学与工程假设

**假设标识**：`H-PHASE63-001`
**假设内容**：
在唯一生成模型为 DeepSeek API、唯一向量模型为阿里千问 1536 维超球面归一化嵌入以及全系统绝无本地大模型的架构基线下：
通过在端侧/边缘节点采用纳秒级轻量符号有限状态机 (Lightweight Symbolic FSM) 执行击键停留阻尼（$t_{\text{dwell}} \ge 300\text{ms}$）与意图特征草稿分类，并在云端通过写时隔离的影子快照 (`SpeculativeShadowContext`) 并行展开千问超球面测地线预检索与 64-token 整数倍前缀对齐装配：
1. 在用户意图命中的正常提交场景下，端到端首 Token 延迟 (TTFT) 相对传统串行等待基线削减幅度 $\ge 60\%$；
2. 在用户改写或取消撤销场景下，通过无害回滚算子保证全局生产状态脏写发生率严格为 $0$，影子资源撤销耗时 $\le 1\text{ms}$；
3. 全流程生成不可变 Java 21 Record 存证凭单 `SpeculativeExecutionReceipt`，内置 SHA-256 签名，密码学自验防篡改通过率 $100\%$。

---

## 二、架构设计与核心组件落地计划

### 1. 组件集合（置于 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/speculative/`）
- `SpeculativeExecutionReceipt.java`：不可变投机执行存证凭单（Java 21 Record），包含 `receiptId`、`epoch`、`intentDraft`、`fsmState`、`speculativeHit`、`speculativeAborted`、`latencySavedMs`、`contextHash`、`timestamp`、`sha256Signature`，内置自签名计算与 `verifySignature()` 密码学防篡改自验。
- `SymbolicStateTransition.java`：轻量符号状态机转移元数据（Java 21 Record），包含源状态、目标状态、触发事件、停顿时间阈值与转移条件。
- `LightweightSymbolicFsm.java`：端侧/边缘轻量确定性符号状态机，支持 IDLE -> TYPING -> DWELLING -> SPECULATING -> COMMITTED / ABORTED 五大状态流转，纳秒级无锁并发判定。
- `SpeculativeIntentPipeline.java`：流式意图投机管道，基于千问 1536 维超球面流形测地线内积并行预检索，结合 DeepSeek 64-token 规整前缀预装载，影子快照写时隔离。
- `BidirectionalCognitiveSyncBus.java`：端云认知双向同步总线，管理有界投机任务队列，支持基于因果代际号的原子提交与无害回滚撤销。
- `SpeculativeStreamingCoordinator.java`：端到端投机执行与双向同步总调度中枢，闭环协调 FSM 击键特征解析 -> 投机预检索 -> 原子提交/回滚仲裁 -> 密码学存证凭单签发。

### 2. 契约测试集（置于 `backend/tests/src/test/java/tech/qiantong/qknow/ai/speculative/Phase63SpeculativeExecutionContractTest.java`）
- 契约 1：`test01_ReceiptSha256SelfVerificationAndTamperResistance`（不可变凭单 SHA-256 签名与防篡改测试）
- 契约 2：`test02_SymbolicFsmDwellTimeOptimalStoppingTransition`（轻量符号状态机击键停顿最优停止状态流转测试，定理 1.1）
- 契约 3：`test03_SpeculativePipelineHyperspherePreRetrievalSpeedup`（千问 1536 维超球面投机预检索与延迟削减达标测试，定理 1.3）
- 契约 4：`test04_SpeculativeRollbackCausalNonInterference`（投机撤销因果无干扰与零脏写测试，定理 1.2）
- 契约 5：`test05_BidirectionalSyncBusCausalOrderingAndEviction`（端云双向同步总线因果偏序与过期清理测试）
- 契约 6：`test06_DeepSeekPrefix64TokenAlignment`（DeepSeek 64-Token 整数倍前缀对齐与哈希自验测试）
- 契约 7：`test07_EndToEndSpeculativeHitWorkflow`（端到端投机命中全流程与延迟收益自验）
- 契约 8：`test08_CoordinatorCircuitBreakerAndFailOpen`（高负载断路器熔断与 Fail-Open 优雅降级保护测试）

---

## 三、验证方案与退出准则

1. **qknow-ai 模块局部编译**：通过 Java 21 隔离环境编译，0 错误；
2. **Phase 63 专属契约测试**：8/8 项 100% 全绿；
3. **全库全量回归测试**：突破 1146 项单测大关（1146/1146 100% 全绿，0 失败 0 错误）；
4. **前端生产构建**：`npm run build:prod` 0 错误通过；
5. **Git 原子提交**：符合 Conventional Commits 规范，独占简体中文，一个真实空行，2-4 条核心要点。
