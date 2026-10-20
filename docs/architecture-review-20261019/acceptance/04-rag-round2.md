# RAG 第二轮验收报告（54fea59b / 基线 HEAD 1ff190d0）

- 复核对象：提交 `54fea59b`（`fix(core): 深度闭环修复API密钥掩码覆盖、流式工具链与多租户权限等核心缺陷`）中的 RAG 侧改动，以**当前工作区真实代码**（`HEAD = 1ff190d0`，工作区 clean）为最终裁决对象
- 复核员：rag-engine-auditor（第一轮 `verify/03-rag-fix.md` 复核员）
- 复核日期：2026-10-20
- 方法：**只读静态复核**。`git show 54fea59b` 全文、逐文件读取当前 HEAD 代码、全仓语义检索 `scoreType/setScoreType`、核对 `CandidateFusionService → RagResult → RetrieveResultRespVO → KmcApiServiceImpl → RetrieveResult → CrossKbScoreCalibrator` 全链路、对照自证报告 `verify/06-deep-fix-resolution.md`。**未执行 mvn、未跑测试、未启动服务**
- 判定口径：**已修复 / 部分修复 / 未修复 / 新增回归 / 无法静态判定**
- 证据等级：**已验证**=源码行直接证明；**推断**=多行逻辑串联、未运行；**未知**=需运行时

## 0 结论速览

| 项 | 本轮判定 | 一句话结论 |
|---|---|---|
| **R3 跨库 RRF 分数死区** | **未修复 + 新增回归（测试自证）** | 生产链路**没有任何一处**给 `RetrieveResult.scoreType` 赋值：全仓唯一 `setScoreType` 在 `Phase17...Test:203`。生产 `isRrfScore` 恒 `false` → `scoreFloor=0.40` 对全部 v2 RRF 命中生效 → 典型 RRF 分（≤4/61≈0.066）全部 <0.40 被丢，跨库装配大概率为空。**比上一轮"0.1~0.4 死区"更严重**（上一轮 RRF ≤0.1 还能豁免） |
| 自证报告"Phase17 6/6、R3 已彻底根除" | **不成立** | 6 个测试确实存在，但新增契约 2b 在测试里手工 `setScoreType(RRF)`；旧契约 2 仍按"未标注=余弦"断言；测试**直接调 `CrossKbScoreCalibrator`**，不经过生产转换链，覆盖不到"生产者缺失" |
| P0-13 权限 fail-open | **未修复（唯一改善：CallerRuns 上下文恢复）** | `RagRetrievalService`/`KMCBASE` 的 catch-skip 原样保留；无 userId 显式贯穿；DAG 虚拟线程/内部 API/legacy 线程池仍无权限 |
| 1536/单位范数 | **未修复（仅参数层）** | 仍只在 `TongYi` 分支 `dimensions(1536)`；`EmbeddingDimensionGuard` 全仓零生产调用；无 L2 强制 |
| 语义缓存权限/版本 | **未修复** | 键仍 `workspace+bot+kbHash+model`；`calculatePermissionHash` 仍只被单元测试调用 |

> **对 Lead 的直接回答：R3 没有真正修复，只做了测试层的"自证"；且当前工作区的生产行为比上一轮更差。**

---

## 1 R3：`scoreType` 生产接线核查 —— **未修复 + 新增回归**

### 1.1 决定性证据：全仓没有任何生产写入点（已验证）

全仓（`backend/` 全部 Java）检索 `scoreType` / `setScoreType`，只有 4 处命中：

| # | 位置 | 性质 |
|---|---|---|
| 1 | `backend/qknow-framework/qknow-thirdparty/.../RetrieveResult.java:88-92` | 新增常量 `SCORE_TYPE_COSINE`/`SCORE_TYPE_RRF` + 字段 `private String scoreType`（Lombok `@Data` 生成 setter） |
| 2 | `backend/qknow-module-kb/.../retrieval/CrossKbScoreCalibrator.java:103` | **读取**：`RetrieveResult.SCORE_TYPE_RRF.equalsIgnoreCase(hit.getScoreType())` |
| 3 | `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase17MultiKbRetrievalAndTenantGateTest.java:203` | **测试**手工 `rrfHit.setScoreType(RetrieveResult.SCORE_TYPE_RRF)` |
| 4 | `RetrieveResult.java:89`（`SCORE_TYPE_RRF` 常量声明本身） | 定义 |

