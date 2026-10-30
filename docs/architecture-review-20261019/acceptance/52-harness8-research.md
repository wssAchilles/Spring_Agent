# Hermes Harness² 阶段八（Stage 8-lite）：定向学术与源码研究报告

- **文档编号**：`52-harness8-research.md`
- **对应台账**：[`52-harness8-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/52-harness8-research.ledger.json)
- **当前分支基线**：`feat/hermes-harness-stage7`（HEAD: `362074a0`）
- **阶段定位**：围绕 **Stage 9 真实 PPC（Propose-Probe-Compare）所必需的最小使能底座**，聚焦可信 usage 计量、真实能力上下文注入、Guard/Cycle 语义合并与阻断归因、RAG 归因打通及最小执行记录开展定向研究。

---

## 一、本阶段唯一待验证研究假设

> **唯一待验证假设（Stage 8-lite Hypothesis）**：
> 在不引入新模型、不扩展企业级重型认证/无限嵌套 Schema、不改变生产默认 `KernelBridgeMode.OFF` 的前提下，只要修复 Stage 7 桥接层中的五处信号失真——（1）从 `ChatResponse.getMetadata().getUsage()` 提取真实 Token 消耗并用 `UsageStatus.UNKNOWN` 诚实表达缺失值（严禁用字符估算或 `0` 冒充）、（2）将 `CapabilityContextRecord` 真正注入 `ChatRequest` 并对空扩展能力标记 `PROMPT_ONLY`、（3）合并 `HarnessKernelBridge.guard` 与 `ReActCycleGuard` 消除重复计数并将被阻断调用记为 `success = false, guardBlocked = true`、（4）打通 `ragProvided / ragUsed / ragScore` 与 `RagAttributionStatus`、（5）将这些字段向后兼容地写入 `HarnessEvidenceRecord`——即可为 Stage 9 真实 PPC 提供零伪造、可比较、可证伪的最小执行凭单底座。

---

## 二、定向文献与源码核验综述（对应 Research Ledger）

### 2.1 `H8-PAPER-01`：RAGChecker — 检索供给与生成利用的解耦诊断（`VERIFIED`）
- **来源**：Dongyu Ru et al., *"RAGChecker: A Fine-Grained Framework for Diagnosing Retrieval-Augmented Generation"*, NeurIPS 2024 (`arXiv:2408.08067`, 全文 HTML 已核验)。
- **核心发现**：
  1. 端到端回答质量无法区分“未提供检索上下文 / 检索召回失败（Retriever Gap）”与“提供了高相关检索片段但生成器未采纳（Context Underutilization）”。
  2. RAGChecker 定义了生成器侧的 **Context Utilization**（已检索上下文中的有效信息被最终回答采纳的比例）。当请求根本未启用 RAG（`NO_RAG`）时，其状态在代数上不同于“注入了 $K>0$ 篇文档但利用率为 $0$（`PROVIDED_UNUSED`）”。
- **对本项目的直接映射**：
  在当前 [`HermesHarnessKernel.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/kernel/HermesHarnessKernel.java) 行 169–170 中，`evaluateTrajectory` 硬编码 `new TrajectoryEvidence(0, 0, 0, 0, toolExecutions)`，把所有请求都变成了 `ragProvided = 0, ragUsed = 0`。Stage 8 引入显式枚举 `RagAttributionStatus { NO_RAG, PROVIDED_UNUSED, PROVIDED_USED }` 与 `RagAttributionRecord`，将 `AgentOrchestrator` 真实检索到的文档数 `ragProvided`、最高余弦相似度 `ragScore` 以及最终输出实际命中的文档数 `ragUsed` 完整传入 `TrajectoryAttributionAnalyzer`，消除全 `0` 假归因。

