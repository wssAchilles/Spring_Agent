# Phase 121 实施详案与工程契约文件

## 课题：支柱一：复杂业务 Agent 认知与编排 —— 多智能体自适应分层协同、意图委托网络与有界状态流转中枢 (Multi-Agent Adaptive Hierarchical Swarm Delegation & Bounded Intent Handover Network)

> **归档路径**：`docs/plans/phase_121_plan.md`  
> **基线环境约束**：
> - 唯一生成模型：DeepSeek API（主干模型参数化思考模式 `thinking: {"type": "enabled"}`，严格遵循官方 API 规范）
> - 唯一向量模型：阿里千问 (Qwen) Embedding (1536 维超球面空间，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$)
> - 隔离环境：Java 21 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)
> - 业务边界：100% 聚焦企业级知识库与智能体编排，严禁力学发散
> - 规范准则：独占使用简体中文

---

### 一、唯一待验证假设 (Hypothesis H-PHASE121-001)

> **假设 H-PHASE121-001**：在严格保持 Java 21 隔离环境、DeepSeek API 唯一生成模型（主干模型参数化思考 `thinking: {"type": "enabled"}`）与阿里千问 1536 维超球面向量空间（$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）基线不变的前提下：
> 1. 通过构建**阿里千问 1536 维超球面意图亲和度匹配器 (`HypersphericalIntentMatcher`)** 并设立 $\tau_{\text{intent}} \ge 0.82$ 严格门禁，能够实现边缘歧义意图 100% 拦截并安全上浮至主干协调者，意图委派准确率由基线的 $78.5\%$ 提升至 $\ge 96.0\%$；
> 2. 通过构建**基于李雅普诺夫单调递减函数与调用栈的有界交接状态机守卫 (`BoundedHandoverGuard`)**，硬编码最大委托深度 $D_{\max} \le 4$ 并实时拦截即时与拓扑闭环，能够将智能体乒乓交接死锁与无限委托发生率**彻底压降至严格为 0**；
> 3. 通过构建**跨智能体思考流与上下文继承传递器 (`ThinkingContextPropagator`)**，严格对齐 DeepSeek 官方 API 规范并无损传递前序 `reasoning_content`，能够 100% 杜绝因上下文格式错误引发的 HTTP 400 Bad Request 异常，并消除跨 Agent 业务幻觉；
> 4. 通过构建**纯 Java 21 Record 格式的不可变委托存证凭单 (`SwarmDelegationReceipt`)**，内嵌拓扑因果树与 SHA-256 密码学签名，单次生成与自验真耗时 $\le 50\mu\text{s}$，实现全链路 100% 防篡改审计追踪。

---

### 二、拟实施文件清单与代码架构

#### 模块：`backend/qknow-framework/qknow-ai`
包路径：`tech.qiantong.qknow.ai.swarm.delegation`

1. **[NEW] `SwarmDelegationReceipt.java`**
   - 纯 Java 21 Record 不可变委托存证凭单；
   - 封装 `receiptId`, `sessionId`, `rootAgentId`, `delegationDepth`, `intentAffinity`, `delegationTopologyTree`, `startTimestampMicros`, `endTimestampMicros`, `status`, `signature`；
   - 内置 SHA-256 自签名与常数时间验真方法 `verifySignature()`。

2. **[NEW] `BoundedHandoverGuard.java`**
   - 基于离散李雅普诺夫能量衰减函数的有界交接状态机守卫；
   - 维护线程安全不可变调用栈 `DelegationCallStack`；
   - 硬编码最大委托调用深度 $D_{\max} \le 4$；
   - 实施双向即时乒乓拦截（$A \to B \to A$）与多节点拓扑闭环拦截（$A \to B \to C \to A$）；
   - 异常时平滑上浮至主控协调者执行收敛。

3. **[NEW] `HypersphericalIntentMatcher.java`**
   - 阿里千问 1536 维超球面高精度意图自适应委托匹配器；
   - 强制向量几何模长校验 $\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$；
   - 纯内存极速计算超球面测地余弦内积，实施 $\tau_{\text{intent}} \ge 0.82$ 置信度门禁；
   - 低置信度安全上浮，拒绝盲目委派。

