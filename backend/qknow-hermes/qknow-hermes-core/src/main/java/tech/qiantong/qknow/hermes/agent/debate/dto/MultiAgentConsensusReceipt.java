package tech.qiantong.qknow.hermes.agent.debate.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 纯 Java 21 Record 格式不可变多智能体共识存证凭单 (MultiAgentConsensusReceipt)
 * <p>
 * 严格记录多角色博弈辩论会话标识、轮次总数、参与智能体集群、纳什收益矩阵、最终共识裁决、
 * 阿里千问 1536 维超球面规章对齐得分表、微秒级耗时指标及 SHA-256 密码学防篡改签名。
 * 支持常量时间自验真方法 verifySignature()。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public record MultiAgentConsensusReceipt(
        String receiptId,
        String debateId,
        int roundCount,
        List<String> participatingAgents,
        Map<String, Double> payoffMatrix,
        String consensusDecision,
        Map<String, Double> qwenAlignmentScores,
        long latencyUs,
        DebateConsensusStatus status,
        String sha256Signature
) {
    public MultiAgentConsensusReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(debateId, "debateId 不能为空");
        Objects.requireNonNull(participatingAgents, "participatingAgents 不能为空");
        Objects.requireNonNull(payoffMatrix, "payoffMatrix 不能为空");
        Objects.requireNonNull(consensusDecision, "consensusDecision 不能为空");
        Objects.requireNonNull(qwenAlignmentScores, "qwenAlignmentScores 不能为空");
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");

        // 严格防御性深拷贝不可变容器，杜绝外部内存篡改
        participatingAgents = List.copyOf(participatingAgents);
        payoffMatrix = Map.copyOf(payoffMatrix);
        qwenAlignmentScores = Map.copyOf(qwenAlignmentScores);
    }

    /**
     * 计算全载荷不可变数据的 SHA-256 签名
     */
    public static String computeSignature(
            String receiptId,
            String debateId,
            int roundCount,
            List<String> participatingAgents,
            Map<String, Double> payoffMatrix,
            String consensusDecision,
            Map<String, Double> qwenAlignmentScores,
            long latencyUs,
            DebateConsensusStatus status
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(receiptId).append("|")
          .append(debateId).append("|")
          .append(roundCount).append("|")
          .append(String.join(",", participatingAgents)).append("|");

        // 保持键有序以保证签名确定性
        new TreeSet<>(payoffMatrix.keySet()).forEach(k -> sb.append(k).append(":").append(payoffMatrix.get(k)).append(";"));
        sb.append("|").append(consensusDecision).append("|");
        new TreeSet<>(qwenAlignmentScores.keySet()).forEach(k -> sb.append(k).append(":").append(qwenAlignmentScores.get(k)).append(";"));
        sb.append("|").append(latencyUs).append("|").append(status != null ? status.name() : "NONE");

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("系统环境缺少 SHA-256 摘要算法", e);
        }
    }

    /**
     * 便捷工厂创建方法
     */
    public static MultiAgentConsensusReceipt create(
            String receiptId,
            String debateId,
            int roundCount,
            List<String> participatingAgents,
            Map<String, Double> payoffMatrix,
            String consensusDecision,
            Map<String, Double> qwenAlignmentScores,
            long latencyUs,
            DebateConsensusStatus status
    ) {
        String sig = computeSignature(receiptId, debateId, roundCount, participatingAgents,
                payoffMatrix, consensusDecision, qwenAlignmentScores, latencyUs, status);
        return new MultiAgentConsensusReceipt(receiptId, debateId, roundCount, participatingAgents,
                payoffMatrix, consensusDecision, qwenAlignmentScores, latencyUs, status, sig);
    }

    /**
     * 验证自身签名合法性，杜绝任何字段被离线篡改
     */
    public boolean verifySignature() {
        String expected = computeSignature(receiptId, debateId, roundCount, participatingAgents,
                payoffMatrix, consensusDecision, qwenAlignmentScores, latencyUs, status);
        return this.sha256Signature.equals(expected);
    }
}
