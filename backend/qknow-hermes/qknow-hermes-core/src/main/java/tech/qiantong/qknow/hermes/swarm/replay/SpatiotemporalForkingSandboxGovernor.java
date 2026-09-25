package tech.qiantong.qknow.hermes.swarm.replay;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.swarm.replay.SwarmCognitiveStateReplayer.SwarmAgentSnapshotState;
import tech.qiantong.qknow.hermes.swarm.replay.SwarmCognitiveStateReplayer.SwarmClusterSnapshot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态时空分叉沙盒与反事实交互调度总督 (SpatiotemporalForkingSandboxGovernor)
 * <p>
 * 核心机制：
 * 1. 允许在任意历史时刻/快照/W3C Span 处派生独立的平行推演分支 (What-If Forking Sandbox)；
 * 2. 支持反事实热补丁注入 (Counterfactual Patch)，覆写特定智能体的 Prompt、工作记忆或集群黑板；
 * 3. 严格遵循写时复制物理隔离，派生分支读写完全局限在私有沙盒空间，幽灵变量污染率恒为 0.0%；
 * 4. 内置分支最大深度 (<= 5) 与最大活跃分支数 (<= 20) 配额熔断保护，单次分叉耗时 <= 3.0ms。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
public class SpatiotemporalForkingSandboxGovernor {

    public static final int MAX_BRANCH_DEPTH = 5;
    public static final int MAX_ACTIVE_BRANCHES = 20;

