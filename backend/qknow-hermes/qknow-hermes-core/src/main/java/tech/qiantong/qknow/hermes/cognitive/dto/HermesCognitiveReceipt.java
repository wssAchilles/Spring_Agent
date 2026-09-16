package tech.qiantong.qknow.hermes.cognitive.dto;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * 不可变认知审计执行凭单 (Java 21 Record)
 * 内置 SHA-256 密码学防篡改自签名与 verifySignature() 验真方法
 *
 * @param receiptId           凭单唯一标识
 * @param requestId           业务请求 ID
 * @param sessionId           会话 ID
 * @param strategy            最终采纳的认知推理策略
 * @param taskComplexityScore 任务综合复杂度得分 [0, 1]
 * @param cotLatencyNanos     思维链评估推理耗时纳秒
 * @param reflexionRoundCount 工具自省反思轮次
 * @param alignedMemoryCount  时序对齐有效召回记忆数量
 * @param busStatus           总线运行状态 (ACTIVE_NOMINAL / DEGRADED_FALLBACK_DIRECT)
 * @param qwenGeodesicScore   千问超球面测地相似度
 * @param timestampNanos      凭单签发纳秒时间戳
 * @param signatureSha256     SHA-256 密码学自签名
 */
public record HermesCognitiveReceipt(
        String receiptId,
        String requestId,
        String sessionId,
        CognitiveStrategy strategy,
        double taskComplexityScore,
        long cotLatencyNanos,
        int reflexionRoundCount,
        int alignedMemoryCount,
        String busStatus,
        double qwenGeodesicScore,
        long timestampNanos,
        String signatureSha256
) {
    /**
     * 计算摘要明文并生成 SHA-256 签名
     */
    public static String computeSignature(
            String receiptId,
            String requestId,
            String sessionId,
            CognitiveStrategy strategy,
            double taskComplexityScore,
            long cotLatencyNanos,
            int reflexionRoundCount,
            int alignedMemoryCount,
            String busStatus,
            double qwenGeodesicScore,
            long timestampNanos
    ) {
        String payload = String.format("%s|%s|%s|%s|%.4f|%d|%d|%d|%s|%.4f|%d",
                receiptId, requestId, sessionId, strategy, taskComplexityScore,
                cotLatencyNanos, reflexionRoundCount, alignedMemoryCount,
                busStatus, qwenGeodesicScore, timestampNanos);
        return DigestUtil.sha256Hex(payload);
    }

    /**
     * 便捷构造方法，自动计算 SHA-256 签名
     */
    public static HermesCognitiveReceipt createSigned(
            String receiptId,
            String requestId,
            String sessionId,
            CognitiveStrategy strategy,
            double taskComplexityScore,
            long cotLatencyNanos,
            int reflexionRoundCount,
            int alignedMemoryCount,
            String busStatus,
            double qwenGeodesicScore,
            long timestampNanos
    ) {
        String sig = computeSignature(receiptId, requestId, sessionId, strategy,
                taskComplexityScore, cotLatencyNanos, reflexionRoundCount,
                alignedMemoryCount, busStatus, qwenGeodesicScore, timestampNanos);
        return new HermesCognitiveReceipt(receiptId, requestId, sessionId, strategy,
                taskComplexityScore, cotLatencyNanos, reflexionRoundCount,
                alignedMemoryCount, busStatus, qwenGeodesicScore, timestampNanos, sig);
    }

    /**
     * 校验凭单防篡改完整性
     *
     * @return 签名是否合法有效
     */
    public boolean verifySignature() {
        if (signatureSha256 == null || signatureSha256.isBlank()) {
            return false;
        }
        String expected = computeSignature(receiptId, requestId, sessionId, strategy,
                taskComplexityScore, cotLatencyNanos, reflexionRoundCount,
                alignedMemoryCount, busStatus, qwenGeodesicScore, timestampNanos);
        return signatureSha256.equalsIgnoreCase(expected);
    }
}
