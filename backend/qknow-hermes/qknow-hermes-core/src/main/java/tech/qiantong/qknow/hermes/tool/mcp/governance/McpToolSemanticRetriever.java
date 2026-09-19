package tech.qiantong.qknow.hermes.tool.mcp.governance;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿里千问 1536 维超球面 MCP 工具动态语义检索器与 Schema 安全裁剪器 (定理 1.1)
 * 在单位超球面 S^{1535} 上基于测地余弦内积实现 MIPS 极速检索与 Top-K 按需投影，消除上下文膨胀
 */
@Component
public class McpToolSemanticRetriever {

    private static final Logger log = LoggerFactory.getLogger(McpToolSemanticRetriever.class);

    /**
     * 阿里千问 1536 维超球面向量固定维度
     */
    public static final int EMBEDDING_DIM = 1536;

    /**
     * 默认检索深度 K
     */
    public static final int DEFAULT_TOP_K = 5;

    // 内存工具超球面向量索引: toolCode -> float[1536] (严格 L2 单位归一化)
    private final Map<String, float[]> toolVectors = new ConcurrentHashMap<>();

    // 内存工具原始元数据缓存: toolCode -> ToolMetadata
    private final Map<String, ToolMetadata> toolMetadataMap = new ConcurrentHashMap<>();

    /**
     * 工具元数据记录
     */
    public record ToolMetadata(
            String toolCode,
            String description,
            Map<String, Object> inputSchema
    ) {}

    /**
     * 投影结果对象
     */
    public record ToolProjectionResult(
            List<String> selectedTools,
            Map<String, Map<String, Object>> prunedSchemas,
            double compressionRatio
    ) {}

    /**
     * 注册或更新工具向量与元数据
     *
     * @param toolCode    工具全局唯一标识 (如 mcp.github.create_issue)
     * @param description 工具职责描述
     * @param inputSchema 原始 JSON Schema
     * @param vector      阿里千问 1536 维嵌入向量
     */
    public void indexTool(String toolCode, String description, Map<String, Object> inputSchema, float[] vector) {
        if (toolCode == null || toolCode.isBlank()) {
            return;
        }

        toolMetadataMap.put(toolCode, new ToolMetadata(
                toolCode,
                description != null ? description : "",
                inputSchema != null ? Map.copyOf(inputSchema) : Map.of()
        ));

        if (vector != null && vector.length == EMBEDDING_DIM) {
            toolVectors.put(toolCode, normalize(vector));
            log.debug("[McpRetriever] 已建立超球面向量索引: {}", toolCode);
        } else {
            log.warn("[McpRetriever] 注册工具 {} 向量为空或维度不匹配 (期望 1536)", toolCode);
        }
    }

    /**
     * 注销工具
     *
     * @param toolCode 工具标识
     */
    public void removeTool(String toolCode) {
        if (toolCode != null) {
            toolVectors.remove(toolCode);
            toolMetadataMap.remove(toolCode);
            log.debug("[McpRetriever] 已移除工具索引: {}", toolCode);
        }
    }

    /**
     * 获取当前注册的所有工具标识
     */
    public Set<String> getAllToolCodes() {
        return Collections.unmodifiableSet(toolMetadataMap.keySet());
    }

    /**
     * 获取注册的工具总数
     */
    public int getRegisteredToolCount() {
        return toolMetadataMap.size();
    }

