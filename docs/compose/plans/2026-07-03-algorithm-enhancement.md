# qKnow 算法补强与重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 5 个致命 Bug + 实现 5 项 SOTA 特性，覆盖 RAG/Agent/DAG/Eval/Memory 全模块

**Architecture:** 先修致命 BUG（ColBERT、Neo4j 泄漏、DAG 断链、评估串行退化、记忆绕过），再开发新特性（DSPy、Dynamic topK、RP-ReAct、限流熔断、Sleep-time OOM 修复）。每个 Task 独立可验证。

**Tech Stack:** Java 17, Spring Boot 3.5.8, Spring AI 1.1.0, Neo4j GDS, PgVector, Redis

---

## 文件清单

| 文件 | 绝对路径 | 职责 |
|------|----------|------|
| ColbertScorer.java | `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java` | ColBERT 粗排 |
| GraphCommunityService.java | `backend/qknow-module-kg/qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/GraphCommunityService.java` | 社区检测 |
| DagExecutor.java | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagExecutor.java` | DAG 执行器 |
| RagasEvaluator.java | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RagasEvaluator.java` | RAGAS 评估 |
| AgentOrchestrator.java | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java` | Agent 编排器 |
| SleepTimeMemoryAgent.java | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/SleepTimeMemoryAgent.java` | Sleep-time Agent |
| ShortTermMemory.java | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/ShortTermMemory.java` | 短期记忆 |
| QueryRouter.java | `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryRouter.java` | 查询路由 |
| RagRetrievalService.java | `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java` | RAG 检索主服务 |

---

## Task 1: 修复 ColBERT 伪向量重排 Bug

**Covers:** implementation_plan.md §1

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java:67`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java:149`

- [ ] **Step 1: 修复 computeMaxSim 中 best 初始化值**

在 `ColbertScorer.java` 第 67 行，将 `double best = 0.0;` 改为 `double best = -Double.MAX_VALUE;`：

```java
// 第 67 行: 旧代码
double best = 0.0;

// 新代码
double best = -Double.MAX_VALUE;
```

原因：Hash-based 伪向量点积余弦相似度可能为负数。当所有 Token 得分全为负时，`best = 0.0` 会错误返回 0.0，破坏 MaxSim 核心逻辑。

- [ ] **Step 2: 修复 ColbertConfig 配置前缀**

在 `ColbertScorer.java` 第 149 行，将 `@ConfigurationProperties(prefix = "hermes.rag.colbert")` 改为 `@ConfigurationProperties(prefix = "qknow.rag.colbert")`：

```java
// 第 149 行: 旧代码
@ConfigurationProperties(prefix = "hermes.rag.colbert")

// 新代码
@ConfigurationProperties(prefix = "qknow.rag.colbert")
```

原因：本项目标准配置前缀为 `qknow.*`，`hermes.*` 是 Hermes 微服务专用前缀。

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl qknow-module-kmc/qknow-module-kmc-biz -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java
git commit -m "fix(rag): 修复 ColBERT MaxSim best 初始化值和配置前缀

- best = 0.0 → -Double.MAX_VALUE，修复负相似度场景
- hermes.rag.colbert → qknow.rag.colbert，统一配置前缀"
```

---

## Task 2: 修复 Neo4j 社区检测内存泄漏

**Covers:** implementation_plan.md §2

**Files:**
- Modify: `backend/qknow-module-kg/qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/GraphCommunityService.java:33-83`

- [ ] **Step 1: 用 try-finally 包装投影图生命周期**

将 `detectCommunities` 方法（第 33-83 行）重构，确保 `gds.graph.drop` 在 `finally` 块中执行：

