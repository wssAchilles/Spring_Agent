# Phase 94 实施详案：复杂业务 Agent 分布式多智能体博弈对抗辩论、共识收敛仲裁与零信任决策凭单中枢
(Phase 94 Implementation Plan: Complex Business Agent Distributed Multi-Agent Game-Theoretic Debate, Consensus Convergence Arbitration & Zero-Trust Decision Voucher Metacenter)

## 一、战略业务定位与核心战役目标

本阶段（Phase 94）严格遵照《业务定位与领域边界铁律（铁律九）》四大战略攻坚支柱之**支柱一（复杂业务 Agent 认知与编排）**与**支柱二（生产级企业 MCP 工具生态）**，针对企业级 AI-Native 知识库与智能体平台（Knowledge Hub）在高价值复杂业务决策场景下，解决群思共谋盲从幻觉、拜占庭异常节点引发共识分裂与缺乏不可变多签凭单导致无法审计定责的三大核心工业生产灾难。

核心目标是落地一套纳什均衡博弈对抗辩论、动态加权 BFT 拜占庭共识收敛与零信任决策凭单签发验真中枢，构筑四级工业工程防线，并以 1000Hz 4096 槽位 Disruptor 无锁总线保障硬实时高吞吐流转。

---

## 二、架构模型基线与环境铁律

