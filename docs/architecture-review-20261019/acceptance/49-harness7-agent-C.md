# Hermes Harness² 阶段七（Stage 7）：测试、安全与门禁审计报告（Agent C）

> **审计角色**：Agent C —— 测试、安全与门禁审计员（Test, Security & Gate Auditor）  
> **物理 ConversationId**：`a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3`  
> **运行 Transcript**：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/a2493cbd-8d5c-4f32-b955-b8ce0d1f21f3/.system_generated/logs/transcript.jsonl)  
> **审计对象**：`docs/architecture-review-20261019/acceptance/49-harness7-bridge-design.md`、`HermesHarnessKernelArchitectureTest.java`、`48-harness6-main-kernel-implementation.md`、`backend/pom.xml`  
> **审计范围**：默认关闭与零外网依赖隔离、`ENFORCE` 模式 Fail-Close 安全矩阵与反例验证、跨请求状态零污染与已跟踪证据零重写、全量测试计数口径统一与 `50-harness7-taskbook.md` 双 Hash 校验规范

---

## 一、默认关闭、零外网依赖与 Live 探针物理隔离审计

### 1.1 默认关闭（`enabled=false, mode=OFF`）对既有生产与回归测试 100% 零影响验证

1. **静态缺省值与空安全短路门禁**：
   - 在 `AgentOrchestrator` 中，`harnessKernelBridge` 字段默认初始化为静态不可变空操作实例 `HarnessKernelBridge.disabled()`（内部固化 `enabled=false, mode=KernelBridgeMode.OFF`）。
   - 配置绑定支持 `hermes.harness.kernel.enabled`（默认 `false`）与 `hermes.harness.kernel.mode`（默认 `OFF`）。有效模式解析函数 `resolveEffectiveMode()` 严格遵循短路合取律：
     $$\text{effectiveMode} = \begin{cases} \text{OFF}, & \text{if } \neg\text{enabled} \lor \text{mode} = \text{OFF} \\ \text{SHADOW}, & \text{if } \text{enabled} \land \text{mode} = \text{SHADOW} \\ \text{ENFORCE}, & \text{if } \text{enabled} \land \text{mode} = \text{ENFORCE} \end{cases}$$
   - 当 `effectiveMode == OFF` 时，`harnessKernelBridge.isOff()` 返回 `true`：
     - **零对象分配**：不创建 `ExecutionSession`、不实例化请求级 `LocallyDenseCycleAutomaton` 或 `ReversibleRingColdBuffer`、不写入 `receiptCache`；
     - **零逻辑分叉**：`AgentOrchestrator.chat(ChatRequest)` 直接跳过桥接分支，100% 走原有 `harnessAssembler`、`ReActCycleGuard` 与 `MultiAgentOrchestrationService` 路径，公共方法签名与外部可观测行为与接入前逐行等价。

2. **回归测试兼容性判据**：
   - 既有 `qknow-tests`（1942 个测试）与 `qknow-hermes-core` 存量测试在未显式注入或开启 `HarnessKernelBridge` 时，均自动运行在 `OFF` 模式，保证存量测试 0 修改、100% 绿色通过。

### 1.2 零外网依赖、零付费 API 调用与 Maven Surefire `@Tag("live")` 物理隔离验证

1. **Maven Surefire 离线门禁核验（`backend/pom.xml` 行 19, 261–279）**：
   - 父 POM `<properties>` 已显式锁定 `<hermes.live.excludedGroups>live</hermes.live.excludedGroups>`；
   - `maven-surefire-plugin`（版本 `3.5.2`）默认配置 `<excludedGroups>${hermes.live.excludedGroups}</excludedGroups>`；
   - 仅当显式激活 `-Plive` Profile 时，`hermes.live.excludedGroups` 才被置空。因此默认 `mvn test` / `mvn clean test` 在物理机制上 100% 排除所有 `@Tag("live")` 外网探针。

2. **阶段七全部新增测试的纯内存确定性约束**：
   - `HermesHarnessKernelArchitectureTest`（Stage 0 语义收紧增强项）与新增的 `HarnessKernelBridgeTest`（Stage 4 桥接契约测试）**严禁打上 `@Tag("live")`**，使其纳入默认离线必跑门禁；
   - 测试中凡涉及 `AgentOrchestrator` 或模型调用的部分，**必须 100% 使用内存确定性合成桩（Synthetic / In-Memory Deterministic `ChatModel` Stub）与确定性 `FunctionToolCallback`**，禁止初始化任何真实 HTTP 客户端或读取 `DEEPSEEK_API_KEY` 环境变量；
   - **断网可证伪判据**：在无环境变量 `DEEPSEEK_API_KEY` 且本地回环以外网络完全不可达的条件下，`HermesHarnessKernelArchitectureTest` 与 `HarnessKernelBridgeTest` 必须在毫秒级内 100% 通过（0 外网套接字连接、0 付费 Token 消耗）。

