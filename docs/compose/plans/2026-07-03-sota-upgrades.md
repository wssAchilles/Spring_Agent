# SOTA 架构演进实施计划 — Dynamic TopK / ColBERT 真向量 / GraphRAG Map-Reduce

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task.

**Goal:** 将三个"伪实现"升级为真正的 SOTA 算法 — Dynamic TopK 真动态计算、ColBERT 真 Embedding 向量、GraphRAG 真 LLM Map-Reduce

**Architecture:** 三个任务互相独立，可并行执行。每个任务修改 1 个文件，注入 1 个已有服务接口。

**Tech Stack:** Java 17, Spring AI 1.1.0, EmbeddingModel, ChatModel, CompletableFuture

---

## Task 1: Dynamic TopK — 激活真实动态计算

**Covers:** implementation_plan.md §1

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/RagRetrievalService.java`

- [ ] **Step 1: 在 retrieve 方法中提前解析意图，替换硬编码**

将第 79-114 行的逻辑改为：

```java
// Query routing
QueryRouter.QueryRoute route = queryRouter.classify(query);
if (debug) {
    debugInfo.put("queryRoute", route.name());
}

// Simple queries: skip retrieval
if (route == QueryRouter.QueryRoute.SIMPLE) {
    // ... existing SIMPLE handling unchanged ...
}

// 权限检查 ... existing ...

// 意图解析前置（原来在 retrieveOnce 内部，现在提前）
QueryIntent queryIntent = queryIntentAnalyzer.analyze(originalQuery);

// Dynamic topK: 使用真实的动态计算替代硬编码
int dynamicTopK = resolveTopK(topK, route, queryIntent);
if (debug) {
    debugInfo.put("dynamicTopK", dynamicTopK);
    debugInfo.put("dynamicTopKFactors", Map.of(
        "route", route.name(),
        "keywordCount", queryIntent.getKeywords() != null ? queryIntent.getKeywords().size() : 0,
        "dayNo", queryIntent.getDayNo() != null ? queryIntent.getDayNo() : "null"
    ));
}

RagResult first = retrieveOnce(knowledgeBaseId, queryIntent, query, dynamicTopK, debug, debugInfo, "first", route);
```

- [ ] **Step 2: 修改 retrieveOnce 方法签名，接收 queryIntent 参数**

将方法签名从：
```java
private RagResult retrieveOnce(Long knowledgeBaseId, String originalQuery, String query, int topK, boolean debug,
                                Map<String, Object> debugInfo, String phase, QueryRouter.QueryRoute route)
```
改为：
```java
private RagResult retrieveOnce(Long knowledgeBaseId, QueryIntent queryIntent, String query, int topK, boolean debug,
                                Map<String, Object> debugInfo, String phase, QueryRouter.QueryRoute route)
```

在方法内部，删除第 165 行的重复解析：
```java
// 删除: QueryIntent queryIntent = queryIntentAnalyzer.analyze(originalQuery);
```

保留 debug 信息输出（第 166-169 行）。

- [ ] **Step 3: 更新所有 retrieveOnce 调用点**

第 116 行（first retrieval）和第 133 行（second retrieval）都需要传入 `queryIntent` 而非 `originalQuery`。

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl qknow-module-kmc/qknow-module-kmc-biz -am -q`
Expected: BUILD SUCCESS

---

## Task 2: ColBERT 真 Token-Level 向量升级

**Covers:** implementation_plan.md §2

**Files:**
- Modify: `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/rerank/ColbertScorer.java`

- [ ] **Step 1: 注入 IEmbeddingService**

在 ColbertScorer 中添加构造函数注入：

```java
@Slf4j
@Component
public class ColbertScorer {

    private final ColbertConfig config;
    private final IEmbeddingService embeddingService;

    public ColbertScorer(ColbertConfig config,
                         @org.springframework.beans.factory.annotation.Autowired(required = false)
                         IEmbeddingService embeddingService) {
        this.config = config;
        this.embeddingService = embeddingService;
    }
```

添加 import:
```java
import tech.qiantong.qknow.ai.service.IEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
```

- [ ] **Step 2: 重写 encodeTokens 方法，使用真实 Embedding API**

废弃 hash-based `tokenVector`，改为批量调用 EmbeddingModel：

```java
private List<double[]> encodeTokens(List<String> tokens) {
    if (tokens.isEmpty()) return List.of();

    // 如果没有 EmbeddingService，回退到 hash-based（兼容）
    if (embeddingService == null) {
        List<double[]> vectors = new ArrayList<>();
        for (String token : tokens) {
            vectors.add(tokenVectorHash(token));
        }
        return vectors;
    }

    try {
        // 使用默认平台获取 EmbeddingModel
        EmbeddingModel model = embeddingService.getEmbeddingModel(
                config.getEmbeddingPlatform() != null ? config.getEmbeddingPlatform() : "TongYi",
                config.getEmbeddingBaseUrl(),
                config.getEmbeddingApiKey(),
                config.getEmbeddingModel() != null ? config.getEmbeddingModel() : "text-embedding-v1");

        // 批量获取 token 向量
        float[][] embeddings = model.embed(tokens.toArray(new String[0]));
        List<double[]> vectors = new ArrayList<>();
        for (float[] emb : embeddings) {
            double[] vec = new double[emb.length];
            for (int i = 0; i < emb.length; i++) {
                vec[i] = emb[i];
            }
            vectors.add(vec);
        }
        return vectors;
    } catch (Exception e) {
        log.warn("ColBERT Embedding API 调用失败，回退到 hash-based: {}", e.getMessage());
        List<double[]> vectors = new ArrayList<>();
        for (String token : tokens) {
            vectors.add(tokenVectorHash(token));
        }
        return vectors;
    }
}
```

