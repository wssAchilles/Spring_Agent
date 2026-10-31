# Hermes Harness² 阶段九（Stage 9）：预算、官方价格与指标可用性冻结契约

- **文档编号**：`55-harness9-budget.md`
- **所属阶段**：Stage 9 — 阶段 0（冻结预算、价格与指标）
- **当前分支基线**：`feat/hermes-harness-stage8`（起点 HEAD：`198a6b69`，工作区 `clean`）
- **生成模型**：DeepSeek 官方活跃模型 `deepseek-flash`（指向版本 `DeepSeek-V4.1-Flash-1017`）
- **向量几何基线**：阿里千问 `text-embedding-v4`（$\mathbb{S}^{1023} \subset \mathbb{R}^{1024}$，$\|\mathbf{v}\|_2 = 1$）
- **冻结状态**：`FROZEN`

---

## 一、用户授权与硬性预算上限（Budget Gate）

本阶段已获得用户显式授权调用真实 DeepSeek API 执行受控 Propose-Probe-Compose (PPC) 实验。所有实验运行受以下**硬性预算门禁**（Hard Budget Gate）约束，由运行时看门狗实时拦截：

| 预算维度 | 硬性上限阈值 | 默认实验规划值 | 熔断行为 |
| :--- | :--- | :--- | :--- |
| **生成模型 ID** | `deepseek-flash` (`DeepSeek-V4.1-Flash-1017`) | `deepseek-flash` | 严禁调用已退役旧模型名 |
| **真实 API 调用** | 允许（仅限 `@Tag("live")` 显式触发） | 默认 `mvn test` 零外网 | 缺 Key 时标记 `EMPIRICAL_BLOCKED` |
| **最大 Probe 总数** | 默认 **`15`**（满足追加条件时绝对上限 **`20`**） | 5 cases × (1 baseline + 2 单变量候选) = `15` | 超过上限立即阻断后续 Probe |
| **最大模型调用次数** | **`60` 次** | 预计 `20 ~ 35` 次（每 Probe 1~3 次） | `totalModelCalls >= 60` 立即抛出/标记 `BUDGET_EXHAUSTED` |
| **最大总 Token 数** | **`200,000` tokens** | 预计 `15,000 ~ 45,000` tokens | `totalTokens >= 200,000` 立即标记 `BUDGET_EXHAUSTED` |
| **单次最大输出** | **`2,000` tokens / 次** | `maxTokens = 1024`（上限 $\le 2000$） | 在请求参数层硬性锁定 |
| **硬性人民币费用上限** | **`¥10.00`（人民币）** | 预计消费 **`¥0.05 ~ ¥0.50`** | `totalCostCny >= 10.00` 立即标记 `BUDGET_EXHAUSTED` |
| **重试纪律** | 重试计入总调用次数与总预算 | `maxRetryAttempts = 1`（禁止无限重试） | 严禁绕过看门狗私自重试 |

> **可选候选 C（组合候选 `A + B`）追加条件**：
> 默认必须先完成 **15 个 probe**（5 个任务 × `[baseline, candidate_A, candidate_B]`）。仅当这 15 个 probe 全部完成、未触发任何预算熔断、且当前累计总费用与预计追加后总费用严格低于 **`¥5.00`** 时，才允许追加候选 C（5 个 probe），追加后总 probe 数严格 $\le 20$。

---

## 二、DeepSeek 官方定价表与北京时间时段规则（Official Pricing）

数据取自 DeepSeek 官方定价文档（<https://api-docs.deepseek.com/zh-cn/quick_start/pricing/>，于 2026-10-31 实测抓取核验）：

### 2.1 模型规格与单价表（`deepseek-flash` / `DeepSeek-V4.1-Flash-1017`）

- **上下文长度**：`1M` tokens
- **最大输出长度**：`384K` tokens（本实验硬限 `2,000` tokens/次）

| 计费项 | 空闲时段（Off-Peak）官方单价 | 高峰时段（Peak）官方单价 | 换算单 Token 价格（元 / token） |
| :--- | :--- | :--- | :--- |
| **输入 Token（缓存命中，`cacheHitTokens`）** | **`0.02 元 / 百万 tokens`** | **`0.04 元 / 百万 tokens`** | 空闲：$2.0 \times 10^{-8}$ 元；高峰：$4.0 \times 10^{-8}$ 元 |
| **输入 Token（缓存未命中，`cacheMissTokens`）** | **`1.00 元 / 百万 tokens`** | **`2.00 元 / 百万 tokens`** | 空闲：$1.0 \times 10^{-6}$ 元；高峰：$2.0 \times 10^{-6}$ 元 |
| **输出 Token（含思维链，`completionTokens`）** | **`4.00 元 / 百万 tokens`** | **`8.00 元 / 百万 tokens`** | 空闲：$4.0 \times 10^{-6}$ 元；高峰：$8.0 \times 10^{-6}$ 元 |

### 2.2 北京时间（`Asia/Shanghai`，UTC+8）高峰与空闲时段官方定义

1. **高峰时段（Peak Hours）**：
   - 北京时间**周一至周五**（不含中国法定节假日）的 **`09:00 - 12:00`**（即 `09:00:00 <= t < 12:00:00`）与 **`14:00 - 18:00`**（即 `14:00:00 <= t < 18:00:00`）。
