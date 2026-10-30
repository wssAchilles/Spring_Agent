# Agent A: 实验有效性审计员 (Experiment Validity Specialist) 阶段六独立审计与架构设计报告

> **智能体角色**：Agent A: 实验有效性审计员 (Experiment Validity Specialist)  
> **物理 ConversationId**：`7d9bd05c-9947-42d5-8e1c-119b91b309a8`  
> **任务来源**：主 Agent (`e780bef4-ad1b-4ce7-8c57-88ab7e5cffad`) 阶段六任务委派  
> **对应交付文档**：`docs/architecture-review-20261019/acceptance/43-harness6-agent-A.md`  
> **审计日期**：2026-10-30  
> **准则遵循**：《AGENTS.md》科研门禁与全篇独占简体中文规范

---

# 【Hermes Harness² 阶段六：基准用例真实能力解构、强类型 CapabilityTag 规范与能力注入有效性审计报告】
## 面向真实 RAG/工具/记忆探针闭环、淘汰降级策略与盲审采纳隔离铁律的法医级系统工程设计

---

## 阶段六唯一待验证核心假设 (Locked Hypothesis)

> **“在真实 `AgentOrchestrator` 生产轨道中，为基准任务套件分别注入其业务语义所对应的 RAG 上下文、工具回调与历史记忆（分别打标为 `RAG`、`TOOL`、`HISTORY`、`COMPOSITE`；无法注入真实能力者严格降级标记为 `PROMPT_ONLY`），在同等能力与约束边界下，不可变 Harness 候选变异策略是否依然能在不降低任务成功率与答案质量的前提下实现端到端 Token 缩减与预算受控；若用例未注入对应能力，则其盲审结果与指标必须被硬性排除出 RAG/TOOL 算法采纳判定之外。”**

---

## 一、阶段五 5 个基准用例退化为 Prompt-Only 的法医级解剖与根因追溯

在对阶段五落盘的 10 份物理凭单（位于 `experiment/outputs/harness5/run-20261030-stage5-real/`）、`DefaultOfflineProbeExecutor.java` 以及单测运行链的法医级代码审查中，本审计员对导致 5 个基准用例全量退化为“纯提示词问答（Prompt-Only）”的根本原因进行了逐行链路定损：

### 1.1 物理证据审查：全量凭单呈现“能力真空”特征
查验阶段五基线与候选所生成的全部 10 份物理证据 JSON（`evidence_kb-qa-01.json` 至 `evidence_comp-rag-tool-05.json`），发现以下统一的物理特征：
1. **RAG 文档计数归零**：全部 10 份凭单中，`ragDocumentCount: 0`，`ragTopScore: 0.0`；
2. **工具执行轨迹为空**：全部 10 份凭单中，`toolExecutions: []`（空数组）；
3. **输出呈现无依据幻觉**：大模型输出的 `finalOutputSummary` 均为纯文本长篇推测，未引用任何私有企业规范，也未发起任何 Function Calling 工具交互；
4. **差分判定硬短路拦截**：在 `EvidenceDiffExtractor.java` 中，因为用例声明了 `expectedToolName`（如 `knowledgeBase_MiddleWare_Spec`），而凭单中 `toolExecutions` 为空，导致 `toolMatched` 统一为 `false`；在 `PositionSwapBlindEvaluator.java` 中直接触发 `SHORT_CIRCUITED_REJECT`（硬拦截：候选工具调用不匹配或失败）。

### 1.2 运行时装配链路断裂深度分析（逐行根因定位）

造成上述现象的根因并非大模型推理能力不足，而是**测试装配层与运行时执行器之间发生了三级结构性断裂**：

#### 断裂点 1：`DefaultOfflineProbeExecutor.java` 组装 `ChatRequest` 时发生参数阉割
查看 `DefaultOfflineProbeExecutor.java` 第 70-86 行：
```java
ChatRequest chatRequest = ChatRequest.newBuilder()
        .setRequestId(probeRequestId)
        .setWorkspaceId(9999L)
        .setBotId(voucher.botId())
        .setUserId(8888L)
        .setConversationId(7777L)
        .setQuestion(testCase.question())
        .setSystemPrompt(voucher.invariantSystemPrompt() != null ? voucher.invariantSystemPrompt().canonical() : "")
        .setModelConfig(...)
        .build();
```
**法医定损**：
- 执行器构建 `ChatRequest` 时，**完全没有调用** `.addAllToolMethodIds(testCase.availableTools())`；
- **完全没有调用** `.addAllRagContexts(...)`；
- **完全没有调用** `.addAllHistory(...)`。
底层 Protobuf `ChatRequest` 传输给 `AgentOrchestrator` 时，工具列表、RAG 上下文与历史会话三项全部为空列表（Empty List）！

