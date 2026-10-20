# 平台基础设施与控制面审计报告（Knowledge Hub / qKnow 2.2.1）

> 审计员：platform-infra-auditor（task-5）
> 审计时间：2026-10-19
> 方法属性：**只读静态审计**。未执行 `mvn`、未启动服务、未修改任何源码/配置/SQL/fixture。
> 结论标注：**【已验证】**= 由源码/配置文件/已编译产物/日志直接证得；**【推断】**= 基于版本、代码结构或运行日志的合理推断，未做运行时验证；**【未知】**= 本次审计无法判定。

---

## 0 阅读范围与方法

### 0.1 覆盖范围（实际读取）

| 区域 | 读取内容 | 规模 |
| --- | --- | --- |
| `backend/pom.xml`、48 个模块 pom | 全文精读根 POM、`qknow-server`、`qknow-framework/qknow-common`、`qknow-framework/qknow-ai`、`tests`；其余模块按依赖与插件命中检索 | 48 个 pom |
| `backend/qknow-server` | 启动类、`config/*`、`resources/application*.yml`（21 个文件）、`logback.xml`、`ehcache.xml`、`mybatis-config.xml` | 10 个 Java + 21 个 yml |
| `backend/qknow-framework/**` | `qknow-security`（24 类）、`qknow-mybatis`（19 类）、`qknow-redis`、`qknow-neo4j`、`qknow-quartz`、`qknow-websocket`、`qknow-config`、`qknow-common` 关键类、`qknow-ai` 网关与 embedding、`qknow-file`、`qknow-thirdparty`、`qknow-generator` | 798 文件 |
| `backend/qknow-module-{system,app,dm,ext,ai,kb,kmc,kg}` | controller/service/mapper 主干；重点 kb / kmc / ai 的会话、SSE、RAG、模型解析 | 944 文件 |
| `backend/tests` | 测试组织、surefire 配置与报告、伪测试抽检 | 349 文件 / 114,863 行 |
| 工程化 | `backend/Dockerfile`、`backend/qknow-server/docker/Dockerfile`、`docker-compose.yml`、`deploy/docker/*`、`scripts/*`、`.github/workflows/ci.yml` | — |
| 已编译产物 | `backend/qknow-server/target/qknow-server.jar`（fat jar，589 个第三方 jar，435 MiB）、4 处 `target/surefire-reports`、`backend/logs`、`backend/qknow-server/logs` | 只读枚举，未重建 |
| 前后端契约 | `frontend/src/api/kb/conversation/index.js`、`api/app/chat/message/index.js`、`views/kb/agent/index.vue` 的 SSE 消费段 | 只读 |

全仓 Java 文件 2,690 个（排除 `target/`），约 386,344 行；测试独占 114,863 行（29.7%）。

### 0.2 方法

1. 以「请求进来 → 鉴权 → 会话 → 模型/知识库/工具解析 → RAG 预检索 → gRPC → SSE → 落库」为唯一主线，逐跳读实现，记录阻塞点与失败模式。
2. 依赖治理以 `target/qknow-server.jar` 的 `BOOT-INF/lib/*.jar` 清单为**地面真值**（编译产物优于 POM 声明），再回查 POM 解释成因。
3. 运行证据来自 `backend/logs/*.log`（2026-07-01 ~ 2026-10-17 的 8 次启动/失败记录）与 `target/surefire-reports/*.xml`（聚合解析）。
4. 密钥类问题只报告**文件:行号与变量名**，不复制任何凭据值；源码中硬编码的字面量亦做掩码处理。
5. 不做任何写操作；唯一产出为本文件。

---

## 1 控制面真实执行路径

### 1.1 链路总览（以 KB 会话流式问答为主干）

```
浏览器 @microsoft/fetch-event-source (POST, Authorization: Bearer)
  └─ Nginx (/prod-api/ 或 /api/，缓冲策略不一致)
      └─ Tomcat 10.1.49 / 8099（800 max threads, 平台线程，虚拟线程未启用）
          ├─ CorsFilter → FlyFlowHmacAuthenticationFilter(仅 /flyflow/**) → JwtAuthenticationTokenFilter
          │    └─ TokenService.getLoginUser() → Redis 反序列化 LoginUser（fastjson2 autoType）
          ├─ KbConversationController.sendMessage()  ← @PreAuthorize 缺省
          │    ├─ chatMessageService.getMessagesByConversationId()  （全量历史，无分页/截断）
          │    ├─ saveMessage(user) + saveMessage(assistant, "思考中")   （先落库占位）
          │    └─ KbAgentConfigServiceImpl.chatMessage()
          │         ├─ aiModelService.getAiModel(modelId) → getAiApiKey()  （明文 API Key 出库）
          │         ├─ findSemanticCache()  → 命中则 Flux.just(...) 直接返回
          │         ├─ 预检索：multiKbCoordinator.coordinateRetrieval() 或逐库 recallTest()（同步、串行）
          │         ├─ 组装 ChatRequest{ modelConfig.apiKey, ragContexts, history, toolMethodIds }
          │         └─ hermesGrpcClient.chat(request)  → Hermes :9090（plaintext）
          │              └─ HermesGrpcService.chat() → AgentOrchestrator.chat() → DeepSeek SSE
          │                   └─ onMessage → ChatEvent → Flux<VO> → Sinks.multicast → SSE 帧
          └─ 终态：updateAssistantMessage(assistantMessage, finalContent) 单次写库
```

### 1.2 逐跳阻塞点与失败模式

**① 鉴权（JWT + Redis 会话）— 有 4 个真实缺陷。**

- 【已验证】Token 只带一个不透明 UUID，用户态完全依赖 Redis：`TokenService.getLoginUser(String)` 解析 `LOGIN_USER_KEY` 后读 `redisCache.getCacheObject(userKey)`（`backend/qknow-framework/qknow-security/src/main/java/tech/qiantong/qknow/security/web/service/TokenService.java:76-95`）。Redis 不可用 = **全站 401**，无本地降级。
- 【已验证】JWT 有效期 600 分钟（10 小时），与注释「默认30分钟」不符：`backend/qknow-server/src/main/resources/application.yml:126-127`（`expireTime: 600`）；续期阈值常量名为 `MILLIS_MINUTE_TEN` 实为 20 分钟（`TokenService.java:53`、`144-152`）。任何一次请求都会给活跃会话续 10 小时，等价于**近乎永久在线**，与”短 Token + Refresh” 的顶级实践相反。
- 【已验证】JWT 库为 `jjwt 0.9.1`（2018 年停更）：根 POM 声明属性 `jwt.version=0.9.1`（`backend/pom.xml:34,151-155`），而子模块 `qknow-common/pom.xml:22` 声明了 `jwt.version=0.12.6` **实际不生效**——父 POM `dependencyManagement` 已锁定版本，子模块属性被静默忽略。签名为 `SignatureAlgorithm.HS512` + 字符串密钥（`TokenService.java:189-196`），未做密钥长度校验、无 `kid`、无密钥轮换位。
- 【已验证】鉴权链上存在**硬编码共享密钥**：`SecurityConfig.filterChain()` 直接 `new FlyFlowHmacAuthenticationFilter("flyflow-internal", "qknow-flyflow-secret-key-****", redisTemplate)`（`backend/qknow-framework/qknow-security/src/main/java/tech/qiantong/qknow/security/config/SecurityConfig.java:155`），同一字面量还作为兜底默认值出现在 `FlyFlowHmacAuthenticationFilter.java:36`。该密钥无法通过配置覆盖、进 Git 历史、且被两个文件重复，属于**凭据入库**。
- 【已验证】放行面偏大（`SecurityConfig.java:116-150`）：
  - `/v3/api-docs/**`、`/swagger-ui.html`、`/swagger-resources/**`、`/webjars/**` 全部 `permitAll`（`135-144`）——生产环境 API 全量暴露；
  - `/api/app/**` 整体 `permitAll`（`143`）——该前缀下若后续新增控制器，将默认匿名可访问；
  - `/websocket/**` 匿名（`140`），WebSocket 的身份校验只在业务层，未在握手层强制；
  - 反向证明：`@Anonymous` 注解在**全仓 0 次使用**（`grep -rn "@Anonymous"` 命中 0），而 `PermitAllUrlProperties` 仅在扫描该注解填充白名单（`qknow-framework/qknow-security/src/main/java/tech/qiantong/qknow/security/config/properties/PermitAllUrlProperties.java:34-53`）——该机制是**死代码**，白名单事实上全部硬编码在 SecurityConfig。
- 【已验证】`ASync` 派发被要求 `authenticated()`（`SecurityConfig.java:145-146`），配套 `JwtAuthenticationTokenFilter.shouldNotFilterAsyncDispatch()` 返回 `false`（`JwtAuthenticationTokenFilter.java:49-52`）——这是 SSE 场景的必要补丁，说明此前确实踩过「SSE 二次派发 401」；补丁正确，但意味着**每个 SSE 异步派发都要重新访问一次 Redis**（每帧/每次派发一次用户查询），是隐性放大的热点。

**② 租户/工作区解析 — 事实上不存在。**

- 【已验证】`BaseController.getWorkSpaceId()` 直接 `return 1001L;`（`backend/qknow-framework/qknow-common/src/main/java/tech/qiantong/qknow/common/core/controller/BaseController.java:215-217`）。所有使用 `super.getWorkSpaceId()` 的控制器（`KbFlowController.java:47,56,68,77`；`KbConversationController.java:103` 等）都在传一个**常量**。
- 【已验证】MyBatis 层没有任何租户拦截器：`MybatisPlusConfig` 仅注册分页 / 乐观锁 / 防全表更新（`backend/qknow-framework/qknow-mybatis/src/main/java/tech/qiantong/qknow/mybatis/config/MybatisPlusConfig.java:22-31`），无 `TenantLineInnerInterceptor`；`DataScopeInterceptor`（硬编码拼 `dept_id = ?`）**全仓仅被测试引用，从未注册为 MyBatis 插件**（`qknow-mybatis/.../core/interceptor/DataScopeInterceptor.java:19-22`，`grep -rn "DataScopeInterceptor"` 仅命中自身与测试）。
- 【已验证】会话消息查询无归属校验：`getMessagesByConversationId` 只按 `conversationId + delFlag` 过滤（`backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/conversation/impl/KbChatMessageServiceImpl.java:20-25`）；`KbConversationController.sendMessage()` 直接使用请求体中的 `botId/conversationId`（`KbConversationController.java:84-111`）。**横向越权（IDOR）已验证成立**：任意登录用户可枚举 `conversationId` 读取他人会话/续写上下文。
- 【已验证】`/internal/api/**` 无内网校验：`KbInternalApiController` 注释自称「仅在 Docker 内网可访问，不经过 Nginx 路由」（`qknow-module-kb/.../controller/admin/KbInternalApiController.java:14-21`），但 SecurityConfig 无 `/internal/api/**` 专门规则，落到 `anyRequest().authenticated()`——**任意普通用户 JWT 即可调用 `POST /internal/api/rag/recall` 并按任意 `knowledgeId` 检索全文**（`KbInternalApiController.java:33-46`）。部署约束不是代码约束。

