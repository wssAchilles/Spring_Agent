# Hermes Harness² 阶段六：真实 RAG/工具/记忆 Probe 与可复现实验门禁 阶段任务书

## 任务书元数据与冻结签名

| 属性项 | 规范值 | 说明 |
|---|---|---|
| **任务书版本** | `1.0.0-FROZEN` | 阶段六不可变法定任务书 |
| **生效状态** | **FROZEN (已冻结)** | 未经多 Agent 交叉重审，严禁擅自修改正文任何条款 |
| **所属分支** | `feat/hermes-harness-stage6` | 基于 `feat/hermes-harness-stage5` 派生的阶段六独立研发分支 |
| **正文签名 (`body_sha256`)** | `dae7aa9029f0728a0172446422a7b1632c993c7ec4cd0aa0aa43963e97196dae` | 自“## 一、阶段六唯一待验证核心假设与契约基准”至文件末尾的哈希签名 |
| **整文件签名 (`full_file_sha256`)** | 参见伴生文件 `45-harness6-taskbook.md.sha256` | 物理磁盘全文件逐字节哈希签名 |
| **执行模型基线** | DeepSeek 官方活跃 API (`deepseek-flash`) | 官方标准接口，HTTP SSE 流式，解析 `reasoning_content` |
| **向量嵌入基线** | 阿里千问 Embedding 1536 维超球面 | 单位 $L_2$ 范数，余弦几何度量 |
| **运行时环境** | SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`) | 严禁污染主机 Java 17 |
| **生产默认配置** | `hermes.orchestration.enabled=false`、`SINGLE` 模式 | 生产环境全局默认保持单智能体且不可变 |

---

## 一、阶段六唯一待验证核心假设与契约基准

### 1.1 唯一待验证核心假设 (Locked Hypothesis)
> **“在真实 `AgentOrchestrator` 生产轨道中，为基准任务套件分别注入其业务语义所对应的 RAG 上下文、工具回调与历史记忆（分别打标为 `RAG`、`TOOL`、`HISTORY`、`COMPOSITE`；无法注入真实能力者严格降级标记为 `PROMPT_ONLY`），在同等能力与约束边界下，不可变 Harness 候选变异策略是否依然能在不降低任务成功率与答案质量的前提下实现端到端 Token 缩减与预算受控；若用例未注入对应能力，则其盲审结果与指标必须被硬性排除出 RAG/TOOL 算法采纳判定之外。”**

### 1.2 阶段六业务与技术边界红线
1. **零生产修改原则**：所有用例扩展、测试工具回调与探针运行器改造必须 100% 局限于 `src/test/java` 作用域与 POM profile，严禁修改任何生产核心代码；
2. **零外网依赖门禁**：常规 `mvn clean test` 必须在无外网、无 API Key 环境下 100% 绿色通过；真实 API 探针必须物理隔离在 `-Plive` profile 之中；
3. **资产只追加不覆盖**：已归档历史资产（`experiment/outputs/harness5/`）设为只读写保护；Live 探针输出至未跟踪的 `experiment/outputs/harness6-live/<runId>/` 或 `target/live-evidence/`，测试完成后 `git status` 必须绝对保持 Clean；
4. **真实性第一**：绝对禁止使用任何 `DeterministicHarnessTestDouble` 生成“真实生产凭单”，探针凭单必须为真实的 `REAL_PRODUCTION`。

---

## 二、5 大基准用例全能力注入与 CapabilityTag 规范

为彻底杜绝阶段五中 5 个用例全部退化为 Prompt-Only 的学术伪相关，阶段六强制绑定强类型能力标签与真实数据载荷：

| 用例标识 (`taskId`) | 强类型标签 (`CapabilityTag`) | 注入的真实载荷 (Injected Fixtures) | 真实执行与验证路径 | 成功判定标准 |
|---|---|---|---|---|
| **`kb-qa-01`** | **`RAG`** | `RagContextFixture`：注入企业中间件规范切片（连接超时 1000ms、读超时 3000ms、重试 3 次、幂等键格式 `IDEMP_{bizType}_{userId}_{timestamp}_{sha256}`） | `AgentOrchestrator` 将其挂载至 `UserMessage` 前缀，并自动合成 `knowledgeQuery` 工具 | 凭单断言 `ragDocumentCount >= 1`，回答命中超时重试与幂等规范实体 |
| **`tool-route-02`** | **`TOOL`** | `availableTools` 与 `toolCallbacks`：注入 `banking_transaction_query`、`banking_account_balance`、`banking_settlement_remedy` 3 个测试工具 | `DynamicTestToolCallbackResolver` 解析工具，模型触发 Function Calling，`wrapWithCycleGuard` 捕获执行 | 凭单断言 `toolExecutions` 包含真实记录，且命中 `banking_transaction_query` |
| **`param-guard-03`** | **`TOOL`** | `availableTools` 与 `toolCallbacks`：注入 `calendar_event_creator` 工具，针对非法时间 `2026/10/29 25:99:99` 抛出参数校验错误 | 模型发起工具调用后收到格式错误反馈并进行自纠，或由守卫前置拦截 | 凭单断言 `toolExecutions` 非空，模型体现参数自纠轨迹 |
| **`ctx-history-04`** | **`HISTORY`** | `historyFixtures`：注入 2 轮关于微服务网格 Sidecar 与网关方案对比的真实会话记录 | `AgentOrchestrator` 按 `shortTermWindow` 截断压入会话消息栈 | 模型成功将“方案 A 的最担忧瓶颈”消解为 `service_mesh_latency` 并给出 eBPF 优化方案 |
| **`comp-rag-tool-05`** | **`COMPOSITE`** | `ragContexts` 与 `toolCallbacks`：注入架构白皮书对比切片，并挂载 `system_metrics_query` 工具 | RAG 切片挂载前缀，同时暴露系统指标工具，输出 4 列 Markdown 表格 | 凭单断言 `ragDocumentCount >= 1`，输出包含 4 列 Markdown 结构与字数总结 |

---

## 三、单一变量控制（Single-Factor Invariance）与基线/候选契约

遵循 DSPy (NeurIPS 2023) 与 Google Research Harness² 原则，Baseline 与 Candidate 的探针运行必须保证环境绝对同质：

### 3.1 完全不可变量清单
- 相同的 `testSuite`（包含相同的 `question`、`ragContexts`、`availableTools`、`history`）；
- 相同的模型端点配置（`deepseek` / `deepseek-flash`）；
- 相同的工具回调实现与输入参数；
- 相同的超时限制（60s）与重试配置（2次）。

### 3.2 唯一受控自变量
- **Baseline Voucher**：
  版本 `1.0.0`，系统提示词包含完整的企业级系统原则、安全指令与详细格式约束（约 210 字）；记忆窗口 `shortTermWindow = 10`；
- **Candidate Voucher**：
  版本 `1.0.0-mut-stage6`，系统提示词由 `SYSTEM_PROMPT_CONCISE` 策略精炼压缩为核心角色与原则声明（约 65 字）；记忆窗口 `shortTermWindow = 4`。

---

## 四、Maven Profile 双轨构建隔离与未跟踪资产目录规范

### 4.1 构建轨道物理隔离
1. **默认离线轨道 (`mvn test`)**：
   - Surefire 插件配置 `<excludedGroups>live</excludedGroups>`；
   - 排除所有真实网络探针类（如 `HermesHarnessStage6RealProbeTest`）；
   - 在断网、无 API Key 环境下运行 `mvn clean test` 必须 100% 绿色构建成功；
2. **Live 探针轨道 (`mvn test -Plive`)**：
   - 激活 `live` Profile 时，指定执行全能力真实探针；
   - 类级配置 `@Tag("live")`，并在 `@BeforeAll` 执行 `Assumptions.assumeTrue(apiKeyValid)` 优雅防守。

### 4.2 资产输出目录与工作区防污染
1. **输出路径隔离**：阶段六真实探针的凭单落盘根路径严格重定向至：
   `experiment/outputs/harness6-live/<runId>/`
2. **Git 忽略保护**：根目录 `.gitignore` 显式添加：
   ```gitignore
   experiment/outputs/*-live/
   target/live-evidence/
   ```
3. **工作区纯净断言**：运行探针后，执行 `git status --porcelain` 必须为空，严禁破坏已归档历史凭单与哈希校验和。

---

## 五、预算控制、时间看门狗与 8 大强制 BLOCKED 停止条件

### 5.1 预算与时间看门狗模型
- **单用例超时看门狗**：单次模型网络调用设置 **60 秒** 硬超时，超时触发 `FAIL_SAFE` 凭单合成；
- **指数退避重试**：最大重试次数 $\le 2$ 次（退避基数 1000ms，乘数 2.0，最大 4000ms，抖动区间 ±20%）；
- **`OfflineBudgetCircuitBreaker` 阈值锁定**：
  - 单批次累计 Token 上限：`50,000L` Tokens；
  - 单批次累计成本上限：`0.10` 元人民币 (CNY)；
  - 单批次总执行耗时上限：`180,000L` 毫秒 (3分钟)。

### 5.2 8 大强制 BLOCKED 停止条件矩阵
若触发以下任一条件，必须立即输出 `RESEARCH_GATE_BLOCKED` 并终止流程：
1. `BLOCKED_RAG_DEFICIENCY`：用例标记为 `RAG` 但生成的物理凭单中 `ragDocumentCount == 0`；
2. `BLOCKED_TOOL_DEFICIENCY`：用例标记为 `TOOL` 但未挂载有效 `ToolCallback` 或 `toolExecutions` 为空；
3. `BLOCKED_HISTORY_DEFICIENCY`：用例标记为 `HISTORY` 但多轮会话未压入上下文导致消歧失败；
4. `BLOCKED_DEFAULT_GATE_NETWORK_LEAK`：默认 `mvn test` 执行期间发起外部公网请求或因缺少 API Key 报错；
5. `BLOCKED_ASSET_OVERWRITE_ATTEMPT`：探针试图写入 `harness5/` 归档目录或测试后工作区非 clean；
6. `BLOCKED_BUDGET_BREAKER_TRIGGERED`：单批次 Token 突破 50,000 或成本突破 0.10 元；
7. `BLOCKED_API_CONSECUTIVE_FAILURE`：连续 2 次真实调用发生 5xx 故障或网络无法连通；
8. `BLOCKED_TASKBOOK_HASH_MISMATCH`：任务书正文签名或全文件签名复算校验失败。

---

## 六、最小修改文件集合与禁止修改边界

### 6.1 允许修改/新增的最小文件集合 (100% 局限于 test 作用域与 POM)
1. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/CapabilityTag.java` (新增枚举)
2. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/OfflineBenchmarkTestCase.java` (扩展能力字段与 Fixtures)
3. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/Stage6BenchmarkSuiteFactory.java` (新增用例工厂)
4. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/probe/DynamicTestToolCallbackResolver.java` (新增测试解析器)
5. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/probe/DefaultOfflineProbeExecutor.java` (重构全能力装配与能力门禁断言)
6. `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/HermesHarnessStage6RealProbeTest.java` (新增 live profile 真实探针单测)
7. `backend/pom.xml` 或子模块 `pom.xml` (配置 `live` Profile 与 Surefire 排除规则)
8. `.gitignore` (追加 `experiment/outputs/*-live/` 与 `target/live-evidence/`)

### 6.2 严禁触碰的架构边界 (Strict Prohibition Boundaries)
- **严禁修改生产核心代码**（`tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java` 保持 0 行修改）；
- **严禁修改生产默认配置**（`hermes.orchestration.enabled` 必须保持 `false`，生产主干继续走确定性单智能体路径）；
- **严禁修改任何既有 qrel 或历史实验证据**；
- **严禁修改主干代码库中的 MCP、HITL、SWARM 核心协议**。

---

## 七、任务书双 Hash 规范与验证命令

### 7.1 双 Hash 提取与复算定义
1. **正文签名 (`body_sha256`)**：
   - 提取范围：从本文件行首标记 `^## 一、阶段六唯一待验证核心假设与契约基准` 起始至文件末尾；
   - 提取命令：
     ```bash
     sed -n '/^## 一、阶段六唯一待验证核心假设与契约基准/,$p' docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md | shasum -a 256
     ```
2. **全文件签名 (`full_file_sha256`)**：
   - 提取范围：物理磁盘文件的全部字节；
   - 存储路径：`docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md.sha256`；
   - 校验命令：
     ```bash
     cd docs/architecture-review-20261019/acceptance && shasum -a 256 -c 45-harness6-taskbook.md.sha256
     ```

### 7.2 验收测试命令集
```bash
# 1. 默认构建门禁验证 (必须 0 外网依赖，全绿通过)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp clean test -f backend/pom.xml -pl tests -am

# 2. Live 探针定向执行 (真实驱动 DeepSeek API)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am -Plive \
  -Dtest=HermesHarnessStage6RealProbeTest -Dsurefire.failIfNoSpecifiedTests=false

# 3. 工作区纯净度验证
git status --porcelain
```