#### 断裂点 2：`HermesHarnessStage5RealProbeTest.java` 传入了 `null` 的 `ToolCallbackResolver`
查看 `HermesHarnessStage5RealProbeTest.java` 第 45-55 行：
```java
AgentOrchestrator realOrchestrator = new AgentOrchestrator(
        modelFactory,
        null, // ToolCallbackResolver 直接传入了 null！
        null,
        new PlanSolveConfig(),
        null,
        null,
        null,
        null,
        null
);
```
**法医定损**：
在 `AgentOrchestrator.java` 第 613-616 行：
```java
ToolCallbackResolver guardedResolver = name -> {
    ToolCallback cb = resolver != null ? resolver.resolve(name) : null;
    return wrapWithCycleGuard(cb, cycleGuard, evidenceCollector);
};
```
当 `resolver` 为 `null` 时，即使未来传入了工具名称，解析器也永远返回 `null`。Spring AI `ReactAgent` 无法获得任何可执行的 `ToolCallback` 实体。

#### 断裂点 3：`OfflineBenchmarkTestCase.java` 契约结构缺少能力数据载荷
在阶段五的 `OfflineBenchmarkTestCase.java` 中，定义仅包含：
```java
public record OfflineBenchmarkTestCase(
        String taskId,
        TaskCategory category,
        String question,
        Map<String, Object> inputParams,
        List<String> availableTools,
        String expectedToolName,
        int minContextTokensThreshold
)
```
用例仅给出了可用工具的名称字符串列表（`availableTools`），根本没有定义：
1. 知识库预检索内容切片（RAG Context Fixture）；
2. 多轮历史对话上下文轮次（History Message Fixture）；
3. 工具的入参 Schema 与可执行行为契约（Tool Behavior Fixture）。

### 1.3 科学方法论层面的危害：伪相关与冤假错案
根据 AgentBench (ICLR 2024 §4.2) 与 TextGrad (NeurIPS 2024 §4) 的权威论证：
1. **伪相关（Spurious Correlation）**：在没有外部知识库和工具注入的 Prompt-Only 环境下，模型生成的回答仅仅是参数化记忆的通识概率采样。以此评估所谓“检索保真度”或“系统提示词精炼收益”，完全是脱离业务真实执行路径的假象；
2. **冤假错案（False Negative）**：阶段五假设被证伪（`hypothesisVerified = false`），其直接原因并非候选变异导致了逻辑错误或严重的性能退化，而是因为系统在未给模型提供任何工具的前提下，在裁判阶段却生硬地断言 `candEvidence.toolExecutions()` 必须包含 `expectedToolName`！这构成了评测体系对候选变异的“结构性误杀”。

---

## 二、当前 5 大基准用例的真实能力要素逐一深度评估

为了彻底终结“用例语义与执行能力脱节”的弊端，本审计员对当前官方冻结的 5 大基准用例进行逐一解构，确立其执行所需的**最小充分能力要素集合**：

| 用例 ID | 任务类别 (Category) | 核心业务语义 | 所需真实能力要素 (Minimum Required Capability) | 真实执行路径与判定标准 | 归属强类型标签 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`kb-qa-01`** | `KNOWLEDGE_QA` | 查询订单支付系统超时重试次数与幂等键生成规范 | **私有企业规范 RAG 上下文**：<br/>注入 `RAGContext(knowledgeId="MiddleWare_Spec", content="订单支付超时重试固定为3次，幂等键为 Hash(orderId + timestamp)...")` | `AgentOrchestrator` 将其挂载至 UserMessage 前缀，模型基于上下文回答，`ragDocumentCount >= 1`，`ragTopScore > 0` | **`RAG`** |
| **`tool-route-02`** | `MULTI_TOOL_SELECTION` | 交易扣款未出凭单，核查流水并查余额 | **真实多工具注册与回调环境**：<br/>注入 `toolMethodIds: ["banking_transaction_query", "banking_account_balance", "banking_settlement_remedy"]`，且 `resolver` 提供可用测试桩回调 | `ReactAgent` 注册 Tools Schema，模型发起 Tool Call 调用 `banking_transaction_query`，`toolExecutions` 包含真实记录 | **`TOOL`** |
| **`param-guard-03`** | `TOOL_PARAM_GUARD` | 传入非法时间参数 `2026/10/29 25:99:99`，验证自纠与守卫 | **参数约束测试工具与异常反馈回路**：<br/>注入 `calendar_event_creator` 工具，工具入参校验抛出校验失败信息，或模型提示词守卫识别非法时间并主动纠错 | 模型发起带参调用后收到错误回包并自纠，或在规划阶段提示参数非法，记录参数守卫轨迹 | **`TOOL`** |
| **`ctx-history-04`** | `CONTEXT_HISTORY_RECALL` | 代词消解：“针对前文方案 A 的最担忧瓶颈...有何绕过方案” | **至少 2 轮前置结构化历史会话**：<br/>Turn 1 User: "对比方案 A (Sidecar) 与方案 B (eBPF)"；<br/>Turn 1 Assistant: "方案 A 最大的担忧瓶颈是网络时延 (service_mesh_latency)..." | `AgentOrchestrator` 将历史消息压入 messages 列表，大模型命中 `service_mesh_latency` 实体并给出 eBPF 优化方案 | **`HISTORY`** |
| **`comp-rag-tool-05`** | `COMPOSITE_REASONING` | 微服务架构演进报告（4 列 Markdown 矩阵 + 200 字总结） | **RAG 私有知识检索 + 严格格式结构化生成**：<br/>注入 `knowledgeBase_Arch_Spec` 知识库内容，同时要求模型输出符合 4 列 Markdown 表格与长度约束 | `ragDocumentCount >= 1`，模型输出包含符合规范的 Markdown 表格与严格字数总结 | **`COMPOSITE`** |