即：**生产代码中 `setScoreType(...)` 的调用次数 = 0**；`builder().scoreType(...)`、`BeanUtils` 映射、JSON 反序列化等其它赋值形式也均不存在（`scoreType` 全仓仅上述 4 个 token 命中）。`SCORE_TYPE_COSINE` 甚至从未被引用。

### 1.2 生产链路逐段核对：缺环不止一处（已验证）

以"机器人聊天 → 多库召回"为例，真实链路与 `scoreType` 的状态：

| 环节 | 代码 | `scoreType` 状态 |
|---|---|---|
| ① 生产调用入口 | `KbAgentConfigServiceImpl.java:278-279`：`coordinateRetrieval(...)` → `calibrateAndAssemble(...)` | — |
| ② 每库召回 | `MultiKbRetrievalCoordinator.java:160`：`kmcApiService.recallTest(kb.getId(), question, historyTurns)` | — |
| ③ kmc 侧返回 | `KmcApiServiceImpl.java:147-175`：`for (RetrieveResultRespVO vo : ...) { RetrieveResult result = new RetrieveResult(); ... result.setScore(vo.getScore()); result.setRagContext(...); }` | **无 `setScoreType`**（手工字段拷贝，明确漏掉；`:172` 仅复制 score） |
| ④ v2 → VO | `KmcKnowledgeBaseServiceImpl.java:1047-1058`（`retrievalResult2vo`）：只 set `id/documentId/content/answer/documentName/score/wordCount/source` | **VO 无 `scoreType` 字段可传** |
| ⑤ VO 模型 | `RetrieveResultRespVO.java:14-95`：字段列表为 id/position/documentId/content/answer/wordCount/tokens/keywords/indexNodeId/indexNodeHash/hitCount/enabled/disabledAt/disabledBy/status/createdBy/createdAt/indexingAt/completedAt/error/stoppedAt/documentName/score/source/debugInfo/ragContext | **无 `scoreType`** |
| ⑥ RRF 得分来源 | `CandidateFusionService.java:16`（`rrfK=60`）、`:84`（`1.0/(rrfK+rank+1)`）、`:104`（`.score(rrfScores...)`）；经 `RagRetrievalService.java:448` 融合、`:518` 返回 `RagResult` | 分数是 RRF 量纲，但**无任何"这是 RRF"的标记向上传递** |
| ⑦ legacy 回退 | `KMCBASE.java:1012-1024`（`aiDocument2vo`）也只 set score | **同样未标 COSINE** |
| ⑧ 门禁读取 | `CrossKbScoreCalibrator.java:103` 读 `hit.getScoreType()` → 生产恒 `null` → `isRrfScore=false`；`:106` `if (!isRrfScore && scoreFloor>0 && score<scoreFloor) continue;` | **`scoreFloor=0.40` 对所有未标记命中生效** |

**结论（已验证）**：`scoreType` 契约只实现了"读取端"，**生产没有"写入端"**，且中间 VO/转换层根本没有该字段——即使未来有人在上游给 `RetrieveResult` 设了标记，`KmcApiServiceImpl` 的手工拷贝也会把它丢掉。这是**双重断裂**：①生产者缺失；②传输通道缺字段。

### 1.3 定量后果：v2 跨库上下文约等于恒空（已验证 + 推断）

- `scoreFloor` 当前默认 **0.40**（`CrossKbScoreCalibrator.java:33-34`），全仓 `application*.yml/*.properties` 无 `qknow.rag.multi-kb.score-floor` 覆盖（grep 为空）→ 默认值生效。
- v2 单库单条命中的 RRF 分值为 `Σ 1/(60+rank+1)`，最大路径数为 4（vector/keyword/metadata/graph，`RagRetrievalService.java:434-448` 区段），即单库最高约 `4/61≈0.0656`；跨库 RRF 是在门禁**之后**才计算的，门禁看到的每条 hit 都 ≤0.0656。
- 因此：**典型 RRF 命中 100% < 0.40 → 全部被 `continue` 丢弃**；`uniqueSegmentMap` 为空 → `MergedContextResult` 空 → 每库 `preRetrievedContent` 为空 → Hermes 无知识上下文。只有两种例外能通过：①被规则重排（`DeterministicRerankerProvider` 的 Day/文档名/关键词 bonus 可把分数抬到 ≥0.40）；②KB 显式启用远程/本地 rerank 且返回 ≥0.40 的相关性分。这两类都属"配置/路径依赖的偶然存活"，不是契约。