**③ 会话与历史 — 无界增长，直接放大 Token 成本。**

- 【已验证】每轮把**全部历史消息**读出并整体塞进 gRPC 请求：`KbConversationController.java:84` 读全量历史 → `:114 setHistoryMessages(history)` → `KbAgentConfigServiceImpl.java:379-387` 全量转 `ChatMessage` → `.addAllHistory(history)`（`:411`）。全链路无 window / token 预算 / 摘要裁剪。
- 【已验证】用户消息与「思考中」占位在调用模型**之前**落库（`KbConversationController.java:87-88`），若进程崩溃，会话中将永久残留「思考中」占位，无对账/清理任务。
- 【已验证】`sendMessage` 对 `config == null` 的处理是「先写错误消息再 `throw new RuntimeException`」（`KbConversationController.java:91-95`）——在 SSE 返回类型的方法里抛未捕获运行时异常，客户端收到的是 500 而非 SSE 错误帧。

**④ 模型解析 — 存在绕过口子，且网关文案与项目模型基线冲突。**

- 【已验证】模型配置来源为数据库中的 `ai_model.model` 与 `ai_api_key.api_key`（`KbAgentConfigServiceImpl.java:242-255`），即**运行期可由管理员改写模型名**，代码层无 allowlist。
- 【已验证】`ModelNameResolver` 的兜底分支是「精确直通」：`return ModelResolutionReceipt.of(..., modelName.trim(), EXACT_MATCH)`（`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/ModelNameResolver.java:77-79`）——只要不是别名、不在退役表，任意字符串都会原样下发。所谓「单一真理源」只对别名生效，不构成准入控制。
- 【已验证】`ModelResolutionReceipt` 使用无密钥 SHA-256 自校验 + `MessageDigest.isEqual` 常量时间比较（`ModelResolutionReceipt.java:50-60`），工程纪律合规，但这是**校验和不是签名**——任何人都能重算，不能作为不可抵赖存证。
- 【已验证】AI 网关对外宣告的模型列表仍包含**已退役**的 `deepseek-chat` / `deepseek-reasoner`：`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/gateway/controller/AiModelGatewayController.java:56-60`。这与 `AiModelAliasProperties` 中把两者列入 `legacyFallbacks`（`AiModelAliasProperties.java:46-52`）自相矛盾，也直接违反项目「退役模型名禁止使用」铁律。
- 【已验证】网关端点 `/v1/**` 无独立鉴权、无限流、无配额（`AiModelGatewayController.java:18-21,28-48`）——由 `anyRequest().authenticated()` 兜底，任何登录用户可无限调用生成/向量化接口，成本侧无闸门。

**⑤ RAG 预检索 — 串行阻塞 + 失败静默，是首字延迟的主因。**

- 【已验证】单库分支是**串行 for 循环**：`knowledgeBaseList.forEach(kb -> { ... kmcApiService.recallTest(kb.getId(), ...) ... })`（`KbAgentConfigServiceImpl.java:315-357`）。N 个知识库就串行 N 次检索，全部完成前 SSE 一帧都不发。首字延迟 ≈ N × 单库检索延迟。
- 【已验证】并发分支虽用虚拟线程（`MultiKnowledgeConcurrentRetriever.java:126` `Executors.newVirtualThreadPerTaskExecutor()`），但外层 `CompletableFuture.runAsync(...)` **未指定执行器**（`:130-152`），默认跑在 `ForkJoinPool.commonPool`；且任务内部阻塞 `singleFuture.get(timeout)`（`:138`）。当知识库数 > CPU 并行度时，commonPool 被阻塞任务占满，**JVM 全局 commonPool 饥饿**（其它模块的 parallelStream/CompletableFuture 一并受影响）。【推断】高并发下表现为「RAG 首字延迟抖动 + 无关接口变慢」。
- 【已验证】预检索失败被静默吞掉：`catch (Exception e) { log.warn("RAG 预检索失败: knowledgeId={}", ...) }`（`KbAgentConfigServiceImpl.java:355-357`），随后仍以**空 `preRetrievedContent`** 组装 `RAGContext`（`:358-362`）。用户在 UI 上只会看到「召回 0 条记忆」，无法区分「检索失败」与「确实无命中」——fail-open 但不可观测。
- 【已验证】多库装配有全局超时与单库超时（`MultiKnowledgeConcurrentRetriever.java:138,159-165`），这一点是**正向设计**，应保留。
- 【已验证】跨库装配采用 RRF + 20KB 硬预算（`KbAgentConfigServiceImpl.java:268-279`），与项目「上下文预算」纪律一致。

**⑥ gRPC 调用 — 明文、无背压、健康检查可无限挂起。**

- 【已验证】`ManagedChannelBuilder.forAddress(host, port).usePlaintext()`（`backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/HermesGrpcClient.java:43-47`）——**明文 HTTP/2**，且同一条 request 里携带 `modelConfig.apiKey`（`KbAgentConfigServiceImpl.java:407`）。即 **LLM API Key 以明文经内网 gRPC 传输**。
- 【已验证】`call.request(Integer.MAX_VALUE)`（`HermesGrpcClient.java:90`）取消流控；`Flux.create` 默认 `OverflowStrategy.BUFFER`（`:64`）——消费端慢于生产端时**无界缓冲**，长回答 + 慢客户端 = 堆内存膨胀。
- 【已验证】健康检查无 deadline：`HermesServiceGrpc.newBlockingStub(channel).healthCheck(...)`（`HermesGrpcClient.java:106-108`）未传 `CallOptions.withDeadlineAfter`，Hermes 半死不活时该线程可**永久阻塞**。
- 【已验证】`Flux.create` 内 `emitter.next()` 从 gRPC 回调线程直接推（`:75`），未做 `tryOnNext` 背压判定；上游取消时靠 `emitter.onDispose → call.cancel`（`:93-99`）兜底，这一点是正向设计。
- 【已验证】单条 ManagedChannel、无 `keepAliveWithoutCalls`、无重试/熔断策略（`:43-48`）；channel 在 `@PreDestroy` 做 5s 优雅关闭（`:52-61`），OK。

**⑦ SSE 回传 — 存在**丢帧竞态**与**取消不传播**，是本次审计最重要的控制面缺陷。**

- 【已验证】竞态：`KbConversationController.sendMessage()` 先 `agentConfigService.chatMessage(agentReq).subscribe(...)`（`KbConversationController.java:125-180`），**然后**才 `return sink.asFlux()`（`:181`）。Sink 为 `Sinks.many().multicast().onBackpressureBuffer()`（`:123`）——`multicast()` 无 replay，首个订阅者出现前的 `tryEmitNext` 一律返回 `FAIL_ZERO_SUBSCRIBER` 且**返回值被丢弃**（`:148`）。
  - 正常链路（Hermes 远端流式）因网络 RTT 通常掩盖该竞态；
  - **语义缓存命中路径必然触发**：`cachedAnswerFlux` 返回 `Flux.just(resp)`（`KbAgentConfigServiceImpl.java:525-540`），在 `subscribe()` 内**同步**发射并 `onComplete`，此时 SSE 客户端尚未订阅 → 帧被丢弃、complete 也被丢弃 → 客户端收到 **200 + 空 body**。【推断】所有「缓存命中」的问答都会显示空白回答（可通过单元测试或一次缓存命中实测确认，本次未运行）。
- 【已验证】取消不传播：`.subscribe(...)` 返回的 `Disposable` 被丢弃（`KbConversationController.java:125`），`doOnCancel` 只把 `clientConnected` 置 false（`:181`）。客户端断开后 **上游 DeepSeek 调用继续跑到结束**，Token 照付、线程/连接照占。这是 SSE 长连接最常见的资源泄漏形态。
- 【已验证】背压：`onBackpressureBuffer()` 无容量上限（`:123`），配合 gRPC 侧无界缓冲构成**双端无界**。
- 【已验证】SSE 生命周期无超时配置：全仓 `grep "configureAsyncSupport|setDefaultTimeout|spring.mvc.async"` **0 命中**。`application.yml` 无 `spring.mvc.async.request-timeout`。SSE 实际存活时间受容器默认 async timeout 约束（Tomcat 默认 30s）。【推断】开启 thinking 的长回答（>30s）会被容器中断；需实测确认。
- 【已验证】Nginx 缓冲策略**按路径分叉**：`deploy/docker/nginx.conf:12-23` 只对 `/api/kb/conversation/send` 关缓冲，而 `/api/kb/flow/executeFlowStream`、`/api/kb/agent/testChatMessages`、`/api/kb/conversation/replay`、`/api/kac/stream-run` 全部落到 `location /api/`（`:25-30`，默认 `proxy_buffering on`）→ **这些 SSE 端点会被 Nginx 缓冲，打字机效果失效**。另一份 `deploy/docker/nginx/sites/qknow.conf:27-48` 则对 `/prod-api/` 全关缓冲。两份 Nginx 配置并存且语义不同，属部署漂移（见 2.5）。
- 【已验证】前后端契约断裂：后端实现了完整的断点续传（`sequenceId` + `GET /kb/conversation/replay`，`KbConversationController.java:118-132,187-207`），但前端从未调用——`frontend/src/api/kb/conversation/index.js:43-60` 只做 `onmessage`，`frontend/src/views/kb/agent/index.vue:714-760` 只解析 `event.data`，全前端 `grep "replay|lastEventId|Last-Event-ID|sequenceId"` **0 命中**。→ 断点续传是**无消费方的死代码**，却持续付出每 token 一次写锁 + 无界 Map 的内存代价。

**⑧ 落库 — 单次写库设计正确，但缺一致性兜底。**

