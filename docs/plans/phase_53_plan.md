# Phase 53 实施方案：多智能体因果信贷归因、反共谋防作弊审计与自适应角色分化演化网络

> **实施编号**：`PHASE-53-PLAN`  
> **学术依据**：`docs/plans/phase_53_academic_report.md`  
> **工业对标**：`docs/plans/phase_53_industrial_report.md`  
> **基线环境**：生成侧唯一 DeepSeek API（V3 / R1），向量侧唯一阿里千问 1536 维超球面，编译与执行唯一 Java 21 隔离环境。

---

## 一、唯一待验证科学假设 (Single Falsifiable Hypothesis)

> **唯一可证伪假设 `H-PHASE53-001`**：  
> 在多智能体协同竞标与复杂任务交付中，通过在竞标阶段引入**基于滑动窗口残差协方差矩阵（Pearson $ho \ge 0.80$）与卡方检验的反共谋审计器 (`AntiCollusionAuditor`)**，在任务交付阶段引入**基于反事实边际优势（$\Delta_i = \max(0, Q(\mathbf{a}) - Q(\mathbf{a}^{-i}, a_{\emptyset}))$）的四维因果信贷归因引擎 (`CounterfactualCreditAssigner`)**，并结合**离散化复制子动力学（Replicator Dynamics with Clamping $[0.10, 0.50]$）自适应角色演化网络 (`AdaptiveRoleEvolutionGovernor`)**：
> 1. 能够在滑动窗口 $W \le 20$ 内，对双智能体或多智能体协同抬价卡特尔实现 $100\%$ 的统计置信度检出与毫秒级熔断拦截（定理 1.1 灵敏度定理）；
> 2. 相对于全员均分或粗粒度基线，反事实因果信贷归因使“搭便车”（零贡献甚至负贡献智能体）获得的信贷奖励严格收敛归零（$	ext{Credit}_i = 0.0$），关键瓶颈智能体获得正向因果加权（定理 1.2 无偏归因定理）；
> 3. 自适应角色演化网络收敛至进化稳定策略（ESS），确保四大核心生态位角色（检索、推理、代码、审查）配额始终处于 $[10\%, 50\%]$ 范围，杜绝角色消亡死锁（定理 1.3 ESS 稳定性定理）；
> 4. 端到端调度与代数计算耗时严格满足 $\le 10	ext{ms}$。

---

## 二、架构拓扑与核心组件设计

在 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/credit/` 目录下落地五大核心组件：

```
[复杂协同业务任务 TaskItem]
             │
             ▼
┌──────────────────────────────────────────────┐
             │
             ▼
┌──────────────────────────────────────────────┐
│           AntiCollusionAuditor               │
│  (组合竞标残差协方差反共谋审计器;            │
│   维护滑动窗口 W=20 残差协方差矩阵;           │
│   Pearson 相关系数 >= 0.80 识别卡特尔同盟;   │
│   动态卡方独立性检验; 毫秒级熔断与协同降权)  │
└──────────────────────┬───────────────────────┘
                       │ (合规合法报价)
                       ▼
┌──────────────────────────────────────────────┐
│        CombinatorialAuctionEngine            │
│  (Phase 52 组合拍卖引擎; 求解最优任务划分 S*) │
└──────────────────────┬───────────────────────┘
                       │ (多智能体流水线协同执行)
                       ▼
┌──────────────────────────────────────────────┐
│     CounterfactualCreditAssigner             │
│  (反事实边际优势因果信贷归因引擎;            │
│   耗时+Token+千问超球面忠实度+通过率四维评估;│
│   COMA 反事实基准消融 Delta_i; 消除搭便车)   │
└──────────────────────┬───────────────────────┘
                       │ (各智能体边际因果信贷)
                       ▼
┌──────────────────────────────────────────────┐
│     AdaptiveRoleEvolutionGovernor            │
│  (自适应技能专长复制子动力学演化器;          │
│   动态跟踪四大角色平均适应度 f_k;            │
│   离散化复制子微分方程更新; [0.1, 0.5] 保底) │
└──────────────────────┬───────────────────────┘
                       │ (种群自组织分化平衡)
                       ▼