### 1.4 "Phase17 6/6" 是怎么绿的：测试自证结构（已验证）

`Phase17MultiKbRetrievalAndTenantGateTest.java` 确有 6 个 `@Test`（`:90/153/199/218/252/300`），与自证报告 `06-deep-fix-resolution.md:20` 的"6/6"数量一致。但：

1. **契约 2（旧）语义没改对**：`:153-192` 仍构造 `score=0.35` 的"低分噪点"（`:162`）并断言必须被 0.40 门禁剔除（`:183`），同时断言过滤后的 RRF 为 `1/61`（`:188-189`）。它在当前生产实现下通过，恰恰是因为**没有 `scoreType` 标记** → 说明该测试验证的是"未标记即余弦"的旧世界，而生产未标记流其实是 RRF。
2. **契约 2b（新）是手动自证**：`:199-215` 构造 `score=0.1639` 的 hit（`:201`），然后**测试代码自己** `rrfHit.setScoreType(RetrieveResult.SCORE_TYPE_RRF)`（`:203`），再直接调用 `calibrator.calibrateAndAssemble(...)`（`:212-213`）。它证明的只是"如果标记存在，门禁会跳过"，**完全不证明生产会打标记**。
3. **测试没有经过生产转换链**：所有契约都直接构造 `MultiKbRetrievalCoordinator.SingleKbRecallResult`（helper `:72-80` 亦不设 `scoreType`），不经过 `KmcApiServiceImpl.recallTest` → `RetrieveResult` 的字段拷贝（`KmcApiServiceImpl.java:147-175`），也不经过 `retrievalResult2vo`（`KMCBASE.java:1047-1058`）。因此对"生产者缺失"这一根因零覆盖。
4. **契约 5（语义缓存权限哈希）也是 helper 级自证**：`:300-345` 直接调用 `semanticCacheService.calculatePermissionHash/buildExactKey`，但生产 `SemanticCacheService` 的 L2 查询并未使用权限哈希（见 §4）。

### 1.5 `score > 0.1` 魔数与 `scoreFloor` 现状

- `score > 0.1D` 魔数**已从判断条件中删除**，仅剩注释文字（`CrossKbScoreCalibrator.java:107` 注释）。从"代码清洁度"看这一项完成。
- 但删除魔数 ≠ 修复量纲：删掉的是"RRF 豁免补丁"，同时新增的 `scoreType` 豁免在生产永不成立，结果是**门禁覆盖面反而从"RRF≤0.1 豁免"退回"全量 0.40 门禁"**。相对上一轮 HEAD（`e4864302` 的 `0.40+score>0.1` 版本）这是**新增回归**；相对 `3898471d` 提交版（`0.0`）也是回退。
- 若部署环境把 `qknow.rag.multi-kb.score-floor` 覆盖为 `0.0`，症状会被掩盖（RRF 不被过滤）；但仓库内**无任何该配置**，默认 0.40 生效。验收时必须同时在运行时确认该属性值。

### 1.6 判定、残留风险与验证方式

**判定：未修复（R3 核心未生效）+ 新增回归（相对上一轮生产行为更差）。**

- 残留风险：跨库 RAG 上下文大概率为空 → 机器人退化为无知识库回答；若某 KB 恰好开启远程 rerank，则呈现"部分 KB 有上下文、部分没有"的不可解释漂移；而所有单元测试保持绿色，回归不可见。
- 最小修法（三选一或组合，按成本排序）：
  1. **补生产者 + 传输字段（推荐，最小闭环）**：`RetrievalResult`（kmc 模型）增加 `scoreType`；`retrievalResult2vo` 对 v2 输出置 `RRF`（`KMCBASE:1047-1058`）；`aiDocument2vo` 对 legacy 置 `COSINE`（`KMCBASE:1012-1024`）；`RetrieveResultRespVO` 增加 `scoreType` 字段；`KmcApiServiceImpl.recallTest` 手工拷贝（`:147-175`）补 `result.setScoreType(vo.getScoreType())`；同时 `CandidateFusionService` 输出处可加 `metadata.put("scoreType","RRF")` 作为冗余校验。
  2. **兜底**：把 `scoreFloor` 默认值改回 `0.0`（或删除绝对门禁），把原始终点阈值下沉到 `VectorRetriever/KeywordRetriever` 的原始分数层——RRF 是 rank 融合，不该再做绝对分数门禁。
  3. **测试**：新增一条**端到端**契约：`KmcApiServiceImpl.recallTest`（mock `search` 返回 v2 RRF 分）→ 断言产出 `RetrieveResult.getScoreType()=="RRF"` 且 `CrossKbScoreCalibrator` 不丢；再补 legacy COSINE 对称用例。删掉/改写契约 2 中"未标注=余弦"的隐含假设。
