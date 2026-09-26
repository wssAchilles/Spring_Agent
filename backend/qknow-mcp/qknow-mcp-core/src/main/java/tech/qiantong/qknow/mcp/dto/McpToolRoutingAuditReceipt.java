package tech.qiantong.qknow.mcp.dto;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 纯 Java 21 Record 格式的大规模企业级 MCP 工具路由与流式质心蒸馏不可变审计凭单
 * 包含 SHA-256 签名自验真机制，严格采用 MessageDigest.isEqual 常量时间比对防时序侧信道攻击
 */
public record McpToolRoutingAuditReceipt(
        String receiptId,
        String tenantId,
        String taskId,
        String traceId,
        String queryHash,
        int totalRegisteredTools,
        int totalVoronoiCells,
        int scannedVoronoiCellsCount,
        List<String> candidateToolIds,
        String selectedToolId,
        double selectedUcbScore,
        double selectedCosineSimilarity,
        int rawPayloadTokens,
        int distilledPayloadTokens,
        double compressionRatio,
        double semanticFidelity,
        long routingLatencyNanos,
        long distillationLatencyNanos,
        long timestampEpochMs,
        String signatureSha256
) implements Serializable {

    public McpToolRoutingAuditReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(candidateToolIds, "candidateToolIds 不能为空");
        Objects.requireNonNull(signatureSha256, "signatureSha256 不能为空");
    }

    /**
     * 静态工厂方法，自动计算并填充 SHA-256 不可变自验真签名
     */
    public static McpToolRoutingAuditReceipt create(
            String receiptId,
            String tenantId,
            String taskId,
            String traceId,
            String queryHash,
            int totalRegisteredTools,
            int totalVoronoiCells,
            int scannedVoronoiCellsCount,
            List<String> candidateToolIds,
            String selectedToolId,
            double selectedUcbScore,
            double selectedCosineSimilarity,
            int rawPayloadTokens,
            int distilledPayloadTokens,
            double compressionRatio,
            double semanticFidelity,
            long routingLatencyNanos,
            long distillationLatencyNanos,
            long timestampEpochMs
    ) {
        String signature = calculateSignature(
                receiptId, tenantId, taskId, traceId, queryHash,
                totalRegisteredTools, totalVoronoiCells, scannedVoronoiCellsCount,
                candidateToolIds, selectedToolId, selectedUcbScore, selectedCosineSimilarity,
                rawPayloadTokens, distilledPayloadTokens, compressionRatio, semanticFidelity,
                routingLatencyNanos, distillationLatencyNanos, timestampEpochMs
        );

        return new McpToolRoutingAuditReceipt(
                receiptId,
                tenantId,
                taskId,
                traceId != null ? traceId : "trace-default",
                queryHash != null ? queryHash : "hash-none",
                totalRegisteredTools,
                totalVoronoiCells,
                scannedVoronoiCellsCount,
                List.copyOf(candidateToolIds),
                selectedToolId != null ? selectedToolId : "NONE",
                selectedUcbScore,
                selectedCosineSimilarity,
                rawPayloadTokens,
                distilledPayloadTokens,
                compressionRatio,
                semanticFidelity,
                routingLatencyNanos,
                distillationLatencyNanos,
                timestampEpochMs,
                signature
        );
    }

    /**
     * 常量时间校验 SHA-256 签名真实性与数据完整性，抵御时序侧信道嗅探攻击
     */
    public boolean verifySignatureConstantTime() {
        if (signatureSha256 == null || signatureSha256.isBlank()) {
            return false;
        }
        String expected = calculateSignature(
                receiptId, tenantId, taskId, traceId, queryHash,
                totalRegisteredTools, totalVoronoiCells, scannedVoronoiCellsCount,
                candidateToolIds, selectedToolId, selectedUcbScore, selectedCosineSimilarity,
                rawPayloadTokens, distilledPayloadTokens, compressionRatio, semanticFidelity,
                routingLatencyNanos, distillationLatencyNanos, timestampEpochMs
        );
        return MessageDigest.isEqual(
                signatureSha256.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 计算核心字段 SHA-256 摘要
     */
    public static String calculateSignature(
            String receiptId,
            String tenantId,
            String taskId,
            String traceId,
            String queryHash,
            int totalRegisteredTools,
            int totalVoronoiCells,
            int scannedVoronoiCellsCount,
            List<String> candidateToolIds,
            String selectedToolId,
            double selectedUcbScore,
            double selectedCosineSimilarity,
            int rawPayloadTokens,
            int distilledPayloadTokens,
            double compressionRatio,
            double semanticFidelity,
            long routingLatencyNanos,
            long distillationLatencyNanos,
            long timestampEpochMs
    ) {
        String payload = String.format(
                "%s|%s|%s|%s|%s|%d|%d|%d|%s|%s|%.6f|%.6f|%d|%d|%.4f|%.6f|%d|%d|%d",
                receiptId, tenantId, taskId, traceId, queryHash,
                totalRegisteredTools, totalVoronoiCells, scannedVoronoiCellsCount,
                String.join(",", candidateToolIds != null ? candidateToolIds : List.of()),
                selectedToolId != null ? selectedToolId : "",
                selectedUcbScore, selectedCosineSimilarity,
                rawPayloadTokens, distilledPayloadTokens, compressionRatio, semanticFidelity,
                routingLatencyNanos, distillationLatencyNanos, timestampEpochMs
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法在当前 JVM 中不可用", e);
        }
    }
}