- 【已验证】逐 token 写库已被消除，改为内存聚合 + 终态单次 `updateById`（`KbConversationController.java:145-146,176`），这是正确的「写放大」治理，应保留。
- 【已验证】语义缓存写为 fire-and-forget：`Mono.fromRunnable(...).subscribeOn(Schedulers.boundedElastic()).subscribe(null, err -> log.warn(...))`（`KbAgentConfigServiceImpl.java:493-509`），无重试、无超时、无落库成功回执 → Redis/PG 抖动时缓存静默丢失（可接受，但需 metrics）。
- 【已验证】缓存写入判据是正则黑名单：`isFailedAnswer()` 命中「未找到/抱歉/不确定…」即不写缓存（`KbAgentConfigServiceImpl.java:459-471`）。副作用是**正常但礼貌的回答（含"抱歉"）永远不入缓存**，命中率被人为压低；且该规则与「答案质量」耦合，属启发式而非契约。
- 【已验证】语义缓存 key 含 `workspaceId`（`KbAgentConfigServiceImpl.java:473-491`），但 `workspaceId` 恒为 1001（见 ②）→ **所有租户共享同一语义缓存命名空间**，一旦多租户上线即成串号事故。

### 1.3 链路失败模式速查

| 故障注入点 | 后果 | 用户可见表现 | 证据 |
| --- | --- | --- | --- |
| Redis 不可用 | 全部请求 401 | 全站不可用 | `TokenService.java:76-95` |
| Hermes:9090 不可达 | `Flux.create` onClose 非 OK → `emitter.error` | SSE 中途 500/断流 | `HermesGrpcClient.java:80-87` |
| Hermes 无 gRPC 服务端部署 | 连接被拒 | 全量问答失败 | 见 2.6 |
| 语义缓存命中 | 空 SSE body | 空白回答 | `KbConversationController.java:123,181` + `KbAgentConfigServiceImpl.java:539` |
| 客户端断开 | 上游继续跑完 | 无（账单上涨） | `KbConversationController.java:125,181` |
| RAG 预检索异常 | fail-open 空上下文 | 「召回 0 条」但无告警 | `KbAgentConfigServiceImpl.java:355-362` |
| 单库检索慢 | 首字延迟 × N | 转圈很久才开始输出 | `KbAgentConfigServiceImpl.java:315-357` |
| 回答 >30s | 容器 async timeout | 回答被截断 | 无 async timeout 配置 |
| 进程崩溃 | 「思考中」残留 | 会话脏数据 | `KbConversationController.java:88` |

---

## 2 工程化与依赖评估

### 2.1 版本矩阵（POM 声明）

| 组件 | 声明版本 | 位置 | 风险 |
| --- | --- | --- | --- |
| Java | 21（`release=21`，含 `--enable-preview`） | `backend/pom.xml:18,226,246` | `--enable-preview` 使产物**绑定编译器版本**，生产 JRE 必须严格同版，禁止升级；且每 6 个月 preview API 可变更 |
| Spring Boot | 3.5.8 | `backend/pom.xml:74` | — |
| Spring Framework | 6.2.15（BOM import） | `backend/pom.xml:20,55-61` | — |
| Spring Security | 6.5.7（BOM import） | `backend/pom.xml:21,63-69` | — |
| Spring AI | 1.1.0 | `backend/pom.xml:35`；`qknow-ai/pom.xml` BOM import | 与实际 classpath 不符（见 2.2） |
| spring-ai-alibaba | 1.1.2.2 | `backend/pom.xml:36` | 其自身依赖 Spring AI 1.1.2，与 1.1.0 冲突 |
| gRPC | 1.84.0（BOM） | `backend/pom.xml:37,79-85` | — |
| protobuf-java | **3.25.1 强制降版** | `backend/qknow-server/pom.xml:83-88`（注释：覆盖 Hadoop 传递的 2.5） | 与 grpc-1.84 / proto-google-common-protos 2.64.1 期望的 4.x 不一致 |
| jjwt | 0.9.1（2018） | `backend/pom.xml:34,151-155` | EOL；子模块 0.12.6 声明被静默忽略 |
| fastjson2 / fastjson1 | 2.0.60 / 1.2.83 | `backend/pom.xml:27,145-149`；`qknow-common/pom.xml:94-99` | 两代并存；1.x 已 EOL |
| Jackson | annotations **2.21** / databind-core **2.19.4** | `backend/pom.xml:28,86-90`；jar 实测 | **版本错配**（annotations 与 databind 跨 2 个 minor） |
| Druid | 1.2.27 | `backend/pom.xml:22` | — |
| PageHelper | 2.1.1（`pagehelper` 6.1.1 另行声明） | `backend/pom.xml:26,104-108`；`qknow-common/pom.xml:64-68` | 两套分页坐标并存 |

### 2.2 编译产物级依赖核查（地面真值：`target/qknow-server.jar`）

【已验证】fat jar 含 **589 个第三方 jar**、总计 **435 MiB**（`target/original-qknow-server.jar` 仅 28 KiB，说明全部重量在依赖）。以下为**同一 classpath 内并存冲突版本**（均在 `BOOT-INF/lib/` 实测）：

| 冲突族 | 并存版本 | 后果等级 |
| --- | --- | --- |
| Jackson | `jackson-annotations-2.21` + `jackson-core/databind-2.19.4` | 高：注解与实现跨 minor，`@JsonAppend/@JsonProperty` 新特性在 2.19 运行时可能 `NoSuchMethodError`；【推断】 |
| Spring AI | `spring-ai-model/commons 1.1.0` 与 `spring-ai-rag/client-chat/commons 1.1.2` 混装 | 高：Spring AI 内部 API 在 1.1.x 内已有签名变化 |
| MyBatis-Plus | `3.5.15`（core/spring/starter）与 `3.5.16`（extension/jsqlparser） | 中高：`ServiceImpl` 与 `jsqlparser` 适配层版本不一致 |
| POI | `poi-5.3.0` + `poi-ooxml/poi-ooxml-lite-5.2.3` | 中高：`XSSFWorkbook` 依赖 `poi` 内部类，跨 minor 易 `NoSuchMethodError` |
| protobuf | `protobuf-java-3.25.1` + `proto-google-common-protos-2.64.1`（需 4.x） | 高：A2A/MCP 走 protobuf 4 生成代码时可能 `UnsupportedOperationException` / `VerifyError`；【推断，需实测】 |
| hutool | `hutool-all-5.8.31` + `hutool-core-5.8.28` | 中 |
| Elasticsearch | `elasticsearch-7.17.20` + `elasticsearch-rest-client-8.18.8` | 中：客户端与 rest-client 跨大版本 |
| Jetty | `jetty-* 12.0.30` + `jetty-servlet/webapp 9.4.53` + `websocket-* 9.4.53` | 中 |
| Servlet API | `jakarta.*` + `javax.servlet-api-3.1.0` + `servlet-api-2.5` + `jsp-api-2.1` | 中 |
| 日志实现 | `logback-1.5.21` + `log4j-core-2.24.3` + `reload4j-1.2.22` | 中：三重后端，SLF4J 绑定行为依赖排包顺序 |
| 校验 | `jakarta.validation-api-3.0.2` + `validation-api-1.1.0.Final` + `hibernate-validator-8.0.3` | 中 |
| MCP SDK | `mcp-0.14.0` + `mcp-json-0.17.0` | 中 |
| MapStruct | `mapstruct-1.5.5` 与 `qknow-server` 编译器插件 `1.6.3` | 低中：processor 与 runtime 版本不一致 |
| MySQL 驱动 / 连接池 | `mysql-connector-j-9.5.0`、`clickhouse-jdbc-0.6.0`、`ojdbc8-23.7`、`kingbase8-8.6`、`Dm8JdbcDriver18-8.1`、`postgresql-42.7.4`（6 套 JDBC） | 体积/攻击面 |

【已验证】**测试依赖泄漏进生产包**：
- `qknow-framework/qknow-common/pom.xml:163-166` 声明 `spring-boot-starter-test` **未加 `<scope>test</scope>`** → 编译期依赖 → fat jar 中出现 `junit-jupiter-5.12.2`、`mockito-core-5.17.0`、`assertj-core-3.27.6`、`spring-boot-test-3.5.8`、`jsonassert-1.5.3`、`xmlunit-core-2.10.4`、`hamcrest-3.0`、`json-path-2.9.0`、`byte-buddy-agent`、`objenesis`、`opentest4j`、`apiguardian` 等全套。
- `backend/qknow-server/pom.xml:119-123` 的 `embedded-redis-1.4.3` **未加 scope** → 嵌入式 Redis 服务器随生产包发布（其配置类 `EmbeddedRedisConfig` 仅在 `dev` 生效：`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/EmbeddedRedisConfig.java:31`，但类与依赖都进了生产）。
- `org.jacoco.agent-0.8.5-runtime` 出现在 fat jar：0.8.5（2020）**不支持 Java 21 字节码**，若未来接覆盖率将直接失败。

【已验证】体积来源（估算）：Hadoop 3.4.0 全家桶（`hadoop-client/common/hdfs/yarn/mapreduce` + zookeeper 3.8.3 + kerby 2.0.3 + avro + leveldbjni）、Tika 2.9.1 全 parser（24 个 `tika-parser-*` 模块）、Lucene 8.11.3 全家桶（13 个）、Elasticsearch 7.17.20 服务端核心（`elasticsearch-7.17.20` 本体在 Web 应用里毫无必要——只需 rest-client）、Flexmark 0.64.8 全家桶（30+ 模块）、Netty 全 classifier（含 `linux-riscv64`、`osx-x86_64`）。这些依赖由 `qknow-common/pom.xml:313-352`（Hadoop）、`:410-428`（Tika）、`:430-435`（Flexmark）、`:196-217`（ES）、`:389-401`（Mongo/ClickHouse）直接引入。

### 2.3 许可与安全面

