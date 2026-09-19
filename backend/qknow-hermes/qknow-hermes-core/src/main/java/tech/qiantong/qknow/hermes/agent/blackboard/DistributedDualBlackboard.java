package tech.qiantong.qknow.hermes.agent.blackboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L1/L2 双态分布式事件黑板中枢
 * 落实定理 2：L1 本地 CAS 原子操作 + L2 分布式全序广播与单调半格收敛，Fencing 租约防脑裂脏写
 */
public class DistributedDualBlackboard {

    private static final Logger log = LoggerFactory.getLogger(DistributedDualBlackboard.class);

    private final String nodeId;
    private final long term;

    // L1 本地事实表字典
    private final Map<String, FactEntry> factsTable = new ConcurrentHashMap<>();
    private final AtomicLong globalVersionCounter = new AtomicLong(0);

    // 分布式 Fencing 租约状态
    private volatile String securityLeaseToken;
    private volatile long leaseExpiryTimestamp = 0L;

    public record FactEntry(
            String key,
            String value,
            String sourceTag,
            BlackboardVersion version,
            Instant timestamp
    ) {}

    public DistributedDualBlackboard(String nodeId) {
        this(nodeId, 1L);
    }

    public DistributedDualBlackboard(String nodeId, long term) {
        this.nodeId = nodeId != null ? nodeId : "default-node";
        this.term = term;
    }

    /**
     * 设置/续约分布式安全租约
     */
    public void setSecurityLease(String token, long expiryTimestamp) {
        this.securityLeaseToken = token;
        this.leaseExpiryTimestamp = expiryTimestamp;
        log.debug("[DualBlackboard] 节点 [{}] 更新租约: token={}, expiry={}", nodeId, token, expiryTimestamp);
    }

    /**
     * 判定当前黑板是否降级为只读模式 (Read-Only)
     * 若租约为空或已超时，立即降级为只读，以杜绝脑裂脏写
     */
    public boolean isReadOnly() {
        if (securityLeaseToken == null || securityLeaseToken.isBlank()) {
            return true;
        }
        return System.currentTimeMillis() > leaseExpiryTimestamp;
    }

    /**
     * L1 基于 CAS 乐观锁版本提交事实
     *
     * @param key             事实键
     * @param value           事实值
     * @param sourceTag       来源智能体标记
     * @param expectedVersion 预期当前版本号（新建为 0）
     * @return 成功写入返回 true，版本冲突返回 false
     * @throws IllegalStateException 若租约过期处于只读模式则抛出异常硬拦截
     */
    public boolean commitFact(String key, String value, String sourceTag, long expectedVersion) {
        if (isReadOnly()) {
            log.error("[DualBlackboard] 节点 [{}] 租约失效，处于只读模式，拒绝写入 key={}", nodeId, key);
            throw new IllegalStateException("黑板租约已过期，当前处于只读降级模式，拒绝写入");
        }

        // 使用 compute 执行线程安全的原子检查与版本自增
        final boolean[] success = new boolean[1];
        factsTable.compute(key, (k, current) -> {
            long curVer = current != null ? current.version().globalVersion() : 0L;
            if (curVer != expectedVersion) {
                log.warn("[DualBlackboard] CAS 乐观锁版本冲突 [key={}]: 预期={}, 实际={}", key, expectedVersion, curVer);
                success[0] = false;
                return current;
            }

            long nextGlobalVer = expectedVersion + 1;
            globalVersionCounter.accumulateAndGet(nextGlobalVer, Math::max);

            BlackboardVersion nextVersion = new BlackboardVersion(nextGlobalVer, term, nodeId);
            FactEntry newEntry = new FactEntry(key, value, sourceTag, nextVersion, Instant.now());
            success[0] = true;
            return newEntry;
        });

        return success[0];
    }

    /**
     * 获取指定事实条目
     */
    public Optional<FactEntry> getFact(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factsTable.get(key));
    }

    /**
     * 落实定理 2：半格 (Join-Semilattice) 偏序合并算子
     * 当收到 L2 远端广播的事实条目时执行单调收敛
     *
     * @param remoteEntry 远端广播的事实条目
     * @return 若远端版本更优且合并成功返回 true；若远端版本落后则忽略并返回 false
     */
    public boolean mergeRemoteFact(FactEntry remoteEntry) {
        if (remoteEntry == null || remoteEntry.key() == null) {
            return false;
        }

        final boolean[] merged = new boolean[1];
        factsTable.compute(remoteEntry.key(), (k, local) -> {
            if (local == null) {
                merged[0] = true;
                return remoteEntry;
            }

            // 比较 Lamport 全序时钟元组
            if (remoteEntry.version().compareTo(local.version()) > 0) {
                merged[0] = true;
                log.debug("[DualBlackboard] 节点 [{}] 接收并合并高版本条目 [key={}]: 远端={}, 本地={}",
                        nodeId, remoteEntry.key(), remoteEntry.version(), local.version());
                return remoteEntry;
            } else {
                merged[0] = false;
                log.debug("[DualBlackboard] 节点 [{}] 忽略旧版本或并发落后条目 [key={}]: 远端={}, 本地={}",
                        nodeId, remoteEntry.key(), remoteEntry.version(), local.version());
                return local;
            }
        });

        return merged[0];
    }

    public String getNodeId() {
        return nodeId;
    }

    public long getTerm() {
        return term;
    }

    public long getGlobalVersion() {
        return globalVersionCounter.get();
    }
}
