# Phase 31 决策完备实施方案：多智能体分布式共识机制与拜占庭容错协作网络 (Multi-Agent Consensus & Byzantine Fault Tolerance Collaboration Network)

> **拟归档路径**：`docs/plans/phase_31_plan.md`  
> **前置依赖**：Phase 10（智能体运行时反思熔断与状态图自愈）、Phase 21（动态自适应记忆图谱）、Phase 22（多智能体分工协作与蜂群通信协议）、Phase 23（反应式工作流引擎）  
> **执行准绳**：`@AGENTS.md` Research-to-Implementation Gate 规范  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3，`deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding（1536 维）**，全链路绝无本地大模型，彻底弃用 OpenAI API；唯一编译运行环境为 **Java 21 隔离环境**（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

## 一、唯一核心待验证假设 (Sole Verifiable Hypothesis)

> **假设 (H-Phase31)**：在唯一生成模型（DeepSeek API）与唯一向量模型（阿里千问 1536 维 Embedding）约束下，在 `backend/qknow-hermes` 与 `backend/qknow-framework/qknow-ai` 中构建工业级多智能体分布式共识引擎与拜占庭容错协作网络：  
> 1. **多数派投票与动态 Quorum 裁决器 (`ConsensusArbiter`)**：实现结构化离散提案快速加权投票，以及自由文本提案基于阿里千问 1536 维超球面向量余弦矩阵的加权中心度（Medoid Selection）共识提取；  
> 2. **拜占庭智能体对抗过滤门禁 (`ByzantineWorkerFilter`)**：建立覆盖 Crash-Stop（超时/空内容）、Noisy（语义离群 $>2.5\sigma$）与 Adversarial（Prompt Injection 越狱检测）的三类拜占庭故障多重清洗流水线；  
> 3. **滑动窗口信誉账本 (`WorkerReputationLedger`)**：实现 EMA 动态衰减计分（$\alpha = 0.85$）、低于阈值（$R_t < 0.40$）自动静默入狱（`JAILED`）隔离与探活自愈恢复；  
> 4. **多轮自适应辩论状态机 (`DebateStateMachine`)**：实现 PROPOSE -> REVIEW -> CHECK -> ARBITRATE 四态有限状态机，设定硬上限轮次 $R_{\max} \le 3$、余弦相似度 $\ge 0.90$ 提前短路与停滞漂移 $\le 0.02$ 强行短路双重收敛判据；  
> 5. **快速 Quorum 响应与高可用降级 (Fast Quorum & Fail-Open)**：达到法定多数 $Q \ge 2f + 1$ 且节点内聚度 $\ge 0.88$ 时立即提前裁决返回；  
> 6. **黑板原子 CAS 乐观锁防重校验 (`SharedBlackboard.commitFactWithVersion`)**：携带版本号防并发覆写，彻底根除分布式投票脑裂导致的脏数据。  
> 
> **能够证明**：在面对混杂 1/3 拜占庭恶意注入节点、慢节点与幻觉节点的对抗场景下，系统共识裁决正确率 $\ge 98.0\%$；拜占庭对抗攻击过滤拦截率达 $100\%$；多轮辩论死循环发生率为 $0.00\%$；在 Fast Quorum 加持下 P99 响应延迟较全等待模型降低 $60\%$ 以上；黑板并发写冲突导致脏数据覆盖率为 $0.00\%$。

---

## 二、核心理论与工业设计模式闭环映射

| 理论与工业来源 | 核心理论 / 工业模式 | 本项目 Phase 31 落地实现组件 | 对应验证契约 |
|---|---|---|---|
| **Lamport et al. 1982 / Castro & Liskov 1999 (PBFT)** | 拜占庭容错界限充要条件 $n \ge 3f + 1$ 与 Quorum 相交性不变量 $Q \ge 2f + 1$ | `ByzantineWorkerFilter.java`, `ConsensusArbiter.java` (Quorum 校验断言) | 契约 01, 契约 02 |
| **Dempster 1967 / Shafer 1976 证据理论** | 基本概率分配 (BPA) 折扣法则 $m_i(\Theta) = 1 - \omega_i$，多源证据正交连乘合成 | `WorkerReputationLedger.java` + 加权中心度计算 | 契约 03, 契约 04 |
| **Prompt Injection 防护与静态 AST 安全** | 正则匹配拦截系统指令越狱与注入攻击 | `ByzantineWorkerFilter.detectInjection` 静态规则库 | 契约 05 |
| **超球面 vMF 核密度与 DBSCAN 测地线聚类** | 阿里千问 1536 维超球面 $\mathbb{S}^{1535}$ 归一化内积余弦度量与离群判定 | `ByzantineWorkerFilter.filterNoisyOutliers` | 契约 06 |
| **Du et al. 2023 / Liang et al. 2023 多智能体辩论** | 多轮反思修订，李雅普诺夫势能单调负漂移，有限步终止 | `DebateStateMachine.runDebateLoop`（硬轮次 $\le 3$） | 契约 07, 契约 08 |
| **Fast Quorum 异步提前响应机制** | 并行收集法定多数即刻提交，消除长尾拖累 | `ConsensusArbiter.arbitrateFreeTextMedoid` | 契约 09 |
| **分布式状态版本号与租约防脑裂** | CAS 乐观锁版本号校验 `commitFactWithVersion` | `SharedBlackboard.commitFactWithVersion` | 契约 10 |

---

## 三、TDD 测试驱动开发 10 项专项核心契约清单

测试类路径：[`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase31MultiAgentConsensusContractTest.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase31MultiAgentConsensusContractTest.java)

