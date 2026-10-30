# Hermes Harness² 阶段六：真实 RAG/工具/记忆 Probe 与可复现实验门禁 前沿研究报告

## 一、研究背景与核心关切

在阶段五真实 DeepSeek 探针闭环验收中，系统成功验证了真实公网 API 的连通性与诚实证伪结论，但暴露了“业务用例语义与执行脱节（Prompt-Only 冒充 Full Agent）”、“真实 API 测试污染默认单元测试门禁”以及“测试运行时覆盖已提交历史资产”等工程缺陷。

针对上述阻断，本报告遵循《AGENTS.md》第二章 Reading-First 原则，定向检索并研读了 Google Research Harness²、AgentBench (ICLR 2024)、DSPy (NeurIPS 2023)、OPRO (ICLR 2024)、TextGrad (2024) 与 Reflexion (NeurIPS 2023) 6 大权威成果，系统回答阶段六方案设计中的 4 大核心问题。

---

## 二、四大核心问题深度论证

### 1. 为什么 Prompt-Only 实验绝不能代表 Tool/RAG/Memory Harness？

- **理论推导与证据支持**（AgentBench §4.2、TextGrad §4）：
  一个完整的 Agent Harness 包含感知（Perception/Retrieval）、规划（Planning）、行动（Tool Execution）与反思记忆（Memory）四大闭环。
  - **信息流阻断**：当 `ChatRequest` 缺少 `rag_contexts` 时，模型无法接触到外部企业知识库，其回答完全依赖预训练参数中的通用知识。此时所谓的“检索保真度”或“无偏回答”评价本质上是在评估大模型的通识幻觉，而非系统的知识检索与增强能力；
  - **决策空间退化**：当缺少 `tool_method_ids` 时，模型无法生成符合规范的 Function Call Payload，也不可能收到环境执行反馈。此时所谓的“工具路由匹配”退化为无实体的纯文本推测；
  - **认知上下文真空**：当缺少 `history` 时，多轮对话中的指代消歧（Co-reference Resolution）与状态转移无从发生。
- **结论**：将未挂载 RAG、工具和历史的 Prompt-Only 执行结果表述为“Agent Harness PPC 评测”，在科学方法论上属于伪相关（Spurious Correlation）。阶段六必须为每个用例明确划分其所具备的真实能力标签。

### 2. 多能力基准用例（Multi-Capability Cases）应如何严格控制变量与隔离？

- **方法论参考**（DSPy §3、Harness² `test_workflow.py`）：
  在评估 Harness 策略变异（如系统提示词精炼、工具引导增强、记忆窗口调优）时，必须严格遵守**单一变量原则（Single-Factor Invariance）**：
  1. **输入要素固定**：对于用例 $C_i$，Baseline 与 Candidate 在探针执行时，所注入的 `question`、`rag_contexts`（文档内容与置信度分数）、`tool_method_ids`（工具签名与入参定义）以及 `history`（历史对话轮次）必须 **100% 完全相同**；
  2. **唯一变异项锁定**：两者的唯一差异必须且只能是不可变凭单 `HermesHarnessVoucher` 中的特定组件配置（如 `invariantSystemPrompt` 或 `memoryContext`）；
  3. **能力标签强制校验**：
     - 若用例标注为 `RAG`，执行器在收集证据时必须断言 `ragDocumentCount > 0`；
     - 若用例标注为 `TOOL`，必须断言 `toolExecutions` 包含真实执行轨迹；
     - 若用例标注为 `HISTORY`，必须断言历史消息成功注入消息列表；
     - 若系统当前环境无法挂载对应能力，该用例必须显式降级标记为 `PROMPT_ONLY`，严禁纳入 RAG/TOOL 策略采纳计算。

### 3. Live 探针实验如何保证可复现性（Reproducibility）与成本可控？

- **工程实践参考**（Harness² `schedule.py` 与 Maven 标准构建模型）：
  1. **构建轨道严格双轨化**：
     - **默认构建门禁 (`mvn test`)**：仅运行确定性契约测试与内存隔离单测，零外部网络依赖，零 API Key 需求，保证 CI/CD 与无网络环境 100% 稳定通过；
     - **Live 探针套件 (`-Plive`)**：仅在显式传入 `-Plive` 激活 profile 并指定测试类时才触发真实外部大模型网络探针；
  2. **资产只追加不覆盖（Append-Only Isolation）**：
     - 阶段五已提交的历史资产（`experiment/outputs/harness5/run-20261030-stage5-real/`）设为永久归档只读；
     - Live 探针执行结果必须输出至未跟踪或临时目录（如 `experiment/outputs/harness6-live/<runId>/` 或 `target/live-evidence/`），并在 `.gitignore` 中显式忽略；
     - 运行测试后，执行 `git status` 必须保持绝对 clean，杜绝修改已跟踪文件的哈希签名；
  3. **预算硬熔断与看门狗**：
     - 单任务公网调用设置 60s 硬超时与 2 次指数退避重试；
     - 累加 Token 预算熔断器（如单次完整 PPC 循环不得超过 50,000 Tokens / 0.1 元人民币），超额自动熔断。

### 4. 哪些结论只能作为科学假设（Hypothesis），绝不能直接推导为生产结论？

- **学术与工业边界清晰划分**（OPRO §3、Reflexion §3）：
  1. **小样本 PPC 仅能作为候选变异的准入或拒绝门禁**：在 5 个基准离线用例上获得的 Token 节省或耗时变化，只能证明该变异在当前特定分布下“是否值得进一步验证”，绝不能直接等同于“线上生产环境全量收益”；
  2. **反向证伪结论具备绝对否决权**：根据反证法，若候选变异在离线基准用例（如 `ctx-history-04`）上已经发生了致命的性能退化（如 Token 暴涨 +125% 或死循环），该变异必须被立即否决（`REJECT`），严禁流入生产；
  3. **生产部署的独立授权边界**：离线验证通过仅代表其具备进入下一阶段（如影子测试 Shadow Mode 或小流量 A/B 灰度）的候选资格，未经生产专项授权，绝不允许自动在线修改任何生产 Prompt、Tool 或 Guardrails 配置。

---

## 三、研究结论与实施指导

1. 必须立即通过 Maven Profile 将 `HermesHarnessStage5RealProbeTest` 隔离在 `live` profile 之外，恢复默认 `mvn test` 的零依赖稳定性；
2. 为基准用例构建支持挂载 `rag_contexts`、`tool_method_ids` 与 `history` 的扩展装配器；
3. 将实时实验凭单输出重定向至未跟踪隔离路径，确保已提交资产的 SHA-256 签名不可篡改。
