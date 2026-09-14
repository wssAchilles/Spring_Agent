package tech.qiantong.qknow.ai.federated.dto;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Paillier 加法同态密文封装对象
 */
public class HomomorphicCiphertextDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 密文大数值 */
    private BigInteger ciphertext;
    /** 公钥模数 n */
    private BigInteger modulusN;
    /** 模数平方 n^2 (预计算用于同态运算) */
    private BigInteger modulusNSquared;
    /** 参与方标识 */
    private String participantId;
    /** 标量描述 */
    private String metricLabel;

    public HomomorphicCiphertextDTO() {}

    public HomomorphicCiphertextDTO(BigInteger ciphertext, BigInteger modulusN, BigInteger modulusNSquared, String participantId, String metricLabel) {
        this.ciphertext = ciphertext;
        this.modulusN = modulusN;
        this.modulusNSquared = modulusNSquared;
        this.participantId = participantId;
        this.metricLabel = metricLabel;
    }

    public BigInteger getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(BigInteger ciphertext) {
        this.ciphertext = ciphertext;
    }

    public BigInteger getModulusN() {
        return modulusN;
    }

    public void setModulusN(BigInteger modulusN) {
        this.modulusN = modulusN;
    }

    public BigInteger getModulusNSquared() {
        return modulusNSquared;
    }

    public void setModulusNSquared(BigInteger modulusNSquared) {
        this.modulusNSquared = modulusNSquared;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getMetricLabel() {
        return metricLabel;
    }

    public void setMetricLabel(String metricLabel) {
        this.metricLabel = metricLabel;
    }
}
