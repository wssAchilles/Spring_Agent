# Phase 64: 跨模态时序多源感知流协同、因果注意力掩码融合与具身智能体事件驱动决策中枢 实施方案

## 一、唯一核心待验证假设 (H-PHASE64-001)

**假设标识**：`H-PHASE64-001`  
**假设内容**：  
在唯一生成模型为 DeepSeek API、唯一向量模型为阿里千问 1536 维超球面归一化嵌入以及全系统绝无本地大模型的架构基线下：  
通过在具身智能体感知层引入集成 200ms 滑动窗口与单调逻辑时钟的多源时序对齐器（`MultimodalTemporalIngestor`）、在特征融合层引入千问 1536 维超球面因果下三角注意力掩码融合器（`CausalAttentionMasker`）、在决策执行层引入单次状态迁移耗时 $\le 1\text{ms}$ 的具身事件驱动有限状态机（`EmbodiedDecisionFsm`），并结合不可变存证凭据（`MultimodalDecisionReceipt`）与定长 4096 槽位无锁并发环形总线（`EventDrivenDecisionBus`）：  
1. **超球面流形覆盖性**：在 $L_2$ 模长 $\|\mathbf{v}\|_2 = 1.0$ 约束下，多源感知模态在 1536 维超球面上的测地线漂移误差具备严格确定性上界 $\Delta_{\max} \le \arccos(0.9) \approx 0.45\text{ rad}$，紧致覆盖率达到 $100\%$（定理 1.1）；  
2. **前向防反转与零数据穿越**：未来时间步 $t' > t$ 的感知输入对当前决策表征的偏导数恒为零（$\frac{\partial \mathbf{z}_t}{\partial \mathbf{h}_{t'}} \equiv \mathbf{0}$），反向因果污染与时间旅行漏洞发生率严格为 0（定理 1.2）；  
3. **有限视界指数收敛与零死锁零抖动**：在 1000Hz 传感器事件洪峰下，决策状态轨迹在有限视界 $H \le 10$（物理时延 $\le 20\text{ms}$）内指数收敛至安全稳定流形，端到端闭环时间稳定于 $MTTC \le 1000\text{ms}$，且死锁与物理抖动发生率严格为 0（定理 1.3）；  
4. **全流程密码学可审计存证**：决策存证凭据内置 SHA-256 签名，密码学自验通过率 $100\%$。

---

## 二、架构设计与核心组件落地计划

### 1. 核心类落位（`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/embodied/`）
- `dto/MultimodalTemporalFrame.java`：多源时序对齐感知帧，封装文本、遥测与视觉符号三元组以及单调逻辑时序标识。
- `dto/MultimodalDecisionReceipt.java`：不可变具身决策存证凭单（Java 21 Record），内置 SHA-256 自签名与 `verifyIntegrity()` 自验。
- `engine/MultimodalTemporalIngestor.java`：多源时序感知流采集与对齐器，维护 200ms 滑动时间窗口与单调递增逻辑时钟，消除 NTP 漂移与因果倒流。
- `engine/CausalAttentionMasker.java`：阿里千问 1536 维超球面特征映射与因果注意力掩码计算器，严格生成下三角二值掩码，硬隔离未来时序。
- `engine/EmbodiedDecisionFsm.java`：具身事件驱动有限状态机，管理 SENSING -> MASKING_FUSION -> DELIBERATING -> ACTING -> FEEDBACK 五态闭环，单次迁移耗时 $\le 1\text{ms}$。
- `engine/EventDrivenDecisionBus.java`：生产级无锁并发环形总线与熔断保护器，4096 定长预分配槽位，超载 Fail-Open 降级，杜绝 OOM。

### 2. 契约测试集（`backend/tests/src/test/java/tech/qiantong/qknow/ai/embodied/Phase64EmbodiedDecisionContractTest.java`）
- 契约 1：`test01_DecisionReceiptSha256IntegrityAndTamperProof`（不可变决策存证凭据 SHA-256 签名与防篡改测试）
- 契约 2：`test02_HypersphereProjectionAndNormInvariant`（千问 1536 维超球面投影模长严格归一化测试，定理 1.1）
- 契约 3：`test03_CausalAttentionMaskLowerTriangularProperty`（因果注意力下三角掩码前向防反转测试，定理 1.2）
- 契约 4：`test04_TemporalIngestorSlidingWindowAndMonotonicOrder`（多源时序感知流 200ms 滑动窗口与单调时钟对齐测试）
- 契约 5：`test05_FsmStateTransitionLatencyBudget`（有限状态机单步微转移耗时 $\le 1\text{ms}$ 测试，定理 1.3）
- 契约 6：`test06_EventBusRingBufferCapacityAndFailOpen`（事件总线定长 4096 槽位与 Fail-Open 熔断降级防 OOM 测试）
- 契约 7：`test07_EndToEndEmbodiedDecisionCycleConvergence`（端到端具身决策状态机闭环收敛与 $MTTC \le 1000\text{ms}$ 测试）
- 契约 8：`test08_TemporalAntiLookaheadZeroLeakageInvariant`（反事实未来噪声注入下历史注意力零泄漏不变量测试）

---

## 三、验证方案与退出准则

1. **qknow-ai 模块局部编译**：通过 Java 21 隔离环境编译，0 错误；
2. **Phase 64 专属契约测试**：8/8 项 100% 全绿；
3. **全库全量回归测试**：突破 1154 项单测大关（1154/1154 100% 全绿，0 失败 0 错误）；
4. **前端生产构建**：`npm run build:prod` 0 错误通过；
5. **Git 原子提交**：Conventional Commits 规范，独占简体中文，一个真实空行，2-4 条核心要点。