4. **[NEW] `ThinkingContextPropagator.java`**
   - 跨智能体思考流与上下文继承传递器；
   - 对齐 DeepSeek 官方 API 参数化思考规范（`thinking: {"type": "enabled"}`）；
   - 跨 Agent 移交时抽取前序因果脚手架 `InheritedRationale`，工具调用模式无损透传 `reasoning_content`，消除 HTTP 400 异常；
   - 保持因果下闭包，消除跨智能体认知幻觉。

5. **[NEW] `HierarchicalSwarmDelegationMetacenter.java`**
   - 多智能体自适应分层协同与意图委托中枢外观调度器；
   - 组合 Matcher、Guard、Propagator，编排从意图解析、亲和度匹配、安全交接守卫、思考流传递到最终存证凭单签发的全链路。

#### 模块：`backend/tests`
包路径：`tech.qiantong.qknow.hermes.delegation`

6. **[NEW] `Phase121SwarmDelegationContractTest.java`**
   - 完备的 Phase 121 工程契约自动化测试套件；
   - 覆盖 8 大核心契约断言，验证数学定理与工程防线指标。

---

### 三、八大核心工程契约设计 (Contracts 1 ~ 8)

1. **契约 1（超球面流形几何约束与千问 1536 维向量校验契约）**：
   - 输入向量维度必须严格等于 1536；
   - 模长必须满足 $\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$，非标向量拒绝计算并抛出异常。
2. **契约 2（测地意图匹配精度与置信度门禁拦截契约）**：
   - 当最高亲和度 $\ge 0.82$ 时成功委派至目标智能体；
   - 当最高亲和度 $< 0.82$ 时 100% 拦截并触发安全上浮。
3. **契约 3（李雅普诺夫单调深度硬熔断契约 $D_{\max} \le 4$）**：
   - 连续向下委托至第 4 层后，第 5 次委派必须被绝对拦截，返回 `MAX_DELEGATION_DEPTH_EXCEEDED` 并触发主控收敛。
4. **契约 4（双向即时乒乓交接死锁拦截契约）**：
   - 发生 $A \to B$ 后，若 $B$ 试图重新移交给 $A$，必须在 $\le 10\mu\text{s}$ 内被状态机守卫拦截，返回 `SWARM_PING_PONG_DETECTED`。
5. **契约 5（多节点拓扑闭环死锁拦截契约）**：
   - 发生 $A \to B \to C$ 后，若 $C$ 试图移交给 $A$，必须被拓扑闭环探测器拦截，返回 `SWARM_TOPOLOGICAL_CYCLE_DETECTED`。
6. **契约 6（DeepSeek 参数化思考流规范回传与因果脚手架抽取契约）**：
   - 组装包含 `reasoning_content` 的 Assistant 消息，验证字段完整性；
   - 跨 Agent 委托时成功抽取 `InheritedRationale`，格式无损。
7. **契约 7（不可变委托执行凭单 SHA-256 密码学签名与验真契约）**：
   - 凭单各字段不可变，签名长度严格为 64 字符十六进制；
   - 原始凭单自验真 100% 为 true；单比特字段篡改后验真 100% 为 false。
8. **契约 8（端到端分层委托多智能体业务流转闭环契约）**：
   - 模拟复合业务请求（例如“合规审计 + 预算核算”），驱动 Metacenter 完成自动分发、思考继承与结果聚合，最终签发完整凭单。

---

### 四、验证与全量回归命令

```bash
# 1. 运行 Phase 121 专属契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -pl tests -Dtest=tech.qiantong.qknow.hermes.delegation.Phase121SwarmDelegationContractTest

# 2. 运行后端全量多智能体与 Hermes 跨模块防退化回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -pl qknow-framework/qknow-ai,tests -Dtest="Phase121SwarmDelegationContractTest,Phase119HierarchicalGraphRagContractTest,Phase118McpSagaPipelineContractTest,Phase117NashDebateConsensusContractTest,CrossBorderProcurementEndToEndTest"
```

---

### 五、实施纪律声明

本实施计划已严格遵循《Research-to-Implementation Gate（铁律二）》要求，完成了真实项目追踪、双路学术与工业深度调研、三大核心数学定理推导与六大开源生态对标。在获得用户明确批准前，绝不创建任何业务实现代码。
