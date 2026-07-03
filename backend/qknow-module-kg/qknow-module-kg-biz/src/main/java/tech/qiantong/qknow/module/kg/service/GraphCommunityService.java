package tech.qiantong.qknow.module.kg.service;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.service.IChatModelService;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 知识图谱社区检测服务
 * 参考：Microsoft GraphRAG Leiden 社区检测（34k⭐）
 * 参考：LightRAG 轻量级图构建（37k⭐）
 *
 * 使用 Neo4j GDS 库的 Leiden 算法检测实体社区，
 * 为每个社区生成摘要，支持 GraphRAG 式全局问答。
 *
 * 当 IChatModelService 可用时，使用 LLM 进行：
 * 1. 社区摘要生成（LLM理解社区语义）
 * 2. 全局搜索 Map-Reduce（并发局部问答 + 合并最终答案）
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.neo4j.uri")
public class GraphCommunityService {

    private final Driver driver;
    private final IChatModelService chatModelService;

    @org.springframework.beans.factory.annotation.Value("${HERMES_OPENAI_BASE_URL:https://api.deepseek.com}")
    private String llmBaseUrl;

    @org.springframework.beans.factory.annotation.Value("${HERMES_OPENAI_API_KEY:}")
    private String llmApiKey;

    private static final String COMMUNITY_SUMMARY_SYSTEM_PROMPT = """
            你是一个图谱社区摘要专家。以下是图谱社区中的实体和标签。
            请用一段连贯、有见地的文本总结该社区的总体概念和隐藏联系。
            要求：不超过150字，突出核心主题和实体间的关系。
            """;

    private static final String MAP_PROMPT_TEMPLATE = """
            你是一个知识图谱分析助手。基于以下社区摘要信息，回答用户问题。
            如果社区信息与问题无关，请回答"无关联"。
            如果有关，请基于社区摘要提供详细、准确的回答。

            社区摘要：
            %s

            用户问题：
            %s
            """;

    private static final String REDUCE_PROMPT_TEMPLATE = """
            你是一个知识整合专家。以下是针对用户问题从多个知识社区收集到的局部答案。
            请整合这些答案，生成一个全面、连贯、准确的最终回答。
            如果所有答案都是"无关联"，请回答"未找到相关信息"。

            用户问题：
            %s

            局部答案：
            %s

            请提供最终整合答案：
            """;

    public GraphCommunityService(
            org.neo4j.driver.Driver driver,
            @Autowired(required = false) IChatModelService chatModelService) {
        this.driver = driver;
        this.chatModelService = chatModelService;
    }

