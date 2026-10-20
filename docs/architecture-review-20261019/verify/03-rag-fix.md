# RAG 修复复核报告（3898471d + 工作区未提交改动）

- 复核对象：提交 `3898471d`（`fix(rag): 多库并发安全上下文透传与超球面几何守卫`），以**当前工作区真实代码**（`HEAD = cb6e185c` + 8 个未提交改动）为最终裁决对象
- 复核员：rag-engine-auditor（原 `03-rag-knowledge-engine.md` 审计员）
- 复核日期：2026-10-20
- 方法：**只读静态复核**。`git show 3898471d` 全文、`git diff` 工作区 8 项改动、逐文件读取最终工作区代码、交叉核对既有测试与 Spring AI 1.1.0 二进制签名。**未执行 mvn、未跑测试、未启动服务**（Lead 已统一编译通过；编译通过不代表单测/运行时正确）
- 判定口径：**已修复 / 部分修复 / 未修复 / 新增回归 / 无法静态判定**
- 证据等级：**已验证**=源码行或二进制直接证明；**推断**=多行逻辑串联、未运行验证；**未知**=需运行时确认

## 0 结论速览（先看五条）

| 编号 | 复核项 | 工作区最终判定 | 一句话结论 |
|---|---|---|---|
| P0-13 | SecurityContext 丢失 / 权限上下文透传 | **部分修复** | 只修好了"聊天主链路的多库并发"这一段；userId 未显式贯穿，fail-open 仍原样保留，DAG/虚拟线程/内部 API/legacy 子线程四条路径仍无权限 |
| P0-14 | 跨库分数门禁 | **新增回归（方案不正确）** | 工作区把提交版 `scoreFloor=0.0` 改回 `0.40` 并加 `score > 0.1` 魔数，制造了一个 0.1~0.4 的"死区"：多库数量 7~24 的知识库会**丢掉 RRF 最高的共识切片**，且分数越低反而越可能存活 |
| P0-04 | 1536 维 / 单位范数强制 | **部分修复** | 只在 `TongYi` 的 Embedding 请求里加了 `dimensions(1536)`；`EmbeddingDimensionGuard` 仍无生产调用、仍不校验写入/检索路径；范数容差 0.05 与项目自身 1e-4 规范冲突 |
| P0-15 | 语义缓存键权限/版本 | **未修复（仅做了缓存卫生）** | 键仍是 workspace+bot+kbHash+model，无权限/版本；`permissionHash` 仍未接线；只是增加了手动清缓存时同步驱逐和收窄 `isFailedAnswer` |
| — | 接口签名/编译风险 | **无破坏性变更** | 7 个文件均为内部实现修改，无 public 接口签名变化；但 `Phase17` 测试仍按旧"原始分门禁"断言，与生产 RRF 语义冲突，且本次修复**未新增任何测试** |

> 最重要的两条：**P0-14 在工作区是一个可复现的逻辑回归**（不需要运行即可用算术证明），**P0-13 是一次"窄修复"**（只让主聊天路径恢复权限，未形成全链路 fail-closed）。下面逐项给证据。

---

## 1 P0-13 SecurityContext 丢失 —— **部分修复**

### 1.1 提交做了什么（已验证）

`3898471d` 在 `MultiKbRetrievalCoordinator` 的每个异步任务里显式绑定父线程 `SecurityContext`，任务结束 `finally` 清除：

- `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/retrieval/MultiKbRetrievalCoordinator.java:87`：捕获父 `SecurityContext`
- 同文件 `:94-102`：`createEmptyContext()` → 复制 `Authentication` → `setContext(childContext)` → `finally clearContext()`
- 同文件 `:104`：任务提交到 `multiKbExecutor`（构造器 `@Qualifier("threadPoolTaskExecutor")`，`:47-51`）

**这个修复对"机器人聊天主链路"是有效的（已验证调用时序）**：`KbConversationController.sendMessage` 在请求线程上**直接调用** `agentConfigService.chatMessage(agentReq)`（`backend/qknow-module-kb/.../controller/admin/conversation/KbConversationController.java:125`），而 `chatMessage` 方法体（含 `KBCFG:278` 的 `multiKbCoordinator.coordinateRetrieval`）是**急切执行**的（`KBCFG:226`，方法返回 `Flux` 但预检索不在 `Flux.defer` 内），因此协调器捕获时 JWT 过滤器设置的 `SecurityContext` 确实存在。→ 多库并发子线程内的 `recallTest` 能拿到用户身份，KB 级权限过滤恢复生效，同时 `recallTest` 入口权限检查（`KMCBASE:325-333`）先于缓存查询执行，**顺带缓解了主链路上"缓存先于权限"的问题**。

### 1.2 必查点逐项裁决

