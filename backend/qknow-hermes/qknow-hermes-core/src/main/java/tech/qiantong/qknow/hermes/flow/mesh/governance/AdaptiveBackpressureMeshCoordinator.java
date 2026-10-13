package tech.qiantong.qknow.hermes.flow.mesh.governance;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 自适应背压网格调度器接口
 * 为并发智能体与 DAG 节点提供带动态背压配额的槽位调度
 */
public interface AdaptiveBackpressureMeshCoordinator {

    /**
     * 为 DAG 并行节点申请网格执行槽位（带背压排队与超时控制）
     *
     * @param runtimeId 运行时 ID
     * @param nodeId    DAG 节点 ID
     * @param timeoutMs 最长排队超时时间
     * @return 签发的只读不可变凭单（若背压满载超时则返回 empty）
     */
    CompletableFuture<Optional<MeshSchedulingTicket>> acquireSlotAsync(String runtimeId, String nodeId, long timeoutMs);

    /**
     * 节点执行完毕后释放槽位，并回传节点执行延迟与状态用于 AIMD 调谐
     *
     * @param ticket        执行凭单
     * @param latencyMs     实际执行耗时 (毫秒)
     * @param isSuccess     是否成功执行
     * @param isRateLimited 是否遭遇 429
     */
    void releaseSlot(MeshSchedulingTicket ticket, long latencyMs, boolean isSuccess, boolean isRateLimited);

    /**
     * 获取当前网格遥测统计快照
     */
    MeshBackpressureTelemetry getTelemetrySnapshot();
}