1. **唯一生成模型**：DeepSeek API（DeepSeek-V3 负责快速博弈角色策略生成与观点提出，DeepSeek-R1 负责长程博弈对抗反思、拜占庭仲裁与共识收敛证明）。
2. **唯一向量模型**：阿里千问 (Qwen) Embedding 1536 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）。
3. **彻底弃用声明**：项目中绝无任何本地部署大模型，彻底弃用 OpenAI API。
4. **唯一编译运行环境 (Java 21 隔离铁律)**：
   - 全量模块统一且唯一使用 Java 21 编译运行；
   - 宿主 Mac 全局环境保持 Java 17，隔离环境绝对路径固定为：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`；
   - 严禁污染主机环境，Maven 编译执行通过局部环境变量前缀显式传入。
5. **Git 提交信息规范铁律**：严格遵循 Conventional Commits 中文规范，空行隔开，真实换行符与 `- ` 列表 2-4 条核心要点，严禁出现字面量 `\n`。
6. **全中文规则**：所有说明文本与代码注释独占使用简体中文。

---

## 三、唯一待验证算法假设 (`H-PHASE94-001`)

1. **纳什均衡博弈辩论引擎 (`GameTheoreticDebateEngine`)**：结合阿里千问 1536 维超球面测地投影，单步策略推演耗时严格 $\le 50\mu\text{s}$，至多 3 轮辩论内论据完备性与观点收敛率 $\ge 98.0\%$；
2. **动态加权 BFT 拜占庭共识仲裁器 (`BftConsensusArbitrator`)**：在容忍 $f < N/3$ 异常节点下，单步仲裁耗时严格 $\le 30\mu\text{s}$，高危共谋渗透率恒为 $0.0\%$，一致性达成率 $\ge 99.0\%$；
3. **零信任多签决策门禁 (`ZeroTrustDecisionVoucherGate`)**：单步验真耗时严格 $\le 20\mu\text{s}$，未获法定门限签名背书的高危决策物理拦截率 $100.0\%$；
4. **Disruptor 无锁交互总线 (`DebateConsensusControlBus`)**：1000Hz 4096 槽位无锁总线非阻塞写入延迟 $\le 50\text{ns}$，JitterGuard 连续 3 帧抖动（>2ms）瞬切缓冲软着陆，不可变决策存证凭单 SHA-256 签名自验真 100% 通过。

---

## 四、核心契约类设计与代码落盘规划

生产代码落盘于 `backend/qknow-hermes/qknow-hermes-core` 模块：
包路径：`tech.qiantong.qknow.hermes.consensus.dto` 与 `tech.qiantong.qknow.hermes.consensus.engine`。

### 4.1 DTO 契约类 (`dto`)
1. `AgentDebateRole.java`：博弈辩论角色枚举：
   - `PROPOSER`（正方提案者）
   - `OPPONENT`（反方质询者）
   - `FACT_VERIFIER`（独立事实核查者）
   - `NEUTRAL_ARBITRATOR`（中立仲裁者）
2. `BftConsensusPhase.java`：BFT 拜占庭共识阶段枚举：
   - `PRE_PREPARE`, `PREPARE`, `COMMIT`, `COMMITTED`, `ABORTED`
3. `DebateArgumentFrame.java`：单轮论据事件 Java 21 Record：
   - `String argumentId`, `String sessionId`, `AgentDebateRole role`, `String claimText`, `float[] argumentEmbedding`, `double logicScore`, `long timestamp`
   - 内置 `isValidEmbedding()` 强校验 1536 维超球面单位向量模长。
4. `BftVoteMessage.java`：BFT 投票报文 Java 21 Record：
   - `String voteId`, `String sessionId`, `String agentId`, `BftConsensusPhase phase`, `String proposalHash`, `double weight`, `boolean approve`, `String signature`, `long timestamp`
5. `DebateConsensusEventFrame.java`：1000Hz 总线高频事件单帧 Java 21 Record：
   - `long sequenceId`, `String sessionId`, `String eventType`, `String payload`, `double latencyJitterMs`, `long timestamp`
6. `ZeroTrustDecisionVoucher.java`：不可变零信任决策存证凭单 Java 21 Record：
   - `String voucherId`, `String sessionId`, `String topic`, `String winningProposal`, `double quorumPercentage`, `int totalRounds`, `double nashResidualMargin`, `double elapsedMicros`, `String busStatus`, `long timestamp`, `String signature`
   - 内置 `verifySignature()` 密码学自签名验真逻辑。

### 4.2 核心执行引擎类 (`engine`)
1. `GameTheoreticDebateEngine.java`：
   - 推进正方、反方与核查方的多轮博弈对抗；
   - 8 路循环展开极速计算 1536 维超球面测地线距离；
   - 判定观点散度与论据收敛，硬限制最大 3 轮辩论，单步耗时 $\le 50\mu\text{s}$。
2. `BftConsensusArbitrator.java`：
   - 动态权重拜占庭容错三阶段共识协议仲裁；
   - 收集 Prepare 与 Commit 投票，计算加权 Quorum（$\ge 2/3$）；
   - 抵御共谋与恶意异常节点，单步仲裁耗时 $\le 30\mu\text{s}$。
3. `ZeroTrustDecisionVoucherGate.java`：
   - 汇聚共识各方签名，签发不可变存证凭单；
   - 对未获法定门限签名背书或关键字段被篡改的决策执行 100% 物理硬拦截，单步验真耗时 $\le 20\mu\text{s}$。
4. `DebateConsensusControlBus.java`：
   - 1000Hz 4096 槽位 Disruptor 无锁并发环形队列，单帧推帧 $\le 50\text{ns}$；
   - JitterGuard 连续 3 帧时钟抖动（>2ms）瞬切 `STATUS_DEGRADED_BUFFERED` 缓冲软着陆模式。

---

## 五、8 项严苛契约测试设计 (`Phase94DebateConsensusContractTest.java`)

测试落盘于 `backend/tests/src/test/java/tech/qiantong/qknow/hermes/consensus/Phase94DebateConsensusContractTest.java`：

1. `testGameTheoreticDebate_NashConvergenceWithin50Micros` (定理 1.1)：纳什均衡博弈辩论单步求解耗时 $\le 50\mu\text{s}$，至多 3 轮内收敛至 $\epsilon$-纳什均衡，观点散度低于阈值；
2. `testGameTheoreticDebate_QwenEmbeddingValidation` (命题 2.1)：阿里千问 1536 维超球面单位向量强校验（非法维度或非单位向量抛出 `IllegalArgumentException`）；
3. `testBftConsensusArbitrator_ByzantineFaultToleranceAndConsensus` (定理 1.2)：容忍异常拜占庭节点（投反对票或恶意分叉），正常达成 $Q \ge 2/3$ 加权共识，单步耗时 $\le 30\mu\text{s}$；
4. `testBftConsensusArbitrator_CollusionInterception` (定理 1.2)：拜占庭节点合谋伪造恶意提案但权重未达法定门限，共识仲裁器严格拦截并判定 ABORTED，共谋渗透率恒为 $0.0\%$；
5. `testZeroTrustDecisionGate_VoucherGenerationAndVerification` (定理 1.3)：达成共识后生成不可变凭单，凭单自签名验真 100% 通过，单步验真耗时 $\le 20\mu\text{s}$；
6. `testZeroTrustDecisionGate_TamperedVoucherStrictInterception` (定理 1.3)：篡改决策内容或参与方签名，验真算法 100% 检出并拦截执行；
7. `testControlBus_DisruptorThroughputAndJitterGuard`：1000Hz 4096 槽位 Disruptor 无锁总线非阻塞写入 $\le 50\text{ns}$，连续 3 帧时钟抖动（>2ms）瞬切缓冲降级；
8. `testEndToEnd_DebateToConsensusToVoucherPipeline`：全链路贯通端到端测试，从三方博弈辩论到 BFT 拜占庭共识再到凭单签发，各阶段状态一致性 100%。

---

## 六、全量防退化回归与上线纪律

1. 第一回合严格遵守只读研学，提交实施详案与 `implementation_plan.md`，等待用户明确批准；
2. 获批后执行 TDD 契约驱动研发；
3. 确保 8/8 契约测试全绿，全库回归突破 **1408 项大关**（1408/1408 100% 全绿）；
4. 前端打包构建 0 错误通过；
5. 遵循 Conventional Commits 规范执行原子提交并推送到远端仓库。