- 验证方式（只读静态已可完成）：`grep -rn "setScoreType" backend --include=*.java | grep -v /src/test/` → 空；运行时再抓一次多库聊天 `debugInfo`，确认 `merged.getEmittedSegments()` 是否为空即可。

---

## 2 P0-13 权限 fail-open 残留 —— **未修复（仅修复 CallerRuns 副作用）**

- **fail-open 原样保留**（已验证）：
  - `RagRetrievalService.java:132-136`、`:235-239`：`try { currentUserId = SecurityUtils.getUserId(); } catch (Exception ignored) {}`，`currentUserId==null` 时**整段权限检查被跳过**，仍无日志/指标。
  - `KmcKnowledgeBaseServiceImpl.java:325-333`、`:470-479`、`:726-734`、`:780-787`：recallTest/recallDebug/legacy 三处仍 `catch` 后继续（`:732` 注释"无用户上下文时跳过权限过滤（如 Agent 对话）"）。
  - `KmcKnowledgeBaseServiceImpl.java:388-413`：v2 异常仍静默回退 legacy（`:412`）。
- **无 userId 显式贯穿**（已验证）：`IKmcApiService.java:62-67` 的 `recallTest(Long, String[, List])` 签名未变；无 `userId` 参数，仍依赖 `SecurityContextHolder` ThreadLocal。
- **仍有上下文的异步/无上下文路径**（已验证）：`KbFlowServiceImpl.java:77`（虚拟线程池）、`:418`（`supplyAsync`）；`MultiKnowledgeConcurrentRetriever.java:126/135`（虚拟线程 + `recallTest`）；`KbInternalApiController.java:33-40`（`/internal/api/rag/recall`，仅受 `SecurityConfig.java:156` 的 `anyRequest().authenticated()` 约束；`FlyFlowHmacAuthenticationFilter.java:45` 只覆盖 `/flyflow/`，不覆盖 `/internal/**`；Hermes 默认 `hermes.flow.rag.token` 为空）。
- **唯一正向改善**：`MultiKbRetrievalCoordinator.java:93-108` 新增 `previousContext` 并在 `finally` 恢复，修复了上一轮指出的 "CallerRunsPolicy 清空请求线程上下文"（V-4）隐患——**已修复**。另 `54fea59b` 新增 `HermesGrpcAuthInterceptor`（gRPC 服务端鉴权）属正向加固，但不覆盖上述内部 HTTP 召回与 DAG 虚拟线程。
- 判定：**未修复**；残留越权面与上一轮相同，只在主聊天路径（有认证上下文）下有效。

---

## 3 1536 维 / 单位范数 —— **未修复（仅参数层，守卫仍是死代码）**

- `EmbeddingServiceImpl.java:97-100` 仍只在 `TongYi → getDashScopeModel` 分支设置 `dimensions(1536)`；`getOpenAiModel`（`:69-76`）与 switch 的 `default` 分支（`:53-56`）仍无维度参数 → 契约不覆盖全 provider。
- `EmbeddingDimensionGuard` 全仓**仍无任何生产调用**：`grep -rn "validateBeforeWrite|EmbeddingDimensionGuard" backend --include=*.java | grep -v /src/test/ | grep -v guard/EmbeddingDimensionGuard.java` 为空。其新增的 L2 逻辑（`:41-63`、`:69-94`）只有测试直接调用；`onStartupCheck`（`:107-121`）仍只查 `information_schema.tables` 有无 `vector_store` 表，**不审计维度/范数**。
- 容差仍是 `|‖v‖−1|>0.05`（`:58`、`:86`），与项目自身 1e-4 规范（`MultimodalPyramidChunker.java:24`、`HypersphericalGraphRagGovernor.java:25`）不一致；float 路径静默改写输入、List 路径只告警，行为不一致；零向量（norm==0）直接通过。
- 硬编码 1536 与历史 1024d 实测模型（`tests/evidence/h6-chunk-ab/README.md:7`、`a1-colbert-fail-closed/a0-a1-live-ann-report.json:439`）能力是否匹配，仍**无法静态判定**，上线前需一次真实 embed 探针（关注 400/维度不符）。
- 判定：**未修复**（相对第一轮无变化；本轮提交未触碰该文件）。