- 【已验证】**AGPL 传染风险**：`com.itextpdf:itextpdf:5.5.13`（`qknow-framework/qknow-common/pom.xml:263-267`，jar 实测存在）。iText 5 的许可是 **AGPL v3 / 商业双许可**；若该二进制随服务对外提供，AGPL 会要求整体源码开放。当前代码路径为 PDF 生成相关（配合 `openhtmltopdf-pdfbox`、`pdfbox-2.0.29`、`fontbox`）。**必须由法务/Lead 决策：买商业许可，或替换为 PDFBox/Apache-2.0 方案。**
- 【已验证】**CVE 面（仅按版本推断，未做扫描器验证）**：
  - `fastjson 1.2.83`（EOL，1.x 分支已停止维护，历史 autotype 绕过链丰富）；
  - `commons-collections 3.2.2`、`commons-beanutils 1.9.4`、`jsch 0.1.55`、`jettison 1.5.4`、`xercesImpl 2.12.2`、`reload4j 1.2.22`、`woodstox-core 5.4.0`、`bcprov-jdk15on 1.70`（与 `bcprov-jdk18on 1.76` 并存）、`jjwt 0.9.1`、`snakeyaml`（版本由 Spring Boot 管理，需实测）；
  - `hadoop 3.4.0` / `zookeeper 3.8.3` / `avro 1.9.2` / `junrar 7.5.5` / `pdfbox 2.0.29` 均有历史 CVE 记录；
  - `embedded-redis 1.4.3`、`Dm8JdbcDriver18`、`kingbase8` 为闭源/来源不明二进制，供应链不可审计。
  - **结论：CVE 面无法用“升级 Spring Boot”一句话解决，必须先做依赖收敛（见 4.1）再谈补丁。**
- 【已验证】**Redis 反序列化面**：`FastJson2JsonRedisSerializer` 序列化开启 `JSONWriter.Feature.WriteClassName`，反序列化使用 `JSONReader.autoTypeFilter("org.springframework","tech.qiantong")`（`backend/qknow-framework/qknow-redis/src/main/java/tech/qiantong/qknow/redis/config/FastJson2JsonRedisSerializer.java:22,31,46`；白名单 `qknow-framework/qknow-redis/src/main/java/tech/qiantong/qknow/redis/constant/Constants.java:156`）。白名单不算宽，但「Redis 内容 = 可实例化类型来源」这一架构前提仍然危险；dev 环境 Redis **无密码**（`application-dev.yml:24`）进一步放大。【推断】需同时具备 Redis 写权限才可武器化，故定为 P1 而非 P0。

### 2.4 构建与启动

- 【已验证】**构建**：`backend/Dockerfile` 为 maven 多阶段 + `eclipse-temurin:21-jre-jammy` 非 root + cgroup 感知 JVM 参数（`-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`），这一层是**正向工程实践**。但 `COPY` 清单缺 `qknow-mcp`（`backend/Dockerfile:9-20`），而 `qknow-hermes-core` 由 `qknow-module-kb-biz/pom.xml:99-109` 编译依赖，Hermes 又依赖 mcp，缺失时 `-pl qknow-server -am` 会因找不到 reactor 模块而失败。【推断，未构建验证】
- 【已验证】**双 Dockerfile 并存且端口不一致**：`backend/Dockerfile:40` `EXPOSE 8099`；`backend/qknow-server/docker/Dockerfile:49-51` `EXPOSE 8090` + `CMD ["--server.port=8090"]`，且后者以 **root** 运行、安装 Miniconda 4.7.12（2020 EOL）+ Python 3.8.0 + torch 1.10 + transformers 3.4.0（2021），硬编码 `-Xms512m -Xmx2g`（无视 cgroup）。Maven `exec-maven-plugin` 实际使用的是**后者**（`backend/qknow-server/pom.xml:239,259,284,310` 指向 `--file=docker/Dockerfile`）。→ 生产构建路径与「现代化 Dockerfile」不是同一个。
- 【已验证】**`--enable-preview` 贯穿编译与运行**（`backend/pom.xml:246,256`；`qknow-server/docker/Dockerfile:51` 的 ENTRYPOINT 也带 `--enable-preview`）。任何 JRE 小版本升级都要重编，且与 `maven.compiler.release=21` 组合意味着**产物不可跨 JDK 21 补丁版本迁移**。
- 【已验证】**启动时长与失败模式**（`backend/logs/`）：
  - 唯一成功记录 `Started QKnowApplication in 10.454 seconds`（`backend/qknow-server/logs/info.log`，使用 **Java 17.0.9**）；
  - 最近一次（2026-10-17 10:39，Java 21.0.5）**启动失败**：`10:39:16.409 启动 → 10:39:19.464 Application run failed`，根因 `Could not resolve placeholder 'TOKEN_SECRET' in value "${TOKEN_SECRET}"`（`backend/logs/error.log:84`，`backend/logs/info.log:22-23`）。**即：`java -jar` 不加载 `.env`，缺 `TOKEN_SECRET` 时 3 秒即失败。**
  - 【已验证】日志中 **86 次使用 Java 17.0.9、仅 3 次 Java 21.0.5**（`grep -ho "using Java [0-9.]*"` 统计）→ 「Java 21 隔离铁律」在历史执行中**被反复违反而无门禁**。
- 【已验证】启动期噪声：`ConflictingBeanDefinitionException` 命中 6 个 bean（`aiJudgeService`、`http_request`、`mcpToolAdapter`、`nodeFactory`、`toolCallbackResolverConfig`、`toolPermissionEnforcer`），`NoClassDefFoundError: tech/qiantong/qknow/common/core/domain/AjaxResult` 命中 9 次，`mapper[ExtUnstructTaskMapper.updateExtUnstructTask]` 重复 MappedStatement。前者是 `@ComponentScan(tech.qiantong)` 与 Hermes 包重名冲突（`QKnowApplication.java:21-26` 已用正则排除 `tech.qiantong.qknow.hermes.*`，说明历史上有过同类事故）；后者是 **XML 与注解同 id 重复**的真实缺陷。

### 2.5 配置与密钥管理

- 【已验证】密钥来源分层**名义正确、执行断裂**：
  - `application.yml:125` `token.secret: ${TOKEN_SECRET}`、`:139` `${HERMES_OPENAI_API_KEY:}`、`:21` `${CRAG_SEARCH_API_KEY:}`、`:267-268` `${KNIFE4J_USERNAME}` / `${KNIFE4J_PASSWORD}`（均有弱默认值）、`application-prod.yml:18` `${REDIS_PASSWORD}`、`:40` `${DRUID_STAT_PASSWORD}` —— 都走环境变量；
  - `.env` / `.env.example` 被 `.gitignore:2,60-62` 忽略，仅 `.env.example` 入库（`git ls-files` 确认）；
  - 但 Spring Boot **不读 `.env`**，只有 `scripts/start.sh:14-19` 用 `set -a; source .env` 注入 —— 于是「脚本启动」与「jar/compose 启动」是两套语义。
- 【已验证】**生产形态可直接启动失败的缺项**：`deploy/docker/docker-compose-qknow.yml:12-40` 未注入 `TOKEN_SECRET`；同时它注入的 `DEEPSEEK_API_KEY`/`DASHSCOPE_API_KEY`（`:38-40`）在代码中**无任何 `${DEEPSEEK_API_KEY}` 引用**（配置读取的是 `HERMES_OPENAI_API_KEY`，见 `application-dev.yml:168,176,182`），`POSTGRESQL_URL`（`:24`）也未被任何 yml 引用（`application-prod.yml:94` 硬编码 `jdbc:postgresql://postgres:5432/ai_agent`）。→ **Compose 与配置互不匹配**。
- 【已验证】**明文默认口令散布**（仅报位置，不复述值）：`application-prod.yml:96-98`（`POSTGRESQL_PASSWORD` 有弱默认值）、`:107-109`（`MYSQL_PASSWORD` 有弱默认值）、`:118-120`（`DM8_PASSWORD` 有弱默认值）、`application-dev.yml:102-104`（DB 用户名/口令均有弱默认值）、`application-dev.yml:24`（Redis 空密码）、`application.yml:266-268`（Knife4j 有弱默认口令）、`qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml:15-16`（DB 用户名/口令均有本机用户名默认值）、`:20`（API Key 默认占位值）。这些**有默认值**，意味着「忘了配」不会报错，只会静默使用弱口令。
- 【已验证】**配置多副本漂移**：`deploy/docker/qknow-server/application.yml` 与 `backend/qknow-server/src/main/resources/application.yml` 已分叉（前者缺 `qknow.rag.crag`、`hermes.rag.*` 等整节，`diff` 已验证）。全仓 `application*.yml` 共 36 份。
- 【已验证】**生产把 dev 级开关带上线**：`application.yml:57` `tech.qiantong: debug` 是**基础配置**，prod 未覆盖（`application-prod.yml` 全文 153 行无 `logging` 节）→ 生产全命名空间 DEBUG 日志。`application.yml:109` `devtools.restart.enabled: true`（依赖却被注释掉：`qknow-server/pom.xml:90-97`）；`application.yml:111` `allow-circular-references: true`；`application.yml:102-104` 单文件/总上传 **3000MB**。Knife4j `production: false` + `enable-debug: true`（`:260,264`）。

### 2.6 编排完整性（最严重的工程化缺口）

- 【已验证】**Hermes 服务无任何部署物**：
  - 存在独立启动类 `backend/qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java`，配置 `server.port: 8081` / `grpc.server.port: 9090`（`qknow-hermes-starter/src/main/resources/application.yml:1-8`）；
  - gRPC 服务实现为 `@GrpcService`（`qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/grpc/HermesGrpcService.java:22`），依赖 `net.devh:grpc-server-spring-boot-starter`（`qknow-hermes-core/pom.xml:46`）；
  - **没有任何 Dockerfile 构建它**（`backend/Dockerfile` 与 `qknow-server/docker/Dockerfile` 均只产出 qknow-server），**没有任何 compose 服务定义它**（`docker-compose.yml:2-20` 只有 neo4j；`deploy/docker/docker-compose-qknow.yml:7-63` 只有 backend；`deploy/docker/docker-compose-base.yml` 只含 postgres/redis/neo4j/tantivy/nginx）；
  - 而控制面生产配置默认指向 `HERMES_GRPC_HOST:hermes`（`application-prod.yml:150-153`）。
  - → **生产部署起来后，`/kb/conversation/send` 必然全量失败**（连接拒绝），且该失败在健康检查中不可见（`/actuator/health` 未纳入 Hermes 健康指示器）。
- 【已验证】控制面自身**不启动 gRPC 服务端**：fat jar 中无 `grpc-server-spring-boot-*`（`unzip -l | grep grpc-server-spring-boot` = 0），`grep "ServerBuilder|NettyServerBuilder|addService"` 在 `qknow-hermes`/`qknow-server` **0 命中**。架构上的「控制面 ↔ gRPC ↔ Hermes」是**两个可部署单元**，但目前只有一半有部署物。
- 【已验证】`deploy/docker/docker-compose-qknow.yml:56-61` 的 backend 健康检查指向 `/actuator/health/readiness`，而 `application.yml:277-279` 仅开启 `probes.enabled: true`，readiness/liveness 组是否可用取决于 `management.endpoint.health.probes.add-additional-paths`（未配置）——探针可用性【未知】。