    /**
     * 在超球面 S^{1535} 上执行 MIPS 检索，选出 Top-K 最优工具标识列表
     *
     * @param queryVector 用户意图查询向量 (1536 维)
     * @param k           检索深度 K (默认 5)
     * @return 按相似度从高到低排序的工具代码列表
     */
    public List<String> retrieveTopK(float[] queryVector, int k) {
        int targetK = k > 0 ? k : DEFAULT_TOP_K;
        if (toolVectors.isEmpty()) {
            return List.copyOf(toolMetadataMap.keySet());
        }

        if (queryVector == null || queryVector.length != EMBEDDING_DIM) {
            // 无有效查询向量时，返回默认前 targetK 个工具
            return toolMetadataMap.keySet().stream().limit(targetK).toList();
        }

        float[] normalizedQuery = normalize(queryVector);

        // 使用固定容量为 targetK 的最小堆维护 Top-K
        PriorityQueue<ScoredTool> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a.score));

        for (Map.Entry<String, float[]> entry : toolVectors.entrySet()) {
            double cosineSimilarity = dotProduct(normalizedQuery, entry.getValue());
            if (pq.size() < targetK) {
                pq.offer(new ScoredTool(entry.getKey(), cosineSimilarity));
            } else if (cosineSimilarity > pq.peek().score) {
                pq.poll();
                pq.offer(new ScoredTool(entry.getKey(), cosineSimilarity));
            }
        }

        List<ScoredTool> scoredList = new ArrayList<>();
        while (!pq.isEmpty()) {
            scoredList.add(pq.poll());
        }
        // 倒序排列，得分最高者在前
        Collections.reverse(scoredList);

        return scoredList.stream().map(s -> s.toolCode).toList();
    }

    /**
     * 执行意图驱动的工具全流程投影：Top-K 语义检索 + 安全 Schema 裁剪 + 压缩率计算
     *
     * @param queryVector 用户意图向量
     * @param k           检索深度
     * @return 投影结果
     */
    public ToolProjectionResult projectTools(float[] queryVector, int k) {
        List<String> selectedToolCodes = retrieveTopK(queryVector, k);
        Map<String, Map<String, Object>> prunedSchemas = new LinkedHashMap<>();

        long totalOriginalTokens = 0;
        long totalPrunedTokens = 0;

        // 计算全量工具总 Token 估算
        for (ToolMetadata meta : toolMetadataMap.values()) {
            totalOriginalTokens += estimateTokens(meta.inputSchema());
        }

        // 对选中的 Top-K 工具执行裁剪
        for (String code : selectedToolCodes) {
            ToolMetadata meta = toolMetadataMap.get(code);
            if (meta != null) {
                Map<String, Object> pruned = pruneInputSchema(meta.inputSchema());
                prunedSchemas.put(code, pruned);
                totalPrunedTokens += estimateTokens(pruned);
            }
        }

        double compressionRatio = 0.0;
        if (totalOriginalTokens > 0) {
            compressionRatio = Math.max(0.0, 1.0 - ((double) totalPrunedTokens / (double) totalOriginalTokens));
        }

        return new ToolProjectionResult(selectedToolCodes, prunedSchemas, compressionRatio);
    }

    /**
     * 安全 Schema 裁剪算子 (Safe Schema Pruner)
     * 严格保留 type, properties, required, enum，剔除 $schema, title, examples, additionalProperties
     * 并对超过 120 字符的冗长说明执行平滑截断，确保模型专注度
     *
     * @param originalSchema 原始 JSON Schema
     * @return 裁剪后的轻量 JSON Schema
     */
    public Map<String, Object> pruneInputSchema(Map<String, Object> originalSchema) {
        if (originalSchema == null || originalSchema.isEmpty()) {
            return Map.of("type", "object", "properties", Map.of());
        }

        JSONObject pruned = new JSONObject();
        pruned.put("type", originalSchema.getOrDefault("type", "object"));

        if (originalSchema.containsKey("properties")) {
            Object propsObj = originalSchema.get("properties");
            if (propsObj instanceof Map<?, ?> properties) {
                JSONObject prunedProps = new JSONObject();

                for (Map.Entry<?, ?> entry : properties.entrySet()) {
                    String propName = String.valueOf(entry.getKey());
                    if (entry.getValue() instanceof Map<?, ?> rawPropDef) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> propDef = (Map<String, Object>) rawPropDef;
                        JSONObject p = new JSONObject();
                        p.put("type", propDef.getOrDefault("type", "string"));

                        if (propDef.containsKey("description")) {
                            String desc = String.valueOf(propDef.get("description"));
                            // 限制属性描述长度至 120 字符
                            if (desc.length() > 120) {
                                p.put("description", desc.substring(0, 117) + "...");
                            } else {
                                p.put("description", desc);
                            }
                        }

                        if (propDef.containsKey("enum")) {
                            p.put("enum", propDef.get("enum"));
                        }

                        prunedProps.put(propName, p);
                    }
                }
                pruned.put("properties", prunedProps);
            }
        }

        if (originalSchema.containsKey("required")) {
            pruned.put("required", originalSchema.get("required"));
        }

        return pruned;
    }

    /**
     * 估算 Schema 对象的 Token 消耗 (依据字符数/4 的经验法则)
     */
    private long estimateTokens(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return 10L;
        }
        String json = JSONObject.toJSONString(schema);
        return Math.max(1L, json.length() / 4);
    }

    /**
     * 向量单位超球面 L2 归一化 (||v||_2 = 1.0)
     */
    private float[] normalize(float[] v) {
        double sumSq = 0.0;
        for (float val : v) {
            sumSq += val * val;
        }
        double norm = Math.sqrt(sumSq);
        if (norm < 1e-9) {
            return v.clone();
        }
        float[] normV = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            normV[i] = (float) (v[i] / norm);
        }
        return normV;
    }

    /**
     * SIMD 风格余弦点积计算
     */
    private double dotProduct(float[] a, float[] b) {
        double dot = 0.0;
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    private record ScoredTool(String toolCode, double score) {}
}