---

## 4 语义缓存权限/版本维度 —— **未修复**

- L2 查询键仍只有四个维度：`SemanticCacheService.java:116-120`（`workspace_id`、`bot_id`、`knowledge_ids_hash`、`model_name`），**无权限、无 KB 版本/更新时间**；TTL 仍 24h（`:375`）。
- `EnhancedSemanticCacheService.calculatePermissionHash`（`:241`）仍只有测试调用；生产便捷入口 `findExact/putExactCache`（`:58-59`、`:98-104`）继续把权限哈希写成 `"default"`（`:231-232`）。
- 答案级缓存查询仍发生在权限检查之前（`KbAgentConfigServiceImpl.java:258`）。
- `Phase17` 契约 5（`:300-345`）只测 helper 方法本身（`calculatePermissionHash`/`buildExactKey`），**不测 `SemanticCacheService` 的 SQL 是否接入权限哈希** → 又一例 helper 级自证。
- 判定：**未修复**（相对第一轮无变化）。

---

## 5 其它观察

1. **自证报告的可信边界**：`verify/06-deep-fix-resolution.md:20` 把 R3 标为"已彻底根除"、证据为 `Phase17... (6/6)`；`06:134` 自述执行了 `mvn ... -Dtest=Phase17... test`。但即使 6/6 真实通过，也只覆盖"手工构造 + 直接调用门禁"的单元路径，**不能证明生产接线**——本次复核已证明接线不存在。
2. **本轮未新增任何 RAG 端到端/集成测试**：54fea59b 对 RAG 只改了 `RetrieveResult` 字段与 `CrossKbScoreCalibrator` 读取分支（`git show --stat`），测试仅加 2b 单元用例。
3. **上一轮遗留问题未动**：v2→legacy 静默回退、内部 API 无独立鉴权、DAG/虚拟线程无权限、`isFailedAnswer` 收窄后的缓存污染风险、GraphRAG 死代码等均保持原状（不属本轮声称修复范围，但影响"验收通过"的整体判断）。
4. **正向改动**：`MultiKbRetrievalCoordinator` 的 CallerRuns 上下文恢复（V-4）已修复；`HermesGrpcAuthInterceptor` 为 gRPC 增加服务端鉴权（正向，需另行验收其密钥/时间窗实现）。

---

## 6 验收结论与整改顺序

1. **R3 验收：不通过。** 生产无 `scoreType` 生产者，`isRrfScore` 恒 false，`scoreFloor=0.40` 误杀全部典型 RRF 命中；自证测试只覆盖读取端。必须补"生产者 + VO 字段 + 手工拷贝"三点，或把 `scoreFloor` 默认回 0.0 并下沉原始分门禁。
2. **P0-13 验收：不通过。** fail-open 未消除、userId 未显式贯穿、DAG/内部 API/虚拟线程仍无权限；仅 CallerRuns 修复可计分。
3. **1536/范数验收：不通过。** 仍只是 API 参数；守卫零生产调用；L2 容差/行为不一致。
4. **语义缓存验收：不通过。** 键无权限/版本，helper 存在但未接线。
5. 建议把"Phase17 6/6"从验收证据降级为"单元分支自证"，并要求新增至少一条经过 `KmcApiServiceImpl.recallTest` 的端到端契约（含 RRF 标记与 COSINE 标记双向用例），否则同类"标记语义"修复还会以同样方式复现。

---

## 附录 A 证据索引