### 2.2 `H8-PAPER-02`：AgentRx — 轨迹级验证日志与守卫触发根因分类（`VERIFIED`）
- **来源**：Shraddha Barke et al. (Microsoft Research), *"AgentRx: Diagnosing AI Agent Failures from Execution Trajectories"*, 2026 (`arXiv:2602.02475`, 全文 HTML 已核验)。
- **核心发现**：
  1. AgentRx 将多步执行轨迹归一化后，对每一步 $k$ 评估组合成的约束集 $\mathcal{C}_k$，生成结构化验证日志 $\mathsf{V} = \{(k, C, e)\}$。
  2. 在其 9 类故障根因分类法（Table 1）中，**`Invalid Invocation`（无效调用/参数缺失）** 与 **`Guardrails Triggered`（安全守卫触发拦截）** 被严格标记为负向违规事件，绝不能与正常的工具成功观测（Observation）混同。如果守卫拦截被错误标记为成功执行，后续归因器将完全错失最早关键故障步（Critical Failure Step）。
- **对本项目的直接映射**：
  直接命中 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java) 行 1554–1570 的缺陷：当前 `HarnessKernelBridge.guard` 返回 `"[HARNESS_GUARD_REJECTED:...]"` 字符串，被外层 `wrapWithCycleGuard` 视为正常返回并调用 `evidenceCollector.recordToolExecution(toolName, durationMs, true, ...)`。Stage 8 必须将所有被 Guard/Cycle 阻断的调用在 `HarnessEvidenceCollector` 中记为 `success = false`，同时记录 `guardBlocked = true` 与 `guardBlockedCount`。

### 2.3 `H8-PAPER-03`：TraceSafe — 多步工具调用轨迹中的结构化守卫拦截（`VERIFIED`）
- **来源**：Yen-Shan Chen et al., *"TraceSafe: A Systematic Assessment of LLM Guardrails on Multi-Step Tool-Calling Trajectories"*, 2026 (`arXiv:2604.07223`, 全文 HTML 已核验)。
- **核心发现**：
  1. 针对 1,000+ 条多步工具调用轨迹的评测表明，守卫在工具调用场景中的首要瓶颈是**结构化校验能力（Structural Bottleneck，$\rho = 0.79$）**——即对工具幻觉（Hallucinated Tool）、缺失必填参数、冗余/越权参数的拦截，且必须在调用触达底层环境前（pre-invocation）阻断。
  2. 守卫在原生轨迹长度内具有良好有界时序稳定性，无需引入重型大模型守卫即可通过确定性前置规则拦截绝大多数接口不一致与参数幻觉风险。
- **对本项目的直接映射**：
  支撑 Stage 8 保持轻量确定性 `HoareTripleToolGuard` + `LocallyDenseCycleAutomaton` 组合，不做无限嵌套 JSON Schema，只聚焦请求级工具白名单（拦截 Hallucinated Tool）、顶层必填参数检查与局部稠密死循环阻断，并与 `ReActCycleGuard` 合并消除双层重复包装。

### 2.4 `H8-CODE-01` & `H8-CODE-02`：Harness² 原生账本与本项目 Stage 7 源码对照（`VERIFIED`）
- **来源**：本地 `/Users/achilles/Documents/优秀开源项目/harness2`（`ledger.py`, `runmark.py`, `selection.py`）与本项目 `backend`（commit `362074a0`）。
- **核心对比与适用性结论**：
  - **直接采用**：Harness² 的不可变执行凭单（Runmark）与 Compare 阶段硬约束优先判定原则；本项目 `DeepSeekCompatibleChatModel` 已实现的 `ChatResponse.getMetadata().getUsage()` 解析能力。
  - **必须改造**：
    1. 修复 `AgentOrchestrator` 流式响应丢失 `Usage` 的断链，增加 `UsageStatus.KNOWN | UNKNOWN`；
    2. 在 `HarnessKernelBridge.prepareExecution` / `AgentOrchestrator` 中真正调用 `CapabilityContextRecord.applyTo`；
    3. 消除 `HarnessKernelBridge.guard` 与 `ReActCycleGuard` 的重复包装，修正拒绝调用的 `success = false` 记录；
    4. 将真实 RAG 检索统计传入 `completeExecution`；
    5. 向后兼容扩充 `HarnessEvidenceRecord`。
  - **坚决拒绝（推迟至 Stage 9 之后或永久不做）**：完整生产级多租户认证、无限深度 JSON Schema 解析器、企业级 SLA 监控大盘、默认开启付费 live API 测试、MCP/HITL/SWARM 改造。