1. **契约 01：拜占庭容错硬界限定理验证 ($n \ge 3f + 1$ 与 $Q \ge 2f + 1$)**
   - 验证配置 $n = 4, f = 1$ 时系统能完全容忍 1 个拜占庭恶意节点并成功达成 Quorum；
   - 验证当拜占庭节点达到 $f = 2$（$n = 4 < 3 \times 2 + 1$ 破损）时，仲裁器准确识别并触发法定人数不足拦截，拒绝达成虚假共识。
2. **契约 02：离散结构化提案加权多数派投票 (Weighted Discrete Voting)**
   - 模拟 4 个 Worker 针对离散选项或决策方案进行投票（如 A: 2 票，B: 2 票）；
   - 结合 `WorkerReputationLedger` 历史信誉加权后，权重更高的诚实方案平稳胜出，消除平票死锁。
3. **契约 03：自由文本千问 1536 维超球面加权 Medoid 中心选取**
   - 4 个 Worker 输出自然语言文本（3 个诚实表述近义，1 个语义偏离）；
   - 提取千问 1536 维向量余弦距离矩阵，正确计算加权中心度并精准选定代表性 Medoid 提案。
4. **契约 04：滑动窗口 EMA 信誉账本自适应奖惩与入狱隔离机制**
   - 诚实 Worker 连续贡献后信誉分平滑回升；
   - 产生拜占庭故障或注入攻击的 Worker 被单次严重扣分或降权；
   - 当信誉分低于 $0.40$ 时，Worker 状态被自动标记为 `JAILED` 并剥夺派单权。
5. **契约 05：拜占庭对抗提示词越狱注入 (Prompt Injection) 100% 静态硬核阻断**
   - 模拟恶意 Worker 输出包含 `ignore previous instructions`、`DAN mode`、`system prompt override` 等攻击载荷；
   - `ByzantineWorkerFilter` 100% 拦截并标记为 `ADVERSARIAL_INJECTION`，直接剥夺投票权。
6. **契约 06：超球面语义离群节点 (Noisy Outlier) $2.0\sigma$ 统计学剪枝过滤**
   - 构造 3 个高度聚合的诚实提案向量与 1 个胡言乱语的离群向量；
   - 过滤器基于均值与标准差统计学分析，自动识别并过滤离群 Worker，防止噪声拉低共识质量。