- [ ] **Step 3: 将原 tokenVector 方法重命名为 tokenVectorHash**

```java
private double[] tokenVectorHash(String token) {
    // ... 原有 hash-based 实现不变 ...
}
```

- [ ] **Step 4: 更新 ColbertConfig 添加 Embedding 配置字段**

```java
@Data
@Component
@ConfigurationProperties(prefix = "qknow.rag.colbert")
public static class ColbertConfig {
    private boolean enabled = false;
    private int ngramSize = 3;
    private int dimensions = 64;
    // 新增: Embedding API 配置
    private String embeddingPlatform;
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private String embeddingModel;
}
```

- [ ] **Step 5: 限制最大 token 数量防止超时**

在 `rerank` 方法中，限制每个文档的最大 token 数：

```java
public List<Document> rerank(String query, List<Document> documents, int topK) {
    if (!config.isEnabled() || documents == null || documents.isEmpty()) {
        return documents;
    }

    List<String> queryTokens = tokenize(query);
    // 限制最大 token 数量
    int maxTokens = config.getMaxTokensPerDoc();
    if (maxTokens > 0 && queryTokens.size() > maxTokens) {
        queryTokens = queryTokens.subList(0, maxTokens);
    }
    List<double[]> queryVectors = encodeTokens(queryTokens);

    List<ScoredDocument> scored = new ArrayList<>();
    for (Document doc : documents) {
        List<String> docTokens = tokenize(doc.getText());
        if (maxTokens > 0 && docTokens.size() > maxTokens) {
            docTokens = docTokens.subList(0, maxTokens);
        }
        List<double[]> docVectors = encodeTokens(docTokens);
        double maxSim = computeMaxSim(queryVectors, docVectors);
        scored.add(new ScoredDocument(doc, maxSim));
    }
    // ... rest unchanged ...
}
```

在 ColbertConfig 中添加：
```java
private int maxTokensPerDoc = 128;
```

- [ ] **Step 6: 编译验证**

Run: `mvn compile -pl qknow-module-kmc/qknow-module-kmc-biz -am -q`
Expected: BUILD SUCCESS

---

## Task 3: GraphRAG LLM Map-Reduce 全局搜索

**Covers:** implementation_plan.md §3

**Files:**
- Modify: `backend/qknow-module-kg/qknow-module-kg-biz/src/main/java/tech/qiantong/qknow/module/kg/service/GraphCommunityService.java`

- [ ] **Step 1: 注入 IChatModelService**

```java
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.neo4j.uri")
public class GraphCommunityService {

    private final Driver driver;
    private final IChatModelService chatModelService;

    public GraphCommunityService(org.neo4j.driver.Driver driver,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false)
                                 IChatModelService chatModelService) {
        this.driver = driver;
        this.chatModelService = chatModelService;
    }
```

添加 import:
```java
import org.springframework.beans.factory.annotation.Autowired;
import tech.qiantong.qknow.ai.service.IChatModelService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.concurrent.*;
import java.util.stream.Collectors;
```

- [ ] **Step 2: 重构 buildSummary — 基于 LLM 的社区摘要**

```java
private String buildSummary(Community community) {
    List<String> entities = community.getEntities() != null ? community.getEntities() : List.of();
    List<String> labels = community.getLabels() != null ? community.getLabels() : List.of();

    // 如果没有 LLM 服务，回退到规则拼接
    if (chatModelService == null) {
        return buildSummaryFallback(community);
    }

    try {
        ChatModel chatModel = chatModelService.getChatModel("DeepSeek", null, null, "deepseek-chat");
        String prompt = String.format("""
                你是一个图谱社区摘要专家。以下是图谱社区中的实体和标签。
                请用一段连贯、有见地的文本总结该社区的总体概念和隐藏联系。
                要求：不超过150字，突出核心主题和实体间的关系。

                实体(%d个): %s
                标签: %s
                """,
                entities.size(),
                entities.stream().limit(20).collect(Collectors.joining("、")),
                labels.stream().filter(Objects::nonNull).distinct().collect(Collectors.joining("、")));

        String response = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是知识图谱社区分析专家。"),
                new UserMessage(prompt)
        ))).getResult().getOutput().getText();

        return response != null && !response.isBlank() ? response.trim() : buildSummaryFallback(community);
    } catch (Exception e) {
        log.warn("LLM 社区摘要生成失败，回退到规则模式: {}", e.getMessage());
        return buildSummaryFallback(community);
    }
}

private String buildSummaryFallback(Community community) {
    List<String> entities = community.getEntities() != null ? community.getEntities() : List.of();
    List<String> labels = community.getLabels() != null ? community.getLabels() : List.of();
    String topEntities = entities.stream().limit(12).collect(Collectors.joining("、"));
    String topLabels = labels.stream().filter(Objects::nonNull).distinct().limit(6)
            .collect(Collectors.joining("、"));
    return "社区 " + community.getId() + " 包含 " + community.getSize()
            + " 个实体；核心实体：" + topEntities
            + (topLabels.isBlank() ? "" : "；主题标签：" + topLabels) + "。";
}
```

