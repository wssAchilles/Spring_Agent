package tech.qiantong.qknow.hermes.agent.blackboard;

import java.io.Serializable;

/**
 * 分布式 Lamport 全序逻辑时钟元组（Java 21 Record）
 * 落实定理 2：V = <globalVersion, term, nodeId>，形成严格全序偏序半格，消除并发时序倒错
 */
public record BlackboardVersion(
        long globalVersion,
        long term,
        String nodeId
) implements Comparable<BlackboardVersion>, Serializable {

    public BlackboardVersion {
        if (nodeId == null) {
            nodeId = "default-node";
        }
    }

    @Override
    public int compareTo(BlackboardVersion other) {
        if (other == null) {
            return 1;
        }
        // 1. globalVersion 优先
        if (this.globalVersion != other.globalVersion) {
            return Long.compare(this.globalVersion, other.globalVersion);
        }
        // 2. 任期 term 优先
        if (this.term != other.term) {
            return Long.compare(this.term, other.term);
        }
        // 3. 节点标识字典序兜底（确保全序）
        return this.nodeId.compareTo(other.nodeId);
    }

    public BlackboardVersion nextVersion() {
        return new BlackboardVersion(this.globalVersion + 1, this.term, this.nodeId);
    }
}
