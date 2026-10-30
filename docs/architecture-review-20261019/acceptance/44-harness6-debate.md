# Hermes Harness² 阶段六：真实 RAG/工具/记忆 Probe 与可复现实验门禁 交叉辩论裁决书

> **辩论主持**：主 Agent (Orchestrator Agent, `e780bef4-ad1b-4ce7-8c57-88ab7e5cffad`)  
> **辩论参与方**：
> - **Agent A**：实验有效性审计员 (`7d9bd05c-9947-42d5-8e1c-119b91b309a8`)
> - **Agent B**：Java 真实 Probe 工程师 (`b08baf02-243a-4f5c-9f02-7b6194b0cc12`)
> - **Agent C**：可复现性、成本与门禁审计员 (`d66ad607-4d22-4bb4-ac88-f7d2c8c861cb`)  
> **辩论时间**：2026-10-30  
> **裁决原则**：《AGENTS.md》科研门禁、Java 21 隔离环境、独占简体中文铁律

---

## 一、交叉辩论背景与核心议题

在阶段五真实 DeepSeek 探针闭环之后，虽然真实公网调用得到了验证，但暴露了“Prompt-Only 冒充 Full Agent”、“默认构建门禁被公网测试污染”以及“测试运行就地覆盖历史资产破坏验签”三大严重阻断。

本次交叉辩论围绕以下 6 大核心关键议题展开：
1. **用例能力定性**：5 个基准用例中，哪些可以真实注入 RAG？哪些可以真实注入工具？哪些只能保留为 `PROMPT_ONLY`？
2. **工具回调实现**：在脱离外部微服务的受控测试环境下，如何保证工具真实被调用而不引入庞大的外部容器依赖？
3. **隔离与防污染**：如何物理保证 `mvn test` 默认构建零外网依赖，且运行探针后 `git status` 绝对保持 Clean？
4. **单一变量原则**：Baseline 与 Candidate 如何在能力挂载后保持严格同条件对比？
5. **任务书签名反脆弱设计**：如何彻底解决阶段五中正文哈希与全文件哈希混淆的表述缺陷？
6. **最小工程实现边界**：如何做到生产核心路径 0 修改，全部能力 100% 局限于 `src/test` 作用域？

---

## 二、第一轮交叉辩论：能力注入、测试桩真实性与降级边界

### 2.1 议题一：5 大基准用例的能力划分与真实注入可行性

- **Agent A (实验有效性审计员)**：
  “阶段五最大的学术假象是将 5 个用例全部当作同质化的纯文本处理。根据 AgentBench (ICLR 2024 §4.2) 规范，我们必须对 5 个用例进行能力显式分类：
  - `kb-qa-01` 必须归为 `RAG`；
  - `tool-route-02` 与 `param-guard-03` 必须归为 `TOOL`；
  - `ctx-history-04` 必须归为 `HISTORY`；
  - `comp-rag-tool-05` 必须归为 `COMPOSITE`。
  如果测试环境无法提供真实工具解析，用例必须降级为 `PROMPT_ONLY`，且绝对不能把纯文本回答当做工具策略成功的证据！”

- **Agent B (Java 真实 Probe 工程师)**：
  “我完全赞成 Agent A 的能力划分，且已经在工程层面确认了其可行性。`AgentOrchestrator`（行 420-570）既有代码中，已经具备完整的注入分支：
  1. `RAGContext` 切片传入后，会自动生成 `<knowledge_base>` 提示词块并注册 `knowledgeQuery` 工具；
  2. `ChatMessage` 传入后，会自动按凭单中的 `shortTermWindow` 截断压入消息栈；
  3. 业务工具方面，我们只需在 `src/test` 下实现 `DynamicTestToolCallbackResolver`，并为用例绑定轻量级内存测试工具（如 `banking_transaction_query`、`calendar_event_creator`、`system_metrics_query`），即可让真实 DeepSeek 模型产生真实的 Function Calling，并收到真实返回！不需要降级为 `PROMPT_ONLY`，5 个用例均可真实挂载完整能力！”

- **Agent C (可复现性与门禁审计员)**：
  “同意 Agent B 的内存测试工具方案。但必须强调：**内存测试工具回调必须遵循不可变契约，严禁在调用中产生未受控的副作用**。此外，必须对 Agent A 提出的降级规则实施硬门禁：如果测试在 CI 严格模式下运行，能力注入失败必须 Fail-Fast，绝不允许以降级为借口掩盖注入缺陷。”

- **主 Agent 裁决**：
  **【裁决一】**：采纳 Agent A 与 Agent B 的方案。5 个用例全部赋予强类型 `CapabilityTag`，不采用任何虚假的纯文本降级，全部注入真实载荷：
  - `kb-qa-01` -> 注入真实企业中间件规范 RAG 切片（`CapabilityTag.RAG`）；
  - `tool-route-02` -> 注入 3 个银行流水核查测试工具回调（`CapabilityTag.TOOL`）；
  - `param-guard-03` -> 注入日历时间参数校验工具回调（`CapabilityTag.TOOL`）；
  - `ctx-history-04` -> 注入真实 2 轮前置服务网格架构探讨会话（`CapabilityTag.HISTORY`）；
  - `comp-rag-tool-05` -> 注入微服务白皮书 RAG 切片与系统指标工具回调（`CapabilityTag.COMPOSITE`）。