| 必查项 | 结论 | 证据 |
|---|---|---|
| userId 是否**显式**贯穿 MultiKbRetrievalCoordinator→recallTest→RagRetrievalService→permissionFilter | **未修复**：没有 userId 参数；仍是 `SecurityUtils.getUserId()` ThreadLocal | `IKmcApiService.java:62-67`（`recallTest(Long knowledgeId, String query[, history])` 签名未变）；`RagRetrievalService.java:132`、`:235`；`KMCBASE.java:327`、`:472`、`:727`、`:782` |
| legacy catch 跳过权限是否改为 fail-closed | **未修复**：仍是 catch 后跳过 | `KMCBASE:325-333`（`:332` "跳过权限检查"）、`:470-479`、`:726-734`（注释"无用户上下文时跳过权限过滤（如 Agent 对话）"）、`:780-787` |
| v2→legacy 静默回退是否删除 | **未修复**：catch 仍在 | `KMCBASE:388-413`（`:412` "RAG v2 retrieval failed, falling back to legacy pipeline"） |
| RagRetrievalService 自身权限检查 | **部分修复但方向错误**：把原来会抛出的 `SecurityUtils.getUserId()` 改成 `try/catch` 静默吞掉；拿不到 userId 就跳过权限（fail-open） | `RagRetrievalService.java:128-146`（`:132-135`）、`:231-251`（`:235-238`） |
| 是否还有异步线程/虚拟线程 SecurityUtils 依赖 | **有，且至少 4 条**：①kb DAG 虚拟线程；②`MultiKnowledgeConcurrentRetriever` 虚拟线程；③内部 HTTP 召回线程；④legacy `hybridSearch` 的 `ext-task-thread-*` 固定池 | ①`KbFlowServiceImpl.java:77`（`newVirtualThreadPerTaskExecutor`）、`:417`（`supplyAsync`）→ `KnowledgeNodeBO.java:114/117/122`；②`MultiKnowledgeConcurrentRetriever.java:126`、`:135`；③`KbInternalApiController.java:33-45`、`HFR/RagRetrievalService.java:39-44`；④`KMCBASE:951-953`（submit 到 `getOrRebuildExecutor(3)`）、`:1164-1184`（自建 `ThreadPoolExecutor`，无 TaskDecorator） |
| 内部 API 是否有独立鉴权 | **未修复**：`/internal/api/rag/recall` 无 HMAC/mTLS，靠全局 `anyRequest().authenticated()`；Hermes 默认 `hermes.flow.rag.token` 为空 | `KbInternalApiController.java:33-45`；`HFR/RagRetrievalService.java:39-44`；`SecurityConfig.java:156` |

**关键补充（已验证）**：legacy 回退路径即使调用方是带上下文的请求线程，也会**再次丢上下文**——`hybridSearch` 把 `semanticSearch/fullTextSearch` 提交到自己的 `ext-task-thread-*` 线程池（`KMCBASE:951-953`、`:1164-1184`），该池没有 `TaskDecorator`，所以 legacy 路径的权限过滤**永远走 catch-skip 分支**。这意味着提交的修复只覆盖"v2 正常完成"的主库并发场景；一旦 v2 抛异常（例如任意检索组件故障），系统仍然无权限地返回 legacy 数据。

### 1.3 新增风险（本次修复引入）

1. **CallerRunsPolicy 会清掉调用线程的 SecurityContext（推断，需压测）**：`multiKbExecutor` 就是全局 `threadPoolTaskExecutor`，其拒绝策略为 `CallerRunsPolicy`（`ThreadPoolConfig.java:41`）。当队列满/线程耗尽时，任务在**请求线程**上执行，任务体先 `setContext(childContext)`、`finally clearContext()`（`MultiKbRetrievalCoordinator.java:98-102`），执行完后请求线程原本的 SecurityContext 被清空而不是恢复。当前 `chatMessage` 后半段没有再用 `SecurityUtils`，所以暂未爆雷；但任何后续在同线程上的权限判断都会退化为匿名。最小改法：`finally` 里恢复原 context（`setContext(previous)`）或改用 `DelegatingSecurityContextExecutor`/`TaskDecorator`。
2. **静默吞异常导致权限失效不可观测**：`RagRetrievalService:132-135`、`:235-238` 的 `catch (Exception ignored)` 不再记录任何日志/指标；`currentUserId != null` 才做权限检查，等价于"身份解析失败=放行"。
3. **修复面依赖调用时序**：只有"`chatMessage` 在请求线程急切执行预检索"这一事实成立时才有效。若未来把预检索包进 `Flux.defer`/`subscribeOn`/`@Async`，修复会**静默失效**（无测试守护，见 §6）。

### 1.4 判定与验证方式