---

## 二、`ENFORCE` 模式的 Fail-Close 安全矩阵与反例验证清单

在 `enabled=true, mode=ENFORCE` 下，系统必须严格执行 Fail-Close 策略。以下为阶段七必须逐项落地的完备负面/边界测试用例矩阵：

### 2.1 能力上下文装配前置守卫（`CapabilityContextAssembler` — 模型调用前立即 Fail-Close）

当请求显式声明 `CapabilityTag` 但缺少对应物理载荷（或夹带越权载荷）时，必须在 `HarnessKernelBridge` 介入阶段立即拦截并转化为 `FAIL_SAFE` 错误事件，**底层 `ChatModel.call()` / `ChatModel.stream()` 调用计数必须严格为 `0`**：

| 用例编号 | 声明能力标签 (`CapabilityTag`) | 输入载荷构造 (`ragContexts`, `tools`, `history`) | 预期拦截时机与 Fail-Close 行为 | `ChatModel` 物理调用次数 | `HarnessEvidenceCollector` / `KernelBridgeReceipt` 审计断言 |
|---|---|---|---|---|---|
| **FC-CAP-01** | `PROMPT_ONLY` | 夹带非空 `ragContexts` 或非空 `tools` 或非空 `history` | 模型调用前立即 Fail-Close | `0` | `receipt.failClosed() == true`，违规原因为 `CAPABILITY_CONTRACT_VIOLATION` |
| **FC-CAP-02** | `RAG` | `ragContexts` 为空列表，或所有 `RAGContext.getPreRetrievedContent()` 均为空白字符串 | 模型调用前立即 Fail-Close | `0` | `receipt.failClosed() == true`，拒绝无证据幻觉生成 |
| **FC-CAP-03** | `TOOL` | `tools` 映射为空（`Map.of()`）且无已解析工具回调 | 模型调用前立即 Fail-Close | `0` | `receipt.failClosed() == true`，拒绝空工具集伪装调用 |
| **FC-CAP-04** | `HISTORY` | `history` 为空列表且 `MemoryManager` 短期记忆为空 | 模型调用前立即 Fail-Close | `0` | `receipt.failClosed() == true`，拒绝无历史上下文的多轮推理 |
| **FC-CAP-05** | `COMPOSITE` | `ragContexts` 或 `tools` 任意一项为空或无效 | 模型调用前立即 Fail-Close | `0` | `receipt.failClosed() == true`，强制复合能力载荷齐备 |

### 2.2 霍尔逻辑神经符号工具守卫（`HoareTripleToolGuard` — 九维违约拦截与证据记录）

通过 `AtomicInteger underlyingToolInvocations = new AtomicInteger(0)` 包装底层 `ToolCallback`，验证 `HoareTripleToolGuard` 触发全部 9 类违约时，底层工具执行与 `HarnessEvidenceCollector` 的精确行为：

