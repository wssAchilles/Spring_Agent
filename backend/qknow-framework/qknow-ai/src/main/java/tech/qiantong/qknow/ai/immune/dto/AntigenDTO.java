package tech.qiantong.qknow.ai.immune.dto;

import java.util.Arrays;

/**
 * 抗原特征数据传输对象
 *
 * @author Achilles
 * @since Phase 45
 */
public class AntigenDTO {

    /**
     * 原始输入载荷或检索切片
     */
    private String rawPayload;

    /**
     * 阿里千问 1536 维超球面单位特征向量
     */
    private float[] semanticEmbedding;

    /**
     * 64 位 SimHash 词法特征位图
     */
    private long lexicalSimHash;

    /**
     * 语法与控制指令结构风险得分 [0.0, 1.0]
     */
    private double syntaxRiskScore;

    /**
     * 抗原提取时间戳
     */
    private long timestamp;

    public AntigenDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public AntigenDTO(String rawPayload, float[] semanticEmbedding, long lexicalSimHash, double syntaxRiskScore) {
        this.rawPayload = rawPayload;
        this.semanticEmbedding = semanticEmbedding;
        this.lexicalSimHash = lexicalSimHash;
        this.syntaxRiskScore = syntaxRiskScore;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public float[] getSemanticEmbedding() {
        return semanticEmbedding;
    }

    public void setSemanticEmbedding(float[] semanticEmbedding) {
        this.semanticEmbedding = semanticEmbedding;
    }

    public long getLexicalSimHash() {
        return lexicalSimHash;
    }

    public void setLexicalSimHash(long lexicalSimHash) {
        this.lexicalSimHash = lexicalSimHash;
    }

    public double getSyntaxRiskScore() {
        return syntaxRiskScore;
    }

    public void setSyntaxRiskScore(double syntaxRiskScore) {
        this.syntaxRiskScore = syntaxRiskScore;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AntigenDTO{" +
                "rawPayloadLength=" + (rawPayload != null ? rawPayload.length() : 0) +
                ", embeddingDim=" + (semanticEmbedding != null ? semanticEmbedding.length : 0) +
                ", lexicalSimHash=" + lexicalSimHash +
                ", syntaxRiskScore=" + syntaxRiskScore +
                '}';
    }
}