┌──────────────────────────────────────────────┐
│  MultiAgentCreditAndEvolutionCoordinator     │
│  (统一总控协调中枢; 端到端调度耗时 <= 10ms)   │
└──────────────────────┬───────────────────────┘
                       │ (审计存证)
                       ▼
┌──────────────────────────────────────────────┐
│        AttributionAuditReceipt               │
│  (不可变存证凭单 Record; SHA-256 密码学存证) │
└──────────────────────────────────────────────┘
```

### 2.1 核心组件清单

1. **`AttributionAuditReceipt.java`**：不可变 Java 21 Record 存证收据，记录拍卖 ID、任务 ID、各智能体因果信贷、共谋风险指数、卡特尔名单、角色适应度向量、下一期角色配额、四维交付质量与 64 位 SHA-256 密码学防篡改哈希。
2. **`AntiCollusionAuditor.java`**：组合竞标反共谋审计器，维护 $W=20$ 滑动窗口报价残差，基于 Pearson 相关系数与动态卡方检验，识别合谋抬价团伙并毫秒级熔断。
3. **`CounterfactualCreditAssigner.java`**：反事实因果信贷归因引擎，多目标四维交付质量度量，COMA 反事实基准消融计算，搭便车零信贷精准置零，瓶颈节点正向信贷加权。
4. **`AdaptiveRoleEvolutionGovernor.java`**：复制子动力学角色演化器，管理检索、推理、代码、审查四大角色生态位，欧拉离散化复制子更新，$[0.10, 0.50]$ 强保底防灭绝死锁。
5. **`MultiAgentCreditAndEvolutionCoordinator.java`**：全流程总控中枢，端到端协调竞标审计、VCG 决标、协同执行、四维质检、反事实因果分流、角色演化回写与不可变收据签发。

---

## 三、测试与验证契约设计 (`Phase53MultiAgentEvolutionContractTest.java`)

在 `backend/tests/src/test/java/tech/qiantong/qknow/ai/credit/` 下构建 8 项严苛契约测试：

1. **`test1_AntiCollusionAuditorNormalIndependentBiddingPasses()`**：验证诚实独立竞标智能体的残差协方差 $ho < 0.30$，审计 100% 安全放行；
2. **`test2_AntiCollusionAuditorCartelCollusionDetectionAndQuarantine()`**：验证定理 1.1，模拟两个智能体暗中串通协同抬价（残差相关性 $ho \ge 0.85$），审计器 100% 精确捕获卡特尔同盟并触发熔断隔离；
3. **`test3_CounterfactualCreditAssignerZeroCreditForFreeRiders()`**：验证定理 1.2，在多智能体协同流水线中，针对静默空输出或无增量贡献的“搭便车”节点，其反事实因果信贷分配严格精确为 $0.0$；
4. **`test4_CounterfactualCreditAssignerBottleneckAgentHighCredit()`**：验证定理 1.2，关键攻坚节点（如深度推理与高精代码节点）在反事实基线消融下产生显著边际跃迁，获得正向高信贷；
5. **`test5_AdaptiveRoleEvolutionGovernorESSConvergence()`**：验证定理 1.3，复制子动力学演化器在多轮迭代中自发收敛至进化稳定策略（ESS），四大角色配额处于 $[0.10, 0.50]$ 之间，无任何角色灭绝；
6. **`test6_AttributionAuditReceiptImmutabilityAndSha256Integrity()`**：验证不可变凭单 Record 的防御性不可变集合与 SHA-256 存证哈希雪崩抗碰撞性；
7. **`test7_MultiAgentCreditAndEvolutionCoordinatorEndToEndLifecycle()`**：验证端到端生命周期全链路协调（审计 -> 决标 -> 执行 -> 因果归因 -> 角色演化 -> 凭单签发），全流程耗时 $\le 10	ext{ms}$；
8. **`test8_HighConcurrencyCreditAllocationThroughput()`**：验证 8 线程高并发任务下的无锁并发安全性、无死锁与高吞吐结算。

---

## 四、全库防退化回归与前端构建目标

- **单测规模基线**：当前全库通过基线为 1058 项单测；
- **交付目标规模**：Phase 53 交付后，全库单测突破 **1066 项 100% 全绿**（0 失败 0 错误）；
- **前端生产构建**：`npm run build:prod` 保持 0 错误 0 警告极速通过。