2. **空闲时段（Off-Peak Hours）**：
   - 除高峰时段以外的全部时段，包括**工作日其余时间**、**周末全天（周六、周日 00:00 - 24:00）**以及**中国法定节假日全天**。空闲时段价格为高峰时段的一半（50%）。

### 2.3 真实成本计算公式（`actualCostCny`）

对于每次 `UsageStatus == KNOWN` 的模型调用或 Probe 汇总，设：
- $T_{\text{prompt}} = \text{promptTokens}$
- $T_{\text{hit}} = \min(T_{\text{prompt}}, \max(0, \text{cacheHitTokens}))$
- $T_{\text{miss}} = \max(0, T_{\text{prompt}} - T_{\text{hit}})$
- $T_{\text{out}} = \text{completionTokens}$（DeepSeek 官方 API 中 `completion_tokens` 已包含思维链 `reasoning_tokens`）

根据调用完成时刻 $t$ 在 `ZoneId.of("Asia/Shanghai")` 下所属时段（`PEAK` 或 `OFF_PEAK`），选择对应单价 $(P_{\text{hit}}, P_{\text{miss}}, P_{\text{out}})$（单位：元 / 百万 tokens）：

$$\text{actualCostCny} = \frac{T_{\text{hit}} \cdot P_{\text{hit}} + T_{\text{miss}} \cdot P_{\text{miss}} + T_{\text{out}} \cdot P_{\text{out}}}{10^6}$$

> **铁律约束**：
> - 当 `usageStatus == UsageStatus.UNKNOWN` 时，必须标记 `costKnown = false`（或 `costStatus = UNKNOWN`），严禁将未知 usage 视作 `0.00 元` 参与候选优选！
> - 严禁使用字符长度估算 Token 数冒充 provider usage。

---

## 三、阶段九指标可用性边界冻结表（Metric Availability Gate）

为彻底杜绝“虚假指标参与优化”，阶段九对所有评测指标的可用性与使用方式做如下硬性冻结：

| 指标名称 | 阶段九状态 | 数据来源与计算规则 | 允许用途 |
| :--- | :--- | :--- | :--- |
| `qualityPass` | **可用（AVAILABLE）** | 离线后验校验：回答非空、无未捕获异常、且满足基准 case 的确定性契约（如关键锚点/工具调用结果核验） | 仅作为候选准入门槛（Gate），严禁将 qrel 泄漏给 Proposer 或匿名 Judge |
| `promptTokens` | **可用（AVAILABLE）** | `UsageCapturingChatModel` 从 DeepSeek 响应 `usage.prompt_tokens` 真实提取 | 成本计算与 Token 效率对比 |
| `completionTokens` | **可用（AVAILABLE）** | `UsageCapturingChatModel` 从 DeepSeek 响应 `usage.completion_tokens` 真实提取 | 成本计算与输出紧凑度对比 |
| `cacheHitTokens` | **可用（AVAILABLE）** | `UsageCapturingChatModel` 从 `nativeUsage.prompt_cache_hit_tokens` 提取（缺省为 0） | 精确拆分缓存命中与未命中成本 |
| `totalTokens` | **可用（AVAILABLE）** | `UsageCapturingChatModel` 从 `usage.total_tokens` 真实提取 | 硬预算熔断（$\le 200,000$）与效率评估 |
| `usageStatus` | **可用（AVAILABLE）** | `KNOWN`（provider 真实返回且 $>0$）或 `UNKNOWN` | 凡 `UNKNOWN` 的候选立即剔除，禁止入选 |
| `actualCostCny` | **可用（AVAILABLE）** | 由上述真实 token 数与北京时间 Peak/Off-Peak 官方单价精确算出 | 硬预算熔断（$\le ¥10$）与 Pareto 成本比较 |
| `durationMs` | **可用（AVAILABLE）** | `System.currentTimeMillis()` 真实端到端墙钟耗时（$>0$） | 延迟回归监控与平局决胜参考 |
| `toolCallCount` / `toolNames` | **可用（AVAILABLE）** | `HarnessEvidenceCollector` 真实拦截记录的工具执行轨迹 | 验证 `TOOL` / `COMPOSITE` 场景真实性 |
| `guardVerdict` | **可用（AVAILABLE）** | `HoareTripleToolGuard` 在 `ENFORCE` 模式下的真实拦截/放行结论 | 验证工具前置条件守卫有效性 |
| `cycleDetected` | **可用（AVAILABLE）** | `LocallyDenseCycleAutomaton` 真实检测到的语义局部稠密环标志 | 任何触发死循环的候选直接淘汰 |
| `ragRetrieved` / `ragChunkIds` | **可用（AVAILABLE）** | `HarnessEvidenceCollector` 记录的布尔检索状态与真实注入切片 ID 列表 | 验证 `RAG` / `COMPOSITE` 场景归因 |
| `ragScore`（向量相似度分） | **禁用（DISABLED）** | 当前未在离线 Probe 夹具中对每个切片实时调用 Qwen `text-embedding-v4` 计算余弦分 | **严禁在阶段九使用伪造的常数 `ragScore` 参与候选选择或加权打分** |