---

## 3 缺陷与风险清单

### 3.1 P0（阻断上线 / 严重数据与资金风险）

| ID | 缺陷 | 证据 | 影响 | 性质 |
| --- | --- | --- | --- | --- |
| P0-1 | 生产编排缺 `TOKEN_SECRET` 注入，控制面启动即失败 | `deploy/docker/docker-compose-qknow.yml:12-40`（无此项）；`application.yml:125`；`backend/logs/error.log:84` 实证 `Could not resolve placeholder 'TOKEN_SECRET'` | 生产冷启动 100% 失败 | 已验证 |
| P0-2 | Hermes（gRPC:9090）无 Dockerfile / 无 compose 服务，但控制面默认依赖它 | `HermesApplication.java`；`HermesGrpcService.java:22`；`application-prod.yml:150-153`；compose 全文无 hermes | 生产问答主链路全量不可用 | 已验证 |
| P0-3 | LLM API Key **明文落库 + 明文回读 + 明文传输** | `AiApiKeyDO.java:40`（无 `@TableField(typeHandler=…)`）；`EncryptTypeHandler` 全仓 0 引用；`AiApiKeyRespVO.java:37`（`@Excel` 明文导出）；`KbAgentConfigServiceImpl.java:407` + `HermesGrpcClient.java:44` `usePlaintext()` | 一次 DB/接口/内网抓包 = DeepSeek 与千问密钥泄漏，直接资金损失 | 已验证 |
| P0-4 | 源码硬编码 HMAC 共享密钥 | `SecurityConfig.java:155`（字面量 `qknow-flyflow-secret-key-****`）；`FlyFlowHmacAuthenticationFilter.java:36`（同一字面量作兜底） | `/flyflow/**` 防伪形同虚设；密钥不可轮换 | 已验证 |
| P0-5 | 多租户/工作区隔离为常量桩 + 会话无归属校验 + 内部 API 无内网约束 | `BaseController.java:215-217`（`return 1001L`）；`KbChatMessageServiceImpl.java:20-25`；`KbInternalApiController.java:14-46`；`MybatisPlusConfig.java:22-31`（无租户插件） | 横向越权读他人会话/知识库；多租户上线即数据串号 | 已验证 |
| P0-6 | SSE 语义缓存命中路径丢帧（多播 Sink 无订阅者竞态） | `KbConversationController.java:123,125-149,181`；`KbAgentConfigServiceImpl.java:539` `Flux.just(resp)` | 缓存命中时返回空回答（可用性事故） | 推断（代码路径确定，未运行） |

### 3.2 P1（显著性能 / 安全 / 可维护性）

| ID | 缺陷 | 证据 | 影响 |
| --- | --- | --- | --- |
| P1-1 | SSE 取消不传播，客户端断开后上游仍跑完 | `KbConversationController.java:125`（Disposable 丢弃）、`:181` | Token 与线程白烧；单机 SSE 连接可被低成本打死 |
| P1-2 | SSE 双端无界缓冲 | `KbConversationController.java:123`；`HermesGrpcClient.java:64,90` | 慢客户端 → 堆膨胀 |
| P1-3 | SSE 无超时配置，长生成受容器默认 async timeout 约束 | 全仓 `configureAsyncSupport/setDefaultTimeout/spring.mvc.async` 0 命中 | >30s 的回答被截断【推断】 |
| P1-4 | 断点续传前后端契约断裂 + `SseReplayWindowBuffer` 无界 | 后端 `KbConversationController.java:118-132,187-207`、`SseReplayWindowBuffer.java:109,118,148-167`；前端 0 命中 `replay/sequenceId` | 死代码 + 每 token 写锁 + `ConcurrentHashMap` 永不清理（`cleanExpiredSessions` 无任何调度方，`:148-150` 仅被 `getActiveSessionCount` 调用而后者无调用方） |
| P1-5 | 预检索串行阻塞首字 | `KbAgentConfigServiceImpl.java:315-357` | 首字延迟 × 知识库数 |
| P1-6 | `CompletableFuture.runAsync` 无执行器 → 阻塞 commonPool | `MultiKnowledgeConcurrentRetriever.java:130-152` | JVM 级 commonPool 饥饿，牵连无关接口 |
| P1-7 | RAG 预检索失败静默 fail-open | `KbAgentConfigServiceImpl.java:355-362` | 检索故障不可见，答案质量异常无告警 |
| P1-8 | 限流体系存在但 0 使用 | `RateLimiterAspect` 存在，`grep "@RateLimiter"` = 0 | 登录爆破、LLM 成本放大、SSE 洪泛均无闸门 |
| P1-9 | 授权覆盖不足：`KbFlowController` 0 个 `@PreAuthorize`；kb 模块 37/76 | `KbFlowController.java`（全文 0 命中 `@PreAuthorize`）；统计 37 vs 76 | 任意登录用户可执行任意工作流（含工具节点） |
| P1-10 | 测试依赖 / 嵌入式 Redis 泄漏进生产包 | `qknow-common/pom.xml:163-166`；`qknow-server/pom.xml:119-123` | junit/mockito/assertj + embedded-redis 进生产；攻击面与体积双增 |
| P1-11 | 589 jar / 435 MiB fat jar，多族版本冲突 | jar 实测；见 2.2 表 | 构建/启动/排障成本极高，`NoSuchMethodError` 概率显著上升 |
| P1-12 | Spring AI 1.1.0/1.1.2 混装 | jar 实测 | 上游 API 漂移导致运行时链接错误 |
| P1-13 | jackson-annotations 2.21 vs databind 2.19.4 | `backend/pom.xml:28`；jar 实测 | 序列化行为不一致 |
| P1-14 | protobuf-java 强降 3.25.1 | `qknow-server/pom.xml:83-88`；jar 中 `proto-google-common-protos-2.64.1` | A2A/MCP 场景高概率运行时错误【推断】 |
| P1-15 | 生产全命名空间 DEBUG 日志 | `application.yml:57`（基础配置）；`application-prod.yml` 无 logging 覆盖 | 日志量/IO/PII 泄漏风险 + 性能 |
| P1-16 | logback 两个 `<root>` 元素 + INFO appender `onMismatch=ACCEPT` | `qknow-server/src/main/resources/logback.xml:94-102`、`:26-33` | 日志重复写入、级别过滤失效；且**无 traceId/MDC、无 JSON**，非结构化 |
| P1-17 | 可观测性未真实接线 | 控制面 `MeterRegistry` 0 处使用（仅 Hermes `DeepSeekCostGovernor` 与一个测试）；LangFuse 仅 Hermes 侧且默认关（`qknow-hermes-starter/src/main/resources/application.yml:84-89` `LANGFUSE_ENABLED:false`）；`.env` 不被 Spring 加载 | Prometheus 只有默认 JVM/Tomcat 指标，无 RAG 延迟/SSE 连接数/Token 成本 |
| P1-18 | 租户无关的登录/权限风险面 | `application.yml:119` `/login` 匿名 + 无 `@RateLimiter`；captcha 类型 math（`:14`） | 口令爆破防护弱 |
| P1-19 | 敏感面暴露：`/api/app/**`、`/v3/api-docs/**`、swagger-ui 匿名；Knife4j 默认口令 | `SecurityConfig.java:135-144`；`application.yml:266-268` | API 面侦察 + 文档接口匿名访问 |
| P1-20 | Quartz 反射调用白名单过宽 | `Constants.java:166`（`{"tech.qiantong.quartz.task","tech.qiantong.qknow"}`）；`JobInvokeUtil.java:23-40`（含 `Class.forName(...).newInstance()`） | 有 `monitor:job:*` 权限者可反射调用任意 qknow Bean 方法 |
| P1-21 | Redis 反序列化 autoType + WriteClassName | `FastJson2JsonRedisSerializer.java:22,31,46`；`Constants.java:156` | 需 Redis 写权限（dev 无密码：`application-dev.yml:24`） |
| P1-22 | gRPC 健康检查无 deadline | `HermesGrpcClient.java:103-113` | Hermes 半死时线程永久阻塞 |
| P1-23 | 虚拟线程未启用，Tomcat 800 线程 / min-spare 100 | `application.yml:48-52`（注释）、`:42-46` | SSE 长连接下线程被大量占用；800 平台线程 + 无背压 |
| P1-24 | 连接池与线程极不匹配 | `application-prod.yml:48` `maxActive: 20`、`:50` `maxWait: 60000` vs Tomcat 800 | 数据库成为硬瓶颈，60s 等待放大尾延迟 |
| P1-25 | 测试证据与 CI 声明不符 | 349 文件/114,863 行；最新 surefire 仅 41 份报告/204 用例（其余 281 个测试类无本次运行证据）；CI 文案称「1258+ 集成单测」「47 项契约」（`.github/workflows/ci.yml:235-236`） | 质量门禁不可信 |
| P1-26 | 伪测试 / 无效覆盖 | `tests/src/test/java/tech/qiantong/qknow/mybatis/DataScopeInterceptorTest.java`（测的是**未注册**的拦截器）；7 个用例被 surefire 永久排除（`tests/pom.xml:167-175`）；21 个 `@Disabled`、14 处 `assume*` | 「绿」不代表生产路径被覆盖 |

### 3.3 P2（治理 / 一致性 / 可维护性）

