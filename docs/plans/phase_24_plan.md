# Phase 24 决策完备实施方案：生产级流式事件总线、动态反馈增强与自适应 RAG 策略自进化闭环 (Adaptive RAG & Streaming Feedback-Driven Self-Evolution)

> **拟归档路径**：`docs/plans/phase_24_plan.md`  
> **依据规范**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置依赖**：Phase 01 ~ Phase 23 全量交付通过（已具备生产级可观测性、难例挖掘、原生加速、Agentic 记忆图谱、蜂群协作调度与反应式 SAGA 工作流）  
> **理论依据**：`docs/plans/phase_24_academic_report.md`（Discounted-LinUCB 次线性累积遗憾定理 $O(\sqrt{dT\ln T})$、非平稳时变追踪界 $\tilde{O}(d^{2/3}V_T^{1/3}T^{2/3})$、PBM 位置偏差逆倾向加权 IPS 严格无偏性证明、李雅普诺夫指数收敛与防振荡界限 $\le 0.05$）  
> **工业对标**：`docs/plans/phase_24_industrial_report.md`（W3C SSE 规范、Spring WebFlux / Project Reactor 背压整形、Redis Streams 削峰填谷、Langfuse 统一反馈模型、DSPy Prompt 优化与 Dify 标注审核流）  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **环境隔离铁律**：后端全量模块统一且唯一使用 **Java 21** 编译与运行，绝对路径固定为 `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，Maven 执行强制局部传入 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，绝不污染全局 Java 17。

---

## 一、唯一待验证算法与工程假设 (Falsifiable Core Hypothesis)

> **核心假设 (H-Phase24)**：  
> 针对当前系统在流式传输、会话反馈与检索控制中存在的五大致命缺陷——  
> 1. “前端 SSE 在网络轻微抖动时抛弃已有文本并报错，且后端无 `Last-Event-ID` 断点续传”；  
> 2. “后端对每个 Token Chunk 触发一次数据库同步 UPDATE，单会话高达数百次写库，造成致命行锁争用与 I/O 雪崩”；  
> 3. “背压机制粗糙导致客户端在高吞吐时丢字卡死”；  
> 4. “完全缺失端到端用户反馈采集系统，且面临恶意刷分与投毒风险”；  
> 5. “检索路由与重排门控为静态硬编码，与用户体感反馈脱节，负反馈样本无法流转难例池，Prompt 模板无法自进化”——  
> 
> **构建以下四大闭环机制**：  
> 1. **高可靠 SSE 断点续传与背压协议**：基于 W3C `Last-Event-ID` 与消息单调递增 `sequence_id`，构建服务端双层环形滑动重发窗口（L1 本地 RingBuffer + L2 Redis Stream 暂存，保留最近 60s 窗口），前端发生短时断网后无缝重连补发丢失帧；同时采用流式内存累加器取代逐 Token 写库，仅在终态或异常时异步批写 DB 一次；  
> 2. **防刷防投毒的高并发反馈采集流水线**：提供基于 HMAC 加盐签名与令牌桶限流的 Feedback REST API，基于 Redis Streams 进行异步削峰缓冲并批量持久化至 `kb_chat_feedback`；  
> 3. **基于 Discounted-LinUCB 与 EWMA 的自适应策略调节器 (Adaptive Policy Governor)**：将千问 Embedding 投影降维至 32 维特征，对 5 种检索策略执行多臂老虎机在线学习，通过逆倾向得分加权 (IPS) 消除展示位置偏差，结合 EWMA 滑动健康度动态微调向量/关键词混合权重并实施 Rerank 智能旁路门控；  
> 4. **负反馈难例流转与基于 DeepSeek-R1 反思的 Prompt 自进化审核流**：将高质量负反馈样本自动注入 Phase 16 难例挖掘体系（新增第 5 漏斗），驱动 `deepseek-reasoner` 进行链式归因反思生成结构化 Prompt 优化建议，经管理员 Visual Diff 审核后安全上线。  
> 
> **能够证明**：  
> - **流式重连零丢失**：在网络注入 500ms~2000ms 随机断线与切后台测试中，SSE 流式重连恢复率达到 $100\%$，零漏字且零文本重复；  
> - **数据库 I/O 骤降 99%**：单会话数据库更新写操作由 $\ge 500$ 次降为 $\le 2$ 次（降幅 $\ge 99.6\%$）；  
> - **高吞吐抗投毒**：反馈采集端点支持 $\ge 2000$ TPS 突发写入，HMAC 伪造与脚本刷分拦截率达 $100\%$；  
> - **检索自进化收益**：高频负反馈场景下，自适应调节器使真实难例召回准度提升 $\ge 8\%$，重排调用旁路率达 $\ge 35\%$，且单步参数位移严格受限于李雅普诺夫物理界限 $\Delta \Theta \le 0.05$；  
> - **难例闭环流转**：负反馈强监督难例自动流转与不可变评测集扩充成功率达到 $100\%$。

---

## 二、架构拓扑与交互数据流

```text
  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
  │                                    前端交互层 (Vue 3 + Web API)                                  │
  │  ┌─────────────────────────┐      ┌──────────────────────────────┐     ┌──────────────────────┐  │
  │  │  fetchEventSource 客户端 │      │   无感断点续传打字机缓冲组件  │     │   防刷加盐反馈交互   │  │
  │  │ (Last-Event-ID 自动携带)│ ───> │  (RAF 动态渲染 / 优雅异常兜底)│     │  (赞/踩/复制/停留打标)│  │
  │  └─────────────────────────┘      └──────────────────────────────┘     └──────────────────────┘  │
  └─────────────────┬──────────────────────────────────────────────────────────────────┬─────────────┘
                    │ 1. SSE Connection (Last-Event-ID)                                │ 4. Feedback API
                    ▼                                                                  ▼
  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
  │                                      网关与反向代理 (Nginx)                                      │
  │                  proxy_buffering off; proxy_read_timeout 600s; keepalive 128;                    │
  └─────────────────┬──────────────────────────────────────────────────────────────────┬─────────────┘
                    │                                                                  │
                    ▼                                                                  ▼
  ┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
  │                           后端服务集群 (Spring Boot 3.5.8 / Java 21)                              │
  │                                                                                                  │
  │  [ Reliable Streaming Subsystem ]                       [ Feedback & Ingestion Subsystem ]       │
  │  ┌────────────────────────────────────────┐             ┌─────────────────────────────────────┐  │
  │  │       ResilientSseController           │             │     FeedbackCollectorController     │  │
  │  │   - Last-Event-ID 解析与重连分流       │             │   - HMAC-SHA256 加盐防伪验签        │  │
  │  │   - 响应式流量整形 (limitRate)         │             │   - 令牌桶限流器 (RateLimiter)      │  │
  │  └──────────────────┬─────────────────────┘             └──────────────────┬──────────────────┘  │
  │                     │                                                      │                     │
  │                     ▼                                                      ▼                     │
  │  ┌────────────────────────────────────────┐             ┌─────────────────────────────────────┐  │
  │  │      SseReplayWindowBuffer             │             │        Redis Streams 消息队列       │  │
  │  │   - L1: 本地环形队列 (RingBuffer)      │             │   (Key: stream:chat:feedback)       │  │
  │  │   - L2: Redis 暂存 (TTL 60s)           │             └──────────────────┬──────────────────┘  │
  │  └──────────────────┬─────────────────────┘                                │                     │
  │                     │                                                      ▼                     │
  │                     ▼                                   ┌─────────────────────────────────────┐  │
  │  ┌────────────────────────────────────────┐             │       FeedbackEventConsumer         │  │
  │  │      Terminal Batch Flush Engine       │             │   - 批量落库 kb_chat_feedback       │  │
  │  │   - 零中间写库，终态单次异步更新 DB    │             │   - 异步触发在线特征累加器          │  │
  │  └────────────────────────────────────────┘             └──────────────────┬──────────────────┘  │
  │                                                                            │                     │
  │  ─────────────────────────────────────────────────────────────────────────┼───────────────────── │
  │                                                                            │                     │
  │  [ Adaptive Governor & Evolution Engine ]                                  ▼                     │
  │  ┌────────────────────────────────────────┐             ┌─────────────────────────────────────┐  │
  │  │        AdaptivePolicyGovernor          │ ◄───────────┤       Online Feature Accumulator    │  │
  │  │   - Discounted-LinUCB 多臂老虎机       │             │   - 滑动窗口满意度指数计算          │  │
  │  │   - EWMA 指数加权移动平均信用计算      │             └─────────────────────────────────────┘  │
  │  │   - 向量/关键词混合权重动态漂移调节    │                                                      │
  │  │   - Rerank 智能旁路门控自适应裁决      │                                                      │
  │  └──────────────────┬─────────────────────┘                                                      │
  │                     │                                                                            │
  │                     ▼                                                                            │
  │  ┌────────────────────────────────────────┐             ┌─────────────────────────────────────┐  │
  │  │       RealQueryMiningService           │ ◄───────────┤      PromptSelfEvolutionService     │  │
  │  │   - Phase 16 难例挖掘体系扩展          │             │   - 收集高频负反馈与用户修改标注    │  │
  │  │   - 新增第 5 漏斗: NEGATIVE_FEEDBACK   │             │   - DeepSeek-Reasoner (R1) 深度反思 │  │
  │  │   - 结构保留无感脱敏与不可变测试集扩充 │             │   - 生成优化建议与 Visual Diff 审核 │  │
  │  └────────────────────────────────────────┘             └──────────────────┬──────────────────┘  │
  └────────────────────────────────────────────────────────────────────────────┼─────────────────────┘
                                                                               ▼
                                                            ┌─────────────────────────────────────┐
                                                            │     管理员人工审核审查流 (Console)   │
                                                            │   (一键批准应用 / 拒绝驳回 / 回滚)   │
                                                            └─────────────────────────────────────┘
