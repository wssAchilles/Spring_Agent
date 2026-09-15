package tech.qiantong.qknow.ai.geosync;

import java.util.Objects;

/**
 * Phase 61: 结合半格强最终一致性状态型 CRDT 寄存器 (State-based LWW-Register with Vector Clocks)
 * <p>
 * 基于定理 1.1，合并算子严格满足交换律、结合律与幂等律，抗 NTP 物理时钟漂移。
 */
public class StateBasedCrdtRegister<T> {

    private final String registerId;
    private T value;
    private CausalVectorClock vectorClock;
    private long logicalEpoch;
    private String lastUpdatedNode;
    private boolean tombstone;

    public StateBasedCrdtRegister(String registerId, T initialValue, String originNodeId) {
        this.registerId = Objects.requireNonNull(registerId, "寄存器 ID 不能为空");
        this.value = initialValue;
        this.vectorClock = new CausalVectorClock();
        if (originNodeId != null) {
            this.vectorClock.tick(originNodeId);
        }
        this.logicalEpoch = 1L;
        this.lastUpdatedNode = originNodeId;
        this.tombstone = false;
    }

    private StateBasedCrdtRegister(
            String registerId, T value, CausalVectorClock vectorClock,
            long logicalEpoch, String lastUpdatedNode, boolean tombstone
    ) {
        this.registerId = registerId;
        this.value = value;
        this.vectorClock = vectorClock.clone();
        this.logicalEpoch = logicalEpoch;
        this.lastUpdatedNode = lastUpdatedNode;
        this.tombstone = tombstone;
    }

    /**
     * 本地更新赋值
     */
    public synchronized void assign(String nodeId, T newValue, long epoch) {
        this.value = newValue;
        this.vectorClock.tick(nodeId);
        this.logicalEpoch = Math.max(this.logicalEpoch + 1, epoch);
        this.lastUpdatedNode = nodeId;
        this.tombstone = false;
    }

    /**
     * 标记删除（墓碑机制）
     */
    public synchronized void delete(String nodeId, long epoch) {
        this.value = null;
        this.vectorClock.tick(nodeId);
        this.logicalEpoch = Math.max(this.logicalEpoch + 1, epoch);
        this.lastUpdatedNode = nodeId;
        this.tombstone = true;
    }

    /**
     * 结合半格算子：最小上界合并 (Least Upper Bound Merge) s = s1 \sqcup s2
     *
     * @return true 若产生实质性合并更新，false 若本地状态严格占优无变更
     */
    public synchronized boolean merge(StateBasedCrdtRegister<T> remote) {
        if (remote == null || remote == this) {
            return false;
        }
        if (!Objects.equals(this.registerId, remote.registerId)) {
            throw new IllegalArgumentException("无法合并不同寄存器 ID 的状态: " + this.registerId + " vs " + remote.registerId);
        }

        CausalVectorClock.Ordering ordering = this.vectorClock.compare(remote.vectorClock);

        if (ordering == CausalVectorClock.Ordering.BEFORE) {
            // 远程因果严格领先于本地 -> 直接采纳远程状态
            adoptRemoteState(remote);
            return true;
        } else if (ordering == CausalVectorClock.Ordering.AFTER) {
            // 本地因果严格领先于远程 -> 保持本地，仅合并推进向量时钟
            this.vectorClock.update(remote.vectorClock, null);
            return false;
        } else if (ordering == CausalVectorClock.Ordering.EQUAL) {
            // 状态已严格等价 -> 幂等无变更
            return false;
        } else {
            // 并发冲突 (CONCURRENT) -> 执行确定性打破平局算子 (Deterministic Tie-Breaking)
            // 规则：1. 逻辑代际号 (logicalEpoch) 大者胜出；
            //      2. 若代际号相同，按节点 ID 字典序确定性胜出，杜绝 NTP 物理时钟漂移。
            this.vectorClock.update(remote.vectorClock, null);

            if (remote.logicalEpoch > this.logicalEpoch) {
                adoptRemoteState(remote);
                return true;
            } else if (remote.logicalEpoch < this.logicalEpoch) {
                return false;
            } else {
                // 代际号相同，比对节点字典序
                String localNode = this.lastUpdatedNode != null ? this.lastUpdatedNode : "";
                String remoteNode = remote.lastUpdatedNode != null ? remote.lastUpdatedNode : "";
                if (remoteNode.compareTo(localNode) > 0) {
                    adoptRemoteState(remote);
                    return true;
                }
                return false;
            }
        }
    }

    private void adoptRemoteState(StateBasedCrdtRegister<T> remote) {
        this.value = remote.value;
        this.vectorClock.update(remote.vectorClock, null);
        this.logicalEpoch = Math.max(this.logicalEpoch, remote.logicalEpoch);
        this.lastUpdatedNode = remote.lastUpdatedNode;
        this.tombstone = remote.tombstone;
    }

    public StateBasedCrdtRegister<T> snapshot() {
        return new StateBasedCrdtRegister<>(
                this.registerId, this.value, this.vectorClock.clone(),
                this.logicalEpoch, this.lastUpdatedNode, this.tombstone
        );
    }

    public String getRegisterId() {
        return registerId;
    }

    public T getValue() {
        return value;
    }

    public CausalVectorClock getVectorClock() {
        return vectorClock;
    }

    public long getLogicalEpoch() {
        return logicalEpoch;
    }

    public String getLastUpdatedNode() {
        return lastUpdatedNode;
    }

    public boolean isTombstone() {
        return tombstone;
    }
}
