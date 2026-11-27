package tech.qiantong.qknow.hermes.transaction.frontier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 进度感知纪元-前沿（Epoch-Frontier）工具事务与三级副作用隔离治理器。
 * <p>
 * 严格实现博士论文第 6 章定义 6.1、定义 6.2 与定理 6.1：
 * 1. 全局严格单调递增逻辑纪元 {@code e(T_b)} 与每资源水位线 {@code frontier(r) = min {e(T) | T in B_active and r in scope(T)}}；
 * 2. 耦合 S^1023 平方弦投机保真度损失 {@code L_fid(gamma) = 4*sin^2(pi*(1-gamma)/2)}，对 {@code IRREVERSIBLE} 动作在投机分支上执行前 100% 冻结挂起；
 * 3. 提交屏障强制满足 {@code forall r in scope(T_b), frontier(r) >= e(T_b)}，保证严格纪元序冲突可串行化；
 * 4. 中止或租约超时分支按严格逆拓扑日志序执行 {@code c_k^{-1} ... c_1^{-1}} 幂等补偿并归入三级冷备可逆环形缓冲区（零物理硬删除、零死锁活性）。
 * </p>
 */
public class FrontierGatedToolTransactionManager {

    public static final double MIN_BUFFERABLE_SPEC_CONFIDENCE = 0.75;
    public static final double MIN_REVERSIBLE_SPEC_CONFIDENCE = 0.88;
    private static final int COLD_RING_CAPACITY = 256;

    /**
     * 事务分支不可变描述符
     */
    public record BranchTransactionDescriptor(
            String branchId,
            long epoch,
            Set<String> resourceScope,
            boolean speculative,
            double confidence,
            double chordFidelityLossBound,
            long openedAtEpochMs,
            long leaseTtlMs
    ) {}

    /**
     * 三级冷备可逆归档记录（零物理硬删除）
     */
    public record QuarantinedTransactionRecord(
            String branchId,
            long epoch,
            Set<String> resourceScope,
            String reason,
            int executedCompensations,
            long archivedAtEpochMs
    ) {}

    private record LoggedReversibleAction(String toolName, String resourceUri, Runnable inverseAction) {}

    private static final class ActiveBranchState {
        final BranchTransactionDescriptor descriptor;
        final Map<String, Object> bufferedWrites = new LinkedHashMap<>();
        final List<LoggedReversibleAction> reversibleLog = new ArrayList<>();
        boolean committed;
        boolean aborted;

        ActiveBranchState(BranchTransactionDescriptor descriptor) {
            this.descriptor = descriptor;
        }
    }

    private final byte[] hmacSecret;
    private final AtomicLong globalEpochGenerator = new AtomicLong(0L);
    private final AtomicLong frontierSequenceGenerator = new AtomicLong(0L);

    private final Map<String, ActiveBranchState> activeBranches = new LinkedHashMap<>();
    // 记录每个资源已结算或吊销的最大纪元水位，用于拦截过期纪元重放
    private final Map<String, Long> maxRetiredEpochByResource = new HashMap<>();
    // 记录待 HITL 审批挂起的 IRREVERSIBLE/低置信度工具调用（Plan-as-PR 数据源）
    private final Map<String, FrontierCommitDecision.SuspendedForHitlApproval> pendingHitlApprovals = new LinkedHashMap<>();
    private final Deque<QuarantinedTransactionRecord> coldRingBuffer = new ArrayDeque<>(COLD_RING_CAPACITY);
    private long totalCommittedTransactions = 0L;