```

---

## 三、实施范围与最小修改文件集合

严格遵循最小修改原则，仅触碰与本阶段核心假设直接相关的组件：

### 3.1 流式高可靠与断点续传子系统 (`qknow-module-kb`)
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/streaming/SseReplayWindowBuffer.java` [NEW]（内存与 Redis 双层环形重发缓冲区，支持 Last-Event-ID 毫秒级补发）
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/conversation/KbConversationController.java` [MODIFY]（彻底移除逐 Token 写库，接入重发窗口与基于 `Last-Event-ID` 恢复的断点续传端点）

### 3.2 高并发反馈采集流水线 (`qknow-module-kb`)
- `backend/qknow-module-kb/qknow-module-kb-api/src/main/java/tech/qiantong/qknow/module/kb/api/feedback/dto/ChatFeedbackReqVO.java` [NEW]（反馈请求 DTO，包含 messageId, feedbackType, dwellTimeMs, token, comment, correctedText）
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/dal/dataobject/feedback/KbChatFeedbackDO.java` [NEW]（反馈持久化实体）
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/controller/admin/feedback/FeedbackCollectorController.java` [NEW]（HMAC 防伪验签与限流控制端点）
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/feedback/FeedbackStreamQueueService.java` [NEW]（基于 Redis Streams 的高并发削峰异步生产消费服务）