    /**
     * 执行 Leiden 社区检测
     * 使用 Neo4j GDS 库的 leiden 算法
     */
    public List<Community> detectCommunities(String workspaceId) {
        String graphName = "community-graph-" + workspaceId;
        try (Session session = driver.session()) {
            // 1. 创建投影图（仅包含有效实体和关系）
            session.run("""
                CALL gds.graph.project.cypher(
                    '%s',
                    'MATCH (n:KgNode) WHERE n.workspace_id = %s AND n.del_flag = 0 RETURN id(n) AS id, labels(n) AS labels',
                    'MATCH (a:KgNode)-[r:KgEdge]->(b:KgNode) WHERE a.workspace_id = %s AND b.workspace_id = %s RETURN id(a) AS source, id(b) AS target, type(r) AS type'
                )
                """.formatted(graphName, workspaceId, workspaceId, workspaceId));

            // 2. 执行 Leiden 算法
            // 首先将有向图转为无向边，因为 Leiden 要求无向图
            session.run("""
                CALL gds.graph.relationships.toUndirected(
                  '%s',
                  {
                    relationshipType: 'KgEdge',
                    mutateRelationshipType: 'KgEdge_UNDIRECTED'
                  }
                ) YIELD relationshipsWritten
                """.formatted(graphName));

            var result = session.run("""
                CALL gds.leiden.stream('%s', { relationshipTypes: ['KgEdge_UNDIRECTED'] })
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
            try (Session cleanupSession = driver.session()) {
                cleanupSession.run("CALL gds.graph.drop('%s')".formatted(graphName));
                log.debug("投影图已清理: {}", graphName);
            } catch (Exception e) {
                log.warn("清理投影图失败: {}", graphName, e);
            }
        }
    }

    /**
     * 保存社区到数据库
     */
    public void saveCommunities(String workspaceId, List<Community> communities) {
        try (Session session = driver.session()) {
            for (Community community : communities) {
                session.run("""
                    MERGE (c:Community {workspace_id: $workspaceId, community_id: $communityId})
                    SET c.size = $size,
                        c.entities = $entities,
                        c.labels = $labels,
                        c.summary = $summary,
                        c.updated_at = datetime()
                    """,
                    Values.parameters(
                        "workspaceId", workspaceId,
                        "communityId", community.getId(),
                        "size", community.getSize(),
                        "entities", community.getEntities(),
                        "labels", community.getLabels(),
                        "summary", community.getSummary()
                    ));
            }
            log.info("社区保存完成: workspaceId={}, count={}", workspaceId, communities.size());
        }
    }

    public List<Community> summarizeCommunities(String workspaceId) {
        List<Community> communities = loadCommunities(workspaceId);
        if (communities.isEmpty()) {
            communities = detectCommunities(workspaceId);
        }
        for (Community community : communities) {
            community.setSummary(buildSummary(community));
        }
        saveCommunities(workspaceId, communities);
        return communities;
    }

    /**
     * 全局搜索 - GraphRAG Map-Reduce 模式
     *
     * 当 IChatModelService 可用时：
     * 1. 加载社区并按相关性排名
     * 2. MAP 阶段：并发向每个社区提问
     * 3. REDUCE 阶段：合并所有局部答案生成最终解答
     *
     * 当 IChatModelService 不可用时：回退到简单拼接
     */
    public GlobalSearchResult globalSearch(String workspaceId, String query, int topK) {
        List<Community> communities = loadCommunities(workspaceId);
        if (communities.isEmpty()) {
            communities = summarizeCommunities(workspaceId);
        }
        List<Community> ranked = communities.stream()
                .sorted((a, b) -> Double.compare(score(query, b), score(query, a)))
                .limit(Math.max(1, topK))
                .toList();

        // 如果 LLM 服务不可用，回退到简单拼接
        if (chatModelService == null) {
            log.debug("IChatModelService 不可用，使用简单拼接模式");
            String answer = ranked.stream()
                    .map(Community::getSummary)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            return new GlobalSearchResult(answer, ranked);
        }

        // LLM Map-Reduce 模式
        try {
            ChatModel chatModel = chatModelService.getChatModel(
                    "DeepSeek", llmBaseUrl, llmApiKey, "deepseek-chat");

            // MAP 阶段：并发向每个社区提问
            ExecutorService executor = Executors.newFixedThreadPool(
                    Math.min(ranked.size(), Runtime.getRuntime().availableProcessors()));
            List<CompletableFuture<String>> futures = ranked.stream()
                    .map(community -> CompletableFuture.supplyAsync(
                            () -> mapPhase(chatModel, community, query), executor))
                    .toList();

            // 收集所有结果
            List<String> partialAnswers = futures.stream()
                    .map(future -> {
                        try {
                            return future.get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.warn("MAP 阶段任务失败: {}", e.getMessage());
                            return "无关联";
                        }
                    })
                    .filter(answer -> !"无关联".equals(answer))
                    .toList();

            executor.shutdown();

            // REDUCE 阶段：合并所有局部答案
            if (partialAnswers.isEmpty()) {
                return new GlobalSearchResult("未找到相关信息", ranked);
            }

            String finalAnswer = reducePhase(chatModel, query, partialAnswers);
            return new GlobalSearchResult(finalAnswer, ranked);

        } catch (Exception e) {
            log.error("LLM Map-Reduce 搜索失败，回退到简单拼接: {}", e.getMessage());
            String answer = ranked.stream()
                    .map(Community::getSummary)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            return new GlobalSearchResult(answer, ranked);
        }
    }

    /**
     * MAP 阶段：向单个社区提问
     */
    private String mapPhase(ChatModel chatModel, Community community, String query) {
        try {
            String communityInfo = community.getSummary();
            if (communityInfo == null || communityInfo.isBlank()) {
                return "无关联";
            }

            String promptText = MAP_PROMPT_TEMPLATE.formatted(communityInfo, query);
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个知识图谱分析助手。"));
            messages.add(new UserMessage(promptText));

            ChatResponse response = chatModel.call(new Prompt(messages));
            String answer = response.getResult().getOutput().getText();

            if (answer == null || answer.isBlank() || answer.contains("无关联")) {
                return "无关联";
            }
            return answer;
        } catch (Exception e) {
            log.warn("MAP 阶段处理社区 {} 失败: {}", community.getId(), e.getMessage());
            return "无关联";
        }
    }

    /**
     * REDUCE 阶段：合并所有局部答案
     */
    private String reducePhase(ChatModel chatModel, String query, List<String> partialAnswers) {
        try {
            String answersText = partialAnswers.stream()
                    .map(answer -> "- " + answer)
                    .collect(Collectors.joining("\n"));

            String promptText = REDUCE_PROMPT_TEMPLATE.formatted(query, answersText);
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个知识整合专家。"));
            messages.add(new UserMessage(promptText));

            ChatResponse response = chatModel.call(new Prompt(messages));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("REDUCE 阶段失败: {}", e.getMessage());
            return String.join("\n\n", partialAnswers);
        }
    }

    private List<Community> loadCommunities(String workspaceId) {
        try (Session session = driver.session()) {
            var result = session.run("""
                MATCH (c:Community {workspace_id: $workspaceId})
                RETURN c.community_id AS communityId, c.size AS size, c.entities AS entities,
                       c.labels AS labels, c.summary AS summary
                ORDER BY c.size DESC
                """, Values.parameters("workspaceId", workspaceId));
            List<Community> communities = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                Community community = new Community();
                community.setId(record.get("communityId").asLong());
                community.setSize(record.get("size").asInt(0));
                community.setEntities(toStringList(record.get("entities")));
                community.setLabels(toStringList(record.get("labels")));
                community.setSummary(record.get("summary").isNull() ? null : record.get("summary").asString());
                if (community.getSummary() == null || community.getSummary().isBlank()) {
                    community.setSummary(buildSummary(community));
                }
                communities.add(community);
            }
            return communities;
        }
    }

    private List<String> toStringList(Value value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        return value.asList(v -> v.isNull() ? "" : v.asString()).stream()
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    /**
     * 构建社区摘要
     * 优先使用 LLM 生成语义摘要，不可用时回退到规则拼接
     */
    private String buildSummary(Community community) {
        // 如果 LLM 服务不可用，回退到规则拼接
        if (chatModelService == null) {
            return buildSummaryFallback(community);
        }

        try {
            ChatModel chatModel = chatModelService.getChatModel(
                    "DeepSeek", llmBaseUrl, llmApiKey, "deepseek-chat");

            String entities = community.getEntities() != null
                    ? String.join("、", community.getEntities().stream().limit(20).toList())
                    : "";
            String labels = community.getLabels() != null
                    ? String.join("、", community.getLabels().stream().filter(Objects::nonNull).distinct().limit(10).toList())
                    : "";

            String promptText = COMMUNITY_SUMMARY_SYSTEM_PROMPT
                    + "\n\n实体: " + entities + "\n标签: " + labels;

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(COMMUNITY_SUMMARY_SYSTEM_PROMPT));
            messages.add(new UserMessage("实体: " + entities + "\n标签: " + labels));

            ChatResponse response = chatModel.call(new Prompt(messages));
            String summary = response.getResult().getOutput().getText();

            if (summary != null && !summary.isBlank()) {
                return summary.trim();
            }
        } catch (Exception e) {
            log.warn("LLM 社区摘要生成失败，回退到规则拼接: {}", e.getMessage());
        }

        return buildSummaryFallback(community);
    }

    /**
     * 回退方法：使用规则拼接生成摘要
     */
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

    private double score(String query, Community community) {
        if (query == null || query.isBlank()) {
            return community.getSize();
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        double score = Math.log1p(community.getSize());
        for (String entity : community.getEntities() != null ? community.getEntities() : List.<String>of()) {
            if (entity != null && normalized.contains(entity.toLowerCase(Locale.ROOT))) {
                score += 3.0;
            }
        }
        String summary = community.getSummary();
        if (summary != null) {
            String summaryText = summary.toLowerCase(Locale.ROOT);
            for (String token : normalized.split("\\s+")) {
                if (!token.isBlank() && summaryText.contains(token)) {
                    score += 1.0;
                }
            }
        }
        return score;
    }

    public record GlobalSearchResult(String answer, List<Community> communities) {
    }

    /**
     * 社区数据模型
     */
    public static class Community {
        private long id;
        private List<String> entities;
        private List<String> labels;
        private int size;
        private String summary;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public List<String> getEntities() { return entities; }
        public void setEntities(List<String> entities) { this.entities = entities; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
}
