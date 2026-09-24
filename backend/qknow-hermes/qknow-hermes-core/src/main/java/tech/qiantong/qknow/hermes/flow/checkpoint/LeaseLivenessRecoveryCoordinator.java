package tech.qiantong.qknow.hermes.flow.checkpoint;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.dag.DagCheckpointManager;

import java.util.*;
import java.util.concurrent.*;

/**
 * 分布式 MCP 断点租约活性自愈中枢 (Lease Liveness Recovery Coordinator)
 * 周期性探活扫描由于节点宕机、长 GC 或物理隔离导致超时的断点
 * 并基于双轨原子 CAS 进行所有权仲裁强占自愈，消解长事务永久悬挂风险
 */
@Slf4j
@Component
public class LeaseLivenessRecoveryCoordinator implements AutoCloseable {

    @Autowired
    private DagCheckpointManager checkpointManager;

    @Getter
    private final String nodeId;

    @Getter
    private final long defaultLeaseDurationMs;

    private final ScheduledExecutorService watchdogScheduler;
    private ScheduledFuture<?> scheduledTask;

    public LeaseLivenessRecoveryCoordinator() {
        this(null, "node-" + UUID.randomUUID().toString().substring(0, 8), 10_000L);
    }

    public LeaseLivenessRecoveryCoordinator(DagCheckpointManager checkpointManager, String nodeId, long defaultLeaseDurationMs) {
        this.checkpointManager = checkpointManager;
        this.nodeId = nodeId != null ? nodeId : "node-" + UUID.randomUUID().toString().substring(0, 8);
        this.defaultLeaseDurationMs = defaultLeaseDurationMs > 0 ? defaultLeaseDurationMs : 10_000L;
        this.watchdogScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lease-recovery-watchdog-" + this.nodeId);
            t.setDaemon(true);
            return t;
        });
    }

    public void setCheckpointManager(DagCheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    /**
     * 启动周期性看门狗探活自愈
     */
    public synchronized void startWatchdog(long initialDelayMs, long periodMs) {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            return;
        }
        scheduledTask = watchdogScheduler.scheduleAtFixedRate(() -> {
            try {
                recoverExpiredLeases();
            } catch (Exception e) {
                log.warn("看门狗探活自愈轮询异常: {}", e.getMessage());
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        log.info("分布式断点租约看门狗已启动: nodeId={}, periodMs={}", nodeId, periodMs);
    }

    /**
     * 扫描超时的断点并执行原子抢占自愈
     * @return 本轮成功接管恢复的存证凭单列表
     */
    public List<LeaseLivenessRecoveryReceipt> recoverExpiredLeases() {
        if (checkpointManager == null) {
            return Collections.emptyList();
        }
        List<DagCheckpointManager.DagCheckpoint> expiredList = checkpointManager.findExpiredRunningCheckpoints();
        if (expiredList.isEmpty()) {
            return Collections.emptyList();
        }

        List<LeaseLivenessRecoveryReceipt> recoveredReceipts = new ArrayList<>();
        for (DagCheckpointManager.DagCheckpoint cp : expiredList) {
            Optional<LeaseLivenessRecoveryReceipt> receiptOpt = checkpointManager.wakeSuspendedOrRecoverLease(
                    cp.getRuntimeId(),
                    nodeId,
                    defaultLeaseDurationMs,
                    null
            );
            receiptOpt.ifPresent(receipt -> {
                log.info("成功自愈接管超时断点: runtimeId={}, prevOwner={}, newOwner={}, fencingToken={}",
                        receipt.runtimeId(), receipt.previousOwnerId(), receipt.newOwnerId(), receipt.newFencingToken());
                recoveredReceipts.add(receipt);
            });
        }
        return recoveredReceipts;
    }

    /**
     * 主动竞争抢占或唤醒指定断点
     */
    public Optional<LeaseLivenessRecoveryReceipt> claimOrWake(String runtimeId, Map<String, Object> humanInput) {
        if (checkpointManager == null) {
            return Optional.empty();
        }
        return checkpointManager.wakeSuspendedOrRecoverLease(runtimeId, nodeId, defaultLeaseDurationMs, humanInput);
    }

    /**
     * 续租心跳
     */
    public boolean renewHeartbeat(String runtimeId, long fencingToken) {
        if (checkpointManager == null) {
            return false;
        }
        return checkpointManager.refreshLease(runtimeId, nodeId, fencingToken, defaultLeaseDurationMs);
    }

    @Override
    public void close() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        watchdogScheduler.shutdownNow();
    }
}