- [ ] **Step 3: 重构 globalSearch — LLM Map-Reduce**

```java
public GlobalSearchResult globalSearch(String workspaceId, String query, int topK) {
    List<Community> communities = loadCommunities(workspaceId);
    if (communities.isEmpty()) {
        communities = summarizeCommunities(workspaceId);
    }

    // 选取 Top-K 相关社区
    List<Community> ranked = communities.stream()
            .sorted((a, b) -> Double.compare(score(query, b), score(query, a)))
            .limit(Math.max(1, topK))
            .toList();

    // 如果没有 LLM 服务，回退到简单拼接
    if (chatModelService == null) {
        String answer = ranked.stream()
                .map(Community::getSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return new GlobalSearchResult(answer, ranked);
    }

    // ===== MAP 阶段：并发向每个社区提问 =====
    ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(ranked.size(), Runtime.getRuntime().availableProcessors()));
    List<CompletableFuture<String>> mapFutures = ranked.stream()
            .map(community -> CompletableFuture.supplyAsync(() -> {
                return mapPhase(query, community);
            }, executor))
            .toList();

    // 收集所有非空的局部答复
    List<String> partialAnswers = new ArrayList<>();
    for (CompletableFuture<String> future : mapFutures) {
        try {
            String answer = future.get(30, TimeUnit.SECONDS);
            if (answer != null && !answer.isBlank() && !answer.contains("无关联")) {
                partialAnswers.add(answer);
            }
        } catch (Exception e) {
            log.warn("Map 阶段任务失败: {}", e.getMessage());
        }
    }
    executor.shutdown();

    // 如果没有任何相关社区，回退到简单拼接
    if (partialAnswers.isEmpty()) {
        String fallback = ranked.stream()
                .map(Community::getSummary)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
        return new GlobalSearchResult(fallback, ranked);
    }

    // ===== REDUCE 阶段：综合所有局部答复 =====
    String finalAnswer = reducePhase(query, partialAnswers);
    return new GlobalSearchResult(finalAnswer, ranked);
}

private String mapPhase(String query, Community community) {
    try {
        ChatModel chatModel = chatModelService.getChatModel("DeepSeek", null, null, "deepseek-chat");
        String prompt = String.format("""
                用户问题: %s

                参考社区信息:
                社区ID: %d, 实体数: %d
                摘要: %s

                如果社区信息能回答该问题，请提取关键信息。
                如果不相关，请只回复"无关联"。
                """,
                query, community.getId(), community.getSize(), community.getSummary());

        String response = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是信息提取专家。只输出与问题相关的关键信息，不相关的回复'无关联'。"),
                new UserMessage(prompt)
        ))).getResult().getOutput().getText();

        return (response != null && !response.isBlank()) ? response.trim() : "无关联";
    } catch (Exception e) {
        log.warn("Map 阶段 LLM 调用失败 (community={}): {}", community.getId(), e.getMessage());
        return "无关联";
    }
}

private String reducePhase(String query, List<String> partialAnswers) {
    try {
        ChatModel chatModel = chatModelService.getChatModel("DeepSeek", null, null, "deepseek-chat");
        String context = String.join("\n---\n", partialAnswers);
        String prompt = String.format("""
                用户问题: %s

                以下是从不同图谱社区中提取的部分信息:
                %s

                请作为一个全局分析师，综合这些信息，生成一份全面、深入的最终解答。
                要求：条理清晰，重点突出，不要重复。
                """, query, context);

        String response = chatModel.call(new Prompt(List.of(
                new SystemMessage("你是全局分析师。综合多个信息源，生成高质量的最终解答。"),
                new UserMessage(prompt)
        ))).getResult().getOutput().getText();

        return (response != null && !response.isBlank()) ? response.trim()
                : partialAnswers.stream().collect(Collectors.joining("\n"));
    } catch (Exception e) {
        log.warn("Reduce 阶段 LLM 调用失败: {}", e.getMessage());
        return partialAnswers.stream().collect(Collectors.joining("\n"));
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn compile -pl qknow-module-kg/qknow-module-kg-biz -am -q`
Expected: BUILD SUCCESS

---

## 执行顺序

三个 Task 互相独立，可并行执行。建议顺序：
1. **Task 1** (Dynamic TopK) — 最简单，改 1 个文件
2. **Task 2** (ColBERT) — 中等复杂度，改 1 个文件
3. **Task 3** (GraphRAG) — 最复杂，改 1 个文件

每个 Task 完成后运行 `mvn compile` 验证。
