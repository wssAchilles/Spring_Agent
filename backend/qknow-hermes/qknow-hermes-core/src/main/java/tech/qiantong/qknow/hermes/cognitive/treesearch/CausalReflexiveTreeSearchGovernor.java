package tech.qiantong.qknow.hermes.cognitive.treesearch;

import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.BudgetVector6D;
import tech.qiantong.qknow.hermes.cognitive.control.EpistemicStoppingReceipt;
import tech.qiantong.qknow.hermes.cognitive.control.HypersphericalEpistemicStoppingGovernor;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 超球面漂移触发的有界因果反思树搜索治理器（BCBT，Phase 8.3 核心认知控制引擎，对应定理 8.3）。
 * <p>
 * 核心架构闭环：
 * 1. 基于 {@link PgStepJournalStateCheckpointer#forkBranchFromJournal} 实行写时复制时间旅行分叉，保障零主干状态污染；
 * 2. 基于 {@link NeuroSymbolicBranchEvaluator} 的 PUCT 探索-利用调度（客观沙盒见证 + 双向一致性 + {@code S^1023} 测地新息）；
 * 3. 基于 {@link CausalFailureReflector} 的反事实尾部补丁纠偏（Zone-0 SHA-256 严格冻结，仅增量追加 Zone-1 尾部）与 {@code ERROR_BOOK} 错题沉淀；
 * 4. 失败分支绝不物理硬删除，强制经 {@link PgStepJournalStateCheckpointer#quarantineBranchToColdRing} 软隔离至三级冷备可逆环；
 * 5. 硬搜索配额护栏（{@code maxDepth <= 4, maxActiveBranches <= 6, maxTotalNodes <= 10}）联动 {@link HypersphericalEpistemicStoppingGovernor} 最优停机。
 * </p>
 */
public class CausalReflexiveTreeSearchGovernor {

    public static final int DEFAULT_MAX_DEPTH = 4;
    public static final int DEFAULT_MAX_ACTIVE_BRANCHES = 6;
    public static final int DEFAULT_MAX_TOTAL_NODES = 10;
    public static final double DEFAULT_PRUNE_VALUE_THRESHOLD = 0.40;
    public static final double DEFAULT_CONVERGED_WINNING_VALUE = 0.88;

    /**
     * 传给外层节点展开器的不可变分支执行上下文（含持久化重构状态与尾部追加反事实约束）。
     */
    public record BranchExpansionContext(
            String executionId,
            String branchId,
            String parentNodeId,
            int depth,
            String question,
            String zone0FrozenPrefix,
            List<String> appendOnlyTailConstraints,
            Map<String, Object> reconstructedBranchState,
            CounterfactualBranchPatch appliedPatch,
            int expansionIndex
    ) {}

    /**
     * 单步分支展开执行产出（可携带真实的 {@link VerifiedCodeActReceipt} 与思维链/状态增量；Phase 10.2 新增原假设基线保全标志）。
     */
    public record BranchExpansionResult(
            String proposedActionSummary,
            String reasoningContent,
            String candidateOutput,
            String evidenceSummary,
            VerifiedCodeActReceipt codeActReceipt,
            List<AttestedToolWitness> witnesses,
            Map<String, Object> stateDeltas,
            List<String> executedTools,
            long tokensUsed,
            boolean temporalContradictionDetected,
            String returnedZone0Prefix,
            boolean preserveAsNullBaseline
    ) {
        public BranchExpansionResult(
                String proposedActionSummary,
                String reasoningContent,
                String candidateOutput,
                String evidenceSummary,
                VerifiedCodeActReceipt codeActReceipt,
                List<AttestedToolWitness> witnesses,
                Map<String, Object> stateDeltas,
                List<String> executedTools,
                long tokensUsed,
                boolean temporalContradictionDetected,
                String returnedZone0Prefix
        ) {
            this(proposedActionSummary, reasoningContent, candidateOutput, evidenceSummary,
                    codeActReceipt, witnesses, stateDeltas, executedTools, tokensUsed,
                    temporalContradictionDetected, returnedZone0Prefix, false);
        }
    }

    /**
     * 函数式分支展开器 SPI（便于离线契约单测与在线 DeepSeek/CodeAct 编排统一接入）。
     */
    @FunctionalInterface
    public interface BranchStepExpander {
        BranchExpansionResult expand(BranchExpansionContext context);
    }

    /**
     * 完整有界树搜索返回结果（含审计凭单、获胜分支节点、最优输出、全量节点与补丁清单）。
     */
    public record SearchOutcome(
            CausalTreeSearchReceipt receipt,
            CausalSearchBranchNode winningNode,
            String winningOutput,
            List<CausalSearchBranchNode> allNodes,
            List<CounterfactualBranchPatch> generatedPatches
    ) {}

    private final PgStepJournalStateCheckpointer stepJournal;
    private final NeuroSymbolicBranchEvaluator evaluator;
    private final CausalFailureReflector reflector;
    private final HypersphericalEpistemicStoppingGovernor epistemicGovernor;
    private final UnitSphereEmbeddingResolver embeddingResolver;
    private final String hmacSecretKey;
    private final int maxDepth;
    private final int maxActiveBranches;
    private final int maxTotalNodes;

    private final AtomicLong totalSearches = new AtomicLong(0L);
    private final AtomicLong totalExpandedBranches = new AtomicLong(0L);
    private final AtomicLong totalPrunedQuarantinedBranches = new AtomicLong(0L);
    private final AtomicLong totalEpistemicEarlyStops = new AtomicLong(0L);
    private final AtomicLong totalErrorBookWrites = new AtomicLong(0L);

    public CausalReflexiveTreeSearchGovernor(
            PgStepJournalStateCheckpointer stepJournal,
            NeuroSymbolicBranchEvaluator evaluator,
            CausalFailureReflector reflector,
            HypersphericalEpistemicStoppingGovernor epistemicGovernor,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        this(
                stepJournal,
                evaluator,
                reflector,
                epistemicGovernor,
                embeddingResolver,
                "hermes-bcbt-tree-search-secret",
                DEFAULT_MAX_DEPTH,
                DEFAULT_MAX_ACTIVE_BRANCHES,
                DEFAULT_MAX_TOTAL_NODES
        );
    }

    public CausalReflexiveTreeSearchGovernor(
            PgStepJournalStateCheckpointer stepJournal,
            NeuroSymbolicBranchEvaluator evaluator,
            CausalFailureReflector reflector,
            HypersphericalEpistemicStoppingGovernor epistemicGovernor,
            UnitSphereEmbeddingResolver embeddingResolver,
            String hmacSecretKey,
            int maxDepth,
            int maxActiveBranches,
            int maxTotalNodes
    ) {
        this.stepJournal = stepJournal != null ? stepJournal : new PgStepJournalStateCheckpointer();
        this.embeddingResolver = embeddingResolver != null ? embeddingResolver : new UnitSphereEmbeddingResolver();
        this.evaluator = evaluator != null ? evaluator : new NeuroSymbolicBranchEvaluator();
        this.reflector = reflector != null ? reflector : new CausalFailureReflector();
        this.epistemicGovernor = epistemicGovernor;
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-bcbt-tree-search-secret"
                : hmacSecretKey;
        this.maxDepth = Math.max(1, maxDepth);
        this.maxActiveBranches = Math.max(1, maxActiveBranches);
        this.maxTotalNodes = Math.max(1, maxTotalNodes);
    }

    /**
     * 执行严格有界的因果反思树搜索（支持消融正交退化、写时复制分叉、Zone-0 冻结校对与三级冷备剪枝）。
     */
    public SearchOutcome executeBoundedSearch(
            String executionId,
            String rootBranchId,
            int rootSuperstep,
            String question,
            String zone0FrozenPrefix,
            BudgetVector6D budget,
            HnscdsAblationConfig ablationConfig,
            BranchStepExpander expander
    ) {
        Objects.requireNonNull(expander, "BranchStepExpander 不能为空");
        long startNs = System.nanoTime();
        String safeExec = (executionId == null || executionId.isBlank())
                ? "bcbt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                : executionId;
        String safeRootBranch = (rootBranchId == null || rootBranchId.isBlank()) ? "main" : rootBranchId;
        String safeQuestion = question != null ? question : "";
        String safeZone0 = zone0FrozenPrefix != null ? zone0FrozenPrefix : "";
        String zone0Sha = sha256Hex(safeZone0);
        byte[] zone0DigestBytes = sha256Bytes(safeZone0);
        BudgetVector6D safeBudget = budget != null ? budget : BudgetVector6D.defaults();
        HnscdsAblationConfig cfg = ablationConfig != null ? ablationConfig : HnscdsAblationConfig.defaults();

        float[] queryVec = embeddingResolver.resolveUnit1024(safeQuestion);
        totalSearches.incrementAndGet();

        // 1. 消融退化开关：当未开启因果树搜索时，零分叉直行单步展开
        if (!cfg.causalReflexiveTreeSearchEnabled()) {
            return executeLinearAblatedFallback(
                    safeExec, safeRootBranch, rootSuperstep, safeQuestion, safeZone0,
                    zone0Sha, zone0DigestBytes, queryVec, safeBudget, cfg, expander, startNs
            );
        }

        // 2. 初始化根节点并建立节点索引
        Map<String, CausalSearchBranchNode> nodeMap = new LinkedHashMap<>();
        Map<String, Integer> nodeSupersteps = new LinkedHashMap<>();
        Map<String, String> nodeOutputs = new LinkedHashMap<>();
        List<CounterfactualBranchPatch> patches = new ArrayList<>();
        List<String> appendOnlyTailConstraints = new ArrayList<>();

        String rootNodeId = "bcbt_root";
        CausalSearchBranchNode rootNode = new CausalSearchBranchNode(
                rootNodeId,
                null,
                safeRootBranch,
                0,
                "Root state before counterfactual exploration",
                queryVec,
                1,
                0.50,
                evaluator.computePuctScore(0.50, 1.0, 1, 1, NeuroSymbolicBranchEvaluator.DEFAULT_C_PUCT),
                null,
                0.50,
                false,
                false
        );
        nodeMap.put(rootNodeId, rootNode);
        nodeSupersteps.put(rootNodeId, rootSuperstep);

        boolean zone0Preserved = true;
        boolean epistemicEarlyStopped = false;
        int expandedBranches = 0;
        int prunedAndQuarantined = 0;
        int maxDepthReached = 0;
        int errorBookWrites = 0;
        float[] previousBestVec = null;
        double bestNonPrunedValue = -1.0;
        CounterfactualBranchPatch pendingPatch = null;

        // 3. 有界 PUCT 主循环（硬上限 maxTotalNodes 与 maxActiveBranches）
        while (expandedBranches < maxTotalNodes) {
            int activeNonPruned = countActiveNonPruned(nodeMap);
            if (activeNonPruned >= maxActiveBranches) {
                break;
            }
            CausalSearchBranchNode parent = selectBestExpandableParent(nodeMap);
            if (parent == null) {
                break;
            }

            expandedBranches++;
            int parentStep = nodeSupersteps.getOrDefault(parent.nodeId(), rootSuperstep);
            int childDepth = parent.depth() + 1;
            maxDepthReached = Math.max(maxDepthReached, childDepth);

            CounterfactualBranchPatch patchToApply = pendingPatch;
            pendingPatch = null;
            Map<String, Object> forkOverrides = new LinkedHashMap<>();
            if (patchToApply != null) {
                forkOverrides.putAll(patchToApply.stateOverrides());
                appendOnlyTailConstraints.add(patchToApply.toAppendOnlyTailDelta());
            }

            // 3.1 PgStepJournal 写时复制持久化分叉，保证主干分支零状态污染
            String childBranchId = stepJournal.forkBranchFromJournal(
                    safeExec, parent.journalBranchId(), parentStep, forkOverrides
            );
            Map<String, Object> reconstructedState = stepJournal.reconstructStateAt(
                    safeExec, childBranchId, parentStep
            );
            String childNodeId = "bcbt_n" + expandedBranches;
            int childSuperstep = parentStep + 1;

            BranchExpansionContext expandCtx = new BranchExpansionContext(
                    safeExec,
                    childBranchId,
                    parent.nodeId(),
                    childDepth,
                    safeQuestion,
                    safeZone0,
                    List.copyOf(appendOnlyTailConstraints),
                    reconstructedState,
                    patchToApply,
                    expandedBranches
            );
            BranchExpansionResult expansion = expander.expand(expandCtx);
            if (expansion == null) {
                expansion = new BranchExpansionResult(
                        "Empty expansion", "", "", "", null, List.of(),
                        Map.of(), List.of(), 64L, false, safeZone0
                );
            }

            // 3.2 常量时间校对 Zone-0 静态前缀未被分叉逻辑改写
            String returnedZone0 = expansion.returnedZone0Prefix();
            boolean branchZone0Intact = (returnedZone0 == null)
                    || MessageDigest.isEqual(zone0DigestBytes, sha256Bytes(returnedZone0));
            if (!branchZone0Intact) {
                zone0Preserved = false;
            }

            // 3.3 将子分支步进增量持久化写入独立 branchId（fencingToken 严格 > forkBranchFromJournal 初始化的 1L）
            Map<String, Object> branchDeltas = expansion.stateDeltas();
            if (branchDeltas != null && !branchDeltas.isEmpty()) {
                long childFencing = 2L + expandedBranches;
                stepJournal.executeOrReplayIdempotentStep(
                        safeExec,
                        childBranchId,
                        childSuperstep,
                        childNodeId,
                        "bcbt-step-" + childNodeId,
                        childFencing,
                        Map.of(),
                        () -> branchDeltas
                );
            }

            String outputText = expansion.candidateOutput() != null ? expansion.candidateOutput() : "";
            float[] branchVec = embeddingResolver.resolveUnit1024(
                    outputText.isBlank() ? expansion.proposedActionSummary() : outputText
            );

            NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome eval = evaluator.evaluate(
                    new NeuroSymbolicBranchEvaluator.BranchEvaluationInput(
                            safeExec + ":" + childNodeId,
                            safeQuestion,
                            expansion.reasoningContent(),
                            outputText,
                            expansion.evidenceSummary(),
                            expansion.codeActReceipt(),
                            expansion.witnesses(),
                            queryVec,
                            branchVec,
                            previousBestVec,
                            expansion.tokensUsed(),
                            safeBudget.maxTotalTokens(),
                            expansion.temporalContradictionDetected(),
                            cfg
                    )
            );

            double nodeValue = branchZone0Intact ? eval.compositeValue() : 0.0;
            String receiptId = expansion.codeActReceipt() != null ? expansion.codeActReceipt().receiptId() : null;
            CausalSearchBranchNode childNode = new CausalSearchBranchNode(
                    childNodeId,
                    parent.nodeId(),
                    childBranchId,
                    childDepth,
                    expansion.proposedActionSummary(),
                    branchVec,
                    1,
                    nodeValue,
                    evaluator.computePuctScore(nodeValue, 1.0, parent.visitCount(), 1, NeuroSymbolicBranchEvaluator.DEFAULT_C_PUCT),
                    receiptId,
                    eval.consistencyScore(),
                    false,
                    false
            );
            nodeMap.put(childNodeId, childNode);
            nodeSupersteps.put(childNodeId, childSuperstep);
            nodeOutputs.put(childNodeId, outputText);
            totalExpandedBranches.incrementAndGet();

            // 3.4 反向传播回溯更新祖先访问计数与平均价值
            backpropagateValue(nodeMap, parent.nodeId(), nodeValue);

            boolean hasHardDefect = !branchZone0Intact || eval.hardFailureDetected();
            // Phase 10.2 原假设保全：若显式标记为 preserveAsNullBaseline 且无客观硬缺陷，即使分数低于阈值也不剥夺竞争资格
            boolean shouldPrune = hasHardDefect
                    || (!expansion.preserveAsNullBaseline() && nodeValue < DEFAULT_PRUNE_VALUE_THRESHOLD);
            boolean needReflectionHint = shouldPrune || expansion.preserveAsNullBaseline();

            if (needReflectionHint) {
                // 生成反事实反思补丁并写回海马体 ERROR_BOOK；仅当 shouldPrune 为 true 时才软隔离进冷备环
                NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome diagOutcome = branchZone0Intact
                        ? eval
                        : new NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome(
                                0.0, eval.codeActScore(), eval.consistencyScore(), eval.geodesicNovelty(),
                                eval.tokenCostPenalty(), true,
                                CounterfactualBranchPatch.FailureRootCause.TOOL_CONTRACT_VIOLATION,
                                "Zone-0 frozen prefix modified inside branch " + childBranchId
                        );
                CausalFailureReflector.ReflectionOutcome refOut = reflector.reflectAndRecord(
                        safeExec,
                        childBranchId,
                        safeQuestion,
                        diagOutcome,
                        expansion.codeActReceipt(),
                        expansion.executedTools()
                );
                patches.add(refOut.patch());
                pendingPatch = refOut.patch();
                if (refOut.persistedToErrorBook()) {
                    errorBookWrites++;
                    totalErrorBookWrites.incrementAndGet();
                }

                if (shouldPrune) {
                    stepJournal.quarantineBranchToColdRing(safeExec, childBranchId);
                    nodeMap.put(childNodeId, childNode.withPrunedAndQuarantined());
                    prunedAndQuarantined++;
                    totalPrunedQuarantinedBranches.incrementAndGet();
                } else if (nodeValue > bestNonPrunedValue) {
                    bestNonPrunedValue = nodeValue;
                    previousBestVec = branchVec;
                }
            } else if (nodeValue > bestNonPrunedValue) {
                bestNonPrunedValue = nodeValue;
                previousBestVec = branchVec;
            }

            // 3.5 HEIG-BAS 测地新息最优停机与高分收敛早停联动
            boolean heigStop = false;
            if (epistemicGovernor != null) {
                String obsText = outputText.isBlank() ? expansion.evidenceSummary() : outputText;
                EpistemicStoppingReceipt stopReceipt = epistemicGovernor.evaluateStep(
                        safeExec + ":bcbt",
                        expandedBranches,
                        obsText,
                        Math.max(64L, expansion.tokensUsed()),
                        safeBudget,
                        cfg
                );
                if (stopReceipt.decision() == EpistemicStoppingReceipt.StoppingDecision.STOP_AND_SYNTHESIZE) {
                    heigStop = true;
                }
            }
            if (heigStop || (!shouldPrune && nodeValue >= DEFAULT_CONVERGED_WINNING_VALUE)) {
                epistemicEarlyStopped = true;
                totalEpistemicEarlyStops.incrementAndGet();
                break;
            }
        }

        CausalSearchBranchNode winningNode = selectWinningNode(nodeMap);
        List<String> trajectoryIds = buildTrajectoryNodeIds(nodeMap, winningNode.nodeId());
        String winningOutput = nodeOutputs.getOrDefault(winningNode.nodeId(), "");
        long elapsedMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);

        CausalTreeSearchReceipt receipt = CausalTreeSearchReceipt.createSigned(
                "bcbt_r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                safeExec,
                zone0Sha,
                zone0Preserved,
                expandedBranches,
                prunedAndQuarantined,
                maxDepthReached,
                epistemicEarlyStopped,
                winningNode.journalBranchId(),
                winningNode.meanValue(),
                errorBookWrites,
                trajectoryIds,
                elapsedMicros,
                System.currentTimeMillis(),
                hmacSecretKey
        );
        return new SearchOutcome(
                receipt,
                winningNode,
                winningOutput,
                List.copyOf(nodeMap.values()),
                Collections.unmodifiableList(patches)
        );
    }

    private SearchOutcome executeLinearAblatedFallback(
            String execId,
            String rootBranchId,
            int rootSuperstep,
            String question,
            String safeZone0,
            String zone0Sha,
            byte[] zone0DigestBytes,
            float[] queryVec,
            BudgetVector6D budget,
            HnscdsAblationConfig cfg,
            BranchStepExpander expander,
            long startNs
    ) {
        Map<String, Object> reconstructed = stepJournal.reconstructStateAt(execId, rootBranchId, rootSuperstep);
        BranchExpansionContext ctx = new BranchExpansionContext(
                execId, rootBranchId, null, 0, question, safeZone0, List.of(), reconstructed, null, 1
        );
        BranchExpansionResult result = expander.expand(ctx);
        String outText = (result != null && result.candidateOutput() != null) ? result.candidateOutput() : "";
        boolean preserved = result == null || result.returnedZone0Prefix() == null
                || MessageDigest.isEqual(zone0DigestBytes, sha256Bytes(result.returnedZone0Prefix()));
        float[] branchVec = embeddingResolver.resolveUnit1024(outText.isBlank() ? question : outText);
        NeuroSymbolicBranchEvaluator.BranchEvaluationOutcome eval = evaluator.evaluate(
                new NeuroSymbolicBranchEvaluator.BranchEvaluationInput(
                        execId + ":linear",
                        question,
                        result != null ? result.reasoningContent() : "",
                        outText,
                        result != null ? result.evidenceSummary() : "",
                        result != null ? result.codeActReceipt() : null,
                        result != null ? result.witnesses() : List.of(),
                        queryVec,
                        branchVec,
                        null,
                        result != null ? result.tokensUsed() : 64L,
                        budget.maxTotalTokens(),
                        result != null && result.temporalContradictionDetected(),
                        cfg
                )
        );
        String receiptId = (result != null && result.codeActReceipt() != null) ? result.codeActReceipt().receiptId() : null;
        CausalSearchBranchNode node = new CausalSearchBranchNode(
                "bcbt_linear_1",
                null,
                rootBranchId,
                0,
                result != null ? result.proposedActionSummary() : "Linear fallback execution",
                branchVec,
                1,
                eval.compositeValue(),
                eval.compositeValue(),
                receiptId,
                eval.consistencyScore(),
                false,
                false
        );
        totalExpandedBranches.incrementAndGet();
        long elapsedMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
        CausalTreeSearchReceipt receipt = CausalTreeSearchReceipt.createSigned(
                "bcbt_r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                execId,
                zone0Sha,
                preserved,
                1,
                0,
                0,
                false,
                rootBranchId,
                node.meanValue(),
                0,
                List.of(node.nodeId()),
                elapsedMicros,
                System.currentTimeMillis(),
                hmacSecretKey
        );
        return new SearchOutcome(receipt, node, outText, List.of(node), List.of());
    }

    private CausalSearchBranchNode selectBestExpandableParent(Map<String, CausalSearchBranchNode> nodeMap) {
        CausalSearchBranchNode best = null;
        for (CausalSearchBranchNode node : nodeMap.values()) {
            if (!node.pruned() && node.depth() < maxDepth) {
                if (best == null || node.puctScore() > best.puctScore()) {
                    best = node;
                }
            }
        }
        return best;
    }

    private int countActiveNonPruned(Map<String, CausalSearchBranchNode> nodeMap) {
        int count = 0;
        for (CausalSearchBranchNode node : nodeMap.values()) {
            if (!node.pruned() && node.depth() > 0) {
                count++;
            }
        }
        return count;
    }

    private void backpropagateValue(
            Map<String, CausalSearchBranchNode> nodeMap,
            String startParentId,
            double childValue
    ) {
        String currId = startParentId;
        while (currId != null) {
            CausalSearchBranchNode node = nodeMap.get(currId);
            if (node == null) {
                break;
            }
            int newVisits = node.visitCount() + 1;
            double newMean = ((node.meanValue() * node.visitCount()) + childValue) / newVisits;
            CausalSearchBranchNode gp = node.parentNodeId() != null ? nodeMap.get(node.parentNodeId()) : null;
            int gpVisits = gp != null ? gp.visitCount() : newVisits;
            double newPuct = evaluator.computePuctScore(
                    newMean, 1.0, gpVisits, newVisits, NeuroSymbolicBranchEvaluator.DEFAULT_C_PUCT
            );
            nodeMap.put(currId, node.withBackpropagatedStats(newVisits, newMean, newPuct));
            currId = node.parentNodeId();
        }
    }

    private static CausalSearchBranchNode selectWinningNode(Map<String, CausalSearchBranchNode> nodeMap) {
        CausalSearchBranchNode bestActive = null;
        CausalSearchBranchNode bestAny = null;
        for (CausalSearchBranchNode node : nodeMap.values()) {
            if (node.depth() > 0) {
                if (bestAny == null || node.meanValue() > bestAny.meanValue()) {
                    bestAny = node;
                }
                if (!node.pruned() && (bestActive == null || node.meanValue() > bestActive.meanValue())) {
                    bestActive = node;
                }
            }
        }
        if (bestActive != null) {
            return bestActive;
        }
        if (bestAny != null) {
            return bestAny;
        }
        return nodeMap.values().iterator().next();
    }

    private static List<String> buildTrajectoryNodeIds(
            Map<String, CausalSearchBranchNode> nodeMap,
            String leafNodeId
    ) {
        List<String> reversed = new ArrayList<>();
        String curr = leafNodeId;
        int guard = 0;
        while (curr != null && guard++ < 64) {
            reversed.add(curr);
            CausalSearchBranchNode node = nodeMap.get(curr);
            curr = node != null ? node.parentNodeId() : null;
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    /**
     * 输出供博士工作台监控消费的实时遥测指标。
     */
    public Map<String, Object> getTelemetryStats() {
        return Map.of(
                "totalSearches", totalSearches.get(),
                "totalExpandedBranches", totalExpandedBranches.get(),
                "totalPrunedQuarantinedBranches", totalPrunedQuarantinedBranches.get(),
                "totalEpistemicEarlyStops", totalEpistemicEarlyStops.get(),
                "totalErrorBookWrites", totalErrorBookWrites.get(),
                "maxDepthLimit", maxDepth,
                "maxActiveBranchesLimit", maxActiveBranches,
                "maxTotalNodesLimit", maxTotalNodes
        );
    }

    private static String sha256Hex(String text) {
        return HexFormat.of().formatHex(sha256Bytes(text));
    }

    private static byte[] sha256Bytes(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest((text != null ? text : "").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }
}