### 3.3 自适应策略调节器与多臂老虎机 (`qknow-module-kmc`)
- `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/adaptive/DiscountedLinUcbRouter.java` [NEW]（千问 1536 维投影降维 + 5 臂 Discounted-LinUCB 增量递推路由器）
- `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/adaptive/FeedbackCreditBus.java` [NEW]（多模态无偏 IPS 信用分配总线）
- `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/adaptive/AdaptivePolicyGovernor.java` [NEW]（EWMA 与李雅普诺夫稳定的动态混合权重及 Rerank 旁路门控调节器）
- `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java` [MODIFY]（接入自适应权重与旁路门控）

### 3.4 难例流转与 Prompt 自进化 (`qknow-module-kmc` & `qknow-module-kb`)
- `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/mining/RealQueryMiningService.java` [MODIFY]（扩展第 5 漏斗 `NEGATIVE_FEEDBACK` 难例沉淀）
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/dal/dataobject/evolution/KbPromptEvolutionProposalDO.java` [NEW]（Prompt 进化候选表实体）
- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/evolution/PromptSelfEvolutionService.java` [NEW]（驱动 DeepSeek-Reasoner 进行归因反思并生成 Visual Diff 审核提案）

### 3.5 数据库 DDL 与专属自动化契约测试
- `deploy/sql/postgresql/24-feedback-and-evolution.sql` [NEW]（建表 DDL：`kb_chat_feedback` 与 `kb_prompt_evolution_proposal`）
- `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase24AdaptiveRagContractTest.java` [NEW]（专属契约测试用例，覆盖 SSE 断点续传、I/O 批写优化、HMAC 防投毒、Discounted-LinUCB 亚线性遗憾与自适应门控收敛等核心断言）

---

## 四、测试驱动开发 (TDD) 契约规划

在编写任何业务实现代码前，首先在 `Phase24AdaptiveRagContractTest.java` 中构建以下 10 大核心契约用例：