```java
public List<Community> detectCommunities(String workspaceId) {
    String graphName = "community-graph-" + workspaceId;
    try (Session session = driver.session()) {
        // 1. 创建投影图
        session.run("""
            CALL gds.graph.project.cypher(
                '%s',
                'MATCH (n:KgNode) WHERE n.workspace_id = %s AND n.del_flag = 0 RETURN id(n) AS id, labels(n) AS labels, n.name AS name',
                'MATCH (a:KgNode)-[r:KgEdge]->(b:KgNode) WHERE a.workspace_id = %s AND b.workspace_id = %s RETURN id(a) AS source, id(b) AS target, type(r) AS type'
            )
            """.formatted(graphName, workspaceId, workspaceId, workspaceId));

        // 2. 执行 Leiden 算法
        var result = session.run("""
            CALL gds.leiden.stream('%s')
            YIELD nodeId, communityId
            RETURN gds.util.asNode(nodeId).name AS entityName,
                   gds.util.asNode(nodeId).label AS entityLabel,
                   communityId
            ORDER BY communityId, entityName
            """.formatted(graphName));

        // 3. 按社区分组
        Map<Long, List<String>> communityEntities = new HashMap<>();
        Map<Long, List<String>> communityLabels = new HashMap<>();
        while (result.hasNext()) {
            var record = result.next();
            long communityId = record.get("communityId").asLong();
            String entityName = record.get("entityName").asString();
            String entityLabel = record.get("entityLabel").asString();
            communityEntities.computeIfAbsent(communityId, k -> new ArrayList<>()).add(entityName);
            communityLabels.computeIfAbsent(communityId, k -> new ArrayList<>()).add(entityLabel);
        }

        // 4. 构建社区列表
        List<Community> communities = new ArrayList<>();
        for (var entry : communityEntities.entrySet()) {
            Community community = new Community();
            community.setId(entry.getKey());
            community.setEntities(entry.getValue());
            community.setLabels(communityLabels.getOrDefault(entry.getKey(), List.of()));
            community.setSize(entry.getValue().size());
            community.setSummary(buildSummary(community));
            communities.add(community);
        }

        log.info("社区检测完成: workspaceId={}, communities={}", workspaceId, communities.size());
        return communities;
    } finally {
        // 5. 无条件清理投影图，防止 Neo4j OOM
        try (Session session = driver.session()) {
            session.run("CALL gds.graph.drop('%s')".formatted(graphName));
            log.debug("投影图已清理: {}", graphName);
        } catch (Exception e) {
            log.warn("清理投影图失败: {}", graphName, e);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl qknow-module-kg/qknow-module-kg-biz -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/qknow-module-kg/qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/GraphCommunityService.java
git commit -m "fix(kg): 修复 Neo4j 社区检测投影图内存泄漏

- gds.graph.drop 移入 finally 块
- 确保异常时投影图也会被释放，防止 Neo4j OOM"
```

---

## Task 3: 修复 DAG 挂起节点断链 Bug

