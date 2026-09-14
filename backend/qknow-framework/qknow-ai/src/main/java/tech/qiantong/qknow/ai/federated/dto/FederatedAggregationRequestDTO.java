package tech.qiantong.qknow.ai.federated.dto;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 联邦打分聚合请求对象
 */
public class FederatedAggregationRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;
    /** 各参与方提交的密文列表 */
    private List<HomomorphicCiphertextDTO> ciphertexts;
    /** 各参与方对应的信誉权重映射 (participantId -> weight) */
    private Map<String, Integer> weights;
    /** 公钥模数 n */
    private BigInteger modulusN;

    public FederatedAggregationRequestDTO() {}

    public FederatedAggregationRequestDTO(String taskId, List<HomomorphicCiphertextDTO> ciphertexts, Map<String, Integer> weights, BigInteger modulusN) {
        this.taskId = taskId;
        this.ciphertexts = ciphertexts;
        this.weights = weights;
        this.modulusN = modulusN;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public List<HomomorphicCiphertextDTO> getCiphertexts() {
        return ciphertexts;
    }

    public void setCiphertexts(List<HomomorphicCiphertextDTO> ciphertexts) {
        this.ciphertexts = ciphertexts;
    }

    public Map<String, Integer> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, Integer> weights) {
        this.weights = weights;
    }

    public BigInteger getModulusN() {
        return modulusN;
    }

    public void setModulusN(BigInteger modulusN) {
        this.modulusN = modulusN;
    }
}