| ID | 缺陷 | 证据 |
| --- | --- | --- |
| P2-1 | 双 Dockerfile 端口/运行路径分叉（8090 vs 8099，root vs 非 root） | `backend/Dockerfile:40`；`qknow-server/docker/Dockerfile:49-51` |
| P2-2 | Maven 实际使用「旧 Dockerfile」，淘汰中的镜像仍在仓库 | `qknow-server/pom.xml:239,284,310` |
| P2-3 | Compose 环境变量与配置键不匹配（`DEEPSEEK_API_KEY`/`DASHSCOPE_API_KEY`/`POSTGRESQL_URL` 无人读取） | `deploy/docker/docker-compose-qknow.yml:24,38-40`；`application-prod.yml:94` |
| P2-4 | 配置多副本漂移（`deploy/docker/qknow-server/*.yml` 已过时） | `diff` 实测缺 `qknow.rag.crag`、`hermes.rag.*` |
| P2-5 | 双 Nginx 配置 SSE 策略不一致（仅部分路径关缓冲） | `deploy/docker/nginx.conf:12-30` vs `deploy/docker/nginx/sites/qknow.conf:27-48` |
| P2-6 | `/v1/models` 宣告已退役模型名 | `AiModelGatewayController.java:56-60` |
| P2-7 | 模型解析无 allowlist，可直通任意模型名 | `ModelNameResolver.java:77-79` |
| P2-8 | Knife4j `production:false` + `enable-debug:true` | `application.yml:260,264` |
| P2-9 | `--enable-preview` 绑定 JDK 补丁版本 | `backend/pom.xml:246`；`qknow-server/docker/Dockerfile:51` |
| P2-10 | `EmbeddedRedisConfig` dev 分支监听 12138，而 Redis 配 6379 → 死逻辑 | `EmbeddedRedisConfig.java:33,38`；`application-dev.yml:18-22` |
| P2-11 | `universalPassword` 万能密码开关（空则失效） | `application.yml:68-69` |
| P2-12 | 明文默认口令 6 处 | `application-prod.yml:96-109,118-120`；`application-dev.yml:102-104`；`application.yml:267-268`；`qknow-hermes-starter/.../application.yml:15-16` |
| P2-13 | jjwt 子模块属性被父 POM 覆盖（0.12.6 → 实际 0.9.1） | `qknow-common/pom.xml:22` vs `backend/pom.xml:34,151-155` |
| P2-14 | MyBatis MappedStatement 重复定义 | `backend/logs/error.2026-07-05.log`（`ExtUnstructTaskMapper.updateExtUnstructTask`） |
| P2-15 | 启动期 Bean 冲突 6 个 | `backend/qknow-server/logs/error*.log`（`mcpToolAdapter`/`nodeFactory` 等） |
| P2-16 | iText 5.5.13（AGPL/商业双许可） | `qknow-common/pom.xml:263-267` |
| P2-17 | `embedded-redis-1.4.3` 与 `org.jacoco.agent-0.8.5` 在生产包 | jar 实测 |
| P2-18 | `DagExecutor` 每个实例自建 `newFixedThreadPool(min(cpu,8))`，无生命周期管理 | `qknow-module-kb/.../flow/dag/DagExecutor.java:26-33` |
| P2-19 | `paginationInnerInterceptor.setMaxLimit(-1L)` 单页不限量 | `MybatisPlusConfig.java:43` |
| P2-20 | `Ehcache` 仅堆内 1000 条/缓存，无 offheap/disk，AI 缓存易失效 | `qknow-server/src/main/resources/ehcache.xml:11-24` |
| P2-21 | 日志目录为相对路径 `logs`，maxHistory 15（注释写 60） | `logback.xml:4,20-21` |
| P2-22 | 工作日志与仓库日志目录双份（`backend/logs`、`backend/qknow-server/logs`、根 `logs/`） | 目录实测 |
| P2-23 | `AiModelAliasProperties` 硬编码定价（`100L/500L/2000L`）与别名表 | `AiModelAliasProperties.java:35-59` |
| P2-24 | `EmbeddingServiceImpl` 缓存键用 `apiKey.hashCode()`，且 ConcurrentHashMap 永不淘汰（密钥常驻堆） | `qknow-framework/qknow-ai/.../EmbeddingServiceImpl.java:34,60-64` |

> 已核对的正向实践（应在重构中保留）：常量时间比较（`FlyFlowHmacAuthenticationFilter.java:101`、`ModelResolutionReceipt.java:59`）；多库检索虚拟线程 + 单库/全局双超时（`MultiKnowledgeConcurrentRetriever.java:126,138,159-165`）；LangFuse 有界队列 + 守护线程 + Fail-Open（`LangFuseTracingService.java:33,47,62-67`）；`EvalAsyncConfig` 有界队列 + 拒绝策略（`EvalAsyncConfig.java:15-22`）；终态单次写库替代逐 token 写库（`KbConversationController.java:145-146,176`）；Docker 多阶段 + 非 root + cgroup 感知（`backend/Dockerfile:5,28,36-38,44-48`）；PgVector HNSW 参数显式化（`qknow-hermes-starter/.../application.yml:31-34`）。

---

## 4 优化建议

### 4.1 依赖收敛（P0/P1 一并解决，收益最大）

**最小改动、可分批**：

1. **切断测试依赖泄漏（1 行）**：`qknow-framework/qknow-common/pom.xml:163-166` 加 `<scope>test</scope>`；`backend/qknow-server/pom.xml:119-123` 的 `embedded-redis` 同样加 `<scope>test</scope>`（`EmbeddedRedisConfig` 仅 dev 用，应移入 `qknow-server/src/test` 或改为 `@Profile("dev")` 的可选模块）。
2. **移除 Web 应用不需要的重量级依赖**（`qknow-common/pom.xml`）：Hadoop 3 件（`:313-352`）、Tika 全 parser（`:410-428`，仅保留 `tika-core` + 需要格式）、ES 服务端 `elasticsearch-7.17.20`（`:207-217`，仅保留 rest-client）、Flexmark 全家桶改为 `flexmark` + 2 个扩展（`:430-435`）、6 套 JDBC 驱动改为「按 profile 引入」。预计 fat jar 从 435 MiB 降至 <120 MiB，启动扫描类数下降一个量级。
3. **建立 `dependencyManagement` 单一裁决点**：把 `jackson-annotations` 改回与 Boot 3.5.8 对齐的 2.19.x（`backend/pom.xml:28`），把 `protobuf-java` 提升到 `proto-google-common-protos` 要求的 4.x（`qknow-server/pom.xml:83-88`），把 Spring AI 组件与 `spring-ai-alibaba 1.1.2.2` 统一到 1.1.2（`qknow-framework/qknow-ai/pom.xml` 的 BOM 改为 1.1.2 或显式 import 1.1.2），把 MyBatis-Plus 全部锁到 3.5.16，把 POI 全部锁到 5.3.0，把 hutool 锁到单一版本。
4. **加门禁而非加人肉检查**：在 `backend/pom.xml` 加 `maven-enforcer-plugin` 的 `dependencyConvergence`（先 `fail=false` 只告警）+ 一个 `scripts/check-deps.sh` 断言 `BOOT-INF/lib` 中不存在 `junit/mockito/assertj/jacoco/embedded-redis` 与重复 `artifactId-不同版本`。这比人工 review 便宜一个数量级。
5. **License**：`itextpdf` 必须由 Lead/法务决策（替换为 `openhtmltopdf` + PDFBox 或购买商业许可）。在决策前，建议在 CI 增加 `license-maven-plugin` 黑名单断言（AGPL/GPL 出现即 fail），把风险前移到提交时。

### 4.2 控制面链路（按 ROI 排序）

1. **SSE 正确性三处最小改**（每处 <20 行）：
   - Sink 改为 `Sinks.many().unicast().onBackpressureBuffer(1024)` 或 `Sinks.many().replay().limit(128)`，并在 `tryEmitNext` 返回值非 OK 时 `log.warn` + 计数（`KbConversationController.java:123,148`）；
   - 把 `agentConfigService.chatMessage(...)` 的订阅改为 `sink.asFlux().doOnSubscribe(...)` 里触发，或直接 `chatMessage(...).doOnNext(...).subscribe(sink::tryEmitNext, sink::tryEmitError, sink::tryEmitComplete)` 并把返回的 `Disposable` 存进 `AtomicReference`，`doOnCancel/dispose` 时 `dispose()` 上游（解决 P0-6 与 P1-1）；
   - 显式设置 SSE 超时：`WebMvcConfigurer.configureAsyncSupport(configurer -> configurer.setDefaultTimeout(600_000))`，并对齐 `proxy_read_timeout 600s`。
2. **断点续传要么接通要么删除**：前端接入 `Last-Event-ID` + `/kb/conversation/replay`（后端已就绪），或删除 `SseReplayWindowBuffer` 与 `sequenceId`。当前状态是「两不得」——付出成本零收益。若保留，必须加 `@Scheduled(fixedDelay=60s) cleanExpiredSessions()`（当前 `cleanExpiredSessions` 无任何调用方）。
3. **预检索并发化 + 可观测**：把单库 `forEach` 改为复用 `MultiKnowledgeConcurrentRetriever`（已是虚拟线程 + 双超时实现），并给 `RagFallbackMonitor` 增加「检索失败率」指标；失败时向 SSE 推一帧显式降级事件，让前端可提示。
4. **背压与限流**：`HermesGrpcClient` 改 `call.request(N)` 按批请求；`@RateLimiter` 至少套在 `/login`、`/kb/conversation/send`、`/v1/chat/completions`、`/v1/embeddings` 四处（注解与切面已存在，改动仅 4 行）。
5. **鉴权收口**：`SecurityConfig` 把 `/v3/api-docs/**`、`/swagger-ui.html` 用 profile 条件控制在 dev；删除 `/api/app/**` 整体放行改为按注解；给 `/internal/api/**` 加 `hasAuthority('ROLE_INTERNAL_SYSTEM')` 并复用 `FlyFlowHmacAuthenticationFilter` 的 HMAC 机制（密钥走 `${FLYFLOW_HMAC_SECRET}`，删除 `SecurityConfig.java:155` 的字面量）。
6. **租户落实**：把 `BaseController.getWorkSpaceId()` 改为从 `LoginUser` 读取；引入 MyBatis-Plus `TenantLineInnerInterceptor` 并在 `KbChatMessageDO`/`KbConversationDO`/`KbAgentConfigDO` 等表上强制 `workspace_id`；`getMessagesByConversationId` 增加 `workspaceId + userId` 条件。这是「多租户」从宣称到实现的唯一路径。

### 4.3 配置与密钥

1. **单一路径**：引入 `spring.config.import: optional:file:.env[.properties]`（Spring Boot 3 原生支持）或统一由 `scripts/start.sh`/compose 注入，禁止两套语义。
2. **无默认值原则**：删除 `application-prod.yml:96-109,118-120` 中的全部弱默认口令，改为 `${POSTGRESQL_PASSWORD}` 无默认值、缺失即失败（对齐 `NEO4J_PASSWORD` 的写法）。同理删除 `qknow-hermes-starter/.../application.yml:15-16` 的 DB 弱默认口令。
3. **修 compose**：补 `TOKEN_SECRET`、把 `DEEPSEEK_API_KEY/DASHSCOPE_API_KEY` 改为 `HERMES_OPENAI_API_KEY/TONGYI_API_KEY`，或反向在 yml 里支持 `DEEPSEEK_API_KEY`（推荐后者：变量名与业务语义一致）。
4. **prod 覆盖日志级别**：在 `application-prod.yml` 增加 `logging.level.tech.qiantong: info`、`org.springframework.ai: warn`。这一条可立即降低日志量并减少 PII 落盘。
5. **API Key 加密**：`AiApiKeyDO.apiKey` 加 `@TableField(typeHandler = EncryptTypeHandler.class)`（TypeHandler 已实现，`EncryptTypeHandler.java:21`），密钥走 `mybatis-plus.encryptor.password` 环境变量；`AiApiKeyRespVO` 去掉 `apiKey` 明文与 `@Excel`。改动 <10 行，消掉一个 P0。

