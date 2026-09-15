package tech.qiantong.qknow.ai.geosync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Phase 61: 跨数据中心无冲突状态同步总调度中枢
 * <p>
 * 闭环统筹因果时钟推进、CRDT 状态半格合并、跨域总线异步同步与状态机安全防护。
 */
public class GeoDistributedSyncCoordinator {

    private final CrossDomainSyncBus syncBus;
    private final GeoStateMachineController stateController;
    // 地域 -> 寄存器集合
    private final Map<String, Map<String, StateBasedCrdtRegister<Object>>> regionalRegisters = new HashMap<>();

    public GeoDistributedSyncCoordinator(
            CrossDomainSyncBus syncBus,
            GeoStateMachineController stateController
    ) {
        this.syncBus = Objects.requireNonNull(syncBus, "syncBus 不能为空");
        this.stateController = Objects.requireNonNull(stateController, "stateController 不能为空");
    }

    /**
     * 注册地域并初始化状态
     */
    public synchronized void registerRegion(String regionId) {
        if (regionId == null || regionId.isBlank()) {
            throw new IllegalArgumentException("地域 ID 不能为空");
        }
        stateController.registerRegion(regionId);
        stateController.transitionState(regionId, GeoStateMachineController.GeoSyncState.CONVERGED);
        regionalRegisters.putIfAbsent(regionId, new HashMap<>());
    }

    /**
     * 本地更新寄存器状态并广播同步
     */
    public synchronized void updateLocalState(String regionId, String registerId, Object newValue, long epoch) {
        if (!stateController.isOperationPermitted(regionId)) {
            throw new IllegalStateException("地域 " + regionId + " 处于降级或熔断隔离态，禁止写操作");
        }

        Map<String, StateBasedCrdtRegister<Object>> registers = regionalRegisters.computeIfAbsent(regionId, k -> new HashMap<>());
        StateBasedCrdtRegister<Object> reg = registers.computeIfAbsent(
                registerId, k -> new StateBasedCrdtRegister<>(registerId, null, regionId)
        );

        reg.assign(regionId, newValue, epoch);

        // 广播给其他所有地域
        for (String targetRegion : regionalRegisters.keySet()) {
            if (!Objects.equals(targetRegion, regionId)) {
                syncBus.publishSyncMessage(regionId, targetRegion, reg.snapshot());
            }
        }
    }

    /**
     * 协调跨域两地域状态合并收敛并签发不可变存证账本
     */
    public synchronized GeoStateAuditReceipt synchronizeRegions(
            long roundId,
            String originRegion,
            String targetRegion,
            String registerId
    ) {
        if (roundId <= 0) {
            throw new IllegalArgumentException("同步轮次必须大于 0");
        }
        if (originRegion == null || targetRegion == null) {
            throw new IllegalArgumentException("源地域与目标地域不能为空");
        }

        stateController.recordHeartbeat(originRegion);
        stateController.recordHeartbeat(targetRegion);

        Map<String, StateBasedCrdtRegister<Object>> originMap = regionalRegisters.get(originRegion);
        Map<String, StateBasedCrdtRegister<Object>> targetMap = regionalRegisters.get(targetRegion);

        if (originMap == null || targetMap == null) {
            throw new IllegalArgumentException("参与同步的地域尚未初始化注册");
        }

        StateBasedCrdtRegister<Object> originReg = originMap.get(registerId);
        StateBasedCrdtRegister<Object> targetReg = targetMap.get(registerId);

        boolean conflictResolved = false;
        boolean degraded = !stateController.isOperationPermitted(originRegion) || !stateController.isOperationPermitted(targetRegion);

        if (originReg != null && targetReg != null) {
            CausalVectorClock.Ordering ordering = originReg.getVectorClock().compare(targetReg.getVectorClock());
            if (ordering == CausalVectorClock.Ordering.CONCURRENT) {
                conflictResolved = true;
            }
            // 双向半格合并
            originReg.merge(targetReg.snapshot());
            targetReg.merge(originReg.snapshot());
        } else if (originReg != null) {
            targetMap.put(registerId, originReg.snapshot());
        } else if (targetReg != null) {
            originMap.put(registerId, targetReg.snapshot());
        }

        // 计算收敛状态哈希
        StateBasedCrdtRegister<Object> convergedReg = originMap.get(registerId);
        String stateHash = computeStateHash(convergedReg != null ? convergedReg.getValue() : null);
        String clockSnapshot = convergedReg != null ? convergedReg.getVectorClock().toString() : "{}";

        String decisionSummary;
        if (degraded) {
            decisionSummary = "跨域网络存在延迟抖动或分区风险，处于降级保护态执行受限合并";
        } else if (conflictResolved) {
            decisionSummary = "检测到跨域并发写入冲突，通过结合半格确定性打破平局算子成功消解并单调收敛";
        } else {
            decisionSummary = "跨域状态因果偏序同步完成，两地寄存器严格达到强最终一致性 (SEC)";
        }

        String receiptId = "GEO-SYNC-R" + roundId + "-" + UUID.randomUUID().toString().substring(0, 8);
        return GeoStateAuditReceipt.create(
                receiptId,
                roundId,
                originRegion,
                targetRegion,
                clockSnapshot,
                stateHash,
                conflictResolved,
                degraded,
                decisionSummary,
                System.currentTimeMillis()
        );
    }

    private String computeStateHash(Object val) {
        String raw = val != null ? val.toString() : "NULL_STATE";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    public StateBasedCrdtRegister<Object> getRegister(String regionId, String registerId) {
        Map<String, StateBasedCrdtRegister<Object>> map = regionalRegisters.get(regionId);
        return map != null ? map.get(registerId) : null;
    }

    public CrossDomainSyncBus getSyncBus() {
        return syncBus;
    }

    public GeoStateMachineController getStateController() {
        return stateController;
    }
}