**判定：部分修复。** 已修复：机器人聊天、多库并发、有认证上下文的 v2 主路径的 KB 级权限过滤。未修复：全链路 userId 显式化、越权 fail-closed、DAG/虚拟线程/内部 API/legacy 线程池。

**验证方式（建议 Lead 运行，本审计不执行）**：
- 单测：设置两个用户（A 有 KB1 权限、B 无），分别走 `MultiKbRetrievalCoordinator.coordinateRetrieval`，断言 B 的 `recallTest` 返回空；再在调用前清空 `SecurityContext`，断言**当前实现返回非空**（用于证明 fail-open）。
- 运行：抓一次真实聊天 `debugInfo`，确认 `recallTest` 内未出现 "跳过权限检查" 日志；再用一个无权限用户访问同一 bot，确认 `memory_recall` 来源为空。
- 压测：把 `threadPoolTaskExecutor` 队列打满触发 `CallerRunsPolicy`，观察请求线程 `SecurityContextHolder.getContext().getAuthentication()` 是否被清空。

---

## 2 P0-14 跨库分数门禁 —— **新增回归（工作区方案不正确）**

### 2.1 提交版 vs 工作区版

- 提交版：默认 `scoreFloor=0.0`，且 `if (scoreFloor > 0.0D && score < scoreFloor)`（`git show 3898471d`）。
- 工作区（未提交）：默认改回 `0.40`，并新增 `score > 0.1D` 条件（`backend/qknow-module-kb/.../retrieval/CrossKbScoreCalibrator.java:34`、`:104`），同时新增 `setScoreFloor/getScoreFloor`（`:39-45`，全仓无调用方）。
- 提交说明"跨库融合下调 scoreFloor 默认门限为 0.0，解除对 RRF 排位分数的量纲误杀"——**该说明对工作区代码不成立**。

### 2.2 该方案为什么不对（算术可证）

RRF 打分公式（`CrossKbScoreCalibrator.java:147`）：`rrf += 1.0D / (RRF_K + rank)`，`RRF_K=60`、每库 rank 从 1 开始（`:143`）。一个 segment 若在 K 个知识库中都排第 1，则跨库 RRF 总分 = **K / 61**。

- K≤6：总分 ≤ 0.0984 → 进入 `score > 0.1` 的"豁免区" → 不门禁，正常保留；
- **K=7~24：总分 ∈ (0.1, 0.4) → 恰好落入 `score > 0.1 && score < scoreFloor(0.4)` → 被丢弃**；
- K≥25：总分 ≥ 0.4098 → 通过门禁（荒谬地"库越多反而恢复"）。

也就是说：**当 bot 绑定 7~24 个知识库时，跨库共识最强（在所有库都排第一）的切片会最先被门禁杀掉；而只在 1~6 个库出现、分数 ≤0.1 的弱候选反而全部保留。** 这是严重的排序反转，不是"边界情况"。

同时存在三类边界错误：
1. **非单调死区**：原始余弦 0.09 → 存活，0.20 → 被丢弃，0.45 → 存活；同一"相关性单调增"的分数序列产生非单调存活结果。
2. **混合量纲未解决**：legacy 回退路径返回原始余弦/全文分（`KMCBASE:1047-1057` 的 `retrievalResult2vo` 直接透传 `RetrievalResult.score`），v2 路径返回 RRF 分；进入 `CrossKbScoreCalibrator` 后两种量纲被同一门禁处理。工作区方案只是用 `>0.1` 猜测"这大概是 RRF"，**没有把契约显式化**。
3. **null 通过**：`hit.getScore()==null` 时 `score=0.0`（`CrossKbScoreCalibrator.java:102`），0.0 不触发任何门禁 → null 结果全部保留。

### 2.3 为什么工作区会这么改：被旧测试"反向绑架"（已验证）

`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase17MultiKbRetrievalAndTenantGateTest.java` 的契约 2 仍然按"原始相似度"断言：

- `:153` 用例名"跨库置信度底线门禁 (0.40) …"
- `:162` 构造 `score=0.35` 的低分切片
- `:183` 断言 `0.35 < 0.40` 必须被剔除
- `:188-189` 断言过滤后 RRF 仍为 `1/61`

提交版默认 `0.0` 会让这条 0.35 的切片存活，从而**该测试必然失败**；工作区版 `0.40 + score>0.1` 让 0.35 落在死区被丢，测试恢复绿。也就是说：**这次"改回 0.40"是为了让一条把 RRF 误当余弦分断言的旧测试通过，而不是让生产语义正确。** 测试本身才是需要修的：它既没有覆盖"生产直接透传 RRF 分"的事实，也没有覆盖 K≥7 的跨库场景。

### 2.4 正确的修复方向（最小改动）