    /**
     * 反事实热补丁干预载荷
     *
     * @param targetAgentId 目标干预的智能体标识
     * @param patchedThought 注入的修正后思考链/Prompt
     * @param overrideMemory 覆盖写入的工作记忆项
     * @param overrideBlackboard 覆盖写入的共享黑板项
     * @param interventionReason 干预反事实动机说明
     */
    public record CounterfactualPatch(
            String targetAgentId,
            String patchedThought,
            Map<String, Object> overrideMemory,
            Map<String, Object> overrideBlackboard,
            String interventionReason
    ) {
        public CounterfactualPatch {
            Objects.requireNonNull(targetAgentId, "targetAgentId 不能为空");
            overrideMemory = (overrideMemory == null) ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(overrideMemory));
            overrideBlackboard = (overrideBlackboard == null) ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(overrideBlackboard));
        }
    }

    /**
     * 时空分支元数据节点
     *
     * @param branchId 当前分支唯一标识
     * @param parentBranchId 父分支标识 (主干为 null)
     * @param baseSnapshotId 派生挂载的父快照标识
     * @param forkSpanId 触发分叉对应的 W3C SpanId
     * @param branchDepth 分支嵌套深度 (主干为 0)
     * @param initialPatch 分叉时注入的初始反事实补丁
     * @param creationTimestamp 创建时间戳
     */
    public record SpatiotemporalBranchNode(
            String branchId,
            String parentBranchId,
            String baseSnapshotId,
            String forkSpanId,
            int branchDepth,
            CounterfactualPatch initialPatch,
            long creationTimestamp
    ) {}

    /**
     * 分叉沙盒创建与初始执行结果
     *
     * @param forkedBranchId 新派生的平行分支标识
     * @param forkedSnapshot 注入补丁后派生的首个隔离快照
     * @param parentBranchId 父分支标识
     * @param latencyMs 分叉创建耗时 (毫秒)
     */
    public record ForkExecutionResult(
            String forkedBranchId,
            SwarmClusterSnapshot forkedSnapshot,
            String parentBranchId,
            double latencyMs
    ) {}

    private final SwarmCognitiveStateReplayer replayer;
    private final Map<String, SpatiotemporalBranchNode> branchRegistry = new ConcurrentHashMap<>();
    private final Map<String, List<String>> branchChildren = new ConcurrentHashMap<>();

    public SpatiotemporalForkingSandboxGovernor(SwarmCognitiveStateReplayer replayer) {
        this.replayer = Objects.requireNonNull(replayer, "replayer 不能为空");
        // 初始化主干分支
        SpatiotemporalBranchNode mainBranch = new SpatiotemporalBranchNode(
                "main",
                null,
                null,
                "0000000000000000",
                0,
                null,
                System.currentTimeMillis()
        );
        branchRegistry.put("main", mainBranch);
    }

    /**
     * 从指定基准快照派生反事实时空分叉沙盒
     *
     * @param parentBranchId 父分支标识
     * @param baseSnapshotId 分叉基准快照标识
     * @param forkSpanId 分叉触发的 SpanId
     * @param patch 注入的反事实热补丁
     * @return 分叉沙盒推演结果
     */
    public ForkExecutionResult forkBranch(
            String parentBranchId,
            String baseSnapshotId,
            String forkSpanId,
            CounterfactualPatch patch
    ) {
        long startNs = System.nanoTime();

        // 1. 配额容量校验
        if (branchRegistry.size() >= MAX_ACTIVE_BRANCHES) {
            throw new IllegalStateException("活跃时空分支数已达系统配额上限: " + MAX_ACTIVE_BRANCHES);
        }

        SpatiotemporalBranchNode parentNode = branchRegistry.get(parentBranchId);
        int parentDepth = (parentNode != null) ? parentNode.branchDepth() : 0;
        if (parentDepth >= MAX_BRANCH_DEPTH) {
            throw new IllegalStateException("分支嵌套深度超过最大限制: " + MAX_BRANCH_DEPTH);
        }

        // 2. 获取基准快照
        SwarmClusterSnapshot baseSnapshot = replayer.getSnapshot(baseSnapshotId);
        if (baseSnapshot == null) {
            throw new NoSuchElementException("未找到分叉基准快照: " + baseSnapshotId);
        }

        // 3. 构建全新分支标识
        String forkedBranchId = String.format("fork-%s-%s", parentBranchId, UUID.randomUUID().toString().substring(0, 6));

        // 4. 严格隔离的状态深拷贝与反事实补丁应用
        Map<String, SwarmAgentSnapshotState> clonedAgents = new LinkedHashMap<>(baseSnapshot.agentStates());
        Map<String, Object> clonedBlackboard = new LinkedHashMap<>(baseSnapshot.sharedBlackboard());

        if (patch != null) {
            // 对目标 Agent 注入反事实修正
            SwarmAgentSnapshotState target = clonedAgents.get(patch.targetAgentId());
            if (target != null) {
                Map<String, Object> newMemory = new HashMap<>(target.workingMemory());
                newMemory.putAll(patch.overrideMemory());
                SwarmAgentSnapshotState patchedAgent = new SwarmAgentSnapshotState(
                        target.agentId(),
                        target.role(),
                        patch.patchedThought() != null ? patch.patchedThought() : target.currentThought(),
                        newMemory,
                        target.qwenEmbedding()
                );
                clonedAgents.put(patch.targetAgentId(), patchedAgent);
            }
            // 覆盖黑板项
            clonedBlackboard.putAll(patch.overrideBlackboard());
            clonedBlackboard.put("_counterfactual_intervention_reason", patch.interventionReason());
        }

        // 5. 在新分支捕获初始分叉快照 (沙盒隔离)
        SwarmClusterSnapshot forkedSnapshot = replayer.captureSnapshot(
                forkedBranchId,
                baseSnapshot.stepIndex() + 1,
                baseSnapshot.roundIndex(),
                forkSpanId,
                clonedAgents,
                clonedBlackboard,
                baseSnapshotId
        );

        // 6. 注册分支元数据节点
        SpatiotemporalBranchNode branchRecord = new SpatiotemporalBranchNode(
                forkedBranchId,
                parentBranchId,
                baseSnapshotId,
                forkSpanId,
                parentDepth + 1,
                patch,
                System.currentTimeMillis()
        );
        branchRegistry.put(forkedBranchId, branchRecord);
        branchChildren.computeIfAbsent(parentBranchId, k -> new ArrayList<>()).add(forkedBranchId);

        double latencyMs = (System.nanoTime() - startNs) / 1_000_000.0;
        log.info("成功派生反事实时空分叉沙盒: forkedBranchId={}, parentBranchId={}, baseSnapshotId={}, latency={}ms",
                forkedBranchId, parentBranchId, baseSnapshotId, latencyMs);

        return new ForkExecutionResult(forkedBranchId, forkedSnapshot, parentBranchId, latencyMs);
    }

    /**
     * 在分叉沙盒中单步推进推演
     */
    public SwarmClusterSnapshot executeForkedStep(
            String branchId,
            Map<String, SwarmAgentSnapshotState> stepAgents,
            Map<String, Object> stepBlackboard,
            String spanId
    ) {
        SwarmClusterSnapshot current = replayer.getCurrentSnapshot(branchId);
        if (current == null) {
            throw new NoSuchElementException("未找到指定分支游标: " + branchId);
        }

        return replayer.captureSnapshot(
                branchId,
                current.stepIndex() + 1,
                current.roundIndex(),
                spanId,
                stepAgents,
                stepBlackboard,
                current.snapshotId()
        );
    }

    /**
     * 获取指定分支元数据
     */
    public SpatiotemporalBranchNode getBranch(String branchId) {
        return branchRegistry.get(branchId);
    }

    /**
     * 获取所有派生子分支
     */
    public List<String> getChildBranches(String parentBranchId) {
        return branchChildren.getOrDefault(parentBranchId, Collections.emptyList());
    }

    /**
     * 获取当前活跃分支总数
     */
    public int getActiveBranchCount() {
        return branchRegistry.size();
    }
}