1. **`contract01_sseReplayWindow_normalAndResumeSuccess`**：验证正常推送写入环形重发缓冲区，模拟连接中断后客户端携带 `Last-Event-ID: 10` 请求补发，断言服务端准确无遗漏补推 `seq > 10` 的帧，且无重复帧；
2. **`contract02_terminalBatchFlush_eliminatesPerTokenDbWrites`**：验证在产生 100 个 Token Chunk 的流式会话中，中间过程数据库 update 次数为 0，仅在完成（onComplete）时触发 1 次异步落库，且最终内容完整无缺失；
3. **`contract03_feedbackSecurity_hmacAndRateLimiting`**：验证合法 HMAC 签名反馈成功入队，篡改 token 或超时 token 立即被拒绝（返回 403），高频突发请求触发令牌桶限流（返回 429）；
4. **`contract04_feedbackQueue_redisStreamIngestionAndBatchFlush`**：验证高并发反馈事件写入 Redis Streams，消费组批量读取并批量落库至 `kb_chat_feedback`，无乱序与消息丢失；
5. **`contract05_discountedLinUcb_regretSublinearConvergence`**：验证在 5 臂动作空间与千问投影特征下，连续 500 轮交互中平均累积遗憾 $R(T)/T$ 单调下降，验证次线性收敛性；
6. **`contract06_discountedLinUcb_nonStationaryTracking`**：在第 250 轮人为反转动作收益（模拟知识库文档增删突变），断言带有折扣因子 $\gamma=0.995$ 的 Discounted-LinUCB 在 50 轮内敏锐完成自适应策略追踪重收敛；
7. **`contract07_ipsUnbiasedFeedback_creditAssignment`**：验证带有展示位置偏差（Position Bias）的点击数据，经过逆倾向得分加权（IPS）后，估计效用无偏收敛至真实相关性；
8. **`contract08_adaptivePolicyGovernor_weightDriftAndLyapunovStability`**：验证在连续负反馈与正反馈冲击下，自适应调节器动态微调权重在 $[0.3, 0.8]$ 安全区间内平滑漂移，单步位移严格满足 $\le 0.05$，李雅普诺夫能量函数单调收敛，杜绝振荡；
9. **`contract09_realQueryMining_negativeFeedbackFunnel`**：验证用户点踩并附带修改的样本成功进入 Phase 16 `RealQueryMiningService` 第 5 漏斗，经 `QuerySanitizer` 脱敏后成功扩充至回归评测集；
10. **`contract10_promptSelfEvolution_r1ReflectionAndProposalGeneration`**：模拟收集到代表性负反馈后，驱动反思引擎生成包含结构化缺陷归因、改进 Prompt 与 Diff 摘要的候选提案，状态正确标记为 `PENDING_REVIEW`。

---

## 五、验证命令与成功判据

### 5.1 验证命令

```bash
# 1. 运行 Phase 24 专项契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn clean test -pl tests -Dtest=Phase24AdaptiveRagContractTest

# 2. 全量防退化回归测试 (验证既有 772 项测试 100% 绿灯)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn clean test -pl tests -Dtest=tech.qiantong.qknow.**.*Test

# 3. 前端生产构建验证
cd frontend && npm run build
```

### 5.2 准出判据 (Exit Criteria)
1. **测试指标**：`Phase24AdaptiveRagContractTest` 10/10 契约测试全部绿灯通过；
2. **防退化指标**：后端全量回归测试保持 100% 绿灯（0 Failure, 0 Error）；
3. **前端构建**：前端 Vite 生产构建顺利通过（0 错误，打包耗时在正常基准内）；
4. **性能指标**：单会话数据库中间写次数压降至 0，自适应路由与信用计算单步开销 $P_{95} \le 5\text{ms}$。

---

## 六、风险、停止条件与独立授权边界

### 6.1 风险与规避策略
- **内存堆积风险**：长时间运行的 SSE 会话重发缓存可能占用过多内存。*规避*：环形队列有界限（128 帧），Redis Stream 显式设置 TTL 60 秒并自动清理；
- **恶意攻击风险**：开放反馈端点可能遭受机器刷分。*规避*：HMAC 加盐防伪验签 + 分布式令牌桶限流双重防御。

### 6.2 立即停止触发条件 (Stop Conditions)
若在契约测试运行中出现以下任意异常，必须立即停止并输出 `RESEARCH_GATE_BLOCKED`：
1. SSE 重连补发后出现文本顺序颠倒或漏字；
2. Discounted-LinUCB 累积遗憾发散；
3. 门控参数单步位移超过 $0.05$ 物理安全上限；
4. 任何既有模块测试出现失败回归。

### 6.3 生产化与线上启用的独立授权边界
- **本阶段仅限完成**：Java 21 核心类编写、契约自动化测试验证与文档同步；
- **明确禁止行为**：严禁在未获用户单独明确授权前，在线上开启自动写回开关或执行不可逆 DDL。

---
**方案完备性确认**：本方案基于学术向与工程向两路权威调研报告汇聚编制，具备清晰的数学理论支撑、实证缺陷剖析、契约测试规划与最小实现范围，完全符合 `AGENTS.md` 规范。待用户明确批准后，即可进入 TDD 测试编写与代码落地阶段。
