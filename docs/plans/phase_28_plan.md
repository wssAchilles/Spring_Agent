# Phase 28 实施方案：超高并发异构多模态多模型统一代理网关与动态成本延迟 SLA 最优路由 (Unified Model Gateway, Multi-Provider Resilience & SLA-Optimal Routing)

> **当前状态**：**PROPOSED (待用户批准实施)**  
> **前置依赖**：Phase 15 (生产级可观测性与 DeepSeekCostGovernor), Phase 17 (多知识库联合检索与统一预算), Phase 24 (流式事件总线与动态反馈)  
> **执行标准**：严格遵循 `@AGENTS.md` Research-to-Implementation Gate 规范  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（V3/R1），唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维），全链路绝无本地大模型；后端统一使用 Java 21 隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

## 一、方案背景与架构目标

### 1.1 核心业务痛点与失败机制
1. **硬编码与单点通道脆弱性**：各业务模块直接配置官方单一 BaseURL，当遇到上游服务商 503（Service Unavailable）、429（Rate Limit）或骨干网抖动导致超时时，调用链瞬时中断，缺乏向火山引擎 Ark、硅基流动、阿里云百炼等 DeepSeek 兼容通道的自动毫秒级故障倒换；
2. **缺乏统一反向代理协议层**：系统对外与对内部子系统未暴露标准的 `/v1/chat/completions`、`/v1/embeddings`、`/v1/models`，各客户端无法透明利用网关的负载均衡与熔断能力；
3. **API Key 单点竞争与惊群重试风险**：静态单一 Key 极易触发 RPM 限流；网络抖动时若客户端无退避并发重试，会引发惊群效应与级联雪崩（Cascading Avalanche）；
4. **SLA 时延与成本感知缺失**：无法度量上游各通道的实时首 Token 延迟（TTFT）与 P99 延迟，无法区分在线交互对话（SLA 延迟优先）与离线后台批量分块（吞吐与经济成本优先）进行 Pareto 最优选路；
5. **租户配额账本与高并发无锁限流缺位**：缺乏租户在途并发（In-Flight）硬限制；缺乏纳秒级无锁 CAS 令牌桶，未能与 Phase 15 的 `DeepSeekCostGovernor` 形成纳元级预检与终态扣减闭环。

### 1.2 唯一待验证假设 (H-Phase28-001)
> **假设**：通过在 `backend/qknow-framework/qknow-ai` 落地基于无锁位图的自适应三态断路器（CLOSED/OPEN/HALF-OPEN）、基于 Full Jitter 的指数抖动瀑布倒换调度器、基于滑动窗口 TTFT/P99 延迟感知的 Pareto 最优路由器、以及基于 64 位原子状态打包的无锁 CAS 令牌桶与金融级租户配额账本：  
> 1. 主通道注入 100% 故障（连续 503/429/超时）时，网关在 $\le 50\text{ms}$ 内平滑倒换至备用通道，端到端成功率保持 $\ge 99.9\%$，故障恢复后半开探针自动自愈；  
> 2. Full Jitter 消除重试共振，级联雪崩概率满足指数衰减 $P(\text{Avalanche}) \le C \exp(-\lambda N)$（定理 1.1）；  
> 3. 实时会话优先低 TTFT，离线批处理优先经济通道，消除羊群效应振荡（定理 2.1）；  
> 4. 无锁令牌桶限流在突发高并发下保持严格平滑放行界限（定理 3.1），单核吞吐 $\ge 5 \times 10^6 \text{ ops/s}$，欠费或超额租户 100% 毫秒级阻断。

---

## 二、架构设计与核心组件规范

### 2.1 模块拓扑与职责划分
组件统一位于 `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/`：