| # | 路径:行号 | 用途 |
|---|---|---|
| A1 | `qknow-thirdparty/.../RetrieveResult.java:86-92` | `scoreType` 字段与常量定义（唯一生产者接口） |
| A2 | `qknow-module-kb/.../retrieval/CrossKbScoreCalibrator.java:33-34`、`:103`、`:106-108` | 默认 0.40、读取标记、门禁分支 |
| A3 | `qknow-module-kmc/.../api/KmcApiServiceImpl.java:147-175`（`:172` setScore） | VO→RetrieveResult 手工拷贝，漏 `scoreType` |
| A4 | `qknow-module-kmc/.../controller/.../vo/RetrieveResultRespVO.java:14-95`（`:83` score、`:86` source） | VO 无 `scoreType` 字段 |
| A5 | `qknow-module-kmc/.../knowledgeBase/impl/KmcKnowledgeBaseServiceImpl.java:1047-1058` | v2 `retrievalResult2vo` 无标记 |
| A6 | 同文件 `:1012-1024` | legacy `aiDocument2vo` 无 COSINE 标记 |
| A7 | 同文件 `:401`、`:535` | v2 产出 VO 的两处映射点 |
| A8 | `qknow-module-kmc/.../service/rag/CandidateFusionService.java:16`、`:84`、`:104` | RRF 公式与得分写入（量纲≤4/61） |
| A9 | `qknow-module-kmc/.../service/rag/RagRetrievalService.java:448`、`:518` | 融合结果进入 `RagResult` |
| A10 | `qknow-module-kb/.../retrieval/MultiKbRetrievalCoordinator.java:160`、`:87-108` | 生产召回调用；上下文绑定/恢复 |
| A11 | `qknow-module-kb/.../agent/impl/KbAgentConfigServiceImpl.java:278-279`、`:258` | 协调器/校准器调用；缓存先于权限 |
| A12 | `tests/.../rag/eval/Phase17MultiKbRetrievalAndTenantGateTest.java:72-80`、`:153-192`、`:199-215`（`:203`）、`:300-345`；`@Test` 计数=6 | 旧契约 2、自证 2b、契约 5 helper 自证 |
| A13 | `verify/06-deep-fix-resolution.md:20`、`:50-55`、`:134` | 自证"已彻底根除/6/6"及其方法 |
| A14 | `RagRetrievalService.java:132-136`、`:235-239` | P0-13 catch-skip fail-open |
| A15 | `KmcKnowledgeBaseServiceImpl.java:325-333`、`:470-479`、`:726-734`、`:780-787`、`:388-413` | kmc 四处跳过 + v2 回退 |
| A16 | `IKmcApiService.java:62-67` | 无 userId 参数 |
| A17 | `KbFlowServiceImpl.java:77`、`:418`；`MultiKnowledgeConcurrentRetriever.java:126/135`；`KbInternalApiController.java:33-40`；`FlyFlowHmacAuthenticationFilter.java:45`；`SecurityConfig.java:156` | 无上下文检索路径与内部 API 鉴权面 |
| A18 | `EmbeddingServiceImpl.java:53-56`、`:69-76`、`:82-100`（`:99`） | 仅 TongYi 参数层 1536 |
| A19 | `EmbeddingDimensionGuard.java:41-63`、`:69-94`、`:107-121`；全仓调用=空 | 守卫零生产调用、无 L2 强制 |
| A20 | `MultimodalPyramidChunker.java:24`、`HypersphericalGraphRagGovernor.java:25` | 项目 1e-4 范数容差规范 |
| A21 | `SemanticCacheService.java:116-120`、`:375`；`EnhancedSemanticCacheService.java:58-59`、`:231-232`、`:241` | 缓存键无权限/版本、helper 未接线 |
| A22 | `git show --stat 54fea59b`、`git log -S setScoreType` | RAG 改动范围与"唯一 setScoreType 在测试"的提交史 |

## 附录 B 复核命令（只读，可复现）

```bash
cd backend
grep -rn "setScoreType" --include=*.java .            # 期望：仅 tests/.../Phase17...:203
grep -rn "scoreType" --include=*.java .               # 期望：字段/常量/门禁读取/测试，共 4 处
grep -rn "scoreFloor\|score-floor" --include=*.java --include=*.yml . | head
grep -rn "validateBeforeWrite" --include=*.java . | grep -v /src/test/ | grep -v guard/EmbeddingDimensionGuard.java   # 期望：空
grep -rn "SecurityUtils.getUserId" --include=*.java qknow-module-kmc qknow-module-kb | head
git show 54fea59b --stat | grep -E "Retrieve|CrossKb|Phase17|Rag|kmc"
```

> 本报告仅静态复核；"生产上下文为空"与"DashScope 1536 是否 400"需 Lead 在受控环境做一次多库聊天 debug 与一次真实 embed 探针后最终定性。本报告未修改任何源码/配置/fixture/qrel。