7. **契约 07：多轮自适应辩论状态机收敛性验证 (余弦相似度 $\ge 0.90$ 提前短路)**
   - 初始存在微小分歧的提案经第 1 或第 2 轮交叉审查后，余弦相似度均值提升至 $\ge 0.90$；
   - 状态机自动提前短路返回，避免无谓轮次推进，杜绝死循环。
8. **契约 08：多轮辩论停滞漂移与硬轮次截断防死循环熔断 ($R_{\max} \le 3$)**
   - 模拟两个持相反观点的固执 Worker 相互抬杠；
   - 状态机检测到相邻两轮相似度变化 $\le 0.02$ 或达到 3 轮上限时，强制终止辩论并执行终态裁决，系统 0 挂死。
9. **契约 09：Fast Quorum 异步提前响应与高可用降级 (Fail-Open)**
   - 针对慢节点或挂死节点，在诚实法定多数（2/3）达成时立即短路返回；
   - 极端全员故障时触发 Fail-Open 兜底，保障系统主调用链不崩溃。
10. **契约 10：共享黑板 CAS 乐观锁版本防脑裂并发写入验证**
    - 模拟两台裁决者携带不同或过期版本号并发调用 `commitFactWithVersion`；
    - 预期仅首个持有合法期望版本的提交成功，陈旧请求被 100% 拦截拒绝，黑板数据保持单调强一致。

---

## 四、最小实现文件集合清单

### 1. 新增契约模型与核心组件 (位于 `backend/qknow-framework/qknow-ai`)
- `tech.qiantong.qknow.ai.consensus.model.ByzantineFaultType.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.model.WorkerProposal.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.model.InspectedProposal.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.model.ConsensusResult.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.model.WorkerProfile.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.core.WorkerReputationLedger.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.core.ByzantineWorkerFilter.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.core.ConsensusArbiter.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.core.DebateStateMachine.java` [NEW]
- `tech.qiantong.qknow.ai.consensus.ConsensusCoordinator.java` [NEW]

### 2. 增强现有共享黑板与调度模块 (位于 `backend/qknow-hermes`)
- `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/blackboard/SharedBlackboard.java` [MODIFY]
  - 补充 `commitFactWithVersion(String key, String value, long expectedVersion, String sourceTag)` 接口与原子 CAS 实现。

### 3. 新增专属契约测试 (位于 `backend/tests`)
- `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase31MultiAgentConsensusContractTest.java` [NEW]

---

## 五、实施步骤与验证计划

### 步骤 1：构建 TDD 专属契约测试
创建 `Phase31MultiAgentConsensusContractTest.java`，定义全量 10 项专属契约。

### 步骤 2：落地领域模型与信誉账本
实现 `ByzantineFaultType`, `WorkerProposal`, `InspectedProposal`, `ConsensusResult` 与 `WorkerReputationLedger`。

### 步骤 3：落地拜占庭多维过滤器与共识裁决引擎
实现 `ByzantineWorkerFilter`（支持 1536 维向量归一化、Crash-Stop/Noisy/Adversarial 检测）与 `ConsensusArbiter`（加权 Medoid 与离散投票）。

### 步骤 4：落地自适应多轮辩论状态机
实现 `DebateStateMachine`（PROPOSE -> REVIEW -> CHECK -> ARBITRATE 四态流转、提前短路收敛、硬轮次上限 $\le 3$ 熔断）。

### 步骤 5：升级 SharedBlackboard 并行 CAS 乐观锁
在 `SharedBlackboard` 中引入 `commitFactWithVersion` 方法，防范并发投票脑裂。

### 步骤 6：全量验证
1. 运行 Phase 31 专属契约测试：10/10 全绿；
2. 运行后端全量防退化回归测试：852/852 全绿；
3. 运行前端生产环境构建：`npm run build:prod` 0 错误通过。
