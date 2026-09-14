package tech.qiantong.qknow.ai.chaos.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.chaos.dto.RegionLeaseDTO;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 多活机房防脑裂硬仲裁器 (Theorem 1.1)
 * 实施基于多数派 Quorum 租约管理、Fencing Token 原子递增与下游 CAS 阻断
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class MultiRegionSplitBrainArbiter {

    private static final Logger log = LoggerFactory.getLogger(MultiRegionSplitBrainArbiter.class);

    // 全局集群节点总数
    private final int totalClusterNodes;
    // 全局单调递增代际号生成器
    private final AtomicLong globalFencingToken = new AtomicLong(100);
    // 各机房当前持有的租约信息
    private final Map<String, RegionLeaseDTO> regionLeases = new ConcurrentHashMap<>();
    // 模拟下游物理存储维护的最高生效 Fencing Token
    private final AtomicLong storageCommittedToken = new AtomicLong(0);

    public MultiRegionSplitBrainArbiter(int totalClusterNodes) {
        this.totalClusterNodes = totalClusterNodes;
    }

    /**
     * 计算法定多数 Quorum 阈值：Q = floor(N / 2) + 1
     */
    public int calculateQuorumThreshold() {
        return (totalClusterNodes / 2) + 1;
    }

    /**
     * 申请或续租机房 Leader 租约
     * 必须获得多数派节点投票赞成才允许颁发有效 Lease
     */
    public synchronized RegionLeaseDTO requestLease(String region, String candidateNodeId, Set<String> voterNodes, long durationMs) {
        int quorum = calculateQuorumThreshold();
        int votes = voterNodes != null ? voterNodes.size() : 0;

        if (votes < quorum) {
            log.warn("节点 {} 在机房 {} 申请租约失败：所获投票数 {} 未达法定多数 Quorum {}",
                    candidateNodeId, region, votes, quorum);
            RegionLeaseDTO rejectedLease = new RegionLeaseDTO(region, candidateNodeId, 0, durationMs, voterNodes);
            rejectedLease.setReadOnly(true);
            return rejectedLease;
        }

        // 成功获取 Quorum，原子递增 Fencing Token
        long newToken = globalFencingToken.incrementAndGet();
        RegionLeaseDTO lease = new RegionLeaseDTO(region, candidateNodeId, newToken, durationMs, voterNodes);
        regionLeases.put(region, lease);

        log.info("机房 {} 节点 {} 成功当选 Leader 并取得租约！FencingToken={}, 赞成票数={}/{}",
                region, candidateNodeId, newToken, votes, totalClusterNodes);
        return lease;
    }

    /**
     * 验证写操作权限
     * 若租约已过期或被置为只读，强行抛出拒绝异常
     */
    public boolean verifyWritePermission(String region, long fencingToken) {
        RegionLeaseDTO lease = regionLeases.get(region);
        if (lease == null) {
            log.error("机房 {} 无有效租约记录，写操作被拒绝！", region);
            return false;
        }
        if (lease.isReadOnly()) {
            log.warn("机房 {} 当前处于降级只读状态，写操作被拒绝！", region);
            return false;
        }
        if (!lease.isValid()) {
            log.warn("机房 {} 租约已超时过期，写操作被拒绝！", region);
            return false;
        }
        if (lease.getFencingToken() != fencingToken) {
            log.error("Token 不匹配！租约 Token={}, 传入 Token={}, 写操作被拒绝！", lease.getFencingToken(), fencingToken);
            return false;
        }
        return true;
    }

    /**
     * 主动降级机房为只读模式 (Fail-Close 阻断写)
     */
    public void stepDown(String region) {
        RegionLeaseDTO lease = regionLeases.get(region);
        if (lease != null) {
            lease.setReadOnly(true);
            log.warn("机房 {} 已主动降级为只读模式！", region);
        }
    }

    /**
     * 下游存储层原子 CAS 验证 Fencing Token
     * 只有当传入的 Token 严格大于存储已提交的 Token 时，才允许写入并更新版本
     */
    public boolean validateStorageCas(long incomingFencingToken) {
        long current = storageCommittedToken.get();
        if (incomingFencingToken <= current) {
            log.error("存储层 CAS 校验失败！传入过期 Token: {}, 存储已生效最新 Token: {}", incomingFencingToken, current);
            return false;
        }
        // 尝试 CAS 更新
        return storageCommittedToken.compareAndSet(current, incomingFencingToken);
    }

    public long getStorageCommittedToken() {
        return storageCommittedToken.get();
    }

    public RegionLeaseDTO getLease(String region) {
        return regionLeases.get(region);
    }

    public int getTotalClusterNodes() {
        return totalClusterNodes;
    }
}
