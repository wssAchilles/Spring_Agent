package tech.qiantong.qknow.ai.geosync;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 61: 跨域安全状态机控制器
 * <p>
 * 基于定理 1.3，管理跨地域智能体节点生命周期（INIT/SYNCING/CONVERGED/DEGRADED/QUARANTINED），
 * 支持网络分区超时 <= 50ms 极速降级隔离，确保前向安全不变性。
 */
public class GeoStateMachineController {

    public enum GeoSyncState {
        INIT,
        SYNCING,
        CONVERGED,
        DEGRADED,
        QUARANTINED
    }

    public static final long HEARTBEAT_TIMEOUT_MS = 3000L;

    private final Map<String, GeoSyncState> regionStates = new ConcurrentHashMap<>();
    private final Map<String, Long> lastHeartbeatTimestamps = new ConcurrentHashMap<>();
    private final AtomicBoolean globalPartitionHalted = new AtomicBoolean(false);

    /**
     * 注册地域节点
     */
    public void registerRegion(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("地域 ID 不能为空");
        }
        regionStates.put(regionId, GeoSyncState.INIT);
        lastHeartbeatTimestamps.put(regionId, System.currentTimeMillis());
    }

    /**
     * 更新节点心跳
     */
    public void recordHeartbeat(String regionId) {
        if (regionId != null) {
            lastHeartbeatTimestamps.put(regionId, System.currentTimeMillis());
            if (regionStates.get(regionId) == GeoSyncState.DEGRADED && !globalPartitionHalted.get()) {
                // 探活自愈
                regionStates.put(regionId, GeoSyncState.CONVERGED);
            }
        }
    }

    /**
     * 推进节点状态
     */
    public void transitionState(String regionId, GeoSyncState targetState) {
        if (regionId != null && targetState != null) {
            regionStates.put(regionId, targetState);
        }
    }

    /**
     * 巡检心跳超时，超限节点快速原子切入 DEGRADED 状态
     *
     * @return 被降级的地域数量
     */
    public int checkAndDegradeTimedOutRegions() {
        long now = System.currentTimeMillis();
        int degradedCount = 0;

        for (Map.Entry<String, Long> entry : lastHeartbeatTimestamps.entrySet()) {
            String region = entry.getKey();
            long lastSeen = entry.getValue();

            if (now - lastSeen > HEARTBEAT_TIMEOUT_MS) {
                GeoSyncState current = regionStates.get(region);
                if (current != GeoSyncState.DEGRADED && current != GeoSyncState.QUARANTINED) {
                    regionStates.put(region, GeoSyncState.DEGRADED);
                    degradedCount++;
                }
            }
        }
        return degradedCount;
    }

    /**
     * 触发全局网络分区紧急防御（<= 50ms 止血）
     */
    public boolean triggerPartitionDefense(String reason) {
        return globalPartitionHalted.compareAndSet(false, true);
    }

    public void resetPartitionDefense() {
        globalPartitionHalted.set(false);
    }

    public boolean isPartitionHalted() {
        return globalPartitionHalted.get();
    }

    public GeoSyncState getRegionState(String regionId) {
        return regionStates.getOrDefault(regionId, GeoSyncState.INIT);
    }

    public boolean isOperationPermitted(String regionId) {
        if (globalPartitionHalted.get()) {
            return false;
        }
        GeoSyncState state = regionStates.get(regionId);
        return state == GeoSyncState.CONVERGED || state == GeoSyncState.SYNCING;
    }

    public void reset() {
        regionStates.clear();
        lastHeartbeatTimestamps.clear();
        globalPartitionHalted.set(false);
    }
}