### 4.4 启动时长与构建治理

- 现状：成功启动 **10.454s**（Java 17 记录），最近 Java 21 启动 3s 即失败（缺密钥）。10s 对 435 MiB 的 fat jar 属正常，真正可优化的是 **classpath 规模**——按 4.1 瘦身后预计 45s→**5-7s**（类扫描数下降）。
- 【推断】可开启 JDK 21 Class Data Sharing（`-XX:SharedArchiveFile` + `-Xshare:auto`）再省 15-25%；但 `--enable-preview` 会增加 CDS 失效概率，二者需一起评估。
- 构建可复现性：`--enable-preview` + 浮动 `latest` 基础镜像（`eclipse-temurin:21-jre-jammy`）+ 阿里云镜像源（`backend/pom.xml:262-269`）三者叠加，产物**不保证可复现**。建议：固定 `eclipse-temurin:21.0.5_11-jre-jammy` 摘要、锁定 Maven 3.9.x 具体版本、把 `.mvn/maven.config` 纳入仓库。
- CI 缺口：`ci.yml` 只跑 `-pl tests -am test` 与 `-pl qknow-hermes-core -am test`，**没有** `mvn verify`、没有依赖收敛门禁、没有 license 门禁、没有镜像构建、没有 `-DskipTests` 全量打包验证（即 P0-2/P2-3 这类编排错误永远不会被 CI 发现）。建议至少补一条 `mvn -B -ntp -f backend/pom.xml -DskipTests package` 的 smoke job（约 2-4 分钟），专门抓 reactor/编排断裂。

### 4.5 可观测性（最小可用集）

1. **指标**：在 4 个位置加 Micrometer（每处 2-3 行）：SSE 活跃连接数（`Gauge`）、首字延迟 `Timer`、RAG 检索耗时/失败率、gRPC 调用耗时与错误码。控制面当前 `MeterRegistry` **0 使用**，这是「有 Prometheus 端点但没有业务指标」的典型空转。
2. **日志**：`logback.xml` 合并两个 `<root>`（`:94-102`）、修 `onMismatch=ACCEPT`（`:32`）、pattern 加 `%X{traceId}`（`:6`）、接入 JSON encoder（logstash-encoder）供采集。这 4 项都是纯配置改动。
3. **Trace**：LangFuse 目前只在 Hermes 且默认关（`LANGFUSE_ENABLED:false`）。若要端到端 trace，需在控制面接 OpenTelemetry（fat jar 中已有 `opentelemetry-api-1.49.0`，说明依赖已在，只是没接线）。
4. **告警**：当前无任何告警资产（全仓无 Prometheus rules/Alertmanager 配置）。最低要求：SSE 错误率、gRPC 失败率、RAG 检索失败率、DeepSeek 429/5xx 率、Druid 连接池饱和（`maxActive 20` 极易打满）5 条。

### 4.6 测试与 CI 治理

- 349 文件 / 114,863 行的测试体量本身不是问题，问题是**证据链断裂**：最近一次 surefire 只落了 41 份报告/204 用例，而测试模块有 322 个 `*Test.java`。建议：
  1. CI 输出「期望用例数 vs 实跑用例数」并断言 >95%，否则 fail（防「只跑一部分还绿」）；
  2. 把 7 个被 `tests/pom.xml:167-175` 永久排除的用例改为 `@Tag("requires-docker")` + profile 按需跑，而不是静默消失；
  3. 删除/重写 `DataScopeInterceptorTest`（测未注册组件）与 21 个 `@Disabled` 中已失效的用例；
  4. 补 4 个最缺的集成/契约测试：① SSE 缓存命中不丢帧；② 客户端断开后上游被 dispose（可用 mock Flux 校验 `dispose` 调用）；③ 租户越权（A 用户读 B 会话应 403）；④ 生产配置可启动（`ApplicationContextRunner` + 最小 env，专门抓 `TOKEN_SECRET` 这类缺失）。
- 【已验证】CI 声明与事实不符：`ci.yml:235-236` 的 Step Summary 写死「1258+ 集成单测」「47 项契约」，但实测 hermes-core 只有 5 个报告/21 个用例。**这类写死文案应当删除**，否则质量门禁的可信度被文案自身摧毁。

---

## 5 与顶级 Agent 平台控制面的差距

| 能力维度 | 行业头部实践（如 OpenAI Assistants / Azure AI Foundry / Dify Pro / LangSmith） | 本项目现状 | 差距评级 |
| --- | --- | --- | --- |
| 多租户 | 租户 ID 进 JWT claim，SQL 层强制行级隔离，跨租户查询需显式提权 | `getWorkSpaceId()` 恒 1001；无租户拦截器；会话查询无归属校验 | **严重（P0）** |
| 配额与计费 | 按租户/按 Key 的 RPM/TPM 配额、用量计量、超额 429 + 账单 | 无配额、无限流（`@RateLimiter` 0 使用）、无用量表、仅有 `DeepSeekCostGovernor` 在最末端的估算 | **严重（P0）** |
| 审计 | 全量操作审计（谁/何时/对何资源/何结果），不可篡改 | 有 `@Log` 268 处 + `SysOperLog` 异步落库；但 SSE 主链路无审计、审计写入用 `CallerRunsPolicy` 可能丢 | 中 |
| 灰度 / 版本化 | Prompt/模型/工作流版本化 + 按流量比例灰度 + 秒级回滚 | `prompt-version: manual-v1` 只是一个字符串（`application.yml:132`）；无模型/工作流版本表、无灰度机制 | **严重** |
| 回滚 | 配置/模型/Prompt 三件套原子回滚 | 无（配置靠 Git + 重启；模型别名在内存 Map 里改需重启） | 高 |
| 可观测性 | Trace 贯穿（OTel/ Langfuse）+ 成本/质量指标 + 告警 | Prometheus 端点空转；LangFuse 默认关且只在 Hermes；日志非结构化无 traceId | 高 |
| 密钥治理 | KMS/Secrets Manager + 短期凭据 + 轮换 | Env 变量 + 明文落库 + 源码硬编码 | **严重（P0）** |
| 数据隔离 | PG 行级安全（RLS）/ 独立 schema / 独立库 | 无 | 高 |
| 失败模式 | 明确的 fail-open/fail-close 契约 + 降级优先级 | RAG 失败静默 fail-open，但 Hermes 不可用直接 500，两者不一致 | 中高 |
| 发布工程 | 单 Dockerfile、可复现构建、镜像签名、金丝雀 | 双 Dockerfile 端口不一致；`--enable-preview`；无镜像签名；无金丝雀 | 高 |
| 无服务器/弹性 | SSE 连接数与上游并发解耦（网关层背压） | 全部压在 Tomcat 800 线程 + 无界缓冲 | 高 |

**结论（平台成熟度）**：项目在**算法与 RAG 编排层**的复杂度与深度明显高于业界平均水平（多库 RRF、CRAG、CoLBERT、HNSW 调参、虚拟线程并发召回都在），但在**平台控制面**这一层仍处于「单体 Spring Boot + 手工脚本 + 环境变量」阶段，与顶级 agent 平台的核心差距集中在四个字：**租户、配额、灰度、密钥**。这四项都不是算法问题，而是工程治理问题，且都有低风险的最小落地路径（见第 4 章）。

---

## 6 未验证与需 Lead 决策事项

### 6.1 未能验证（受只读 / 不启动约束）

| 事项 | 原因 | 建议验证方式 |
| --- | --- | --- |
| 语义缓存命中是否真的返回空 body | 需运行服务 + 命中缓存 | 单元测试 `StepVerifier` 直接对 `KbConversationController.sendMessage` 打桩 `chatMessage` 返回 `Flux.just`，断言 SSE 帧非空 |
| protobuf 3.25.1 与 proto-google-common-protos 2.64.1 的实际冲突 | 需触发 A2A/MCP 代码路径 | 一次 MCP 工具调用即可暴露；或用 `jdeps`/`-Xlog:class+load` 观察 |
| SSE 是否真的被容器 30s 切断 | 需长回答实测 | 用 thinking 模式提问 60s 以上，看 `AsyncRequestTimeoutException` |
| `--enable-preview` 产物在其他 JDK 21 补丁版的行为 | 需跨 JDK 环境 | 仅允许同一 21.0.5-tem 镜像 |
| Nginx 两份配置生产实际挂载哪一份 | 需看部署机 | 检查容器内 `/etc/nginx/conf.d` 与 `sites-enabled` |
| readiness/liveness 探针是否真的可用 | 需 curl | `curl /actuator/health/readiness` |
| 349 个测试的真实通过率 | 禁止跑长测试 | CI 全量 job + 报告归档 |
| Tomcat 800 线程与 SSE 并发上限的真实拐点 | 需压测 | 已有 `scripts/stress_test_e2e.py`，需授权执行 |

### 6.2 需 Lead 决策

1. **iText 5 AGPL 处置**：替换实现（推荐，Apache-2.0 组合已具备：`openhtmltopdf` + `pdfbox` 均在包内）还是采购商业许可？这是唯一的**法律级阻断项**。
2. **Hadoop / Tika / Lucene / ES 服务端依赖是否真被生产路径使用**：如果是历史遗留（很可能），直接删除；如果确实被非结构化抽取用到，需要拆成独立 worker 服务，而不是塞进控制面 fat jar。**这会决定 4.1 的瘦身幅度（435 MiB → 120 MiB 或 → 200 MiB）。**
3. **Hermes 的部署形态**：确认是「独立微服务 + compose 编排」还是「并入 qknow-server 单进程」。当前代码两者都不成立（有独立启动类但无部署物；控制面依赖其 classpath 又排除其包扫描）。这决定 P0-2 的修复方向。
4. **多租户是否在近期路线图**：若否，应显式删除 `getWorkSpaceId()` 桩与 `workspaceId` 字段的语义，避免「假隔离」误导后续开发者；若是，需一次 schema 变更窗口 + MyBatis 租户插件接入。
5. **`--enable-preview` 是否必须**：若代码并未真正使用 preview 特性，应移除——它把产物绑死在 21.0.5，是所有升级路径的地雷。
6. **`universalPassword`（万能密码）是否已在生产关闭**：`application.yml:68-69` 为空即失效，但需要确认生产环境变量未注入；建议改为启动时若非 dev 且非空则直接 `fail-fast`。