1. **默认回 0.0 / 删除绝对门禁**：RRF 本身是 rank 融合，弱路径已由 rank 体现；绝对分数门禁应放在 `VectorRetriever/KeywordRetriever` 的原始分数层（那里才是 cosine/keyword 量纲）。
2. 如需保留跨库底线：在 `RetrieveResult`/`RetrievalResultRespVO` 增加 `rawScore`（原始相似度）字段，在 kmc 层按 `rawScore` 过滤，再参与 RRF；跨库层只做 RRF 与预算。
3. 若必须用相对门禁：用 `score < ratio * top1Score`（如 0.1×top1），而不是绝对区间；并把比例做成配置项而非魔数。
4. 修 `Phase17` 契约：删除"0.35 原始分被 0.40 门禁过滤"的断言，改为"RRF 分不被绝对值门禁误杀"+"rawScore 门禁在 kmc 层"；新增 N=7/24 的跨库回归用例（可直接复用 K/61 公式构造）。

**判定：新增回归。** 相对提交版是回退；相对原审计 P0 是"用错误方案掩盖契约问题"。当前工作区不具备上线条件。

---

## 3 1536 维 / 单位范数强制 —— **部分修复（且引入模型兼容性风险）**

### 3.1 已改部分（已验证）

`EmbeddingServiceImpl.getDashScopeModel` 显式设置 `dimensions(1536)`：

- `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/service/impl/EmbeddingServiceImpl.java:97-100`

Spring AI 1.1.0 会把这个参数真正发到请求体：本地 `~/.m2/.../spring-ai-openai-1.1.0.jar` 的 `OpenAiEmbeddingOptions` 含 `Integer dimensions`，`OpenAiEmbeddingModel` 构造 `OpenAiApi.EmbeddingRequest` 时把 `getDimensions()` 作为第 4 个参数传入（`javap -c` 核对）。因此**对 `TongYi` 平台走 DashScope OpenAI 兼容模式的路径，维度参数确实被显式锁定**。

### 3.2 未改/不足部分

| 检查点 | 结论 | 证据 |
|---|---|---|
| 是否覆盖全部 Embedding 提供方 | **否**：只有 `getDashScopeModel` 加；`getOpenAiModel` 仍无 dimensions；`createModel` 的 `default` 分支也回落到 OpenAI 实现 | `EmbeddingServiceImpl.java:52-58`（switch）、`:69-76`（OpenAI）、`:97-100`（DashScope） |
| `EmbeddingDimensionGuard` 是否进生产路径 | **没有**：全仓 `grep "validateBeforeWrite|buildModelMetadata"` 除 guard 自身外为 0；`git log -S validateBeforeWrite` 仅引入它的提交 `bc4252d2`，`3898471d` 只改了方法体 | `EmbeddingDimensionGuard.java:41-63`；全仓零调用 |
| 是否校验 L2 单位范数 | **只新增了"偏离>0.05 就静默归一化"**，不是校验；且 float 数组路径**原地改写调用方数组**，List 路径只打日志不归一化 | `EmbeddingDimensionGuard.java:57-62`（归一化）、`:85-88`（只告警） |
| 容差是否符合项目自身规范 | **不符合**：项目死代码/原型里自己定义的是 1e-4 | `MultimodalPyramidChunker.java:24`（`HYPERSPHERE_NORM_TOLERANCE=1e-4`）、`HypersphericalGraphRagGovernor.java:25`（`VECTOR_NORM_TOLERANCE=1e-4`） |
| 启动探测是否校验维度/范数 | **否**：仍只查 `information_schema.tables` 有无 `vector_store` 表 | `EmbeddingDimensionGuard.java:107-121` |
| 写入/检索路径是否调用 guard | **否**：`KmcSyncServiceImpl.save2VectorStore` 直接 `vectorStore.add`（`:630-650` 区域，重试至 `:660`）；`VectorStoreServiceImpl` 直接包装 `PgVectorStore`（`:33-41`） | 同上 |
| 维度不一致时的行为 | 写：重试 3 次后抛错，文档同步标 ERROR（`KmcSyncServiceImpl.java:652-666`）；读：`VectorRetriever` catch 后返回空列表（`VectorRetriever.java:119-125`）→ **静默空召回** | 已验证 |

### 3.3 新增风险（关键）

`dimensions(1536)` 是**无条件硬编码**：只要 provider 平台是 `TongYi`，无论配置的模型是 `text-embedding-v2`、`text-embedding-v3` 还是 `text-embedding-v4`，都会请求 1536 维。

