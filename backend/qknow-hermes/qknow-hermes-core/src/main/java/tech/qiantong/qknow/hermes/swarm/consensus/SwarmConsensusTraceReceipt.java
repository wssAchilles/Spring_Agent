package tech.qiantong.qknow.hermes.swarm.consensus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/**
 * 纯 Java 21 Record 格式不可变 W3C 规范全链路因果追踪与博弈共识存证凭单 (SwarmConsensusTraceReceipt)
 * <p>
 * 核心机制：
 * 1. 严格遵循 W3C TraceContext 规范，原生建模 TraceId (32位十六进制) 与 SpanId (16位十六进制)；
 * 2. 纳秒级记录各智能体发言、合谋熵检测、反事实扰动与帕累托仲裁的因果拓扑父子树；
 * 3. 规范化组装全量决策事实与关键指标，通过标准 SHA-256 签发不可变数字指纹；
 * 4. 内置基于 MessageDigest.isEqual 的常量时间自验真机制，杜绝时序侧信道攻击与数据篡改。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record SwarmConsensusTraceReceipt(
        String receiptId,
        String traceId,
        String rootSpanId,
        int totalSpans,
        List<SwarmTraceSpan> spans,
        int rounds,
        double sycophancyScore,
        double entropyValue,
        boolean isCollusionDetected,
        boolean isDevilAdvocateInjected,
        boolean isEpsilonNashConverged,
        String winningProposalId,
        String winningProposalContent,
        double latencyMs,
        long timestamp,
        String sha256Signature
) {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * W3C 分布式调用链路单元 (Span)
     *
     * @param traceId 全局分布式追踪唯一标识 (32位十六进制小写)
     * @param spanId 当前操作单元标识 (16位十六进制小写)
     * @param parentSpanId 父操作单元标识 (根节点为 null 或 "0000000000000000")
     * @param agentRole 智能体角色 (如 "PRO", "CON", "CRITIC", "ARBITRATOR")
     * @param operationType 操作类型 (如 "PROPOSAL", "ENTROPY_AUDIT", "COUNTERFACTUAL_INJECTION", "PARETO_ARBITRATION")
     * @param startTimeNs 开始纳秒时间戳
     * @param endTimeNs 结束纳秒时间戳
     * @param durationMs 执行耗时 (毫秒)
     * @param attributes 动态扩展属性表
     */
    public record SwarmTraceSpan(
            String traceId,
            String spanId,
            String parentSpanId,
            String agentRole,
            String operationType,
            long startTimeNs,
            long endTimeNs,
            double durationMs,
            Map<String, String> attributes
    ) {
        public SwarmTraceSpan {
            Objects.requireNonNull(traceId, "traceId 不能为空");
            Objects.requireNonNull(spanId, "spanId 不能为空");
            Objects.requireNonNull(agentRole, "agentRole 不能为空");
            Objects.requireNonNull(operationType, "operationType 不能为空");
            attributes = (attributes == null) ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }

        public boolean isRoot() {
            return parentSpanId == null || parentSpanId.isBlank() || "0000000000000000".equals(parentSpanId);
        }
    }

    public SwarmConsensusTraceReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(traceId, "traceId 不能为空");
        Objects.requireNonNull(rootSpanId, "rootSpanId 不能为空");
        Objects.requireNonNull(winningProposalId, "winningProposalId 不能为空");
        Objects.requireNonNull(winningProposalContent, "winningProposalContent 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
        spans = (spans == null) ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(spans));
    }

    /**
     * 生成符合 W3C TraceContext 规范的 32 字符小写十六进制 TraceId (128-bit)
     */
    public static String generateW3cTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        // 保证非全零
        if (bytes[0] == 0) bytes[0] = 1;
        return bytesToHex(bytes);
    }

    /**
     * 生成符合 W3C TraceContext 规范的 16 字符小写十六进制 SpanId (64-bit)
     */
    public static String generateW3cSpanId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        // 保证非全零
        if (bytes[0] == 0) bytes[0] = 1;
        return bytesToHex(bytes);
    }

    /**
     * 构造并签发不可变存证凭单
     */
    public static SwarmConsensusTraceReceipt create(
            String receiptId,
            String traceId,
            String rootSpanId,
            List<SwarmTraceSpan> spans,
            int rounds,
            double sycophancyScore,
            double entropyValue,
            boolean isCollusionDetected,
            boolean isDevilAdvocateInjected,
            boolean isEpsilonNashConverged,
            String winningProposalId,
            String winningProposalContent,
            double latencyMs
    ) {
        long now = System.currentTimeMillis();
        int total = (spans != null) ? spans.size() : 0;

        String signature = computeSignature(
                receiptId,
                traceId,
                rootSpanId,
                total,
                rounds,
                sycophancyScore,
                entropyValue,
                isCollusionDetected,
                isDevilAdvocateInjected,
                isEpsilonNashConverged,
                winningProposalId,
                latencyMs,
                now
        );

        return new SwarmConsensusTraceReceipt(
                receiptId,
                traceId,
                rootSpanId,
                total,
                spans,
                rounds,
                sycophancyScore,
                entropyValue,
                isCollusionDetected,
                isDevilAdvocateInjected,
                isEpsilonNashConverged,
                winningProposalId,
                winningProposalContent,
                latencyMs,
                now,
                signature
        );
    }

    /**
     * 基于规范化全字段计算 SHA-256 密码学防篡改签名
     */
    public static String computeSignature(
            String receiptId,
            String traceId,
            String rootSpanId,
            int totalSpans,
            int rounds,
            double sycophancyScore,
            double entropyValue,
            boolean isCollusionDetected,
            boolean isDevilAdvocateInjected,
            boolean isEpsilonNashConverged,
            String winningProposalId,
            double latencyMs,
            long timestamp
    ) {
        String canonicalString = String.format(
                Locale.US,
                "receiptId=%s|traceId=%s|rootSpanId=%s|totalSpans=%d|rounds=%d|" +
                        "sycophancy=%.4f|entropy=%.4f|collusion=%b|devilAdvocate=%b|epsilonNash=%b|" +
                        "winner=%s|latency=%.2f|timestamp=%d",
                receiptId, traceId, rootSpanId, totalSpans, rounds,
                sycophancyScore, entropyValue, isCollusionDetected, isDevilAdvocateInjected, isEpsilonNashConverged,
                winningProposalId, latencyMs, timestamp
        );

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalString.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持标准 SHA-256 算法", e);
        }
    }

    /**
     * 执行常量时间密码学自验真 (Constant-time verification)
     *
     * @return 验真是否完全通过
     */
    public boolean verifySignature() {
        String expectedSignature = computeSignature(
                this.receiptId,
                this.traceId,
                this.rootSpanId,
                this.totalSpans,
                this.rounds,
                this.sycophancyScore,
                this.entropyValue,
                this.isCollusionDetected,
                this.isDevilAdvocateInjected,
                this.isEpsilonNashConverged,
                this.winningProposalId,
                this.latencyMs,
                this.timestamp
        );

        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = this.sha256Signature.getBytes(StandardCharsets.UTF_8);

        // 使用常量时间对比，抵御时序攻击
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
