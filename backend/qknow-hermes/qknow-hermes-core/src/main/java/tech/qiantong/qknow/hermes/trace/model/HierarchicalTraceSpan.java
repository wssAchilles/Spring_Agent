package tech.qiantong.qknow.hermes.trace.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 层次化分布式追踪片段模型 (Hierarchical Trace Span)
 * <p>
 * 采用纯 Java 21 Record 封装，天然不可变且线程安全。
 * 内置超过 1KB 大文本自适应摘要化与 SHA-256 指纹锚定机制，杜绝追踪数据吞噬 JVM 堆内存。
 * </p>
 *
 * @param spanId        片段全局唯一 ID
 * @param traceId       追踪调用链全局唯一 ID
 * @param parentSpanId  父级片段 ID (根节点为 null)
 * @param spanName      片段名称 (如 "DeepSeek-V3-IntentParser", "Tool-McpExecution")
 * @param spanType      片段业务类型 (如 "AGENT_REASONING", "SWARM_DEBATE", "TOOL_MCP", "RAG_RETRIEVAL", "SUBGRAPH_PPR")
 * @param startNano     单调时钟起始纳秒时间戳 (基于 System.nanoTime())
 * @param durationUs    执行总耗时 (微秒)
 * @param tokenCount    本阶段消耗 Token 数
 * @param status        执行状态 ("SUCCESS", "FAILED", "SUSPENDED")
 * @param summaryInput  入参摘要 (超长自动截断并标记 SHA-256)
 * @param summaryOutput 出参摘要 (超长自动截断并标记 SHA-256)
 * @param attributes    扩展键值属性集
 * @author Achilles
 * @version 1.0
 */
public record HierarchicalTraceSpan(
        String spanId,
        String traceId,
        String parentSpanId,
        String spanName,
        String spanType,
        long startNano,
        long durationUs,
        int tokenCount,
        String status,
        String summaryInput,
        String summaryOutput,
        Map<String, String> attributes
) {
    public static final int MAX_SUMMARY_LENGTH = 1024; // 1KB 硬限额
    public static final int PRESERVE_HEAD_LENGTH = 256;

    public HierarchicalTraceSpan {
        Objects.requireNonNull(spanId, "spanId 不能为空");
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(spanName, "spanName 不能为空");
        Objects.requireNonNull(spanType, "spanType 不能为空");
        Objects.requireNonNull(status, "status 不能为空");

        summaryInput = sanitizeSummary(summaryInput);
        summaryOutput = sanitizeSummary(summaryOutput);
        attributes = attributes != null ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
    }

    /**
     * 超长文本自适应摘要化与 SHA-256 指纹锚定
     *
     * @param rawText 原始长文本
     * @return 截断后的安全摘要
     */
    public static String sanitizeSummary(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        if (rawText.length() <= MAX_SUMMARY_LENGTH) {
            return rawText;
        }

        String head = rawText.substring(0, PRESERVE_HEAD_LENGTH);
        String hash = computeSha256(rawText);
        return String.format("%s... [TRUNCATED len=%d sha256=%s]", head, rawText.length(), hash);
    }

    private static String computeSha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) { // 取前 8 字节 16 位十六进制
                String hex = Integer.toHexString(0xff & digest[i]);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "hash_unavailable";
        }
    }
}