- 项目自身证据显示历史上用过 1024 维模型：`backend/tests/evidence/h6-chunk-ab/README.md:7`（`qwen3.7-text-embedding (1024d)`）、`backend/tests/evidence/a1-colbert-fail-closed/a0-a1-live-ann-report.json:439`（`text-embedding-v4, 1024d`）；
- `EmbeddingDimensionGuard` 的默认模型名是 `text-embedding-v3`（`EmbeddingDimensionGuard.java:27`），而 Phase13 测试也按 v3 构造元数据（`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase13DataOpsAndHealthGateTest.java:56`）；
- 若该模型不支持 1536 维（v3 类模型的维度选项通常 ≤1024，具体能力本仓库无本地文档，**无法静态判定**），则 **DashScope 返回 400 → 所有 embedding 调用失败 → 文档同步失败 + 检索失败**，属于比修复前更严重的新增回归。

另外，即便模型支持 1536，**写入/检索两端仍没有范数强制**：pgvector 余弦距离对非单位向量数学上仍可计算（`1-cos` 只依赖夹角），但项目 AGENTS.md 明确要求 `‖v‖₂=1` 且 `d²=2(1-cosθ)`；当前只有 API 参数锁定，没有"入库前校验/归一化、检索 query 前校验"的执行点。

**判定：部分修复。** 已修复：DashScope API 参数层面 1536 显式化。未修复：全 provider 覆盖、生产路径 L2 强制、启动期维度/范数审计。新增风险：模型能力与硬编码维度不匹配可导致 embedding 全链路中断（未验证，需一次真实调用确认）。

---

## 4 语义缓存 —— **核心未修复，仅做缓存卫生**

### 4.1 本次实际改动（已验证）

1. 手动清缓存接口 `DELETE /kmc/knowledgeBase/{id}/ragCache` 现在同时驱逐语义缓存：`KmcKnowledgeBaseController.java:175-186`（新增 `semanticCacheService.evictByKnowledgeBase(id)`，带 try/catch）。
2. `isFailedAnswer` 重写：`KbAgentConfigServiceImpl.java:459-475`，不再"包含'抱歉'就判失败"，改为"空/过短 + 系统报错特征 + 精确拒答句式"。

### 4.2 必查项逐项裁决

| 必查项 | 结论 | 证据 |
|---|---|---|
| 缓存键是否补权限维度 | **未修复**：L2 key 仍是 `workspace_id + bot_id + knowledge_ids_hash + model_name` | `SemanticCacheService.java:116-120` |
| 是否补知识库版本/更新时间 | **未修复**：无任何 version/updated_at/segment epoch 字段 | 同上；写入 SQL `:209-215` 无版本列 |
| `permissionHash` 是否接线 | **未修复**：`calculatePermissionHash` 仍无调用；便捷重载继续用 `"default"` | `EnhancedSemanticCacheService.java:231-232`（`buildExactKey(workspaceId,"default",...)`）、`:241`（定义） |
| 缓存查询与权限检查顺序 | **未修复**：答案缓存查询在权限检查之前 | `KbAgentConfigServiceImpl.java:258`（`findSemanticCache`）早于检索；检索权限在 `recallTest` 内 |
| TTL | **未改**：默认 24h | `SemanticCacheService.java:375`（`Duration.ofHours(24)`） |
| 失效入口 | **部分改善**：手动清缓存 + 既有文档同步完成时 evict；但 KB 更新不经过这两条路径时仍不失效 | `KmcKnowledgeBaseController.java:176-184`；`KmcSyncServiceImpl.java:451-452` |

### 4.3 `isFailedAnswer` 收窄带来的新风险

新判定比旧版**窄很多**，能避免"礼貌用语误杀正常答案"（修正确实成立），但也带来反向回归：

- 旧版会拦截"未找到/没有找到/抱歉/不确定/未提供…"；新版只拦截少数**精确句式**（`知识库中未找到相关内容`、`无法从知识库确认相关内容`、`根据当前知识库无法回答`、`未检索到相关内容` 等，`:473-474`）。
- 因此模型用近义表达拒答时（如"未找到相关信息""抱歉，我无法回答该问题""根据现有资料无法确认"）会被判定为**有效答案并写入 24h 缓存**；又因为缓存键无权限/版本（§4.2），错误/拒答答案会被跨用户复用 24h。
- 另一侧：`trim().length() < 5` 会把合法短答案（如布尔值、数字、缩写）判为失败而不缓存；影响较小但语义不精确。

**判定：未修复（核心问题）；部分改善（驱逐与误杀修正）；新增风险（软拒答被缓存）。** 若本次提交的目标只是"缓存卫生"，则 1️⃣ 部分达成；若目标是原审计的"陈旧/串租户/越权泄漏"，则结论是未修复。

---

## 5 接口签名与全量调用方检查 —— **无破坏性变更**