```text
tech.qiantong.qknow.ai.gateway/
├── model/
│   ├── ProviderChannel.java           // 通道元数据记录 (channelId, provider, baseUrl, keyPool, priority, weight, modelMapping)
│   ├── ChannelStatus.java             // 通道运行状态 (HEALTHY, DEGRADED, OPEN, HALF_OPEN, OFFLINE)
│   ├── RoutingScenario.java           // 业务场景 (INTERACTIVE_CHAT, AGENT_REASONING, OFFLINE_EMBEDDING)
│   ├── TenantQuotaLedger.java         // 租户配额与在途并发账本
│   └── GatewayDto.java                // OpenAI 兼容请求与响应标准 DTO
├── registry/
│   └── ProviderChannelRegistry.java   // 动态渠道注册表与 Key 池平滑加权轮询器
├── circuitbreaker/
│   ├── AdaptiveCircuitBreaker.java    // 无锁环形位图自适应三态断路器
│   └── RingBitMetrics.java            // 无锁环形位数组指标采集器
├── retry/
│   └── FullJitterRetryPolicy.java     // AWS 规范 Full Jitter 指数随机抖动退避器
├── router/
│   ├── LatencyAwareSlaRouter.java     // TTFT/P99 延迟感知与 Pareto 场景自适应选路器
│   └── ChannelLatencyTracker.java     // 无锁环形滑动窗口延迟追踪器
├── ratelimit/
│   └── LockFreeTokenBucketLimiter.java// 64 位复合原子 CAS 纳秒级无锁令牌桶
├── service/
│   ├── ModelGatewayProxyService.java  // 网关核心执行服务 (准入、限流、断路、倒换、流式背压、计费结算)
│   └── ChannelHealthCheckScheduler.java// 后台异步心跳探活自愈看门狗
└── controller/
    └── AiModelGatewayController.java  // 对外暴露标准 /v1/chat/completions, /v1/embeddings, /v1/models
```

---

## 三、核心算法与数学实现规格

### 3.1 自适应断路器无锁状态转移方程
- **状态空间**：$\mathcal{S} = \{\text{CLOSED} (0), \text{OPEN} (1), \text{HALF\_OPEN} (2)\}$
- **指标收集**：预分配 $N=100$ 槽位的 `AtomicIntegerArray`，原子递增游标取模无锁记录调用结果；
- **熔断判定**：当采样数 $\ge 20$ 且失败率（含 429、503、超时）$\ge 50\%$ 或连续失败 $\ge 5$ 次，原子 CAS 切换至 `OPEN`；
- **自愈机制**：在 `OPEN` 持续达到 $T_{\text{reset}} = 15\text{s}$ 后原子切换至 `HALF_OPEN`；放行 5 次探针请求，若成功率 $\ge 80\%$ 恢复为 `CLOSED`，否则惩罚回退至 `OPEN`。

### 3.2 Full Jitter 指数抖动重试算法
- 重试间隔严格遵循：
  $$V_{\text{ceiling}} = \min(T_{\max}, T_{\text{base}} \times 2^{\text{attempt}})$$
  $$T_{\text{sleep}} = \text{ThreadLocalRandom.current().nextLong}(0, V_{\text{ceiling}})$$
  其中 $T_{\text{base}} = 200\text{ms}, T_{\max} = 3000\text{ms}, \text{maxAttempts} = 3$。

### 3.3 SLA 延迟-成本双目标 Pareto 最优路由
- 综合成本函数：
  $$J(c) = w_{\text{latency}} \cdot \left(\frac{\text{TTFT}(c)}{\overline{\text{TTFT}}}\right) + w_{\text{cost}} \cdot \left(\frac{\text{CostPerToken}(c)}{\overline{\text{Cost}}}\right) + w_{\text{error}} \cdot \text{FailureRate}(c)$$
- 结合香农负熵正则化 Softmax 概率分配，李雅普诺夫函数证明消除高频流量振荡（Theorem 2.1）。

### 3.4 64 位原子复合状态无锁 CAS 令牌桶
- 高 32 位存储上次刷新时间戳 $\tau$，低 32 位存储可用令牌数 $T$；
- 通过单次 64 位 CAS 完成时间增量补偿与配额原子扣除，单核无锁吞吐 $> 5 \times 10^6 \text{ ops/s}$。

---

## 四、测试驱动开发 (TDD) 自动化契约设计 (10 项专项核心契约)

契约测试文件：`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase28ModelGatewayContractTest.java`

