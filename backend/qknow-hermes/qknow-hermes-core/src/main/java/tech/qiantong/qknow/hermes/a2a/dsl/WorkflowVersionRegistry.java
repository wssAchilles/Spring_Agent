package tech.qiantong.qknow.hermes.a2a.dsl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.agent.dag.PhasedExecutionPlan;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 工作流多版本共存与生命周期租约注册表（支持在途长事务优雅下线 Graceful Drain）
 */
public class WorkflowVersionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkflowVersionRegistry.class);

    public enum VersionStatus {
        ACTIVE,
        DRAINING,
        TERMINATED
    }

    public static class VersionState {
        private final int version;
        private final DslWorkflowDefinition definition;
        private final PhasedExecutionPlan executionPlan;
        private final DslWorkflowCompilationReceipt receipt;
        private volatile VersionStatus status;
        private final LongAdder activeSessionCounter = new LongAdder();

        public VersionState(
                int version,
                DslWorkflowDefinition definition,
                PhasedExecutionPlan executionPlan,
                DslWorkflowCompilationReceipt receipt,
                VersionStatus status
        ) {
            this.version = version;
            this.definition = definition;
            this.executionPlan = executionPlan;
            this.receipt = receipt;
            this.status = status;
        }

        public int getVersion() {
            return version;
        }

        public DslWorkflowDefinition getDefinition() {
            return definition;
        }

        public PhasedExecutionPlan getExecutionPlan() {
            return executionPlan;
        }

        public DslWorkflowCompilationReceipt getReceipt() {
            return receipt;
        }

        public VersionStatus getStatus() {
            return status;
        }

        public void setStatus(VersionStatus status) {
            this.status = status;
        }

        public long getActiveSessions() {
            return activeSessionCounter.sum();
        }

        public void incrementSession() {
            activeSessionCounter.increment();
        }

        public void decrementSession() {
            activeSessionCounter.decrement();
        }
    }

    public record VersionLease(
            int version,
            DslWorkflowDefinition definition,
            PhasedExecutionPlan executionPlan,
            DslWorkflowCompilationReceipt receipt
    ) {}

    private final AtomicInteger activeVersion = new AtomicInteger(-1);
    private final Map<Integer, VersionState> versionMap = new ConcurrentHashMap<>();

    /**
     * 发布新版本并平滑下线旧版本 (Graceful Drain)
     */
    public synchronized VersionState publishVersion(
            DslWorkflowDefinition definition,
            PhasedExecutionPlan plan,
            DslWorkflowCompilationReceipt receipt
    ) {
        int newVer = definition.version();
        int oldVer = activeVersion.get();

        // 1. 若旧版本存在，标记为 DRAINING
        if (oldVer > 0 && versionMap.containsKey(oldVer)) {
            VersionState oldState = versionMap.get(oldVer);
            oldState.setStatus(VersionStatus.DRAINING);
            log.info("[VersionRegistry] 旧版本 v{} 状态置为 DRAINING (当前活跃租约数: {})",
                    oldVer, oldState.getActiveSessions());
        }

        // 2. 注册新版本并置为 ACTIVE
        VersionState newState = new VersionState(newVer, definition, plan, receipt, VersionStatus.ACTIVE);
        versionMap.put(newVer, newState);
        activeVersion.set(newVer);

        log.info("[VersionRegistry] 新版本 v{} 成功发布为 ACTIVE", newVer);
        return newState;
    }

    /**
     * 为新会话申请当前活跃版本租约
     */
    public Optional<VersionLease> acquireActiveLease() {
        int ver = activeVersion.get();
        if (ver <= 0) {
            return Optional.empty();
        }
        return acquireLease(ver);
    }

    /**
     * 为在途会话申请指定历史版本租约
     */
    public Optional<VersionLease> acquireLease(int version) {
        VersionState state = versionMap.get(version);
        if (state == null || state.getStatus() == VersionStatus.TERMINATED) {
            return Optional.empty();
        }
        state.incrementSession();
        return Optional.of(new VersionLease(
                state.getVersion(),
                state.getDefinition(),
                state.getExecutionPlan(),
                state.getReceipt()
        ));
    }

    /**
     * 释放会话租约并在活跃会话归零时回收 DRAINING 状态版本
     */
    public void releaseLease(int version) {
        VersionState state = versionMap.get(version);
        if (state != null) {
            state.decrementSession();
            if (state.getStatus() == VersionStatus.DRAINING && state.getActiveSessions() <= 0) {
                state.setStatus(VersionStatus.TERMINATED);
                log.info("[VersionRegistry] 历史版本 v{} 活跃租约已全部排空，置为 TERMINATED", version);
            }
        }
    }

    /**
     * 一键原子回滚至指定历史版本
     */
    public synchronized boolean rollbackToVersion(int targetVersion) {
        VersionState targetState = versionMap.get(targetVersion);
        if (targetState == null) {
            log.warn("[VersionRegistry] 回滚失败：目标版本 v{} 不存在", targetVersion);
            return false;
        }

        int currentVer = activeVersion.get();
        if (currentVer == targetVersion) {
            log.info("[VersionRegistry] 目标版本已为当前活跃版本 v{}，无需回滚", targetVersion);
            return true;
        }

        if (currentVer > 0 && versionMap.containsKey(currentVer)) {
            VersionState curState = versionMap.get(currentVer);
            curState.setStatus(VersionStatus.DRAINING);
        }

        targetState.setStatus(VersionStatus.ACTIVE);
        activeVersion.set(targetVersion);
        log.warn("[VersionRegistry] 紧急回滚成功! activeVersion 切换为 v{}", targetVersion);
        return true;
    }

    public int getActiveVersion() {
        return activeVersion.get();
    }

    public Optional<VersionState> getVersionState(int version) {
        return Optional.ofNullable(versionMap.get(version));
    }

    public Map<Integer, VersionState> getAllVersions() {
        return Map.copyOf(versionMap);
    }
}