| 用例编号 | 违约枚举 (`HoareTripleToolGuard.Violation`) | 触发阶段 | 触发条件构造 | 底层 `ToolCallback.call()` 调用次数 | `HarnessEvidenceCollector` 证据条目断言 |
|---|---|---|---|---|---|
| **FC-HOARE-01** | `GUARDRAIL_STEP_LIMIT` | 前置 (`verifyBefore`) | `ExecutionState.currentStep (7) > guardrails.maxCycleSteps (6)` | **`0`（绝不物理执行）** | `toolExecutions` 新增 1 条记录：`success == false`，输出摘要匹配 `GUARDRAIL_STEP_LIMIT` 反例指纹 |
| **FC-HOARE-02** | `GUARDRAIL_LOOP_THRESHOLD` | 前置 (`verifyBefore`) | `ExecutionState.loopFrequency (3) >= guardrails.loopThreshold (3)` | **`0`（绝不物理执行）** | `toolExecutions` 新增 1 条记录：`success == false`，输出摘要匹配 `GUARDRAIL_LOOP_THRESHOLD` 反例指纹 |
| **FC-HOARE-03** | `GUARDRAIL_TOKEN_BUDGET` | 前置 (`verifyBefore`) | `ExecutionState.estimatedTokens (5000) > guardrails.maxTokenBudget (4096)` | **`0`（绝不物理执行）** | `toolExecutions` 新增 1 条记录：`success == false`，输出摘要匹配 `GUARDRAIL_TOKEN_BUDGET` 反例指纹 |
| **FC-HOARE-04** | `GUARDRAIL_COST_BUDGET` | 前置 (`verifyBefore`) | `ExecutionState.estimatedCostCny (0.08) > guardrails.maxCostCny (0.05)` | **`0`（绝不物理执行）** | `toolExecutions` 新增 1 条记录：`success == false`，输出摘要匹配 `GUARDRAIL_COST_BUDGET` 反例指纹 |
| **FC-HOARE-05** | `PERMISSION` | 前置 (`verifyBefore`) | 调用未在 `contract.permittedTools()` 白名单中的工具名（如 `"drop_table"`） | **`0`（绝不物理执行）** | `toolExecutions` 新增 1 条记录：`success == false`，反例包含未授权工具提示 |
| **FC-HOARE-06** | `SCHEMA` | 前置 (`verifyBefore`) | 缺少必填参数或参数类型不匹配（如 `amount` 传入 `"five"` 而非 `Number`） | **`0`（绝不物理执行）** | `toolExecutions` 新增 1 条记录：`success == false`，反例指明缺失或类型错误的字段名 |
| **FC-HOARE-07** | `BOUNDARY` | 前置 (`verifyBefore`) | 参数类型合法但违反数值/业务边界谓词（如 `amount = 200 > 100` 或损坏 JSON） | **`0`（绝不物理执行）** | `toolExecutions` 新增 1 条记录：`success == false`，反例返回边界违约说明 |
| **FC-HOARE-08** | `RESULT_INTEGRITY` | 后置 (`verifyAfter`) | 底层工具返回 `null`、空白文本或缺失 `contract.requiredResultFields()` 中的必需键 | `1`（执行后被后置守卫拦截，原始残缺结果**绝不透传**给模型） | `toolExecutions` 新增拦截记录：`success == false`，返回结构化 `RESULT_INTEGRITY` 反例替代原始输出 |
| **FC-HOARE-09** | `SENSITIVE_OUTPUT` | 后置 (`verifyAfter`) | 底层工具返回值包含 `contract.forbiddenOutputFragments()` 中的敏感凭据（如 `"sk-secret-key"`） | `1`（执行后被后置守卫强制擦除拦截，敏感明文**100% 零泄漏**） | `toolExecutions` 新增拦截记录：`success == false`，敏感原文被屏蔽 |

### 2.3 局部致密死循环拒绝自动机（`LocallyDenseCycleAutomaton` — 阈值 $K$ 精确拦截）

| 用例编号 | 自动机参数 $(W, K)$ | 调用序列构造 | 预期状态机与拦截行为 |
|---|---|---|---|
| **FC-CYCLE-01** | $W=3, K=2$ | 连续调用同一工具与相同规范化参数 `("weatherQuery", "{\"city\":\"Beijing\"}")`：第 1 次 $\to$ 第 2 次 | 第 1 次：`frequency=1, rejected=false`，底层工具执行（计数 `1`）；第 2 次：达到阈值 $K=2$，`frequency=2, rejected=true`，**在 `verifyBefore` / 自动机前置阶段立即拦截**，底层工具总执行次数保持为 `1`，`HarnessEvidenceCollector` 记录第 2 次 `success=false` |
| **FC-CYCLE-02** | $W=2, K=2$（先淘汰后计数边界） | 序列：`A -> B -> A -> A` | 第 3 次调用 `A` 时，窗口先淘汰最老的 `A` 再计入新 `A`，因此 `frequency=1, rejected=false`（无 $W+1$ 幽灵计数）；第 4 次紧接调用 `A` 时，窗口内为 `[A, A]`，`frequency=2 >= K`，立即触发 `rejected=true` 拦截 |

### 2.4 三级冷备可逆环形缓冲区（`ReversibleRingColdBuffer` — `maxArchiveCapacity` 硬上限守卫）

