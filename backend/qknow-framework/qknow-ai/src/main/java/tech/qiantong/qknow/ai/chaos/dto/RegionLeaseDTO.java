package tech.qiantong.qknow.ai.chaos.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 机房租约与单调递增 Fencing Token 凭证
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class RegionLeaseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String region;
    private String leaderNodeId;
    private long fencingToken;     // 全局严格单调递增代际号
    private long leaseStartMs;
    private long leaseDurationMs;  // 租约时长，如 5000ms
    private Set<String> quorumVoters; // 签署同意的节点集合
    private boolean readOnly;      // 是否已被降级为只读

    public RegionLeaseDTO() {
        this.quorumVoters = new HashSet<>();
        this.readOnly = false;
    }

    public RegionLeaseDTO(String region, String leaderNodeId, long fencingToken, long leaseDurationMs, Set<String> voters) {
        this.region = region;
        this.leaderNodeId = leaderNodeId;
        this.fencingToken = fencingToken;
        this.leaseStartMs = System.currentTimeMillis();
        this.leaseDurationMs = leaseDurationMs;
        this.quorumVoters = voters != null ? new HashSet<>(voters) : new HashSet<>();
        this.readOnly = false;
    }

    /**
     * 判断租约是否依然有效（未到期且非只读）
     */
    public boolean isValid() {
        if (readOnly) {
            return false;
        }
        return System.currentTimeMillis() - leaseStartMs < leaseDurationMs;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getLeaderNodeId() {
        return leaderNodeId;
    }

    public void setLeaderNodeId(String leaderNodeId) {
        this.leaderNodeId = leaderNodeId;
    }

    public long getFencingToken() {
        return fencingToken;
    }

    public void setFencingToken(long fencingToken) {
        this.fencingToken = fencingToken;
    }

    public long getLeaseStartMs() {
        return leaseStartMs;
    }

    public void setLeaseStartMs(long leaseStartMs) {
        this.leaseStartMs = leaseStartMs;
    }

    public long getLeaseDurationMs() {
        return leaseDurationMs;
    }

    public void setLeaseDurationMs(long leaseDurationMs) {
        this.leaseDurationMs = leaseDurationMs;
    }

    public Set<String> getQuorumVoters() {
        return Collections.unmodifiableSet(quorumVoters);
    }

    public void setQuorumVoters(Set<String> quorumVoters) {
        this.quorumVoters = quorumVoters != null ? new HashSet<>(quorumVoters) : new HashSet<>();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
