# Hermes Harness² 阶段六：真实 RAG/工具/记忆 Probe 与可复现实验门禁 只读审计报告

## 一、审计背景与阶段五遗留问题法医鉴定

在阶段五中，团队成功打通了真实 DeepSeek 官方 API (`deepseek-flash`) 的流式网络调度，生成了 10 份具备 `REAL_PRODUCTION` 执行模式的不可变凭单，并诚实地给出了 `hypothesisVerified = false`（假设被证伪）的实验结论。

然而，在阶段五验收裁定中，发现了 4 大破坏实验有效性与工程门禁的核心阻断：

### 1. 业务用例与真实执行语义脱节（Prompt-Only 假冒 Full Harness）
- **现象**：落盘的 10 份证据中，全部为 `ragDocumentCount: 0` 与 `toolExecutions: []`；
- **根因代码**：在 `DefaultOfflineProbeExecutor.java` 中构建 `ChatRequest` 时，仅注入了 `question` 与 `systemPrompt`，完全没有注入：
  1. `rag_contexts`（导致 `comp-rag-tool-05` 与 `kb-qa-01` 根本没有 RAG 文档输入）；
  2. `tool_method_ids`（导致 `tool-route-02` 与 `param-guard-03` 根本没有可用工具）；
  3. `history`（导致 `ctx-history-04` 根本没有历史消息上下文）。
- **后果**：5 大业务用例实际全部退化为“纯文本问答（Prompt-Only）”，测试得出的工具不匹配并非候选策略失败，而是系统根本无工具可用。

### 2. 真实 API 网络测试污染默认测试门禁 (`mvn test`)
- **现象**：`HermesHarnessStage5RealProbeTest` 位于普通 `src/test` 目录，无 `@Tag("live")` 或 profile 隔离；
- **后果**：CI 或无网络、无 API Key 的开发环境执行 `mvn test` 直接报错；每次运行产生不确定的网络耗时与随机回答。

### 3. 测试覆盖已提交的不可变实验资产（破坏可复现性）
- **现象**：探针测试落盘目录写死为 `experiment/outputs/harness5/run-20261030-stage5-real/`；
- **后果**：每次运行测试都会覆盖该目录下的 JSON 凭单，导致 `checksums.sha256` 频繁失效，git 工作区自动变脏。

### 4. 任务书哈希签名概念混淆
- 阶段五报告中将正文签名（`body_sha256: b4d42155...`）与全文件签名（`full_file_sha256: 9fa342de...`）混淆表述，造成冻结审计歧义。

---

## 二、当前项目执行路径与数据边界法医追踪

### 2.1 AgentOrchestrator 真实能力挂载路径

1. **RAG 上下文注入路径**：
   - 输入：`ChatRequest.getRagContextsList()`；
   - 行为：`AgentOrchestrator` 行 428-456 遍历 `effectiveRagContexts`，动态注册 `knowledgeBase<Id>` 与 `knowledgeQuery` 工具；并在行 559 调用 `buildRagContextBlock` 将知识库内容挂载至 `UserMessage` 前缀；
   - 证据采集：行 569 `evidenceCollector.recordRagEvidence(effectiveRagContexts.size(), ragTopScore)`。

2. **工具注册与方法调用路径**：
   - 输入：`ChatRequest.getToolMethodIdsList()`；
   - 行为：行 459 获取工具名称列表；行 610 将其通过 `ToolCallbackResolver` 包装为带有 `ReActCycleGuard` 与 `evidenceCollector` 监控的 `guardedTools`；
   - 证据采集：工具实际执行完成时通过 `wrapWithCycleGuard` 触发 `evidenceCollector.recordToolExecution(toolName, success)`。

3. **历史记忆上下文注入路径**：
   - 输入：`ChatRequest.getHistoryList()`；
   - 行为：行 504 `List<ChatMessage> historyList = request.getHistoryList()`，依据不可变凭单中的 `shortTermWindowSize` 进行滑动截断，并依次转换为 `UserMessage` 与 `AssistantMessage` 压入当前推理上下文。

---

## 三、阶段六唯一待验证假设 (Locked Hypothesis)

根据《AGENTS.md》第二章门禁要求，正式锁定阶段六唯一可证伪假设：

> **【阶段六唯一待验证假设】**：
> 在真实 `AgentOrchestrator` 生产轨道中，为基准任务套件分别注入其业务语义所对应的 RAG 上下文、工具回调与历史记忆（分别打标为 `RAG`、`TOOL`、`HISTORY`、`COMPOSITE`；无法注入真实能力者严格降级标记为 `PROMPT_ONLY`），在同等能力与约束边界下，不可变 Harness 候选变异策略是否依然能在不降低任务成功率与答案质量的前提下实现端到端 Token 缩减与预算受控；若用例未注入对应能力，则其盲审结果与指标必须被硬性排除出 RAG/TOOL 算法采纳判定之外。

- **输入边界**：包含 5 个具备精确能力标签（`CapabilityTag`）的离线测试用例；
- **输出边界**：不可篡改且标记有能力标签的 `HarnessEvidenceRecord` JSON 文件；
- **失败与拦截判据**：
  1. 若任务标记为 `RAG` 但 `ragDocumentCount == 0`，判定为非法测试并立即中断；
  2. 若任务标记为 `TOOL` 但未挂载有效 `ToolCallback`，判定为非法测试；
  3. 真实探针测试必须物理隔离在 `-Plive` profile 下，默认 `mvn test` 零外网、零 API Key 依赖。

---

## 四、只读审计结论

- **项目真实环境已完全具备 RAGContext、ToolCallback 与 History 的无缝驱动能力**；
- **阶段五的缺陷属于测试用例装配层的疏漏与门禁隔离缺失，无需对生产核心架构进行侵入式重构**；
- **阶段六准入条件达成，正式进入定向文献与代码研读**。
