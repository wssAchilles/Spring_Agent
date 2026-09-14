package tech.qiantong.qknow.ai.federated.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 隐私集合求交结果数据传输对象
 */
public class PsiIntersectionResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 匹配成功的共有实体 ID 列表 */
    private List<String> matchedEntityIds;
    /** 共有实体总数 */
    private int intersectionSize;
    /** 零知识审计标记 (交集外未匹配私有实体明文泄露为 0) */
    private boolean zeroLeakageVerified;
    /** 执行耗时毫秒数 */
    private long elapsedMs;

    public PsiIntersectionResultDTO() {}

    public PsiIntersectionResultDTO(List<String> matchedEntityIds, int intersectionSize, boolean zeroLeakageVerified, long elapsedMs) {
        this.matchedEntityIds = matchedEntityIds;
        this.intersectionSize = intersectionSize;
        this.zeroLeakageVerified = zeroLeakageVerified;
        this.elapsedMs = elapsedMs;
    }

    public List<String> getMatchedEntityIds() {
        return matchedEntityIds;
    }

    public void setMatchedEntityIds(List<String> matchedEntityIds) {
        this.matchedEntityIds = matchedEntityIds;
    }

    public int getIntersectionSize() {
        return intersectionSize;
    }

    public void setIntersectionSize(int intersectionSize) {
        this.intersectionSize = intersectionSize;
    }

    public boolean isZeroLeakageVerified() {
        return zeroLeakageVerified;
    }

    public void setZeroLeakageVerified(boolean zeroLeakageVerified) {
        this.zeroLeakageVerified = zeroLeakageVerified;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
