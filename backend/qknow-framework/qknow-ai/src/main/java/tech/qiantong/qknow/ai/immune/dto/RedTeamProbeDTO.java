package tech.qiantong.qknow.ai.immune.dto;

import tech.qiantong.qknow.ai.immune.enums.AttackMutationType;

/**
 * 红队对抗攻击探针数据传输对象
 *
 * @author Achilles
 * @since Phase 45
 */
public class RedTeamProbeDTO {

    /**
     * 探针唯一编号
     */
    private String probeId;

    /**
     * 对抗攻击变异载荷
     */
    private String attackPayload;

    /**
     * 变异类别
     */
    private AttackMutationType mutationType;

    /**
     * 理论逃逸潜力评估 [0.0, 1.0]
     */
    private double evasionScore;

    /**
     * 靶向脆弱性标识
     */
    private String targetVulnerability;

    public RedTeamProbeDTO() {
    }

    public RedTeamProbeDTO(String probeId, String attackPayload, AttackMutationType mutationType,
                           double evasionScore, String targetVulnerability) {
        this.probeId = probeId;
        this.attackPayload = attackPayload;
        this.mutationType = mutationType;
        this.evasionScore = evasionScore;
        this.targetVulnerability = targetVulnerability;
    }

    public String getProbeId() {
        return probeId;
    }

    public void setProbeId(String probeId) {
        this.probeId = probeId;
    }

    public String getAttackPayload() {
        return attackPayload;
    }

    public void setAttackPayload(String attackPayload) {
        this.attackPayload = attackPayload;
    }

    public AttackMutationType getMutationType() {
        return mutationType;
    }

    public void setMutationType(AttackMutationType mutationType) {
        this.mutationType = mutationType;
    }

    public double getEvasionScore() {
        return evasionScore;
    }

    public void setEvasionScore(double evasionScore) {
        this.evasionScore = evasionScore;
    }

    public String getTargetVulnerability() {
        return targetVulnerability;
    }

    public void setTargetVulnerability(String targetVulnerability) {
        this.targetVulnerability = targetVulnerability;
    }
}
