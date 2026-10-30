# Agent B: Java 真实 Probe 工程师 (Java Live Probe Engineering Specialist) 技术方案设计与法医级代码审计报告

> **智能体角色**：Agent B: Java 真实 Probe 工程师 (Java Live Probe Engineering Specialist)  
> **物理 ConversationId**：`b08baf02-243a-4f5c-9f02-7b6194b0cc12`  
> **报告目标输出路径**：`docs/architecture-review-20261019/acceptance/43-harness6-agent-B.md`  
> **审计基准分支**：`feat/hermes-harness-stage5`  
> **实施目标分支**：`feat/hermes-harness-stage6`  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **系统生成模型基线**：DeepSeek 官方活跃模型（`deepseek-flash`）  
> **系统向量模型基线**：阿里千问 Embedding (`text-embedding-v4`) 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0$，余弦度量）  
> **生产全局配置基线**：`hermes.orchestration.enabled=false` 不可变，SINGLE 模式默认  
> **准则遵循**：《AGENTS.md》科研门禁、Java 21 隔离环境与独占简体中文铁律  
> **审计日期**：2026-10-30  

---

# 阶段六技术架构与工程实施方案：真实 RAG、工具回调与历史记忆在 ChatRequest 中的无缝注入与执行器改造

**报告人**：Java 真实 Probe 工程师 (Java Live Probe Engineering Specialist)  
**设计目标**：针对阶段五验收遗留的“Prompt-Only 冒充 Full Harness”、“测试污染默认构建门禁”以及“执行覆盖历史提交资产”等核心缺陷，结合系统既有架构（`hermes.proto`、`AgentOrchestrator` 行 420-570、`DefaultOfflineProbeExecutor`），为 Hermes Harness² 阶段六设计一套**零生产代码侵入（100% 局限于 test 作用域）、支持全能力注入、基于 Maven `-Plive` profile 物理隔离、可复现防篡改**的真实探针工程方案。

---

## 一、阶段五遗留缺陷法医级代码诊断与阶段六工程目标

### 1.1 阶段五缺陷法医级代码诊断
在阶段五中，团队使用 `ProductionAgentOrchestratorDriver` 成功驱动了真实 DeepSeek API，但通过对落盘凭单（`evidence_*.json`）与执行代码的法医级审计，确认存在以下致命阻断：
1. **`ChatRequest` 组装缺陷（Prompt-Only 伪退化）**：未调用 `.addAllRagContexts(...)`、`.addAllToolMethodIds(...)`、`.addAllHistory(...)`；
2. **`ToolCallbackResolver` 为空阻断**：构造 `AgentOrchestrator` 时传入的 `resolver` 为 `null`，导致工具解析始终返回 `null`；
3. **默认 `mvn test` 受公网测试污染**：无 `@Tag("live")` 或 Profile 过滤，无网络或无 Key 环境执行即报错；
4. **历史凭单被就地覆盖破坏验签**：落盘路径写死在已纳入版本控制的目录中。

### 1.2 阶段六四大核心工程目标
1. **全能力字段注入**：扩展 `OfflineBenchmarkTestCase` 与 `DefaultOfflineProbeExecutor`，实现 RAG 切片（`RAGContext`）、业务工具回调（`ToolCallback`）与多轮历史（`ChatMessage`）在 `ChatRequest` 中的 100% 无缝装配；
2. **零生产修改隔离**：所有扩展类与装配逻辑严格限制在 `src/test/java` 作用域内，`AgentOrchestrator` 与生产主干代码 0 行修改；
3. **`-Plive` 构建双轨隔离**：通过 Maven Profile 机制，保证默认 `mvn test` 100% 离线、零网络调用；只有显式指定 `mvn test -Plive` 时才激活真实 DeepSeek 探针；
4. **资产输出防污染**：将 Live 探针物理凭单输出重定向至未跟踪的隔离目录（`experiment/outputs/harness6-live/<runId>/` 或 `target/live-evidence/`），确保测试前后 `git status` 绝对 clean。

---

## 二、AgentOrchestrator 既有内部能力分支的精准激活机制

在 `AgentOrchestrator.java`（行 420-570）中，已完整内建了处理 RAG、工具与记忆的生产路径：
1. **RAG 激活**：`ChatRequest.getRagContextsList()` 传入后，自动生成 `knowledgeBase{id}` 与 `knowledgeQuery` 工具，且自动挂载 `<knowledge_base>` 前缀至当前轮次 User 消息；
2. **工具激活**：`ChatRequest.getToolMethodIdsList()` 传入后，通过注入的 `DynamicTestToolCallbackResolver` 解析出真实的测试 `ToolCallback`，并由 `wrapWithCycleGuard` 捕获执行轨迹；
3. **记忆激活**：`ChatRequest.getHistoryList()` 传入后，自动按凭单中的 `shortTermWindow` 进行尾部滑动截断并压入当前推理上下文。

---

## 三、数据结构扩展设计：具备全能力语义的 OfflineBenchmarkTestCase

1. 定义 `CapabilityTag`（`PROMPT_ONLY`, `RAG`, `TOOL`, `HISTORY`, `COMPOSITE`）；
2. 定义 `RagContextFixture`（携带 `knowledgeId`, `knowledgeName`, `preRetrievedContent`）；
3. 定义 `ChatMessageFixture`（携带 `role`, `content`）；
4. 在 `Stage6BenchmarkSuiteFactory.java` 中实现 5 大基准用例的全能力装配：
   - `kb-qa-01`：企业中间件超时与幂等规范（RAG）；
   - `tool-route-02`：银行流水与余额核查工具（TOOL）；
   - `param-guard-03`：日历时间参数校验工具（TOOL）；
   - `ctx-history-04`：微服务架构对比前置历史（HISTORY）；
   - `comp-rag-tool-05`：架构白皮书切片与系统指标工具（COMPOSITE）。

---

## 四、执行器重构设计：DefaultOfflineProbeExecutor 的全能力无缝注入改造

1. 实现 `DynamicTestToolCallbackResolver`，支持测试环境动态注册工具；
2. 在 `DefaultOfflineProbeExecutor` 构建 `ChatRequest` 时，完整展开 `addAllRagContexts`、`addAllToolMethodIds` 与 `addAllHistory`；
3. 增加 `validateCapabilityGate` 硬门禁，断言凭单与声明能力的一致性。

---

## 五、零污染隔离铁律：Live 探针与 Maven Profile 隔离设计

1. 在 `pom.xml` 中配置 Surefire 默认排除 `<excludedGroups>live</excludedGroups>`；
2. 声明 `<profile><id>live</id>...</profile>`，仅在该 profile 下执行 `@Tag("live")` 测试；
3. 输出路径隔离至 `experiment/outputs/harness6-live/<runId>/`，并在 `.gitignore` 中配置。

---

## 六、实施契约与准入建议

所有设计均已就绪，100% 局限于 `src/test/java` 作用域，生产核心代码零侵入，正式批准进入任务书冻结与实施阶段。

---
**工程师**：Hermes Harness² 阶段六 Java 真实 Probe 工程师 (Java Live Probe Engineering Specialist)  
**签名日期**：2026-10-30  
**报告归档路径**：`docs/architecture-review-20261019/acceptance/43-harness6-agent-B.md`
