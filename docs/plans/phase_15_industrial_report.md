# Phase 15 核心工程落地课题工业级深度调研与架构设计报告：Micrometer、Prometheus、LangFuse 可观测性与 DeepSeek Token 成本/延迟治理引擎

**建议归档目标**：`docs/plans/phase_15_industrial_report.md`  
**遵循规范**：`AGENTS.md` Research-to-Implementation Gate 规范  
**报告状态**：**RESEARCH_GATE_READY**

---

## 一、前言与系统架构模型基线 (Architecture Model Baseline)

### 1.1 架构模型与生态基准
任何针对本项目可观测性与成本治理的工程落地，必须严格遵守全局不可动摇的模型基线：
1. **唯一生成模型**：本系统的所有生成侧（Chat / Generation / RAG 检索问答 / Tool Calling / 思考链展示）**唯一使用 DeepSeek API**（`deepseek-chat` 即 DeepSeek-V3，`deepseek-reasoner` 即 DeepSeek-R1）。
2. **唯一向量模型**：本系统的所有向量化与语义召回侧（Embedding）**唯一使用阿里千问 (Qwen) Embedding（1536 维）**。
3. **彻底弃用声明**：项目中绝无任何本地部署的大语言模型（如 Llama, Qwen-Chat 等），且已彻底弃用 OpenAI/GPT API。一切关于“昂贵大模型与廉价本地小模型之间路由”的假设在本项目均不成立。

### 1.2 当前代码库现状走查与核心缺陷诊断

经对 `backend/qknow-framework/qknow-ai`、`backend/qknow-hermes` 与 `backend/qknow-module-kmc` 源码进行全链路走查，当前在可观测性、Token 统计与延迟监控方面存在以下致命缺陷：

1. **RAG 链路监控严重粗粒度，关键门控与阶段黑盒化**：
   - 在 `RagRetrievalService.java:97-115` 中，仅有一个外层的宏观计时器：`meterRegistry.timer("rag.retrieve.duration", "outcome", outcome).record(...)`；
   - 内部 7 个核心阶段（Cache 语义缓存、Vector 向量检索、Keyword 关键词检索、Graph 图谱召回、RRF 倒数排名融合、Rerank 排序模型、ParentChild 上下文展开）完全缺失微观指标；
   - 关键门控（语义缓存命中/旁路、重排动态门控 bypass/invoked、零命中状态）缺乏 Prometheus Counter，生产环境下无法评估重排门控的降本提速效益。

2. **DeepSeek 计费规则严重失真，Token 提取漏洞百出**：
   - `TokenCounter.java:27-32` 采用 `ConcurrentHashMap.merge`，每次累加都在堆上频繁 `new TokenUsage(...)`，且在高并发多线程争用同一 Key 时引起哈希桶同步阻塞；
   - `CostEstimator.java:13-36` 仍残留已弃用的 `gpt-4o` 计价，且使用 `double` 浮点数计算 `(tokens / 1_000_000.0) * price`，存在严重的浮点精度丢失风险；
   - **完全丢失 DeepSeek 关键计费元数据**：DeepSeek 官方的 Prompt 缓存命中（`prompt_cache_hit_tokens`）与未命中（`prompt_cache_miss_tokens`）价格相差 **4 倍**（0.5 元 vs 2.0 元/M），且 DeepSeek-R1 的推理思考 Token（`reasoning_tokens`）计价与普通输出一致但需独立监控。当前 `DeepSeekCompatibleChatModel.java` 与 `TokenCounter` 仅提取常规 `prompt_tokens` 与 `completion_tokens`，无法计算真实账单。
   - `AgentOrchestrator.java:429` 中流式输出仅粗暴执行 `completionTokens.incrementAndGet()`（将一个 chunk 当作一个 token），导致 Agent 运行时的 token 统计完全失真。

3. **外部可观测性服务（LangFuse）同步阻塞调用拖垮系统吞吐**：
   - `LangFuseTracingService.java:65-83` 直接在业务请求线程上同步调用 `httpClient.send(...)`，遭遇 429 限流时竟然在工作线程上执行 `Thread.sleep(500L * (retry + 1))`；
   - 一旦外部网络抖动或 LangFuse 响应缓慢，将迅速耗尽 WebFlux/Tomcat 工作线程池，引发全站级雪崩。

---

## 二、Research-to-Implementation Gate 核心对标

### 2.1 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-Phase15)**：在 Spring Boot 3.5.8 + Micrometer 1.15.6 + Prometheus 生态下，通过实现**“RAG 7 阶段低开销 Timer.Sample 观测与门控 Counter” + “DeepSeek V3/R1 缓存感知型纳元精度无锁累加治理引擎 (LongAdder)” + “Agent ReAct 轮次/步数/工具熔断指标暴露” + “异步环形缓冲隔离 (Async RingBuffer / Queue)”**：
> 1. 能在 1000 QPS 高并发下将埋点耗时损耗压制在 10 微秒（μs）以内，完全消除同步锁竞争与堆内存垃圾分配；
> 2. 100% 精确提取 `prompt_cache_hit_tokens`、`prompt_cache_miss_tokens` 与 `reasoning_tokens`，以纳元（Nano-CNY）整数定点运算消除浮点数累加误差，使费用监控与官方账单对账达到 100% 吻合；
> 3. 标签基数（Cardinality）严格控制在常数级（每个 Metric 产生的时间序列数固定 ≤ 50），杜绝 Prometheus OOM 隐患；
> 4. 彻底将外部 Trace（如 LangFuse/OpenTelemetry）与业务执行线程解耦，即使可观测性外部端点发生网络分区或持续 429，业务吞吐量与时延下降 ≤ 0.5%。

### 2.2 核心工业设计与踩坑复盘
1. **高基数 Tag 防爆**：严禁将动态 query、UUID 放入 Tag，严格将 Tag 限定在已知封闭枚举集合内（如 model, stage, outcome）；
2. **纳元定点高并发累加器**：货币以纳元（^{-9}$ 元）为基准，全程 long 整数累加与 LongAdder 分段无锁更新，消除浮点截断与并发锁冲突；
3. **DeepSeek 真实计费标准**：
   - V3：命中输入 0.5 元/M，未命中 2.0 元/M，输出 8.0 元/M；
   - R1：命中输入 1.0 元/M，未命中 4.0 元/M，输出 16.0 元/M；
4. **外部可观测性异步批处理**：LangFuse 采用 ArrayBlockingQueue + 后台守护 Worker 批量上报，队列满触发 DropOldest 保护主业务。
