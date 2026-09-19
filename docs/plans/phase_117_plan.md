# Phase 117 实施详案与工程契约文件

## 课题：支柱一：复杂业务 Agent 认知与编排 —— 多智能体纳什博弈辩论与共识中枢 (Multi-Agent Nash Equilibrium Debate & Consensus Metacenter)

> **归档路径**：`docs/plans/phase_117_plan.md`  
> **基线环境约束**：
> - 唯一生成模型：DeepSeek API（主干模型，参数化思考模式 `thinking: {"type": "enabled"}`, `reasoning_effort: "high"`, 绝无 r1）
> - 唯一向量模型：阿里千问 (Qwen) Embedding (1536 维超球面空间，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$)
> - 隔离环境：Java 21 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)
> - 业务边界：100% 聚焦企业级知识库与智能体编排，严禁力学发散

---

### 一、唯一待验证假设 (Hypothesis H-PHASE117-001)

> 在保持 Java 21 隔离环境、DeepSeek API 参数化思考模式、阿里千问 1536 维超球面向量基线不变的前提下：
> 1. 构建**有界收敛防线**，硬编码最大辩论轮次 $T_{\max} \le 5$，并在概率单纯形 $\Delta^K$ 上引入策略分布的 Jensen-Shannon (JS) 散度作为 $\varepsilon$-Nash 收敛距离（$\varepsilon = 0.05$），能够在 **$t \le 3$ 轮内达到 90% 以上的收敛率**，若第 5 轮仍未收敛则自动触发熔断仲裁，彻底根除死循环震荡，使多 Agent 辩论的 Token 开销相较传统自由轮询**降低 $\ge 68\%$**；
> 2. 构建**独立置信双盲盲审互评与 $M \times N$ 纳什博弈收益矩阵量化引擎**，剥离发言者角色标签，使从众合谋（Sycophancy）发生率从行业基准的 **$43\%$ 降至 $\le 2\%$**，实现合规与风控异见的 100% 显式捕获与定量扣分；
> 3. 构建**DeepSeek 官方 API 思考协议闭环防线**，在多轮工具调用中对前序 Assistant 消息的 `reasoning_content` 进行无损保留与原样回传，实现 **100% 零 400 异常调用**；
> 4. 构建**纯 Java 21 Record 格式的纳什辩论决策凭单 (`NashDebateReceipt.java`)**，内嵌 SHA-256 密码学自签名与运行时自验真，单次凭单生成与验真耗时 **$\le 50\mu\text{s}$**，实现 100% 防篡改可追溯。

---

### 二、拟实施文件清单与代码架构

#### 模块：`backend/qknow-framework/qknow-ai`

1. **[NEW] `tech.qiantong.qknow.ai.consensus.nash.dto.NashDebateReceipt.java`**
   - 纯 Java 21 Record 不可变存证凭单；
   - 封装 `receiptId`, `sessionId`, `debateTopic`, `totalRounds`, `converged`, `finalNashResidual`, `winningStrategy`, `strategyDistribution`, `participatingAgents`, `arbitratorVerdict`, `executionTimeMs`, `timestamp`, `signature`；
   - 提供内置 SHA-256 自签名与 `verifySignature()` 运行时验真。

2. **[NEW] `tech.qiantong.qknow.ai.consensus.nash.NashEquilibriumCalculator.java`**
   - 纯 Java 21 堆内轻量级纳什均衡求解算子；
   - 计算两轮策略分布的 Jensen-Shannon 散度 $D_{\text{JS}}$ 作为 $\varepsilon$-Nash 距离；
   - 纯数学计算，单次耗时 $\le 100\mu\text{s}$，零额外第三方依赖。

3. **[NEW] `tech.qiantong.qknow.ai.consensus.nash.AdaptiveJudgeArbiter.java`**
   - 独立仲裁者算子；
   - 基于千问 1536 维超球面测地投影 $\mathcal{S}_{\text{geo}} = \frac{\mathbf{v}_a^\top \mathbf{v}_{\text{topic}} + 1}{2}$ 与因果覆盖率 $c(a)$ 进行加权打分；
   - 复杂度严格有界于 $\mathcal{O}(|N| \times |\text{Arguments}|)$，纯内存计算耗时 $\le 10\text{ms}$。

4. **[NEW] `tech.qiantong.qknow.ai.consensus.nash.MultiAgentDebateCoordinator.java`**
   - 四级防线编排协调器；
   - 维护 $N=\{\text{采购}, \text{风控}, \text{法务}\}$ 异质角色；
   - 驱动脱敏盲审、收益矩阵动态更新、JS 散度收敛检测、5 轮熔断与凭单签发。

#### 模块：`backend/tests`

5. **[NEW] `tech.qiantong.qknow.hermes.consensus.Phase117NashDebateConsensusContractTest.java`**
   - 综合契约测试，精确覆盖 6 大契约：
     1. 契约 1：定理 1.1 有限轮次 ($T_{\max} \le 5$) 指数收敛界验证 ($\varepsilon \le 0.05$)；
     2. 契约 2：定理 1.2 仲裁算子 $\mathcal{O}(|N| \times |\text{Arguments}|)$ 复杂度与耗时 $\le 10\text{ms}$；
     3. 契约 3：双盲盲审互评与合规一票否决硬约束不被穿透；
     4. 契约 4：DeepSeek 思考模式与多轮 `reasoning_content` 原样回传闭环；
     5. 契约 5：纯 Java 21 Record 凭单 SHA-256 自签名与单比特篡改拦截；
     6. 契约 6：5 轮发散熔断与 Fail-Open 优雅降级。

---

### 三、验证命令与测试计数

在严格隔离的 Java 21 环境下执行：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest="Phase117NashDebateConsensusContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```
预期测试计数：**6 项契约测试 100% 绿灯通过**。

全量回归验证：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest="Phase117NashDebateConsensusContractTest,CrossBorderProcurementEndToEndTest,Phase116GraphRagScaffoldContractTest,Phase115ThinkingStreamInterruptionTest" -Dsurefire.failIfNoSpecifiedTests=false
```
预期测试计数：**23 项跨模块测试 100% 绿灯全量通过**。
