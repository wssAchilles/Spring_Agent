package tech.qiantong.qknow.hermes.agent.debate;

import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;

/**
 * 最终共识仲裁裁决凭单 Record (不可变)
 * 记录多智能体辩论收敛度、权威裁决结果与 DeepSeek 思考链思维推理过程
 */
public record ConsensusArbitrationReceipt(
        String debateId,
        int totalRounds,
        double consensusScore, // 共识度 [0.0, 1.0]
        String winningProposition,
        String arbiterRationale,
        String deepSeekReasoningContent, // DeepSeek 思考模式思维内容
        Map<String, Double> agentConfidenceWeights,
        byte[] cryptographicSeal
) implements Serializable {

    public ConsensusArbitrationReceipt {
        Objects.requireNonNull(debateId, "debateId 不能为空");
        Objects.requireNonNull(winningProposition, "winningProposition 不能为空");
        agentConfidenceWeights = (agentConfidenceWeights == null) ? Map.of() : Map.copyOf(agentConfidenceWeights);
        if (cryptographicSeal != null) {
            cryptographicSeal = cryptographicSeal.clone();
        }
    }

    @Override
    public byte[] cryptographicSeal() {
        return cryptographicSeal == null ? null : cryptographicSeal.clone();
    }

    /**
     * 常量时间防侧信道比对密码学封印
     */
    public boolean verifySeal(byte[] expectedSeal) {
        if (cryptographicSeal == null || expectedSeal == null) {
            return false;
        }
        return MessageDigest.isEqual(cryptographicSeal, expectedSeal);
    }
}