---

## 三、第二轮交叉辩论：构建双轨隔离、资产不可变性与双 Hash 签名规范

### 3.1 议题二：如何彻底根除公网调用对默认 `mvn test` 的污染与资产覆盖？

- **Agent C (可复现性与门禁审计员)**：
  “阶段五将真实 API 探针放在默认测试中是一个严重的设计缺陷。必须实现物理隔离：
  1. 在 `pom.xml` 中将真实 API 探针标记为 `@Tag("live")`，并在默认 Surefire 配置中排除；
  2. 只有在指定 `-Plive` profile 时才激活真实探针；
  3. 探针输出目录重定向至 `experiment/outputs/harness6-live/<runId>/` 或 `target/live-evidence/`，并在 `.gitignore` 中加入该模式，绝不允许覆盖已归档的 `harness5` 目录。测试完成后执行 `git status --porcelain` 必须为空！”

- **Agent B (Java 真实 Probe 工程师)**：
  “POM 配置方案已经在 `43-harness6-agent-B.md` 中给出了标准 XML 片段。同时，我在 `HermesHarnessStage6RealProbeTest` 类中增加了前置断言：
  ```java
  Assumptions.assumeTrue(hasValidKey, "无有效 Key 时优雅跳过");
  ```
  这样即使开发者误调用了该单测，也不会产生非预期的构建失败。输出目录将完全由 `OfflineProbeExecutor.ProbeExecutionOptions` 动态传入，与已归档目录物理隔离。”

- **Agent A (实验有效性审计员)**：
  “补充一点：探针运行器输出的凭单必须包含 `CapabilityTag` 和真实的物理执行特征。例如 RAG 任务必须落盘 `ragDocumentCount >= 1`，工具任务必须落盘非空的 `toolExecutions`。在评判阶段，盲审器根据这些真实物理特征进行客观仲裁，彻底拔除针对 `(精炼客观)` 字符的作弊后门。”

- **主 Agent 裁决**：
  **【裁决二】**：一致通过。Maven 构建体系确立双轨制：
  - 默认构建（`mvn test`）：仅运行确定性契约测试，零外网、零 API Key 依赖；
  - Live 探针（`mvn test -Plive -Dtest=HermesHarnessStage6RealProbeTest`）：真实调用 DeepSeek API，输出定向至未跟踪的隔离目录，严格保证 `git status` 绝对 Clean。

### 3.2 议题三：任务书双 Hash 签名的精确定义与提取命令

- **Agent C (可复现性与门禁审计员)**：
  “阶段五报告中将正文签名（`body_sha256`）与整文件签名（`full_file_sha256`）混淆，给审计带来了困扰。阶段六任务书必须严格明确两个签名的定义与范围：
  1. `body_sha256`（正文签名）：自 `## 一、阶段六唯一待验证核心假设与契约基准` 起始至文件末尾的全部纯文本哈希。它锁定了不可变业务契约，且消除了修改头部元数据造成的自指循环死锁；
  2. `full_file_sha256`（全文件签名）：物理磁盘文件从第一字节到最后一字节的逐字节完整性哈希，存入伴生文件 `45-harness6-taskbook.md.sha256` 中，通过 `shasum -a 256 -c` 进行校验。”

- **Agent A 与 Agent B**：
  “完全认同。双 Hash 体系逻辑自洽、边界清晰，彻底解决了冻结签名的唯一性与可追溯性问题。”

- **主 Agent 裁决**：
  **【裁决三】**：阶段六任务书全面实施双 Hash 规范。头部元数据明确标注 `body_sha256` 与 `full_file_sha256`，并给出可直接复现的 Shell 提取命令。

---

## 四、最终裁决共识与阶段六任务书冻结指令

经过两轮严密、深入的交叉辩论，主 Agent 做出最终决议：

1. **完全采纳 Agent A、B、C 的专业审计与设计成果**；
2. **冻结阶段六最小工程修改范围**：全部局限于 `src/test/java` 作用域与 POM profile 配置，生产代码保持 0 修改；
3. **正式进入任务书起草与签名冻结阶段**：依据本辩论共识，撰写并冻结 `docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md`，并生成其伴生签名文件 `45-harness6-taskbook.md.sha256`。

---
**辩论签署**：
- 主 Agent (Orchestrator): `e780bef4-ad1b-4ce7-8c57-88ab7e5cffad`
- 智能体 A (实验有效性审计员): `7d9bd05c-9947-42d5-8e1c-119b91b309a8`
- 智能体 B (Java 真实 Probe 工程师): `b08baf02-243a-4f5c-9f02-7b6194b0cc12`
- 智能体 C (可复现性与门禁审计员): `d66ad607-4d22-4bb4-ac88-f7d2c8c861cb`  
**签署日期**：2026-10-30
