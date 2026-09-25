# Phase 136 实施方案与契约设计：生产级企业 MCP 工具调用动态多租户分布式配额、断路降级与零信任数据脱敏网关中枢

## 一、方案核心目标与唯一算法假设

### 1.1 唯一待验证假设 (Hypothesis H-136)
在企业级多租户高并发调用外部 MCP 工具场景下，通过构建“多租户分级令牌桶 + 滑动窗口慢调用自适应三态断路器 + 基于预编译 Aho-Corasick/Regex 的高性能零信任双向脱敏网关中枢”，能够实现：
1. 噪声租户在高并发突发流量下被严格隔离在分配的配额内（违规请求 100% 拦截，合规租户吞吐波动率 $\le 5\%$）；
2. 下游 MCP 服务在发生 P99 超时或错误率异常时，自适应断路器在 $\le 10\text{ms}$ 内触发 OPEN 态并无缝回退至只读降级结果，无阻塞且无级联雪崩；
3. 双向敏感数据（身份证、手机号、银行卡、密码、JWT）实现 100% 检出与精准掩码替换，脱敏额外时延严格 $\le 2.0\text{ms}$，且支持纯 Java 21 Record 格式的不可变脱敏存证凭单（`McpZeroTrustGatewayReceipt`）自验真。

---

## 二、架构设计与核心组件规划

### 2.1 后端核心组件设计 (`qknow-hermes-core`)

#### 1. `McpZeroTrustGatewayReceipt.java`
- **定位**：纯 Java 21 Record 不可变密码学网关审计存证凭单；
- **核心字段**：
  * `receiptId` (String): 全局唯一凭单编号 (`RCP-MCP-GW-...`)；
  * `tenantId` (String): 租户唯一标识；
  * `toolName` (String): 被调用 MCP 工具名称；
  * `quotaState` (String): 配额校验结果 (`ALLOWED`, `RATE_LIMITED`, `CONCURRENCY_EXCEEDED`)；
  * `circuitState` (String): 断路器状态 (`CLOSED`, `HALF_OPEN`, `OPEN_DEGRADED`)；
  * `maskedFields` (List<String>): 脱敏检测命中的敏感字段类型列表；
  * `timestamp` (long): 毫秒时间戳；
  * `sha256Signature` (String): 全字段规范化 SHA-256 哈希防篡改签名；
- **特性**：提供常量时间自验真方法 `verifySignature()`。

#### 2. `MultiTenantDistributedQuotaGovernor.java`
- **定位**：多租户分级分布式配额与并发硬隔离治理器；
- **机制**：
  * 租户配额策略模型 `TenantQuotaPolicy(tenantId, burstCapacity, refillRatePerSecond, maxConcurrentInFlight)`；
  * 内部维护每个租户专属的无锁原子 CAS 令牌桶实例 `LockFreeTokenBucketLimiter`；
  * 内部维护每个租户当前在途请求计数器 `AtomicInteger inFlightRequests`；
  * 提供 `evaluateAndAcquire(tenantId)` 与 `release(tenantId)` 接口，严格执行纳秒级两阶段配额核销。

#### 3. `AdaptiveMcpCircuitBreakerGovernor.java`
- **定位**：基于滑动窗口的自适应慢调用三态断路器与降级中枢；
- **机制**：
  * 环形位滑动窗口 `SlidingWindowBitSet`（默认样本量 20 次调用）；
  * 慢调用判定：单次执行时间超过 `slowCallDurationThresholdMs`（默认 2000ms）即记为慢调用；
  * 失败与慢调用双重门限：当失败率 $\ge 25\%$ 或慢调用占比 $\ge 30\%$ 且样本量达到最小判定数时，自动跳闸至 `OPEN`；
  * 熔断冷却自愈：经过冷却期（默认 5000ms）后进入 `HALF_OPEN` 试探态；
  * 语义降级引擎：当处于 `OPEN` 状态时，自动触发注册的 `McpToolFallbackProvider` 生成安全只读 JSON 降级响应，避免抛出硬异常打断 Agent 执行流。

#### 4. `ZeroTrustDataMaskingEngine.java`
- **定位**：基于 Aho-Corasick 与预编译 DFA 正则的高性能零信任敏感数据脱敏引擎；
- **机制**：
  * 静态已知字典匹配：采用 Aho-Corasick 自动机扫描系统密钥关键词（如 `api_key`, `secret_token`, `password` 等）；
  * 正则流式模式识别：
    - 中国大陆 18 位身份证：前 6 位地址码 + 8 位生日 + 3 位顺序码 + 1 位校验码（带 ISO 7064:1983.MOD 11-2 模 11 校验位精确算法），脱敏为 `110101********1234`；
    - 11 位手机号：`1[3-9]\d{9}`，脱敏为 `138****1234`；
    - 16-19 位银行卡：Luhn 算法校验，脱敏保留前 4 后 4；
    - JWT 令牌：`Bearer eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+`，脱敏为 `Bearer eyJ***[REDACTED_JWT]***`；
  * 双向脱敏：提供 `maskInput(String inputJson)` 与 `maskOutput(String outputJson)` 接口，处理耗时 $\le 2.0\text{ms}$。

