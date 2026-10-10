package tech.qiantong.qknow.hermes.flow.stategraph.checkpoint;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 分布式持久化状态图 Checkpointer 核心契约 (对标 LangGraph PostgresSaver / Temporal Event Sourcing)
 * 用于记录状态图每个超步的不可变快照、提供时间旅行回溯寻址与热补丁安全分叉能力。
 */
public interface DurableStateGraphCheckpointer {

    /**
     * 单个超步不可变快照 Record
     */
    record SnapshotRecord(
            String snapshotId,
            String executionId,
            String branchId,
            int superstep,
            Map<String, Object> stateDelta,
            List<String> activeNodes,
            long fencingToken,
            long createdAtMs,
            String checksumSha256
    ) {}

    /**
     * 保存超步不可变快照 (带 CAS Fencing Token 写屏障)
     *
     * @param snapshot 快照对象
     * @return 是否成功保存（CAS 失败返回 false）
     */
    boolean saveSnapshot(SnapshotRecord snapshot);

    /**
     * 读取指定分支与超步的历史快照
     *
     * @param executionId 执行流水线 ID
     * @param branchId    分支 ID
     * @param superstep   超步序号
     * @return 快照记录（若存在）
     */
    Optional<SnapshotRecord> loadSnapshot(String executionId, String branchId, int superstep);

    /**
     * 从指定历史超步快照热补丁分叉新分支 (幽灵变量污染率恒为 0.0%)
     *
     * @param executionId    执行 ID
     * @param parentBranchId 父分支 ID
     * @param fromSuperstep  分叉起始超步
     * @param stateOverrides 状态覆盖修改项
     * @return 新生成的隔离分支 ID
     */
    String forkBranch(String executionId, String parentBranchId, int fromSuperstep, Map<String, Object> stateOverrides);

    /**
     * 获取指定执行与分支的完整时光旅行时间线列表
     *
     * @param executionId 执行 ID
     * @param branchId    分支 ID
     * @return 按超步序号正序排列的快照列表
     */
    List<SnapshotRecord> getTimelineHistory(String executionId, String branchId);
}
