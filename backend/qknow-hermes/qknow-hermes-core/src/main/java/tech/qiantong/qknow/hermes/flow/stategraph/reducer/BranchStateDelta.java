package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 分支节点执行状态增量包 Record (BranchStateDelta)
 * 记录单节点在超步内的局部产出，携带时序序列号以备因果定序。
 */
public record BranchStateDelta(
        String nodeUuid,
        int superstep,
        Map<String, Object> deltaUpdates,
        long branchSequenceToken
) implements Serializable {

    public BranchStateDelta {
        Objects.requireNonNull(nodeUuid, "nodeUuid 不能为空");
        deltaUpdates = Map.copyOf(deltaUpdates != null ? deltaUpdates : Collections.emptyMap());
    }

    public static BranchStateDelta of(String nodeId, int step, Map<String, Object> deltas) {
        return new BranchStateDelta(nodeId, step, deltas, System.nanoTime());
    }

    public static BranchStateDelta of(String nodeId, int step, long seqToken, Map<String, Object> deltas) {
        return new BranchStateDelta(nodeId, step, deltas, seqToken);
    }
}