### 6.3 审计纪律声明

- 本次审计**未修改**任何源码、配置、SQL、fixture；唯一写入为 `docs/architecture-review-20261019/05-platform-infra-controlplane.md`。
- **未执行** `mvn`、`gradle`、测试、服务启动；所有运行证据来自既有日志与既有 `target/` 产物。
- 报告中出现的所有凭据均以 `<REDACTED>` 或 `****` 掩码；仅给出文件:行号与变量名。
- `git status` 中已存在的 9 个 modified + 若干 untracked 文件**非本次审计产生**（审计开始时即存在），未做任何提交。

---

### 附录 证据索引

#### A. POM 与依赖

| 证据 | 位置 |
| --- | --- |
| Java 21 / release / preview | `backend/pom.xml:18,220-249,246` |
| Spring 版本矩阵 | `backend/pom.xml:20-21,55-77` |
| gRPC BOM | `backend/pom.xml:37,79-85` |
| jjwt 0.9.1 + 子模块属性失效 | `backend/pom.xml:34,151-155`；`qknow-framework/qknow-common/pom.xml:22,127-130` |
| jackson-annotations 2.21 | `backend/pom.xml:28,86-90` |
| protobuf 3.25.1 强降 | `backend/qknow-server/pom.xml:83-88` |
| starter-test 无 scope | `qknow-framework/qknow-common/pom.xml:163-166` |
| embedded-redis 无 scope | `backend/qknow-server/pom.xml:119-123` |
| hadoop 引入 | `qknow-framework/qknow-common/pom.xml:313-352` |
| tika 全 parser | `qknow-framework/qknow-common/pom.xml:410-428` |
| itextpdf 5.5.13 | `qknow-framework/qknow-common/pom.xml:263-267` |
| ES 7.17.20 客户端+服务端 | `qknow-framework/qknow-common/pom.xml:196-217` |
| Spring AI BOM 1.1.0 + ollama/openai/deepseek | `qknow-framework/qknow-ai/pom.xml`（`dependencyManagement` 与 deps 段） |
| fat jar 589 jar / 435 MiB | `backend/qknow-server/target/qknow-server.jar`（`unzip -l` 实测） |
| surefire 排除清单 | `backend/tests/pom.xml:157-179` |

#### B. 安全与控制面

| 证据 | 位置 |
| --- | --- |
| 硬编码 HMAC 密钥 | `qknow-framework/qknow-security/.../config/SecurityConfig.java:155`；`.../filter/FlyFlowHmacAuthenticationFilter.java:36` |
| permitAll 面 | `SecurityConfig.java:116-150`（`/api/app/**`→143；swagger→135-144；websocket→140） |
| ASYNC 鉴权补丁 | `SecurityConfig.java:145-146`；`JwtAuthenticationTokenFilter.java:49-52` |
| Token/Redis 会话 | `TokenService.java:76-95,126-152,189-196`；`application.yml:126-127` |
| 工作区常量桩 | `qknow-framework/qknow-common/.../BaseController.java:215-217` |
| 无租户拦截器 | `qknow-framework/qknow-mybatis/.../config/MybatisPlusConfig.java:21-31` |
| 未注册的 DataScopeInterceptor | `qknow-framework/qknow-mybatis/.../interceptor/DataScopeInterceptor.java:19-22`（仅测试引用） |
| 会话无归属校验 | `qknow-module-kb/.../conversation/impl/KbChatMessageServiceImpl.java:20-25` |
| 内部 API 越权面 | `qknow-module-kb/.../controller/admin/KbInternalApiController.java:14-46` |
| 明文 API Key 出库 | `qknow-module-ai/.../dal/dataobject/modelMarket/AiApiKeyDO.java:40`；`.../vo/AiApiKeyRespVO.java:37` |
| API Key 过 gRPC | `qknow-module-kb/.../agent/impl/KbAgentConfigServiceImpl.java:407` |
| gRPC 明文 | `qknow-module-kb/.../agent/HermesGrpcClient.java:43-47` |
| Quartz 反射白名单 | `qknow-framework/qknow-common/.../constant/Constants.java:166`；`qknow-framework/qknow-quartz/.../util/JobInvokeUtil.java:23-40` |
| Redis autoType | `qknow-framework/qknow-redis/.../config/FastJson2JsonRedisSerializer.java:22,31,46`；`.../constant/Constants.java:156` |
| 限流未接线 | `RateLimiterAspect` 存在；`grep "@RateLimiter"` = 0 |
| kb 模块授权覆盖 | `KbFlowController.java`（0 个 `@PreAuthorize`）；统计 37/76 |
| 退役模型名 | `qknow-framework/qknow-ai/.../gateway/controller/AiModelGatewayController.java:56-60` |
| 模型解析直通 | `qknow-hermes/.../config/ModelNameResolver.java:77-79` |

#### C. 运行时与配置

| 证据 | 位置 |
| --- | --- |
| 端口/线程/虚拟线程 | `application.yml:31-46,48-52` |
| 生产 debug 日志 | `application.yml:55-58`；`application-prod.yml`（无 logging 节） |
| 3000MB 上传 | `application.yml:98-104` |
| Knife4j 默认口令 | `application.yml:265-268` |
| Actuator 暴露面 | `application.yml:270-284` |
| 生产 Druid 池 | `application-prod.yml:41-74` |
| 生产弱口令默认值 | `application-prod.yml:96-98,107-109,118-120` |
| dev Redis 空密码 | `application-dev.yml:18-36`（`:24`） |
| Hermes gRPC 目标 | `application-prod.yml:149-153` |
| Hermes 自身配置（含默认 DB 口令） | `qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml:1-33,84-89` |
| logback 双 root | `backend/qknow-server/src/main/resources/logback.xml:94-102`；`LevelFilter onMismatch=ACCEPT`→`:26-33` |
| 启动成功/失败实证 | `backend/qknow-server/logs/info.log`（10.454s）；`backend/logs/info.log:22-23`；`backend/logs/error.log:84` |
| ConflictingBeanDefinition / 重复 MappedStatement | `backend/qknow-server/logs/error.2026-07-*.log` |
| 双 Dockerfile 端口 | `backend/Dockerfile:40`；`backend/qknow-server/docker/Dockerfile:49-51` |
| 生产 compose 缺 TOKEN_SECRET | `deploy/docker/docker-compose-qknow.yml:12-40` |
| Nginx SSE 策略分叉 | `deploy/docker/nginx.conf:12-30`；`deploy/docker/nginx/sites/qknow.conf:27-48` |
| 配置副本漂移 | `deploy/docker/qknow-server/application*.yml`（`diff` 实测） |
| CI 声明与事实 | `.github/workflows/ci.yml:53-85,235-236` |
| 智能体启动隔离 | `QKnowApplication.java:19-29`（排除 hermes 包扫描） |
| Hermes 独立启动类/无部署物 | `qknow-hermes/qknow-hermes-starter/.../HermesApplication.java`；全仓 compose 无 hermes |
| 启动脚本加载 .env | `scripts/start.sh:14-19` |

#### D. 控制面链路

| 证据 | 位置 |
| --- | --- |
| SSE 入口与 Sink 竞态 | `KbConversationController.java:81-82,123,125-149,181` |
| 会话历史全量注入 | `KbConversationController.java:84,114`；`KbAgentConfigServiceImpl.java:444-455` |
| 「思考中」先落库 | `KbConversationController.java:87-88` |
| 语义缓存命中路径 | `KbAgentConfigServiceImpl.java:257-261,525-540` |
| 单库串行预检索 | `KbAgentConfigServiceImpl.java:315-357` |
| 预检索失败静默 | `KbAgentConfigServiceImpl.java:355-362` |
| 多库并发 + 双超时 | `MultiKnowledgeConcurrentRetriever.java:126,130-152,159-165` |
| commonPool 阻塞风险 | `MultiKnowledgeConcurrentRetriever.java:130-152`（`runAsync` 无执行器 + `get(timeout)`） |
| gRPC request 构造（含 apiKey） | `KbAgentConfigServiceImpl.java:393-412`（apiKey→407） |
| gRPC 客户端 | `HermesGrpcClient.java:41-61,63-101,103-113` |
| 缓存写入判定/异步 | `KbAgentConfigServiceImpl.java:450-471,493-509` |
| SSE 重发缓冲 | `SseReplayWindowBuffer.java:23-24,109,114-120,148-167` |
| 前端 SSE 契约 | `frontend/src/api/kb/conversation/index.js:43-60`；`frontend/src/views/kb/agent/index.vue:705-760` |
| 前端无 replay 调用 | 全前端 `grep "replay|lastEventId|Last-Event-ID|sequenceId"` = 0 |
| 工作流 SSE 端点 | `KbFlowController.java:53,74,100` |
| DagExecutor 线程池 | `qknow-module-kb/.../flow/dag/DagExecutor.java:26-33` |

#### E. 可观测性与测试

| 证据 | 位置 |
| --- | --- |
| LangFuse 有界队列（正向） | `qknow-hermes/.../observability/LangFuseTracingService.java:33,47,62-67` |
| LangFuse 默认关闭 | `qknow-hermes-starter/src/main/resources/application.yml:84-89` |
| 控制面无业务指标 | `MeterRegistry` 仅 Hermes `DeepSeekCostGovernor` + 1 测试 |
| 测试规模 | `backend/tests/src/test`：348 文件 / 114,863 行 / 2,024 个 `@Test` |
| 有效测试证据 | 4 处 surefire 报告聚合：48 文件 / 232 用例 / 0 失败 / 10.5s（最近一次为子集运行） |
| 伪测试 | `backend/tests/src/test/java/tech/qiantong/qknow/mybatis/DataScopeInterceptorTest.java` |
| 排除与禁用 | `backend/tests/pom.xml:167-175`；21 个 `@Disabled`、14 处 `assume*` |
| 异步编排（正向） | `qknow-framework/qknow-config/.../ThreadPoolConfig.java:32-62`；`qknow-module-kb/.../config/EvalAsyncConfig.java:13-23` |

---

**报告结束。** 本报告共列出 P0 6 项、P1 26 项、P2 24 项；正向实践 8 项；未验证事项 8 项；待 Lead 决策 6 项。