- `git show --stat 3898471d`：仅 7 个文件，均为内部实现修改（EmbeddingServiceImpl、KbAgentConfigServiceImpl、CrossKbScoreCalibrator、MultiKbRetrievalCoordinator、KmcKnowledgeBaseController、RagRetrievalService、EmbeddingDimensionGuard），**没有 interface/abstract/公开方法签名变化**。
- 工作区未提交改动对 RAG 相关的只有 `CrossKbScoreCalibrator`（新增默认值、getter/setter、门禁条件），无签名破坏；`setScoreFloor/getScoreFloor` 全仓无调用方（`grep "setScoreFloor|getScoreFloor"` 仅定义处）。
- 与 Lead 的编译通过结论一致：**不存在"改了签名但漏改调用方"的编译风险**。
- 但存在**语义调用方错配**：`Phase17MultiKbRetrievalAndTenantGateTest.java:153-192` 仍按原始分语义断言，与生产 RRF 透传（`KMCBASE:1047-1057`）冲突；提交版会被该测试判红，工作区版为了变绿而引入生产回归（§2.3）。这是"测试与生产契约不一致"，不是编译问题。

---

## 6 新增风险与回归清单

| ID | 级别 | 判定 | 风险 | 证据 |
|---|---|---|---|---|
| V-1 | P0 | **新增回归（工作区）** | `scoreFloor=0.4 + score>0.1` 死区：bot 绑 7~24 个 KB 时，跨库共识最强切片被丢（K/61 公式），弱候选反而存活 | `CrossKbScoreCalibrator.java:34`、`:104`、`:147`；`KMCBASE:1047-1057` |
| V-2 | P0 | **无法静态判定（高风险）** | DashScope 模型不支持 1536 维（项目历史用 1024d 模型）时，`dimensions(1536)` 可能 400，embedding 全链路失败 | `EmbeddingServiceImpl.java:97-100`；`h6-chunk-ab/README.md:7`；`a0-a1-live-ann-report.json:439`；`EmbeddingDimensionGuard.java:27` |
| V-3 | P0 | **未修复** | 内部 API `/internal/api/rag/recall` 无独立鉴权；DAG 虚拟线程、`MultiKnowledgeConcurrentRetriever`、legacy `ext-task-thread-*` 全无上下文 → 权限 fail-open | `KbInternalApiController.java:33-45`；`KbFlowServiceImpl.java:77/417`；`MultiKnowledgeConcurrentRetriever.java:126/135`；`KMCBASE:951-953/1164-1184` |
| V-4 | P1 | **新增风险（推断）** | `CallerRunsPolicy` 下任务在请求线程执行，`finally clearContext()` 清掉请求线程原上下文（未恢复），后续权限判断可能退化为匿名 | `ThreadPoolConfig.java:41`；`MultiKbRetrievalCoordinator.java:98-102` |
| V-5 | P1 | **新增风险** | 权限解析异常被 `catch(Exception ignored)` 静默吞掉，fail-open 且无日志/指标 | `RagRetrievalService.java:132-135`、`:235-238` |
| V-6 | P1 | **新增风险** | `isFailedAnswer` 收窄后，软拒答被当作有效答案缓存 24h（缓存键无权限/版本 → 跨用户复用） | `KbAgentConfigServiceImpl.java:459-475`；`SemanticCacheService.java:116-120`、`:375` |
| V-7 | P1 | **未修复** | `EmbeddingDimensionGuard` 仍不在生产路径；L2 范数无强制；容差 0.05 与 1e-4 冲突；float 路径原地改写、List 路径不一致 | `EmbeddingDimensionGuard.java:57-62`、`:85-88`、`:107-121`；`MultimodalPyramidChunker.java:24`；`HypersphericalGraphRagGovernor.java:25` |
| V-8 | P1 | **未修复** | v2→legacy 静默回退仍在，且 legacy 线程池再次丢上下文 → 故障时无权限返回旧管线数据 | `KMCBASE:388-413`、`:951-953` |
| V-9 | P1 | **未修复** | 语义缓存键无权限/版本；`permissionHash` 未接线；TTL 24h | `SemanticCacheService.java:116-120`、`:375`；`EnhancedSemanticCacheService.java:231-241` |
| V-10 | P1 | **验证缺口** | 三个修复点均无新增测试；`Phase13` 的"合法 1536 向量"实际是 0.01 填充（范数≈0.392），新增归一化会静默改写该向量，测试只断言不抛异常 → 测试已不验证真实单位范数 | `Phase13DataOpsAndHealthGateTest.java:48-51`；`EmbeddingDimensionGuard.java:57-62` |
| V-11 | P2 | **新增风险（轻微）** | `setScoreFloor/getScoreFloor` 无调用方，是死 API；若未来测试用它把门禁改 0，会再次掩盖生产混合量纲问题 | `CrossKbScoreCalibrator.java:39-45` |
| V-12 | P2 | **新增风险（轻微）** | `retrieveSimpleLight`/`retrieveScoped` 的 `catch (Exception ignored)` 未区分"无认证上下文"与"权限服务故障"，后者也会被当作 fail-open | `RagRetrievalService.java:132-135`、`:235-238` |
| V-13 | P2 | 需 Lead 留意（非 RAG） | 同一工作区未提交的 `ToolNodeBO.java` 改为"MCP 适配器为空时返回 SUCCESS 桩"，属 MCP 语义变更，与本复核无关但同批未提交 | `backend/qknow-module-kb/.../flow/bo/ToolNodeBO.java` 工作区 diff |