---

### 2.2 前端可视化扩展 (`frontend`)
在工作流画布中针对 MCP 工具节点投射以下实时视觉指标：
1. **断路器健康态微标**：
   - `CLOSED`：绿色常亮脉冲微光；
   - `HALF_OPEN`：黄色动态呼吸闪烁；
   - `OPEN_DEGRADED`：红色警告边框 + 连线转为虚线降级状态；
2. **租户配额实时水位仪表盘**：
   - 投射当前租户令牌桶可用率百分比与在途任务数；
   - 遵从 Rule 2 单色钛金现代暗黑毛玻璃风格（发丝边框 `rgba(255,255,255,0.08)`、背景 `rgba(10,10,12,0.85)`、`backdrop-filter: blur(20px)`）。

---

## 三、8 项严苛契约测试定义 (Contract Tests)

| 测试用例编号 | 契约方法名 | 核心验证指标与断言标准 |
| :--- | :--- | :--- |
| **TC-136-1** | `testMultiTenantQuota_noisyNeighborIsolation()` | 多租户高并发流量隔离：恶意租户 500 QPS 突发流量被 100% 限制在配额内，正常租户成功率 100%，耗时波动 $\le 5.0\%$ |
| **TC-136-2** | `testConcurrentInFlightCap_hardRejection()` | 租户在途并发上限硬拦截：当在途并发超过 `maxConcurrentInFlight` 时，后续并发请求被立即拒绝并标记 `CONCURRENCY_EXCEEDED`，释放后立刻恢复 |
| **TC-136-3** | `testSlidingWindowCircuitBreaker_errorRateTrip()` | 滑动窗口错误率跳闸：滑动窗口 20 样本中错误率超过 25% 时，断路器在 $\le 10\text{ms}$ 内跳闸至 `OPEN`，阻断后续请求 |
| **TC-136-4** | `testSlidingWindowCircuitBreaker_slowCallTripAndFallback()` | 慢调用比例软熔断与智能降级：P99 慢调用（$\ge 2000\text{ms}$）占比超过 30% 时自动熔断，并自动返回预置 Fallback 只读降级响应 |
| **TC-136-5** | `testCircuitBreaker_halfOpenSelfHealing()` | 冷却自愈探测：熔断冷却超时后自动进入 `HALF_OPEN` 状态，探测成功后平滑恢复至 `CLOSED` 态 |
| **TC-136-6** | `testZeroTrustDataMasking_accuracyAndPerformance()` | 双向敏感数据脱敏准确率与性能：对包含身份证、手机号、银行卡号、JWT 的长文本进行脱敏，检出率 100.0%，处理耗时 $\le 2.0\text{ms}$ |
| **TC-136-7** | `testZeroTrustDataMasking_luhnAndIdCardValidation()` | 格式精准校验与反事实用例：对无效校验码的伪身份证号与伪银行卡不产生误伤脱敏，确保有效数据精准掩码 |
| **TC-136-8** | `testEndToEndMcpGateway_fullLifecycleAuditReceipt()` | 端到端全链路闭环：多租户配额分配 $\rightarrow$ 慢调用自适应熔断降级 $\rightarrow$ 双向数据脱敏 $\rightarrow$ 纯 Java 21 Record 凭单签名自验真 |

---

## 四、最小实现文件集合与禁止修改边界

### 4.1 最小修改文件集合
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/gateway/McpZeroTrustGatewayReceipt.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/gateway/MultiTenantDistributedQuotaGovernor.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/gateway/AdaptiveMcpCircuitBreakerGovernor.java` (新建)
4. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/gateway/ZeroTrustDataMaskingEngine.java` (新建)
5. `frontend/src/views/kb/bot/build/components/mcp/McpGatewayStatusWidget.vue` (新建或扩展)
6. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase136McpGatewayContractTest.java` (新建)

### 4.2 严格禁止修改的边界
- 严禁修改已归档封存的力学沙箱目录：`tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改外部系统默认 JDK 17，所有编译执行必须严格使用 Java 21 隔离环境变量；
- 严禁在测试中修改断言期望值以掩盖缺陷；
- 严禁引入任何未获批准的外部三方依赖库。

---

## 五、完整复现与验证命令

```bash
# 1. 编译安装 qknow-hermes-core 模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn clean install -pl qknow-hermes/qknow-hermes-core -DskipTests

# 2. 运行 Phase 136 专项契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase136McpGatewayContractTest

# 3. 运行 Phase 125 ~ Phase 136 跨阶段基准全量回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase125SwarmConsensusContractTest,Phase126DistributedSwarmContractTest,Phase127E2ESwarmContractTest,Phase128AutonomousSwarmContractTest,Phase129UnifiedE2ESwarmContractTest,Phase130SwarmGovernanceContractTest,Phase131EnterpriseMcpProductionBenchmarkContractTest,Phase132HierarchicalGraphRagContractTest,Phase133E2EChaosBenchmarkContractTest,Phase134FrontendDagHitlIntegrationContractTest,Phase135SwarmDynamicTopologyContractTest,Phase136McpGatewayContractTest

# 4. 验证前端 Vite 生产构建
cd frontend && npm run build:prod
```