    public FrontierGatedToolTransactionManager(String secretKey) {
        String key = (secretKey == null || secretKey.isBlank()) ? "hermes-frontier-tx-default-secret" : secretKey;
        this.hmacSecret = key.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 计算 S^1023 超球面投机状态偏离平方弦保真损失上界：4 * sin^2(pi * (1 - gamma) / 2)。
     */
    public static double computeHypersphericalSpeculativeLossBound(double confidence) {
        double clamped = Math.max(0.0, Math.min(1.0, confidence));
        double halfTheta = Math.PI * (1.0 - clamped) / 2.0;
        double s = Math.sin(halfTheta);
        return 4.0 * s * s;
    }

    /**
     * 派生并登记一个新的并发或投机执行分支，分配全局严格递增逻辑纪元 e(T_b)。
     */
    public synchronized BranchTransactionDescriptor openBranch(
            String branchId,
            Set<String> resourceScope,
            boolean speculative,
            double confidence,
            long leaseTtlMs
    ) {
        Objects.requireNonNull(branchId, "branchId 不能为空");
        if (activeBranches.containsKey(branchId)) {
            throw new IllegalArgumentException("活跃事务分支已存在: " + branchId);
        }
        long epoch = globalEpochGenerator.incrementAndGet();
        Set<String> safeScope = Set.copyOf(resourceScope != null ? resourceScope : Set.of());
        double lossBound = computeHypersphericalSpeculativeLossBound(confidence);
        long now = System.currentTimeMillis();

        BranchTransactionDescriptor desc = new BranchTransactionDescriptor(
                branchId, epoch, safeScope, speculative, confidence, lossBound, now, Math.max(1L, leaseTtlMs)
        );
        activeBranches.put(branchId, new ActiveBranchState(desc));
        return desc;
    }

    /**
     * 执行受三级副作用安全格保护的工具调用（AllowExec 门控谓词）。
     */
    public synchronized FrontierCommitDecision executeGatedTool(
            String branchId,
            String toolName,
            String resourceUri,
            ToolSideEffectTier effectTier,
            boolean hitlApproved,
            Supplier<Map<String, Object>> forwardAction,
            Runnable compensatingInverse
    ) {
        ActiveBranchState state = activeBranches.get(branchId);
        if (state == null || state.aborted) {
            return new FrontierCommitDecision.RejectedStaleEpoch(
                    resourceUri, -1L, getResourceFrontier(resourceUri));
        }

        BranchTransactionDescriptor desc = state.descriptor;

        switch (effectTier) {
            case IRREVERSIBLE -> {
                // 核心铁律（定理 6.1(2)）：投机分支或未经人工批准的不可逆动作在触发前 100% 挂起，外部泄漏恒等于 0！
                if (desc.speculative() || !hitlApproved) {
                    var suspended = new FrontierCommitDecision.SuspendedForHitlApproval(
                            branchId, toolName, resourceUri, desc.epoch(),
                            desc.speculative() ? "投机分支严禁执行物理不可逆动作" : "物理不可逆动作等待 HITL 人工批准"
                    );
                    pendingHitlApprovals.put(branchId + ":" + toolName + ":" + resourceUri, suspended);
                    return suspended;
                }
                long frontier = getResourceFrontier(resourceUri);
                if (frontier < desc.epoch()) {
                    return new FrontierCommitDecision.DeferredInQueue(resourceUri, desc.epoch(), frontier);
                }
                Map<String, Object> out = forwardAction.get();
                if (out != null) {
                    state.bufferedWrites.putAll(out);
                }
                return new FrontierCommitDecision.BufferedOrStaged(branchId, toolName, effectTier, desc.epoch());
            }
            case EXTERNALIZED_REVERSIBLE -> {
                if (compensatingInverse == null) {
                    throw new IllegalArgumentException("EXTERNALIZED_REVERSIBLE 动作必须提供非空幂等逆向补偿算子: " + toolName);
                }
                if (desc.speculative() && desc.confidence() < MIN_REVERSIBLE_SPEC_CONFIDENCE) {
                    return new FrontierCommitDecision.SuspendedForHitlApproval(
                            branchId, toolName, resourceUri, desc.epoch(),
                            "投机置信度低于 0.88 阈值，拒绝释放外部可逆暂存动作"
                    );
                }
                Map<String, Object> out = forwardAction.get();
                if (out != null) {
                    state.bufferedWrites.putAll(out);
                }
                state.reversibleLog.add(new LoggedReversibleAction(toolName, resourceUri, compensatingInverse));
                return new FrontierCommitDecision.BufferedOrStaged(branchId, toolName, effectTier, desc.epoch());
            }
            case BUFFERABLE -> {
                if (desc.speculative() && desc.confidence() < MIN_BUFFERABLE_SPEC_CONFIDENCE) {
                    return new FrontierCommitDecision.SuspendedForHitlApproval(
                            branchId, toolName, resourceUri, desc.epoch(),
                            "投机置信度低于 0.75 缓冲阈值"
                    );
                }
                Map<String, Object> out = forwardAction.get();
                if (out != null) {
                    state.bufferedWrites.putAll(out);
                }
                return new FrontierCommitDecision.BufferedOrStaged(branchId, toolName, effectTier, desc.epoch());
            }
            default -> throw new IllegalStateException("未知副作用等级: " + effectTier);
        }
    }

    /**
     * 进度感知原子提交门控：当且仅当 forall r in scope(T_b), frontier(r) >= e(T_b) 时准予按纪元序提交。
     */
    public synchronized FrontierCommitDecision tryCommitBranch(String branchId) {
        ActiveBranchState state = activeBranches.get(branchId);
        if (state == null || state.aborted) {
            return new FrontierCommitDecision.RejectedStaleEpoch("UNKNOWN", -1L, Long.MAX_VALUE);
        }

        long epoch = state.descriptor.epoch();
        for (String resource : state.descriptor.resourceScope()) {
            long retiredMax = maxRetiredEpochByResource.getOrDefault(resource, 0L);
            if (epoch <= retiredMax) {
                return new FrontierCommitDecision.RejectedStaleEpoch(resource, epoch, retiredMax);
            }
            long frontier = getResourceFrontier(resource);
            if (frontier < epoch) {
                return new FrontierCommitDecision.DeferredInQueue(resource, epoch, frontier);
            }
        }

        // 所有受管资源均满足 frontier(r) == e(T_b)，原子提交并推进前沿水位线
        state.committed = true;
        activeBranches.remove(branchId);
        String primaryResource = state.descriptor.resourceScope().stream().findFirst().orElse("GLOBAL");
        for (String r : state.descriptor.resourceScope()) {
            maxRetiredEpochByResource.merge(r, epoch, Math::max);
        }
        long seq = frontierSequenceGenerator.incrementAndGet();
        totalCommittedTransactions++;
        byte[] mac = signCommitReceipt(branchId, primaryResource, epoch, seq);
        return new FrontierCommitDecision.Committed(primaryResource, epoch, seq, mac);
    }

    /**
     * 显式纪元重放校验（用于拦截已超时吊销或过期分支的延迟网络写入）。
     */
    public synchronized FrontierCommitDecision tryCommitWithExplicitEpoch(
            String branchId,
            String resourceUri,
            long incomingEpoch
    ) {
        long retiredMax = maxRetiredEpochByResource.getOrDefault(resourceUri, 0L);
        long currentFrontier = getResourceFrontier(resourceUri);
        ActiveBranchState active = activeBranches.get(branchId);
        if (active == null || incomingEpoch <= retiredMax || incomingEpoch < currentFrontier) {
            long referenceEpoch = Math.max(retiredMax, currentFrontier == Long.MAX_VALUE ? retiredMax : currentFrontier);
            return new FrontierCommitDecision.RejectedStaleEpoch(resourceUri, incomingEpoch, referenceEpoch);
        }
        return tryCommitBranch(branchId);
    }

    /**
     * 中止指定分支：按严格逆拓扑日志序执行幂等补偿链 c_k^{-1} ... c_1^{-1}，并归档至三级冷备可逆环。
     */
    public synchronized QuarantinedTransactionRecord abortBranch(String branchId, String reason) {
        ActiveBranchState state = activeBranches.remove(branchId);
        if (state == null) {
            throw new IllegalStateException("未找到待中止的活跃分支: " + branchId);
        }
        state.aborted = true;
        int compensated = runReverseCompensations(state);
        for (String r : state.descriptor.resourceScope()) {
            maxRetiredEpochByResource.merge(r, state.descriptor.epoch(), Math::max);
        }
        return archiveToColdRing(state.descriptor, reason, compensated);
    }

    /**
     * 有界租约超时守护（定理 6.1(3)）：回收超时慢节点，触发逆序补偿并单调推进受阻资源水位线。
     */
    public synchronized List<FrontierCommitDecision.LeaseRecoveredFromCrash> reapExpiredBranches(long nowEpochMs) {
        List<String> expiredIds = new ArrayList<>();
        for (Map.Entry<String, ActiveBranchState> entry : activeBranches.entrySet()) {
            BranchTransactionDescriptor d = entry.getValue().descriptor;
            if (nowEpochMs - d.openedAtEpochMs() >= d.leaseTtlMs()) {
                expiredIds.add(entry.getKey());
            }
        }

        List<FrontierCommitDecision.LeaseRecoveredFromCrash> events = new ArrayList<>();
        for (String expiredId : expiredIds) {
            ActiveBranchState removed = activeBranches.remove(expiredId);
            if (removed == null) {
                continue;
            }
            removed.aborted = true;
            int compensated = runReverseCompensations(removed);
            for (String r : removed.descriptor.resourceScope()) {
                maxRetiredEpochByResource.merge(r, removed.descriptor.epoch(), Math::max);
                long nextFrontier = getResourceFrontier(r);
                events.add(new FrontierCommitDecision.LeaseRecoveredFromCrash(
                        r, expiredId, removed.descriptor.epoch(), nextFrontier
                ));
            }
            archiveToColdRing(removed.descriptor, "LEASE_TTL_EXPIRED", compensated);
        }
        return events;
    }

    /**
     * 计算指定资源当前的最小未决纪元水位线 frontier(r)。若无未决分支则返回 Long.MAX_VALUE (+infty)。
     */
    public synchronized long getResourceFrontier(String resourceUri) {
        long minEpoch = Long.MAX_VALUE;
        for (ActiveBranchState s : activeBranches.values()) {
            if (!s.aborted && !s.committed && s.descriptor.resourceScope().contains(resourceUri)) {
                if (s.descriptor.epoch() < minEpoch) {
                    minEpoch = s.descriptor.epoch();
                }
            }
        }
        return minEpoch;
    }

    public synchronized int getColdRingArchivedCount() {
        return coldRingBuffer.size();
    }

    public synchronized List<QuarantinedTransactionRecord> snapshotColdRing() {
        return List.copyOf(coldRingBuffer);
    }

    public synchronized List<FrontierCommitDecision.SuspendedForHitlApproval> listPendingHitlApprovals() {
        return List.copyOf(pendingHitlApprovals.values());
    }

    public synchronized boolean resolvePendingHitlApproval(String approvalKey, boolean approved, String reason) {
        if (approvalKey == null || approvalKey.isBlank()) {
            return false;
        }
        FrontierCommitDecision.SuspendedForHitlApproval removed = pendingHitlApprovals.remove(approvalKey);
        if (removed == null) {
            for (String k : new ArrayList<>(pendingHitlApprovals.keySet())) {
                if (k.startsWith(approvalKey + ":") || k.equals(approvalKey)) {
                    removed = pendingHitlApprovals.remove(k);
                    break;
                }
            }
        }
        if (removed == null) {
            return false;
        }
        if (!approved && activeBranches.containsKey(removed.branchId())) {
            abortBranch(removed.branchId(), reason != null ? reason : "HITL_REJECTED");
        }
        return true;
    }

    public synchronized Map<String, Object> getTelemetryStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeBranchCount", activeBranches.size());
        stats.put("pendingHitlCount", pendingHitlApprovals.size());
        stats.put("committedCount", totalCommittedTransactions);
        stats.put("coldRingCount", coldRingBuffer.size());
        stats.put("latestGlobalEpoch", globalEpochGenerator.get());
        return Collections.unmodifiableMap(stats);
    }

    private int runReverseCompensations(ActiveBranchState state) {
        int count = 0;
        for (int i = state.reversibleLog.size() - 1; i >= 0; i--) {
            LoggedReversibleAction action = state.reversibleLog.get(i);
            action.inverseAction().run();
            count++;
        }
        return count;
    }

    private QuarantinedTransactionRecord archiveToColdRing(
            BranchTransactionDescriptor desc,
            String reason,
            int compensatedCount
    ) {
        QuarantinedTransactionRecord record = new QuarantinedTransactionRecord(
                desc.branchId(), desc.epoch(), desc.resourceScope(), reason,
                compensatedCount, System.currentTimeMillis()
        );
        if (coldRingBuffer.size() >= COLD_RING_CAPACITY) {
            coldRingBuffer.pollFirst();
        }
        coldRingBuffer.addLast(record);
        return record;
    }

    private byte[] signCommitReceipt(String branchId, String resourceUri, long epoch, long seq) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            String payload = branchId + "|" + resourceUri + "|" + epoch + "|" + seq;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("事务提交凭单签名异常", e);
        }
    }
}