> 说明：V-2 的 API 兼容性无法在"禁止运行"约束下静态证明，故标"无法静态判定"，但证据（历史 1024d 模型 + 默认模型名 v3 + 硬编码 1536）足以要求上线前做一次显式探针。

---

## 7 复核结论与建议（最小改动顺序）

### 7.1 结论

- **P0-13 部分修复**：主聊天多库并发路径恢复 KB 级权限（有效），但 userId 未显式贯穿、fail-open 未消除、DAG/虚拟线程/内部 API/legacy 线程池四条路径仍无权限，且新增 `CallerRunsPolicy` 清上下文隐患。
- **P0-14 新增回归**：工作区方案用 `0.1~0.4` 死区掩盖量纲问题，7~24 库场景会丢最强共识切片；该方案是为让旧测试变绿而非生产正确，不应上线。
- **1536/单位范数 部分修复**：只在 DashScope 请求参数显式 1536；守卫仍是死代码、无 L2 强制，且硬编码维度与历史 1024d 模型能力冲突，存在 embedding 全链路中断风险（需探针）。
- **语义缓存 核心未修复**：键无权限/版本，`permissionHash` 未接线，TTL 24h；仅做了驱逐与误杀修正，`isFailedAnswer` 收窄反而可能缓存软拒答。
- **接口签名 无破坏性变更**，编译风险不存在；但测试契约与生产语义不一致，且本次修复零新增测试。

### 7.2 建议的最小修复（按顺序）

1. **P0-14（最先，改动最小）**：`CrossKbScoreCalibrator` 默认回 `0.0`，删除 `score > 0.1` 魔数；修 `Phase17` 契约（按 RRF 断言 + 新增 K=7/24 回归）；若确需原始分门禁，在 kmc 侧新增 `rawScore` 字段并在 RRF 前过滤。
2. **P0-13（安全）**：`IKmcApiService.recallTest` 增加 `Long userId` 重载，`MultiKbRetrievalCoordinator` 捕获 userId 后显式传给 `executeSingleRecall`；`RagRetrievalService` 与 `KMCBASE` 权限检查改为"userId==null → 返回空 + 指标 + 告警"（fail-closed）；legacy 回退路径同样传参或直接拒绝；DAG/`MultiKnowledgeConcurrentRetriever`/内部 API 逐步补 userId（内部 API 先加 HMAC + userId 头）。
3. **V-4**：协调器 `finally` 恢复原 `SecurityContext`，或给 `multiKbExecutor` 包 `DelegatingSecurityContextExecutor`，避免 CallerRuns 污染调用线程。
4. **1536（上线前探针）**：把维度做成"模型能力映射 + 可配置"，仅对支持模型设置 `dimensions`；启动时执行一次 embed 探针校验 `length==1536` 且范数∈[1-1e-4,1+1e-4]；把 `EmbeddingDimensionGuard` 接入 `KmcSyncServiceImpl.save2VectorStore` 与 `VectorRetriever` 写入/查询前（fail-close），容差改 1e-4，float/List 行为一致。
5. **语义缓存**：key 加 `permissionHash`（真正调用 `calculatePermissionHash`）+ `kbVersion`（KB/文档 max(updated_at) 或 segment 版本）+ embedding 模型全名；答案缓存查询移到权限检查之后；TTL 降到 5-30 分钟可配置；`isFailedAnswer` 改为"生成状态/证据标志"驱动而非纯文本正则。
6. **测试补齐**：新增三类回归测试——①无权限/有权限的并发检索权限断言；②K=7、K=24 的跨库 RRF 门禁；③embedding 维度/范数守卫（含非 1536 模型拒绝）。把这些测试接入 `-Drag.eval.shadow=true` 之外的快速单测层，避免修复再次被旧测试反向绑架。

### 7.3 验证命令（只读复现，供 Lead）

```bash
# 1) 复核判定证据（全部只读）
git show --stat 3898471d
git show 3898471d -- backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/retrieval/CrossKbScoreCalibrator.java
git diff -- backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/agent/retrieval/CrossKbScoreCalibrator.java
grep -rn "validateBeforeWrite" backend --include=*.java | grep -v /src/test/
grep -rn "SecurityUtils.getUserId" backend/qknow-module-kmc backend/qknow-module-kb --include=*.java
# 2) 关键算术（RRF 死区）
python3 -c "print([(k, round(k/61,4)) for k in range(1,26) if 0.1 < k/61 < 0.4])"
```

