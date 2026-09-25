package tech.qiantong.qknow.hermes.rag.cognitive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * GraphRAG 认知增强与因果路径密码学存证凭单 (Phase 137 防线三)
 * <p>
 * 纯 Java 21 Record 格式不可变载荷，内嵌 SHA-256 密码学自签名与常数时间防篡改验真。
 * 记录金字塔层级覆盖度、因果推演最短路径、DeepSeek 双轨思考链 Prompt、噪声过滤率与执行延迟。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record GraphRagCognitiveAugmentationReceipt(
        String receiptId,
        String query,
        Map<String, Integer> pyramidLevelCoverage,
        List<String> causalPath,
        int selectedNodeCount,
        double filteredNoiseRatio,
        String deepSeekThinkingPrompt,
        double latencyMs,
        long timestamp,
        String sha256Signature
) {
    public GraphRagCognitiveAugmentationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(pyramidLevelCoverage, "pyramidLevelCoverage 不能为空");
        Objects.requireNonNull(causalPath, "causalPath 不能为空");
        Objects.requireNonNull(deepSeekThinkingPrompt, "deepSeekThinkingPrompt 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
        pyramidLevelCoverage = Collections.unmodifiableMap(new TreeMap<>(pyramidLevelCoverage));
        causalPath = List.copyOf(causalPath);
    }

    /**
     * 静态工厂方法，自动计算 SHA-256 密码学签名
     */
    public static GraphRagCognitiveAugmentationReceipt create(
            String query,
            Map<String, Integer> pyramidLevelCoverage,
            List<String> causalPath,
            int selectedNodeCount,
            double filteredNoiseRatio,
            String deepSeekThinkingPrompt,
            double latencyMs
    ) {
        String receiptId = "RCP-COGNITIVE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        long timestamp = System.currentTimeMillis();
        String safeQuery = query != null ? query : "EMPTY_QUERY";
        Map<String, Integer> safeCoverage = pyramidLevelCoverage != null ? new TreeMap<>(pyramidLevelCoverage) : Map.of();
        List<String> safePath = causalPath != null ? List.copyOf(causalPath) : List.of();
        String safePrompt = deepSeekThinkingPrompt != null ? deepSeekThinkingPrompt : "";

        String canonicalPayload = buildCanonicalPayload(
                receiptId, safeQuery, safeCoverage, safePath, selectedNodeCount,
                filteredNoiseRatio, safePrompt, latencyMs, timestamp
        );
        String signature = computeSha256(canonicalPayload);

        return new GraphRagCognitiveAugmentationReceipt(
                receiptId,
                safeQuery,
                safeCoverage,
                safePath,
                selectedNodeCount,
                filteredNoiseRatio,
                safePrompt,
                latencyMs,
                timestamp,
                signature
        );
    }

    /**
     * 针对 DeepSeek 长思考双轨认知增强生成结构化 Prompt 上下文
     *
     * @param query     用户输入问题
     * @param path      因果路径节点顺序
     * @param edges     因果关系边
     * @return 格式化好的推理链注入上下文
     */
    public static String generateDeepSeekThinkingContext(
            String query,
            List<String> path,
            List<SteinerCausalPathReranker.CausalEdge> edges
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("<causal_thinking_grounding>\n");
        sb.append("## 核心推理目标 (Query): ").append(query != null ? query : "").append("\n");
        sb.append("## 因果拓扑演化链 (Causal Pathway):\n");
        if (path != null && !path.isEmpty()) {
            sb.append(String.join(" -> ", path)).append("\n");
        } else {
            sb.append("无显著因果关联链路\n");
        }
        sb.append("## 因果关系事实三元组 (Causal Triples):\n");
        if (edges != null) {
            for (SteinerCausalPathReranker.CausalEdge e : edges) {
                sb.append(String.format("- (%s) -[%s]-> (%s) [因果可达性=%s, 距离=%.3f]\n",
                        e.source(), e.relation(), e.target(), e.hasCausalReachability(), e.rawDistance()));
            }
        }
        sb.append("## 认知演绎指引:\n");
        sb.append("请严格遵循上述因果拓扑链在思维链 (Thinking Process) 中进行反事实与因果推演，杜绝语义虚假关联与幻觉外推。\n");
        sb.append("</causal_thinking_grounding>\n");
        return sb.toString();
    }

    /**
     * 验证凭单 SHA-256 签名，采用常量时间比较以防止时序侧信道攻击
     */
    public boolean verifySignature() {
        String expectedPayload = buildCanonicalPayload(
                receiptId, query, pyramidLevelCoverage, causalPath, selectedNodeCount,
                filteredNoiseRatio, deepSeekThinkingPrompt, latencyMs, timestamp
        );
        String expectedSignature = computeSha256(expectedPayload);
        return MessageDigest.isEqual(
                sha256Signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String buildCanonicalPayload(
            String receiptId,
            String query,
            Map<String, Integer> coverage,
            List<String> path,
            int nodes,
            double noiseRatio,
            String prompt,
            double latencyMs,
            long timestamp
    ) {
        return receiptId + "|" +
                query + "|" +
                coverage.toString() + "|" +
                String.join(",", path) + "|" +
                nodes + "|" +
                String.format(Locale.ROOT, "%.4f", noiseRatio) + "|" +
                computeSha256(prompt) + "|" +
                String.format(Locale.ROOT, "%.4f", latencyMs) + "|" +
                timestamp;
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法在标准 JVM 中不可用", e);
        }
    }
}