| 契约编号 | 测试方法名 | 验证目标与判定阈值 |
|---|---|---|
| **Contract 01** | `contract01_unifiedProtocol_standardizesOpenAiCompatibleEndpoints` | 验证网关能正确解析标准 OpenAI 格式输入，规范转发并输出标准 JSON/SSE 响应，保留 `reasoning_content` |
| **Contract 02** | `contract02_channelFailover_triggersAutomaticProviderFallbackOn429Or503` | 主通道模拟注入 503/429/超时，网关毫秒级瀑布倒换至备用通道，端到端成功率 100% (Theorem 1.1) |
| **Contract 03** | `contract03_circuitBreaker_transitionsStateCorrectlyOpenHalfOpenClosed` | 验证无锁断路器在连续失败下由 CLOSED -> OPEN -> 冷却超时 HALF-OPEN -> 探针恢复 CLOSED 的全生命周期状态流转 |
| **Contract 04** | `contract04_latencyAwareRouting_prefersLowestP99Channel` | 验证通道时延追踪器精准统计 P50/P90/P99/TTFT，路由器动态优先选择时延最低的健康通道 |
| **Contract 05** | `contract05_slaCostDualObjective_balancesLatencyAndTokenCostPareto` | 验证交互式会话与离线批处理场景下权重自动切换，兼顾 TTFT 延迟与 Token 经济成本的最优 Pareto 选路 (Theorem 2.1) |
| **Contract 06** | `contract06_tokenBucketLimiter_throttlesBurstTrafficSmoothly` | 验证无锁 CAS 令牌桶在突发脉冲高并发下保持严格平滑放行界限，超出配额请求 100% 毫秒级返回 429 (Theorem 3.1) |
| **Contract 07** | `contract07_tenantQuotaGovernor_enforcesTokenBudgetAndDeduction` | 验证租户在途并发硬限制拦截、预检纳元余额熔断以及请求结束后与 `DeepSeekCostGovernor` 的纳元精准结算 |
| **Contract 08** | `contract08_keyPoolRoundRobin_distributesLoadAcrossMultipleApiKeys` | 验证多 API Key 平滑加权轮询分布，失效 Key (401/429) 自动触发冷却旁路隔离 |
| **Contract 09** | `contract09_streamingResilience_propagatesCancellationAndMeasuresTtft` | 验证流式 SSE 请求下游主动断流时，网关立即级联取消上游连接（杜绝孤儿连接泄漏）并正确捕获 TTFT |
| **Contract 10** | `contract10_endToEndGatewayRouting_servesHighConcurrencyChatCompletion` | 验证端到端多租户高并发场景下网关综合调度能力，0 异常漏抛，指标上报完全闭环 |

---

## 五、最小文件变更范围与边界限制

### 5.1 新增文件清单
1. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/model/ProviderChannel.java`
2. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/model/ChannelStatus.java`
3. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/model/RoutingScenario.java`
4. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/model/TenantQuotaLedger.java`
5. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/model/GatewayDto.java`
6. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/registry/ProviderChannelRegistry.java`
7. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/circuitbreaker/AdaptiveCircuitBreaker.java`
8. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/circuitbreaker/RingBitMetrics.java`
9. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/retry/FullJitterRetryPolicy.java`
10. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/router/ChannelLatencyTracker.java`
11. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/router/LatencyAwareSlaRouter.java`
12. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/ratelimit/LockFreeTokenBucketLimiter.java`
13. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/service/ModelGatewayProxyService.java`
14. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/controller/AiModelGatewayController.java`
15. `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase28ModelGatewayContractTest.java`

### 5.2 严禁触碰的边界
- **禁止修改既有 812 项测试的断言逻辑与数据**；
- **禁止引入 Python、Node.js 等独立外置进程网关**；
- **禁止全局覆盖系统 JDK，所有命令必须通过局部前缀 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem` 执行**。

---

## 六、验证命令与复现标准

1. **Phase 28 专属契约测试**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn test -am -pl tests -Dtest=Phase28ModelGatewayContractTest -Dsurefire.failIfNoSpecifiedTests=false
   ```
   **通过标准**：10 项测试全部通过（0 失败 0 错误）。

2. **全量后端防退化回归测试**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
   mvn test -am -pl tests
   ```
   **通过标准**：812 + 10 = 822 项测试 100% 绿灯（0 失败 0 错误）。

3. **前端生产构建验证**：
   ```bash
   cd frontend && npm run build:prod
   ```
   **通过标准**：0 错误构建通过。