---

## 附录 A 证据索引

| # | 路径:行号 | 用途 |
|---|---|---|
| A1 | `MultiKbRetrievalCoordinator.java:76`、`:87-104`、`:47-51` | 上下文绑定实现与执行器注入 |
| A2 | `ThreadPoolConfig.java:32-43`（`:41` CallerRunsPolicy） | 线程池拒绝策略风险 |
| A3 | `RagRetrievalService.java:128-146`、`:231-251` | fail-open 权限检查（吞异常，无 userId 跳过） |
| A4 | `KMCBASE.java:325-333`、`:470-479`、`:726-734`、`:780-787` | recallTest/recallDebug/legacy 四处权限跳过 |
| A5 | `KMCBASE.java:388-413`、`:951-953`、`:1164-1184` | v2→legacy 回退 + legacy 自建线程池 |
| A6 | `KbFlowServiceImpl.java:77`、`:417`；`KnowledgeNodeBO.java:113-122`；`MultiKnowledgeConcurrentRetriever.java:126-140` | DAG/虚拟线程检索未传上下文 |
| A7 | `KbInternalApiController.java:33-45`；`HFR/RagRetrievalService.java:39-44`；`SecurityConfig.java:156` | 内部召回无独立鉴权 |
| A8 | `CrossKbScoreCalibrator.java:31-45`、`:100-108`、`:143-147` | 工作区分数门禁与 RRF 公式 |
| A9 | `KMCBASE.java:1047-1057`；`RetrieveResult.java:86`；`RetrieveResultRespVO.java:83` | VO 分数透传（RRF 进入跨库层） |
| A10 | `Phase17MultiKbRetrievalAndTenantGateTest.java:153-192` | 旧测试的原始分契约与 0.35/0.40 断言 |
| A11 | `EmbeddingServiceImpl.java:52-58`、`:69-76`、`:82-100` | 仅 DashScope 路径加 dimensions |
| A12 | 本地 `spring-ai-openai-1.1.0.jar`（`javap -c`） | OpenAiEmbeddingOptions.dimensions 会进入请求 |
| A13 | `EmbeddingDimensionGuard.java:26-27`、`:41-63`、`:69-88`、`:107-121` | 守卫新增 L2 逻辑但仍零调用、容差 0.05 |
| A14 | `MultimodalPyramidChunker.java:24`；`HypersphericalGraphRagGovernor.java:25` | 项目自身 1e-4 范数容差规范 |
| A15 | `KmcSyncServiceImpl.java:630-666`；`VectorRetriever.java:119-125` | 写入失败重试抛错/读取静默空召回 |
| A16 | `SemanticCacheService.java:116-120`、`:209-215`、`:375` | 缓存键/TTL 无权限无版本 |
| A17 | `EnhancedSemanticCacheService.java:231-241` | permissionHash 未接线 |
| A18 | `KbAgentConfigServiceImpl.java:258`、`:459-475`、`:497-514` | 缓存先于权限、isFailedAnswer、异步写缓存 |
| A19 | `KmcKnowledgeBaseController.java:175-186` | 手动清缓存同步驱逐语义缓存 |
| A20 | `Phase13DataOpsAndHealthGateTest.java:21-60` | 守卫测试仍只测维度/NaN，未测单位范数（0.01 填充向量） |
| A21 | `backend/tests/evidence/h6-chunk-ab/README.md:7`；`a1-colbert-fail-closed/a0-a1-live-ann-report.json:439` | 历史实测 1024 维模型证据 |
| A22 | `git show 3898471d --stat` | 7 文件、无接口签名变更 |
| A23 | `KbConversationController.java:125`；`KbAgentConfigServiceImpl.java:226` | 预检索在请求线程急切执行（修复有效的前提） |

## 附录 B 复核边界

- 本轮**没有**运行任何测试/服务，因此"运行时是否真的恢复权限/是否 400/是否被 CallerRuns 触发"均需 Lead 在受控环境复现；本报告对这类项均标注"推断/无法静态判定"。
- 本轮**没有**核对 `3898471d` 之外的 RAG 历史缺陷（原 `03-rag-knowledge-engine.md` 的 P0-1~P0-6 仍未在本提交中处理：双 CRAG、GraphRAG 死代码、评测代理仿真等）；本报告只裁决本次修复及同批工作区改动。
- 工作区 8 项未提交改动中，只有 `CrossKbScoreCalibrator` 与 RAG 相关；其余 MCP/kb 文件（含 `ToolNodeBO`）不在本报告裁决范围，仅作 V-13 提示。