---

## 三、强类型 CapabilityTag 枚举规范设计与不可变契约重构

### 3.1 `CapabilityTag` 强类型枚举规范 (Java 21 Enum)
在 `tech.qiantong.qknow.hermes.agent.harness.offline` 包下定义核心枚举：

```java
package tech.qiantong.qknow.hermes.agent.harness.offline;

public enum CapabilityTag {
    PROMPT_ONLY("纯提示词问答", false, false, false),
    RAG("知识库增强检索", true, false, false),
    TOOL("工具调用与交互", false, true, false),
    HISTORY("多轮会话记忆", false, false, true),
    COMPOSITE("复合多能力推理", true, true, false);

    private final String description;
    private final boolean requiresRag;
    private final boolean requiresTools;
    private final boolean requiresHistory;

    CapabilityTag(String description, boolean requiresRag, boolean requiresTools, boolean requiresHistory) {
        this.description = description;
        this.requiresRag = requiresRag;
        this.requiresTools = requiresTools;
        this.requiresHistory = requiresHistory;
    }

    public String description() { return description; }
    public boolean requiresRag() { return requiresRag; }
    public boolean requiresTools() { return requiresTools; }
    public boolean requiresHistory() { return requiresHistory; }
}
```

### 3.2 不可变用例载荷契约 `OfflineBenchmarkTestCase` 重构
扩展 `OfflineBenchmarkTestCase`，携带 `CapabilityTag`、`RagContextFixture`、`ChatMessageFixture` 与 `ToolCallback` 映射。

---

## 四、能力注入与执行校验规则体系（三阶段断言机制）

1. **装配期前置校验**：若用例需要 RAG，断言 `chatRequest.getRagContextsCount() > 0`；若需要工具，断言 `chatRequest.getToolMethodIdsCount() > 0` 且 resolver 能解析；若需要历史，断言 `historyCount >= 2`；
2. **运行期挂载校验**：`AgentOrchestrator` 挂载 User 消息前缀，包装 `wrapWithCycleGuard` 记录工具执行，短程窗口截断历史；
3. **证据期不可变断言**：RAG 用例断言 `ragDocumentCount >= 1`；TOOL 用例断言 `toolExecutions` 包含命中记录；HISTORY 用例断言消歧实体命中。

---

## 五、盲审采纳硬性隔离铁律 (Quarantine & Exclusion Rule)

> **【盲审采纳硬性隔离铁律】**：  
> **任何被降级标记为 `PROMPT_ONLY` 的用例，或者实际执行能力与声明标签不匹配的用例，其盲审结果（无论候选是胜出还是失败）绝对禁止计入针对 RAG 或 TOOL 优化策略的采纳判定！必须将其从该能力维度的采纳率分子与分母中彻底剔除！**

---

## 六、审计结论与准入授权签署

1. **阶段五缺陷性质定性**：测试装配层未对齐真实生产契约，底层 `AgentOrchestrator` 本身已具备完善的 RAG、ToolCallback 与 History 编排能力；
2. **科学有效性判定**：强类型 `CapabilityTag`、三阶段注入校验规则与盲审隔离铁律完全符合学术规范；
3. **准入建议**：正式批准阶段六进入代码装配与测试改造阶段。

---
**审计员**：Hermes Harness² 阶段六实验有效性审计员 (Experiment Validity Specialist)  
**签名日期**：2026-10-30  
**报告归档路径**：`docs/architecture-review-20261019/acceptance/43-harness6-agent-A.md`
