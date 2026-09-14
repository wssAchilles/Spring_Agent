package tech.qiantong.qknow.ai.federated.dto;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * 隐私集合求交盲化数据包传输对象
 */
public class PsiBlindPayloadDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 参与方标识 (如 Alice, Bob) */
    private String partyId;
    /** 协议阶段：1 = 本地单重盲化, 2 = 远程二次盲化 */
    private int stage;
    /** 盲化点集 (原始 ID -> 盲化值 BigInteger) */
    private Map<String, BigInteger> blindedMap;
    /** 盲化值列表 (二次盲化后乱序列表，用于无泄露交集判定) */
    private List<BigInteger> blindedValues;
    /** 时间戳 */
    private long timestamp;

    public PsiBlindPayloadDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public PsiBlindPayloadDTO(String partyId, int stage, Map<String, BigInteger> blindedMap, List<BigInteger> blindedValues) {
        this.partyId = partyId;
        this.stage = stage;
        this.blindedMap = blindedMap;
        this.blindedValues = blindedValues;
        this.timestamp = System.currentTimeMillis();
    }

    public String getPartyId() {
        return partyId;
    }

    public void setPartyId(String partyId) {
        this.partyId = partyId;
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public Map<String, BigInteger> getBlindedMap() {
        return blindedMap;
    }

    public void setBlindedMap(Map<String, BigInteger> blindedMap) {
        this.blindedMap = blindedMap;
    }

    public List<BigInteger> getBlindedValues() {
        return blindedValues;
    }

    public void setBlindedValues(List<BigInteger> blindedValues) {
        this.blindedValues = blindedValues;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
