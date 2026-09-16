package tech.qiantong.qknow.hermes.agent.debate.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * 多智能体仲裁不可变密码学存证凭单 (Multi-Agent Arbitration Receipt)
 * <p>
 * 采用 Java 21 Record 格式封装争辩最终收敛结果，内置基于 SHA-256 的密码学不可变签名与验真方法。
 * </p>
 *
 * @param receiptId           凭单全局唯一 ID
 * @param debateId            争辩会话 ID
 * @param taskId              任务 ID
 * @param winnerAgentId       最终胜出/采纳的智能体 ID
 * @param consensusStatus     最终收敛状态
 * @param finalRounds         总争辩轮次
 * @param finalShannonEntropy 最终收敛的香农争议信息熵
 * @param vcgCost             VCG 机制计费成本
 * @param latencyUs           全流程耗时 (微秒)
 * @param busStatus           总线状态标志 (如 BUS_HEALTHY, STATUS_DEGRADED_ARBITRATOR_FALLBACK)
 * @param signature           SHA-256 密码学签名
 * @author Achilles
 * @version 1.0
 */
public record MultiAgentArbitrationReceipt(
        String receiptId,
        String debateId,
        String taskId,
        String winnerAgentId,
        DebateConsensusStatus consensusStatus,
        int finalRounds,
        double finalShannonEntropy,
        double vcgCost,
        long latencyUs,
        String busStatus,
        String signature
) {
    public MultiAgentArbitrationReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(debateId, "debateId 不能为空");
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(consensusStatus, "consensusStatus 不能为空");
    }

    /**
     * 计算凭单内容的 SHA-256 签名
     *
     * @param receiptId           凭单 ID
     * @param debateId            争辩 ID
     * @param taskId              任务 ID
     * @param winnerAgentId       胜出智能体 ID
     * @param consensusStatus     收敛状态
     * @param finalRounds         总轮次
     * @param finalShannonEntropy 最终香农熵
     * @param vcgCost             VCG 成本
     * @param latencyUs           耗时
     * @param busStatus           总线状态
     * @return 十六进制 SHA-256 签名字符串
     */
    public static String computeSignature(
            String receiptId,
            String debateId,
            String taskId,
            String winnerAgentId,
            DebateConsensusStatus consensusStatus,
            int finalRounds,
            double finalShannonEntropy,
            double vcgCost,
            long latencyUs,
            String busStatus
    ) {
        String payload = String.format("%s|%s|%s|%s|%s|%d|%.6f|%.6f|%d|%s",
                receiptId, debateId, taskId, winnerAgentId != null ? winnerAgentId : "NONE",
                consensusStatus.name(), finalRounds, finalShannonEntropy, vcgCost, latencyUs,
                busStatus != null ? busStatus : "NONE");
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
    public static MultiAgentArbitrationReceipt create(
            String receiptId,
            String debateId,
            String taskId,
            String winnerAgentId,
            DebateConsensusStatus consensusStatus,
            int finalRounds,
            double finalShannonEntropy,
            double vcgCost,
            long latencyUs,
            String busStatus
    ) {
        String sig = computeSignature(receiptId, debateId, taskId, winnerAgentId,
                consensusStatus, finalRounds, finalShannonEntropy, vcgCost, latencyUs, busStatus);
        return new MultiAgentArbitrationReceipt(receiptId, debateId, taskId, winnerAgentId,
                consensusStatus, finalRounds, finalShannonEntropy, vcgCost, latencyUs, busStatus, sig);
    }

    /**
     * 校验自身密码学签名是否合法
     *
     * @return true 若签名完全一致未被篡改，false 否则
     */
    public boolean verifySignature() {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        String expected = computeSignature(receiptId, debateId, taskId, winnerAgentId,
                consensusStatus, finalRounds, finalShannonEntropy, vcgCost, latencyUs, busStatus);
        return signature.equalsIgnoreCase(expected);
    }
}