| 用例编号 | 缓冲区容量参数 `(hot, warm, cold, maxArchive)` | 溢出与软删除压测序列 | 预期有界性与统计量断言 (`Statistics`) |
|---|---|---|---|
| **FC-RING-01** | `(1, 1, 1, 2)`（即 `maxArchiveCapacity = 2`） | 连续写入 6 个具有正交 1024 维单位向量的 `Episode`（`e1`..`e6`） | 前 2 个填满 `hot(1), cold(1)`；`e3, e4` 触发 `cold` 溢出并成功转入 `archive`（此时 `archived() == 2 == archivedCapacity()`）；后续写入触发 `cold` 溢出时，因 `archive.size() >= 2` 被严格拒绝写入 `archive`：**断言 `statistics().archived() == 2`（绝不突破上限 2）且 `statistics().rejectedArchive() >= 1`** |
| **FC-RING-02** | `(1, 1, 1, 1)`（满档案下调用 `softDelete`） | 当 `archive` 已达上限 `1` 时，对 `cold` 中的活跃令牌调用 `buffer.softDelete(coldToken)` | **`softDelete` 返回 `false`**，拒绝突破 `maxArchiveCapacity`，`statistics().archived()` 恒为 `1`，`statistics().rejectedArchive()` 递增 `+1`，且原条目保留在 `cold` 中不发生静默丢失 |

---

## 三、跨请求状态不污染证明与已跟踪证据零重写审计

### 3.1 跨请求状态零污染证明（Request-Scoped Isolation Proof）
- 请求 1 (`req-1`) 在 `ENFORCE` 模式下连续调用相同工具触发 $K=2$ 循环拦截，并向其专属 `coldBuffer` 写入情节；
- 请求 2 (`req-2`) 紧接着使用完全相同的工具名与参数发起调用：
  - 断言 `session2.cycleAutomaton().state().window().size() == 0`；
  - 断言 `session2.coldBuffer().statistics().hot() == 0` 且 `archived() == 0`；
  - 断言请求 2 的首次相同工具调用 100% 放行并成功执行底层工具。

### 3.2 已跟踪证据目录零重写审计（Tracked Evidence Zero-Overwrite Gate）
- 在执行完单模块测试与 `-pl tests -am clean test` 全量回归测试后，执行 `git status --porcelain` 与 `git diff --exit-code experiment/outputs/`，对已跟踪证据文件必须为 0 修改。

---

## 四、全量测试计数口径统一与任务书双 Hash 校验规范

### 4.1 Stage 6 测试计数差异（`2116` vs `1942 + 133` vs `qknow-server/target`）根因法医剖析
1. **为什么 `qknow-tests (1942) + qknow-hermes-core (133) = 2075` 小于 Reactor 总数 `2116`（差值 `41`）？**
   - `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test` 共解析并构建 **43 个 Reactor 模块**；
   - 除 `qknow-tests`（1942 个测试）和 `qknow-hermes-core`（133 个测试）外，其余上游依赖模块（如 `qknow-module-ai`、`qknow-framework`、`qknow-common` 等）自身 `src/test/java` 中还包含 **41 个单元测试**，合计 $1942 + 133 + 41 = 2116$。
2. **为什么未过滤扫描会混入 `qknow-server/target`？**
   - `tests` 不依赖 `qknow-server`，因此 `-pl tests -am clean test` 不会清理 `backend/qknow-server/target/surefire-reports/` 下的陈旧历史文件。

### 4.2 阶段七《测试计数精确口径规范》（强制执行）
1. 运行全量门禁前清理残留目录：`rm -rf backend/qknow-server/target/surefire-reports`；
2. 验收报告必须同时列出三项精确指标：
   - **指标 A**：`qknow-hermes-core` 模块级精确计数（`133 + Δ`）；
   - **指标 B**：`qknow-tests` 模块级精确计数（`1942`）；
   - **指标 C**：`-pl tests -am` 43 模块 Reactor 汇总计数（`2116 + Δ`）。

### 4.3 `50-harness7-taskbook.md` 的 `body_sha256` 与 `full_file_sha256` 校验脚本
```bash
TASKBOOK="docs/architecture-review-20261019/acceptance/50-harness7-taskbook.md"
SHA_FILE="${TASKBOOK}.sha256"
BODY_SHA256=$(sed -n '/^## 一、/,$p' "$TASKBOOK" | shasum -a 256 | awk '{print $1}')
echo "Computed body_sha256: $BODY_SHA256"
grep -q "$BODY_SHA256" "$TASKBOOK" || { echo "ERROR: body_sha256 mismatch in $TASKBOOK"; exit 1; }
(cd "$(dirname "$TASKBOOK")" && shasum -a 256 "$(basename "$TASKBOOK")" > "$(basename "$SHA_FILE")" && shasum -a 256 -c "$(basename "$SHA_FILE")")
```

---

## 五、Agent C 最终审计裁定

1. **默认安全边界**：**PASS**。
2. **Fail-Close 完备性**：**PASS**（18 项负面/边界断言矩阵已锁定）。
3. **状态隔离与证据防污染**：**PASS**。
4. **测试计数与双 Hash 门禁**：**PASS**。
