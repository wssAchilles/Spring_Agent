package tech.qiantong.qknow.module.kmc.service.rag.advanced.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 知识图谱子图推理与流式回放不可变密码学存证凭单 (GraphRAG Execution Receipt)
 * <p>
 * 采用 Java 21 Record 封装全流程结果，内置基于 SHA-256 的密码学自签名与验真方法。
 * </p>
 *
 * @param receiptId              凭单全局唯一 ID
 * @param sessionId              检索会话 ID
 * @param queryText              检索查询文本
 * @param expandedParentCount    成功展开的父章节上下文计数
 * @param subgraphNodeCount      诱导子图推理节点总数
 * @param pprTopScore            PPR 稳态最高置信度得分
 * @param playbackJitterVariance 打字机回放抖动方差
 * @param latencyUs              全流程处理耗时 (微秒)
 * @param busStatus              总线状态标志
 * @param signature              SHA-256 密码学防篡改签名
 * @author Achilles
 * @version 1.0
 */
public record GraphRagExecutionReceipt(
        String receiptId,
        String sessionId,
        String queryText,
        int expandedParentCount,
        int subgraphNodeCount,
        double pprTopScore,
        double playbackJitterVariance,
        long latencyUs,
        String busStatus,
        String signature
) {
    public GraphRagExecutionReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(queryText, "queryText 不能为空");
    }

    /**
     * 计算凭单内容的 SHA-256 签名
     */
    public static String computeSignature(
            String receiptId,
            String sessionId,
            String queryText,
            int expandedParentCount,
            int subgraphNodeCount,
            double pprTopScore,
            double playbackJitterVariance,
            long latencyUs,
            String busStatus
    ) {
        String payload = String.format("%s|%s|%s|%d|%d|%.6f|%.6f|%d|%s",
                receiptId, sessionId, queryText, expandedParentCount, subgraphNodeCount,
                pprTopScore, playbackJitterVariance, latencyUs, busStatus != null ? busStatus : "NONE");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 工厂方法：自动计算签名构建存证凭单
     */
    public static GraphRagExecutionReceipt create(
            String receiptId,
            String sessionId,
            String queryText,
            int expandedParentCount,
            int subgraphNodeCount,
            double pprTopScore,
            double playbackJitterVariance,
            long latencyUs,
            String busStatus
    ) {
        String sig = computeSignature(receiptId, sessionId, queryText, expandedParentCount,
                subgraphNodeCount, pprTopScore, playbackJitterVariance, latencyUs, busStatus);
        return new GraphRagExecutionReceipt(receiptId, sessionId, queryText, expandedParentCount,
                subgraphNodeCount, pprTopScore, playbackJitterVariance, latencyUs, busStatus, sig);
    }

    /**
     * 校验自身密码学签名是否合法
     *
     * @return true 若签名一致未被篡改，false 否则
     */
    public boolean verifySignature() {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        String expected = computeSignature(receiptId, sessionId, queryText, expandedParentCount,
                subgraphNodeCount, pprTopScore, playbackJitterVariance, latencyUs, busStatus);
        return signature.equalsIgnoreCase(expected);
    }
}
