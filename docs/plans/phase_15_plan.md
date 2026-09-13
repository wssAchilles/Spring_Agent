# Phase 15: 生产级可观测性、Prometheus 核心埋点与大模型调用成本/延迟治理体系实施计划

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置调研**：学术向智能体 (`0f3a60c8`) 与工程向智能体 (`118f957a`) 并发深度对标已闭环完成  
> **学术报告**：`docs/plans/phase_15_academic_report.md` (5 篇顶会论文，延迟累积模型/尾部延迟截断/Token 经济学/基数有界性定理)  
> **工程报告**：`docs/plans/phase_15_industrial_report.md` (Micrometer 7阶段Timer/DeepSeek纳元无锁治理/LangFuse异步批处理)  
> **方案文档**：`docs/plans/phase_15_plan.md`  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型。

---

## User Review Required

> [!IMPORTANT]
> **本阶段核心创新与治理价值**：
> 1. **RAG 7 阶段微观指标暴露**：针对 Cache、Vector、Keyword、Graph、RRF、Rerank、ParentChild 注入预热单例 `Timer.Sample`，配合门控 Counters（缓存命中/重排跳过/冷启动零命中），将指标时序严格锁死在常数 $\le 90$ 条，杜绝 Prometheus OOM。
> 2. **DeepSeek 官方真实计费感知与纳元定点无锁累加**：
>    - 彻底提取并解耦 `prompt_cache_hit_tokens`（0.5元/M）、`prompt_cache_miss_tokens`（2.0元/M）与 `reasoning_tokens`（R1 思考 token）；
>    - 采用 **纳元（Nano-CNY, $10^{-9}$ 元）定点整数** 与 Java 8 Striped `LongAdder` 累加，100% 消除浮点截断失真与多线程争用瓶颈。
> 3. **LangFuse 异步化韧性隔离**：
>    - 拔除主线程同步阻塞 HTTP 与 `Thread.sleep` 恶疾，引入容量 10,000 的有界队列与后台 Worker 批量上报，在外部可观测性不可用时执行优雅丢弃，业务吞吐不受任何反噬。

---

## Proposed Changes

### Component 1: RAG 细粒度微观可观测性 (qknow-module-kmc)

#### [NEW] [RagMetricsService.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/metrics/RagMetricsService.java)
- 枚举预注册 7 阶段 Timer（CACHE, VECTOR, KEYWORD, GRAPH, RRF, RERANK, PARENT_CHILD）；
- 门控计数器：缓存精确/语义命中/未命中/旁路、重排门控调用/旁路、冷启动与零命中状态；
- 标签基数严格控制在静态封闭集合，零 GC 运行期分配。

#### [MODIFY] [RagRetrievalService.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java)
- 注入 `RagMetricsService`，在检索各阶段（并发检索、RRF 融合、重排、Parent 组装）包裹微观计时与门控 Counter 上报。

---

### Component 2: Hermes 大模型 Token 经济学与成本治理 (qknow-hermes)

#### [NEW] [DeepSeekCostGovernor.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/cost/DeepSeekCostGovernor.java)
- 纳元定点整数累加器，基于 `LongAdder` 维护 Cache Hit Prompt、Cache Miss Prompt、Completion、Reasoning Tokens 与总成本；
- 注册并对外暴露 Prometheus Gauges (`llm.active.requests`, `llm.cost.cny.total`, `llm.tokens.total`)。

#### [NEW] [AgentMetricsService.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/metrics/AgentMetricsService.java)
- 统计 ReAct 循环步数 `DistributionSummary`（检测死循环发散倾向）；
- 统计工具调用耗时分布与防死循环熔断器触发计数。

#### [MODIFY] [DeepSeekCompatibleChatModel.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/deepseek/DeepSeekCompatibleChatModel.java)
- 在请求体加入 `stream_options: {"include_usage": true}`；
- 在响应和 SSE 结束 chunk 中准确解析 `prompt_cache_hit_tokens`、`prompt_cache_miss_tokens` 与 `reasoning_tokens`。

#### [MODIFY] [AgentOrchestrator.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java)
- 移除粗暴的 `completionTokens.incrementAndGet()` 伪计数；
- 接入 `DeepSeekCostGovernor` 与 `AgentMetricsService`，在对话结束时精确记账。

#### [MODIFY] [LangFuseTracingService.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/observability/LangFuseTracingService.java)
- 改造为 `ArrayBlockingQueue` 异步入队 + 后台守护 Worker 批量定时推送；
- 彻底移除 `Thread.sleep` 与同步 HTTP 阻塞，加入丢弃告警保护主线程。

---

### Component 3: 契约测试与回归验证 (tests)

#### [NEW] [Phase15ObservabilityAndCostGateTest.java](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase15ObservabilityAndCostGateTest.java)
- 测试 1：验证 `RagMetricsService` 7 阶段耗时正确记录，且在 SimpleLight / 常规 / 异常路径下 outcome 标签正确；
- 测试 2：验证 `DeepSeekCostGovernor` 纳元定点计算精度：给定 1,000,000 miss + 1,000,000 hit + 1,000,000 completion，金额严格等于 10.500000 元（误差为 0）；
- 测试 3：验证并发多线程下 `DeepSeekCostGovernor` 无锁累加无丢失，且 Prometheus Gauge 正确刷新；
- 测试 4：验证 `LangFuseTracingService` 异步队列化在队列满时不阻塞主线程且平稳丢弃；
- 测试 5：验证 Prometheus 标签基数严格有界（无动态 query/uuid 进入 Tag）。

---

## Verification Plan

### Automated Tests
```bash
# 1. 编译相关底层模块并安装到本地
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl qknow-framework/qknow-ai,qknow-hermes/qknow-hermes-core,qknow-module-kmc/qknow-module-kmc-biz install -DskipTests

# 2. 运行 Phase 15 专属契约门禁测试
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests -Dtest=tech.qiantong.qknow.rag.eval.Phase15ObservabilityAndCostGateTest test

# 3. 全量防退化回归测试（要求 722+ 项测试 100% 绿灯）
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests test
```