**Covers:** implementation_plan.md §9

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagExecutor.java:180-184`

- [ ] **Step 1: 修正单节点挂起时的 checkpoint 保存逻辑**

在 `DagExecutor.java` 第 180-181 行，将 `groupIndex + 1` 改为 `groupIndex`：

```java
// 第 180-181 行: 旧代码
if (isSuspended(result)) {
    checkpointManager.saveCheckpoint(runtimeId, flowId, groupIndex + 1, resultMap);

// 新代码
if (isSuspended(result)) {
    checkpointManager.saveCheckpoint(runtimeId, flowId, groupIndex, resultMap);
```

原因：挂起时保存 `groupIndex + 1` 会导致唤醒后跳过当前组的其他未执行节点，造成图遍历断链。应保存当前 `groupIndex`，唤醒后通过 `pending` 过滤已完成节点，确保不漏节点。

- [ ] **Step 2: 修正并行组挂起时的 checkpoint 保存逻辑**

在 `DagExecutor.java` 第 214-215 行，统一挂起和错误的处理逻辑：

```java
// 第 214-215 行: 旧代码
boolean suspended = checkpointManager.hasSuspendedResult(resultMap);
checkpointManager.saveCheckpoint(runtimeId, flowId, suspended ? groupIndex + 1 : groupIndex, resultMap);

// 新代码
checkpointManager.saveCheckpoint(runtimeId, flowId, groupIndex, resultMap);
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl qknow-hermes/qknow-hermes-core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/flow/dag/DagExecutor.java
git commit -m "fix(dag): 修复 SuspendNode 挂起后 checkpoint 越界导致断链

- 挂起时保存 groupIndex 而非 groupIndex + 1
- 唤醒后通过 pending 过滤已完成节点，确保不漏执行"
```

---

## Task 4: 修复 RagasEvaluator 串行阻塞退化

**Covers:** implementation_plan.md §10

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RagasEvaluator.java:62-65`

- [ ] **Step 1: 拆分 Stream 操作为两阶段**

在 `RagasEvaluator.java` 第 62-65 行，将 `.map(...).map(CompletableFuture::join).toList()` 拆分为先收集 Future 再 join：

```java
// 第 62-65 行: 旧代码（Stream 惰性求值导致串行阻塞）
List<ItemEvaluation> evaluated = dataset.getItems().stream()
        .map(item -> CompletableFuture.supplyAsync(() -> evaluateItem(item), SAMPLE_EXECUTOR))
        .map(CompletableFuture::join)
        .toList();

// 新代码（先提交所有任务，再批量等待）
List<CompletableFuture<ItemEvaluation>> futures = dataset.getItems().stream()
        .map(item -> CompletableFuture.supplyAsync(() -> evaluateItem(item), SAMPLE_EXECUTOR))
        .toList();
List<ItemEvaluation> evaluated = futures.stream()
        .map(CompletableFuture::join)
        .toList();
```

原因：Java Stream 是惰性求值的。当 `.map(CompletableFuture::join)` 紧跟在 `.map(...supplyAsync)` 后面时，每个元素的 `join()` 会立即阻塞，导致下一个元素的 `supplyAsync` 无法提前提交，并发机制完全失效。

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl qknow-hermes/qknow-hermes-core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/eval/RagasEvaluator.java
git commit -m "fix(eval): 修复 RagasEvaluator 批处理串行阻塞退化

- 拆分 Stream 操作为两阶段：先收集 Future 再 join
- 修复 Stream 惰性求值导致并发失效退化为单线程"
```

---

## Task 5: 修复 AgentOrchestrator 记忆断层

**Covers:** implementation_plan.md §5

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java:259-267`

- [ ] **Step 1: 替换直接读取 historyList 为从 ShortTermMemory 读取**

在 `AgentOrchestrator.java` 第 259-267 行，将消息历史构建逻辑改为优先从 Redis 短期记忆读取：

```java
// 第 259-267 行: 旧代码
// 5. 构建消息历史
List<Message> messages = new ArrayList<>();
for (ChatMessage historyMsg : request.getHistoryList()) {
    if ("user".equals(historyMsg.getRole())) {
        messages.add(new UserMessage(historyMsg.getContent()));
    } else if ("assistant".equals(historyMsg.getRole())) {
        messages.add(new AssistantMessage(historyMsg.getContent()));
    }
}

// 新代码
// 5. 构建消息历史（优先从短期记忆读取，回退到关系库）
List<Message> messages = new ArrayList<>();
String sessionId = memorySessionId(request);
if (memoryManager != null) {
    try {
        List<Message> shortTermMessages = memoryManager.getShortTerm().getContext(sessionId, 20);
        if (!shortTermMessages.isEmpty()) {
            messages.addAll(shortTermMessages);
            log.debug("从短期记忆加载历史: sessionId={}, count={}", sessionId, shortTermMessages.size());
        }
    } catch (Exception e) {
        log.debug("短期记忆读取失败，回退到关系库: {}", e.getMessage());
    }
}
// Fallback: 若短期记忆为空（被打捞清理或未初始化），从关系库读取
if (messages.isEmpty()) {
    for (ChatMessage historyMsg : request.getHistoryList()) {
        if ("user".equals(historyMsg.getRole())) {
            messages.add(new UserMessage(historyMsg.getContent()));
        } else if ("assistant".equals(historyMsg.getRole())) {
            messages.add(new AssistantMessage(historyMsg.getContent()));
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl qknow-hermes/qknow-hermes-core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java
git commit -m "fix(agent): 修复记忆断层，Agent 优先从 ShortTermMemory 读取历史

- 替换直接读取 request.getHistoryList() 为 memoryManager.getShortTerm().getContext()
- 增加 Fallback 逻辑：Redis 无数据时回退到关系库"
```

---

## Task 6: 修复 Sleep-time OOM + ShortTermMemory TTL

**Covers:** implementation_plan.md §6

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/SleepTimeMemoryAgent.java:25-42`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/ShortTermMemory.java:131-142`

- [ ] **Step 1: 将 SleepTimeMemoryAgent 改为分批处理**

在 `SleepTimeMemoryAgent.java` 第 25-42 行，重构 `consolidateIdleConversations` 方法：

```java
@Scheduled(fixedDelayString = "${hermes.memory.sleep-agent.fixed-delay-ms:300000}")
public void consolidateIdleConversations() {
    long now = System.currentTimeMillis();
    ShortTermMemory shortTerm = memoryManager.getShortTerm();
    // 分批处理，避免 SCAN 全量加载到内存
    int batchSize = 50;
    int processed = 0;
    List<String> sessionIds = shortTerm.listSessionIds(scanCount);
    for (int i = 0; i < sessionIds.size(); i++) {
        String sessionId = sessionIds.get(i);
        long lastActiveAt = shortTerm.getLastActivityAt(sessionId);
        if (lastActiveAt <= 0 || now - lastActiveAt < idleThresholdMs) {
            continue;
        }
        String userId = defaultString(shortTerm.getSessionUserId(sessionId), "unknown");
        String scope = defaultString(shortTerm.getSessionScope(sessionId), "default");
        try {
            memoryManager.onConversationEnd(sessionId, userId, scope);
            shortTerm.clearSession(sessionId);
            processed++;
            log.info("Sleep-time memory consolidated: sessionId={}, userId={}, scope={}", sessionId, userId, scope);
        } catch (Exception e) {
            log.warn("Sleep-time memory consolidation failed: sessionId={}", sessionId, e);
        }
        // 每批处理后让出 CPU，避免长时间阻塞
        if (processed >= batchSize) {
            processed = 0;
            Thread.sleep(100);
        }
    }
}
```

- [ ] **Step 2: 为 ShortTermMemory 的 Redis Key 添加 TTL**

在 `ShortTermMemory.java` 第 131-142 行，修改 `touchSession` 方法，为所有 Key 设置 TTL：

```java
// 第 131-142 行: 旧代码
public void touchSession(String sessionId, String userId, String scope) {
    if (redisService == null || sessionId == null || sessionId.isBlank()) {
        return;
    }
    redisService.set(lastActivityKey(sessionId), String.valueOf(System.currentTimeMillis()));
    if (userId != null && !userId.isBlank()) {
        redisService.set(userKey(sessionId), userId);
    }
    if (scope != null && !scope.isBlank()) {
        redisService.set(scopeKey(sessionId), scope);
    }
}

// 新代码（添加 24 小时 TTL 兜底，防止僵尸数据）
private static final long SESSION_TTL_SECONDS = 86400; // 24 小时

public void touchSession(String sessionId, String userId, String scope) {
    if (redisService == null || sessionId == null || sessionId.isBlank()) {
        return;
    }
    redisService.set(lastActivityKey(sessionId), String.valueOf(System.currentTimeMillis()), SESSION_TTL_SECONDS);
    if (userId != null && !userId.isBlank()) {
        redisService.set(userKey(sessionId), userId, SESSION_TTL_SECONDS);
    }
    if (scope != null && !scope.isBlank()) {
        redisService.set(scopeKey(sessionId), scope, SESSION_TTL_SECONDS);
    }
    // 主 Key 也设置 TTL
    redisService.expire(redisKey(sessionId), SESSION_TTL_SECONDS);
}
```

注意：需要确认 `IRedisService` 接口支持 `set(key, value, ttlSeconds)` 和 `expire(key, ttlSeconds)` 方法。如果不支持带 TTL 的 set，使用 `set(key, value)` + `expire(key, ttl)` 两步操作。

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl qknow-hermes/qknow-hermes-core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/SleepTimeMemoryAgent.java
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/memory/ShortTermMemory.java
git commit -m "fix(memory): 修复 Sleep-time OOM + 添加 Redis TTL 兜底

- SleepTimeMemoryAgent 改为分批处理（batch=50），每批让出 CPU
- ShortTermMemory 所有 Redis Key 添加 24h TTL，防止僵尸数据"
```

---

## Task 7: 实现 Dynamic topK

**Covers:** implementation_plan.md §4

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java:72-94`

- [ ] **Step 1: 在 retrieve 方法中注入动态 topK 逻辑**

在 `RagRetrievalService.java` 第 72-94 行之间（权限检查之后、`retrieveOnce` 调用之前），插入动态 topK 计算：

```java
// 在第 106 行之前插入：
// Dynamic topK: 根据查询复杂度动态调整
int dynamicTopK = topK;
if (route == QueryRouter.QueryRoute.COMPLEX) {
    dynamicTopK = (int) Math.min(topK * 1.5, 80); // COMPLEX 查询放大 1.5 倍，上限 80
    log.debug("Dynamic topK: COMPLEX route, topK {} -> {}", topK, dynamicTopK);
}

RagResult first = retrieveOnce(knowledgeBaseId, originalQuery, query, dynamicTopK, debug, debugInfo, "first", route);
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl qknow-module-kmc/qknow-module-kmc-biz -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java
git commit -m "feat(rag): 实现 Dynamic topK，COMPLEX 查询动态放大召回量

- COMPLEX 路由 topK 放大 1.5 倍，上限 80
- 保证复杂多步推理摄入更宽泛的图谱与文档信息"
```

---

## Task 8: 注入 DSPy 参数化提示词

**Covers:** implementation_plan.md §3

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryRouter.java:16-23`
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryRouter.java:89-98`

- [ ] **Step 1: 将硬编码 CLASSIFY_SYSTEM 改为可配置**

在 `QueryRouter.java` 中，将静态字符串改为从配置读取：

```java
// 第 16-23 行: 旧代码
private static final String CLASSIFY_SYSTEM = """
        You are a query complexity classifier. Given a user query, classify it into one of three levels:
        
        SIMPLE: Factual questions that can be answered directly from general knowledge (e.g., "what is X?", "when did Y happen?")
        MEDIUM: Questions that need document retrieval but are straightforward (e.g., "what does the document say about X?", "summarize Y")
        COMPLEX: Multi-hop reasoning, comparison, synthesis, or analysis questions (e.g., "compare X and Y", "analyze the trend", "what are the implications of X on Y?")
        
        Return ONLY one word: SIMPLE, MEDIUM, or COMPLEX""";

// 新代码: 删除静态常量，改为在 classify 方法中从 config 读取
private String getClassifySystemPrompt() {
    if (config.getClassifyPrompt() != null && !config.getClassifyPrompt().isBlank()) {
        return config.getClassifyPrompt();
    }
    return """
            You are a query complexity classifier. Given a user query, classify it into one of three levels:
            
            SIMPLE: Factual questions that can be answered directly from general knowledge (e.g., "what is X?", "when did Y happen?")
            MEDIUM: Questions that need document retrieval but are straightforward (e.g., "what does the document say about X?", "summarize Y")
            COMPLEX: Multi-hop reasoning, comparison, synthesis, or analysis questions (e.g., "compare X and Y", "analyze the trend", "what are the implications of X on Y?")
            
            Return ONLY one word: SIMPLE, MEDIUM, or COMPLEX""";
}
```

- [ ] **Step 2: 修改 classify 方法使用新方法**

在 `QueryRouter.java` 第 64 行，将 `.system(CLASSIFY_SYSTEM)` 改为 `.system(getClassifySystemPrompt())`：

```java
// 第 64 行: 旧代码
.system(CLASSIFY_SYSTEM)

// 新代码
.system(getClassifySystemPrompt())
```

- [ ] **Step 3: 在 QueryRouterConfig 中添加 classifyPrompt 配置项**

在 `QueryRouterConfig` 类中添加：

```java
// 在 QueryRouterConfig 类中添加新字段
private String classifyPrompt;
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl qknow-module-kmc/qknow-module-kmc-biz -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/QueryRouter.java
git commit -m "feat(rag): 注入 DSPy 参数化提示词，QueryRouter Prompt 可配置

- 移除硬编码 CLASSIFY_SYSTEM 静态常量
- 新增 QueryRouterConfig.classifyPrompt 配置项
- 为后期 DSPy Teleprompter 自动调优打好解耦基础"
```

---

## Task 9: 实现 RP-ReAct 统一编排架构

**Covers:** implementation_plan.md §7

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java:280-287`

- [ ] **Step 1: 将 Plan-and-Solve 从独立分支改为 Supervisor 模式**

在 `AgentOrchestrator.java` 第 280-287 行，修改 Plan-and-Solve 的返回逻辑，使其结果注入到 ReAct 的系统提示词中而非直接返回：

```java
// 第 280-287 行: 旧代码
String planAnswer = planAndSolve(request.getQuestion(), systemPrompt, tools, chatModel);
if (planAnswer != null) {
    fullAnswer.append(planAnswer);
    promptTokens.set(estimateTokenCount(request.getQuestion()));
    completionTokens.set(estimateTokenCount(planAnswer));
    emitSingleAnswer(request, emitter, planAnswer);
    return;
}

// 新代码: Plan-and-Solve 作为 Supervisor，失败时注入规划结果到 ReAct
String planAnswer = planAndSolve(request.getQuestion(), systemPrompt, tools, chatModel);
if (planAnswer != null) {
    // Plan-and-Solve 成功，直接返回
    fullAnswer.append(planAnswer);
    promptTokens.set(estimateTokenCount(request.getQuestion()));
    completionTokens.set(estimateTokenCount(planAnswer));
    emitSingleAnswer(request, emitter, planAnswer);
    return;
}
// Plan-and-Solve 失败或返回 null，继续走 ReAct（已有 plannerSupervision 注入到 systemPrompt）
```

当前代码已经实现了 `createPlannerSupervision` 方法（第 568-594 行），它会将规划结果注入到系统提示词中供 ReAct 使用。这个 Task 的核心改动是确保 Plan-and-Solve 失败时不会跳过 ReAct，而是让 ReAct 带着 Planner 的监督信息执行。

- [ ] **Step 2: 增强 recoverReactFailure 的 Self-Healing 能力**

在 `recoverReactFailure` 方法中，让 Planner 能够重新规划而非仅生成安全答案：

```java
// 在 recoverReactFailure 方法中，增加重新规划逻辑
private String recoverReactFailure(String question, String systemPrompt, Throwable error, ChatModel chatModel) {
    if (planSolveConfig == null || !planSolveConfig.isEnabled() || !planSolveConfig.isRpReactEnabled()
            || chatModel == null || !isComplexQuestion(question)) {
        return null;
    }
    try {
        // Self-Healing: 尝试重新规划并执行
        List<PlanTask> revisedTasks = createPlan(question, systemPrompt, chatModel);
        if (!revisedTasks.isEmpty()) {
            Map<String, String> results = executePlanTasks(revisedTasks, systemPrompt, List.of(), chatModel);
            if (!results.isEmpty() && !hasFailedWorkerResult(results)) {
                return aggregatePlanResults(question, systemPrompt, results, chatModel);
            }
        }
        // 回退到安全答案
        String prompt = """
                ReAct executor failed. As the planner supervisor, produce the best final answer or a concise failure-safe answer.
                Do not expose stack traces. If evidence is insufficient, state the limitation.

                Question: %s
                Executor error: %s
                """.formatted(question, error != null ? error.getMessage() : "unknown");
        ChatResponse response = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt != null ? systemPrompt : ""),
                new UserMessage(prompt)
        )));
        return response.getResult().getOutput().getText();
    } catch (Exception e) {
        log.warn("Planner recovery failed", e);
        return null;
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl qknow-hermes/qknow-hermes-core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java
git commit -m "feat(agent): 实现 RP-ReAct 统一编排架构

- Plan-and-Solve 失败时 ReAct 带着 Planner 监督信息执行
- recoverReactFailure 增加 Self-Healing 重新规划能力
- Planner 作为 Supervisor 循环驱动 Executor"
```

---

## Task 10: 实现调度器容量并发熔断

**Covers:** implementation_plan.md §8

**Files:**
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java:58-62`
- Modify: `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/PlanSolveConfig.java`

- [ ] **Step 1: 在 AgentOrchestrator 中添加全局 Semaphore**

在 `AgentOrchestrator.java` 第 58-62 行之后，添加全局 Token 预算信号量：

```java
// 在 ACTIVE_REACT_RUNS 定义之后添加：
private static final java.util.concurrent.Semaphore LLM_CALL_SEMAPHORE =
        new java.util.concurrent.Semaphore(
                Integer.parseInt(System.getProperty("hermes.capacity.max-llm-concurrent", "8")),
                true); // fair=true，保证 FIFO
```

- [ ] **Step 2: 在 executePlanTasks 中使用 Semaphore**

在 `executePlanTasks` 方法中，每个 CompletableFuture 内部使用 `acquire/release`：

```java
// 修改 executePlanTasks 中的 supplyAsync 内部逻辑
List<CompletableFuture<Map.Entry<String, String>>> futures = ready.stream()
        .map(task -> CompletableFuture.supplyAsync(() -> {
            if (!enterCapacity(ACTIVE_PLAN_TASKS, maxConcurrentPlanTasks())) {
                return Map.entry(task.taskId(), "执行失败: capacity gate open");
            }
            try {
                LLM_CALL_SEMAPHORE.acquire();
                try {
                    WorkerAgent worker = new WorkerAgent(task.worker(), "Plan task worker", systemPrompt, tools, chatModel, resolver);
                    String result = worker.chat(task.objective(), Map.of("previousResults", new LinkedHashMap<>(results)));
                    return Map.entry(task.taskId(), result);
                } finally {
                    LLM_CALL_SEMAPHORE.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Map.entry(task.taskId(), "执行失败: interrupted");
            } finally {
                leaveCapacity(ACTIVE_PLAN_TASKS);
            }
        }, PLAN_EXECUTOR))
        .toList();
```

- [ ] **Step 3: 在 PlanSolveConfig 中添加配置项**

在 `PlanSolveConfig` 类中添加：

```java
private int maxLlmConcurrent = 8;
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl qknow-hermes/qknow-hermes-core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java
git add backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/config/PlanSolveConfig.java
git commit -m "feat(agent): 实现调度器容量并发熔断

- 添加全局 Semaphore 限制 LLM 并发调用数（默认 8）
- Plan-and-Solve 并行任务执行时使用 acquire/release
- 防止高并发下 LLM API 被瞬间打满"
```

---

## 执行顺序

按照 implementation_plan.md 的交接指南，执行顺序为：

1. **Task 1** — ColBERT Bug（致命计算错误）
2. **Task 2** — Neo4j 内存泄漏（严重资源泄漏）
3. **Task 3** — DAG 断链（0 节点执行 Bug）
4. **Task 4** — 评估串行退化（性能严重退化）
5. **Task 5** — 记忆断层（生产环境记忆为空）
6. **Task 6** — Sleep-time OOM + TTL（内存爆炸 + 僵尸数据）
7. **Task 7** — Dynamic topK（新特性）
8. **Task 8** — DSPy 参数化提示词（新特性）
9. **Task 9** — RP-ReAct 统一架构（新特性）
10. **Task 10** — 容量并发熔断（新特性）

每完成一个 Task，运行 `mvn compile` 验证编译通过。
