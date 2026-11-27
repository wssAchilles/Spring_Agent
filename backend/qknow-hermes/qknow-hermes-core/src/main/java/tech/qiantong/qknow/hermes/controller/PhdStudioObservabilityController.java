package tech.qiantong.qknow.hermes.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.common.core.domain.CommonResult;
import tech.qiantong.qknow.hermes.cognitive.context.IncrementalDeltaObservationDeduplicator;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.evolution.ProgrammaticSkillSynthesizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.SleepTimeEvolutionCoordinator;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.tool.BatchParallelReadCoordinator;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalReflexiveTreeSearchGovernor;
import tech.qiantong.qknow.hermes.cognitive.treesearch.MonotonicBranchAcceptanceArbiter;
import tech.qiantong.qknow.hermes.cognitive.verification.PremiseGroundingAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.DurableStateGraphEngine;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.WriteAheadStepJournalEntry;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.NativeHippocampalSimdBridge;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ActiveSlotLedgerIndex;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicViewCompletenessAuditor;
import tech.qiantong.qknow.hermes.memory.hybrid.Bm25HypersphericalHybridIndex;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityAuditor;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanSandboxExecutor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 博士论文代际升级工作台控制面 REST 控制器（H-NSCDS Telemetry & Time-Travel Studio）。
 * <p>
 * 对接 {@link PgStepJournalStateCheckpointer}、{@link FrontierGatedToolTransactionManager}、
 * {@link BiTemporalHippocampalGraphEngine}、{@link HoareToolContractGateway}、
 * {@link HypersphericalSupernetRouter}、{@link PrefixCacheStabilityAuditor} 与 {@link CodeModePlanSandboxExecutor}。
 * 支持跨 JVM（qknow-server 8099 → hermes 8081）联邦遥测聚合与审批透传，并通过 {@code X-Phd-Studio-Proxy: 1} 严防环路代理。
 * </p>
 */
@Tag(name = "H-NSCDS 博士工作台观测与时间旅行控制面")
@RestController
@RequestMapping("/hermes/phd-studio")
public class PhdStudioObservabilityController {

    private static final Logger log = LoggerFactory.getLogger(PhdStudioObservabilityController.class);
    private static final String PROXY_HEADER = "X-Phd-Studio-Proxy";

    public record TimeTravelForkRequest(
            String executionId,
            String parentBranchId,
            int fromSuperstep,
            Map<String, Object> stateOverrides
    ) {}

    public record FrontierDecisionRequest(
            String approvalKey,
            boolean approved,
            String reason
    ) {}

    private final PgStepJournalStateCheckpointer stepJournal;
    private final FrontierGatedToolTransactionManager frontierTxManager;
    private final BiTemporalHippocampalGraphEngine hippoEngine;
    private final HoareToolContractGateway hoareGateway;
    private final HypersphericalSupernetRouter supernetRouter;
    private final PrefixCacheStabilityAuditor prefixAuditor;
    private final CodeModePlanSandboxExecutor codeModeExecutor;
    private BiTemporalCausalEvolutionRetriever temporalCausalRetriever;
    private VerifiedCodeActSandboxRuntime verifiedCodeActSandboxRuntime;
    private CausalReflexiveTreeSearchGovernor causalTreeSearchGovernor;
    private ReasoningChainConsistencyAuditor consistencyAuditor;
    private StepComputePolicy stepComputePolicy;
    private DurableStateGraphEngine durableStateGraphEngine;
    private ActiveSlotLedgerIndex activeSlotLedgerIndex;
    private SleepTimeEvolutionCoordinator sleepTimeEvolutionCoordinator;
    private MonotonicBranchAcceptanceArbiter monotonicBranchAcceptanceArbiter;
    private EpisodicViewCompletenessAuditor episodicViewCompletenessAuditor;
    private PremiseGroundingAuditor premiseGroundingAuditor;
    private ProgrammaticSkillSynthesizer programmaticSkillSynthesizer;
    private IncrementalDeltaObservationDeduplicator incrementalDeltaObservationDeduplicator;
    private BatchParallelReadCoordinator batchParallelReadCoordinator;
    private Bm25HypersphericalHybridIndex bm25HypersphericalHybridIndex;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Value("${hermes.http.base-url:}")
    private String hermesHttpBaseUrl = "";

    @Autowired
    public PhdStudioObservabilityController(
            ObjectProvider<PgStepJournalStateCheckpointer> stepJournalProvider,
            ObjectProvider<FrontierGatedToolTransactionManager> txManagerProvider,
            ObjectProvider<BiTemporalHippocampalGraphEngine> hippoEngineProvider,
            ObjectProvider<HoareToolContractGateway> hoareGatewayProvider,
            ObjectProvider<HypersphericalSupernetRouter> supernetRouterProvider,
            ObjectProvider<PrefixCacheStabilityAuditor> prefixAuditorProvider,
            ObjectProvider<CodeModePlanSandboxExecutor> codeModeExecutorProvider
    ) {
        this(
                stepJournalProvider != null ? stepJournalProvider.getIfAvailable() : null,
                txManagerProvider != null ? txManagerProvider.getIfAvailable() : null,
                hippoEngineProvider != null ? hippoEngineProvider.getIfAvailable() : null,
                hoareGatewayProvider != null ? hoareGatewayProvider.getIfAvailable() : null,
                supernetRouterProvider != null ? supernetRouterProvider.getIfAvailable() : null,
                prefixAuditorProvider != null ? prefixAuditorProvider.getIfAvailable() : null,
                codeModeExecutorProvider != null ? codeModeExecutorProvider.getIfAvailable() : null
        );
    }

    public PhdStudioObservabilityController(
            PgStepJournalStateCheckpointer stepJournal,
            FrontierGatedToolTransactionManager frontierTxManager,
            BiTemporalHippocampalGraphEngine hippoEngine,
            HoareToolContractGateway hoareGateway,
            HypersphericalSupernetRouter supernetRouter
    ) {
        this(stepJournal, frontierTxManager, hippoEngine, hoareGateway, supernetRouter, null, null);
    }

    public PhdStudioObservabilityController(
            PgStepJournalStateCheckpointer stepJournal,
            FrontierGatedToolTransactionManager frontierTxManager,
            BiTemporalHippocampalGraphEngine hippoEngine,
            HoareToolContractGateway hoareGateway,
            HypersphericalSupernetRouter supernetRouter,
            PrefixCacheStabilityAuditor prefixAuditor,
            CodeModePlanSandboxExecutor codeModeExecutor
    ) {
        this.stepJournal = stepJournal != null ? stepJournal : new PgStepJournalStateCheckpointer();
        this.frontierTxManager = frontierTxManager != null ? frontierTxManager : new FrontierGatedToolTransactionManager(null);
        this.hippoEngine = hippoEngine != null ? hippoEngine : new BiTemporalHippocampalGraphEngine();
        this.hoareGateway = hoareGateway != null ? hoareGateway : new HoareToolContractGateway();
        this.supernetRouter = supernetRouter != null ? supernetRouter : new HypersphericalSupernetRouter();
        this.prefixAuditor = prefixAuditor != null ? prefixAuditor : new PrefixCacheStabilityAuditor(null);
        this.codeModeExecutor = codeModeExecutor;
    }

    public void setHermesHttpBaseUrl(String hermesHttpBaseUrl) {
        this.hermesHttpBaseUrl = hermesHttpBaseUrl != null ? hermesHttpBaseUrl : "";
    }

    @Autowired(required = false)
    public void setBiTemporalCausalEvolutionRetriever(BiTemporalCausalEvolutionRetriever temporalCausalRetriever) {
        this.temporalCausalRetriever = temporalCausalRetriever;
    }

    @Autowired(required = false)
    public void setVerifiedCodeActSandboxRuntime(VerifiedCodeActSandboxRuntime verifiedCodeActSandboxRuntime) {
        this.verifiedCodeActSandboxRuntime = verifiedCodeActSandboxRuntime;
    }

    @Autowired(required = false)
    public void setCausalReflexiveTreeSearchGovernor(CausalReflexiveTreeSearchGovernor causalTreeSearchGovernor) {
        this.causalTreeSearchGovernor = causalTreeSearchGovernor;
    }

    @Autowired(required = false)
    public void setReasoningChainConsistencyAuditor(ReasoningChainConsistencyAuditor consistencyAuditor) {
        this.consistencyAuditor = consistencyAuditor;
    }

    @Autowired(required = false)
    public void setStepComputePolicy(StepComputePolicy stepComputePolicy) {
        this.stepComputePolicy = stepComputePolicy;
    }

    @Autowired(required = false)
    public void setDurableStateGraphEngine(DurableStateGraphEngine durableStateGraphEngine) {
        this.durableStateGraphEngine = durableStateGraphEngine;
    }

    @Autowired(required = false)
    public void setActiveSlotLedgerIndex(ActiveSlotLedgerIndex activeSlotLedgerIndex) {
        this.activeSlotLedgerIndex = activeSlotLedgerIndex;
    }

    @Autowired(required = false)
    public void setSleepTimeEvolutionCoordinator(SleepTimeEvolutionCoordinator sleepTimeEvolutionCoordinator) {
        this.sleepTimeEvolutionCoordinator = sleepTimeEvolutionCoordinator;
    }

    @Autowired(required = false)
    public void setMonotonicBranchAcceptanceArbiter(MonotonicBranchAcceptanceArbiter monotonicBranchAcceptanceArbiter) {
        this.monotonicBranchAcceptanceArbiter = monotonicBranchAcceptanceArbiter;
    }

    @Autowired(required = false)
    public void setEpisodicViewCompletenessAuditor(EpisodicViewCompletenessAuditor episodicViewCompletenessAuditor) {
        this.episodicViewCompletenessAuditor = episodicViewCompletenessAuditor;
    }

    @Autowired(required = false)
    public void setPremiseGroundingAuditor(PremiseGroundingAuditor premiseGroundingAuditor) {
        this.premiseGroundingAuditor = premiseGroundingAuditor;
    }

    @Autowired(required = false)
    public void setProgrammaticSkillSynthesizer(ProgrammaticSkillSynthesizer programmaticSkillSynthesizer) {
        this.programmaticSkillSynthesizer = programmaticSkillSynthesizer;
    }

    @Autowired(required = false)
    public void setIncrementalDeltaObservationDeduplicator(IncrementalDeltaObservationDeduplicator incrementalDeltaObservationDeduplicator) {
        this.incrementalDeltaObservationDeduplicator = incrementalDeltaObservationDeduplicator;
    }

    @Autowired(required = false)
    public void setBatchParallelReadCoordinator(BatchParallelReadCoordinator batchParallelReadCoordinator) {
        this.batchParallelReadCoordinator = batchParallelReadCoordinator;
    }

    @Autowired(required = false)
    public void setBm25HypersphericalHybridIndex(Bm25HypersphericalHybridIndex bm25HypersphericalHybridIndex) {
        this.bm25HypersphericalHybridIndex = bm25HypersphericalHybridIndex;
    }

    /**
     * 兼容无参直调重载，供同进程单元测试使用。
     */
    public CommonResult<Map<String, Object>> getTelemetry() {
        return getTelemetry(null);
    }

    @Operation(summary = "读取 H-NSCDS 全系统真实引擎遥测指标")
    @GetMapping("/telemetry")
    public CommonResult<Map<String, Object>> getTelemetry(
            @RequestHeader(value = PROXY_HEADER, required = false) String proxyHeader
    ) {
        Map<String, Object> localPayload = buildLocalTelemetryPayload();
        String baseUrl = normalizeBaseUrl(hermesHttpBaseUrl);
        if (!baseUrl.isEmpty() && !"1".equals(proxyHeader)) {
            JSONObject remoteData = fetchRemoteTelemetry(baseUrl);
            if (remoteData != null) {
                mergeFederatedTelemetry(localPayload, remoteData);
                localPayload.put("hermesRemoteReachable", true);
                return CommonResult.success(localPayload);
            }
            localPayload.put("hermesRemoteReachable", false);
        } else {
            localPayload.put("hermesRemoteReachable", baseUrl.isEmpty());
        }
        return CommonResult.success(localPayload);
    }

    @Operation(summary = "获取指定执行轨迹的预写步骤日志与 DeltaChannel 时间线")
    @GetMapping("/journal/{executionId}")
    public CommonResult<List<Map<String, Object>>> getJournalTimeline(
            @PathVariable("executionId") String executionId,
            @RequestParam(value = "branchId", required = false, defaultValue = "main") String branchId
    ) {
        List<WriteAheadStepJournalEntry> entries = stepJournal.listJournalEntries(executionId, branchId);
        List<Map<String, Object>> viewList = new ArrayList<>(entries.size());
        for (WriteAheadStepJournalEntry e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("executionId", e.executionId());
            row.put("branchId", e.branchId());
            row.put("superstep", e.superstep());
            row.put("nodeId", e.nodeId());
            row.put("idempotencyKey", e.idempotencyKey());
            row.put("fencingToken", e.fencingToken());
            row.put("status", e.status().name());
            row.put("channelDeltas", e.channelDeltas());
            row.put("versionsSeen", e.versionsSeen());
            row.put("createdAtEpochMs", e.createdAtEpochMs());
            viewList.add(row);
        }
        return CommonResult.success(viewList);
    }

    @Operation(summary = "从历史超步发起零幽灵变量时间旅行因果分叉")
    @PostMapping("/journal/fork")
    public CommonResult<Map<String, Object>> forkBranch(@RequestBody TimeTravelForkRequest request) {
        if (request == null || request.executionId() == null || request.executionId().isBlank()) {
            return CommonResult.error(400, "executionId 不能为空");
        }
        String parentBranch = (request.parentBranchId() == null || request.parentBranchId().isBlank())
                ? "main" : request.parentBranchId();
        String forkBranchId = stepJournal.forkBranchFromJournal(
                request.executionId(),
                parentBranch,
                request.fromSuperstep(),
                request.stateOverrides()
        );
        Map<String, Object> reconstructed = stepJournal.reconstructStateAt(
                request.executionId(), forkBranchId, request.fromSuperstep()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", request.executionId());
        result.put("parentBranchId", parentBranch);
        result.put("fromSuperstep", request.fromSuperstep());
        result.put("forkBranchId", forkBranchId);
        result.put("reconstructedState", reconstructed);
        return CommonResult.success(result);
    }

    /**
     * 兼容两参直调重载，供同进程单元测试使用。
     */
    public CommonResult<Boolean> decideFrontierApproval(FrontierDecisionRequest request) {
        return decideFrontierApproval(request, null);
    }

    @Operation(summary = "批准或驳回 Frontier 事务挂起的高危不可逆动作")
    @PostMapping("/frontier/decide")
    public CommonResult<Boolean> decideFrontierApproval(
            @RequestBody FrontierDecisionRequest request,
            @RequestHeader(value = PROXY_HEADER, required = false) String proxyHeader
    ) {
        if (request == null || request.approvalKey() == null || request.approvalKey().isBlank()) {
            return CommonResult.error(400, "approvalKey 不能为空");
        }
        boolean resolved = frontierTxManager.resolvePendingHitlApproval(
                request.approvalKey(), request.approved(), request.reason()
        );
        if (resolved) {
            return CommonResult.success(true);
        }
        String baseUrl = normalizeBaseUrl(hermesHttpBaseUrl);
        if (!baseUrl.isEmpty() && !"1".equals(proxyHeader)) {
            Boolean remoteResolved = forwardFrontierDecision(baseUrl, request);
            if (Boolean.TRUE.equals(remoteResolved)) {
                return CommonResult.success(true);
            }
        }
        return CommonResult.error(404, "未找到对应的挂起事务分支");
    }

    private Map<String, Object> buildLocalTelemetryPayload() {
        HypersphericalSupernetRouter.TelemetrySnapshot supernetSnap = supernetRouter.getTelemetrySnapshot();
        Map<String, Object> supernetMap = new LinkedHashMap<>();
        supernetMap.put("totalRoutingDecisions", supernetSnap.totalRoutingDecisions());
        supernetMap.put("totalReplansTriggered", supernetSnap.totalReplansTriggered());
        supernetMap.put("totalEarlyExits", supernetSnap.totalEarlyExits());
        supernetMap.put("lastChordDriftSquared", supernetSnap.lastChordDriftSquared());
        supernetMap.put("activeTopologyOperator", supernetSnap.activeTopologyOperator());
        supernetMap.put("activeReasoningEffort", supernetSnap.activeReasoningEffort());
        supernetMap.put("averageExpectedUtility", supernetSnap.averageExpectedUtility());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supernet", supernetMap);
        payload.put("stepJournal", stepJournal.getTelemetryStats());
        payload.put("frontierTx", frontierTxManager.getTelemetryStats());
        payload.put("hippocampal", hippoEngine.getTelemetryStats());
        payload.put("formalGuard", hoareGateway.getFormalTelemetryStats());
        payload.put("prefixCache", prefixAuditor.getTelemetryStats());
        payload.put("codeModeSandbox", codeModeExecutor != null ? codeModeExecutor.getTelemetryStats() : Map.of(
                "staticMetaToolCount", CodeModePlanSandboxExecutor.STATIC_META_TOOL_IDS.size(),
                "totalDiscoveries", 0L,
                "totalPlanExecutions", 0L,
                "totalDeferredToolCalls", 0L,
                "blockedScriptCount", 0L
        ));
        payload.put("temporalCausal", temporalCausalRetriever != null
                ? temporalCausalRetriever.getTelemetryStats()
                : Map.of(
                        "totalRetrievals", 0L,
                        "temporalAnchorBoundedRetrievals", 0L,
                        "totalSupersededFiltered", 0L,
                        "totalCausalStepsReturned", 0L
                ));
        payload.put("verifiedCodeAct", verifiedCodeActSandboxRuntime != null
                ? verifiedCodeActSandboxRuntime.getTelemetryStats()
                : Map.of(
                        "totalExecutions", 0L,
                        "successfulExecutions", 0L,
                        "astBlockedExecutions", 0L,
                        "timedOutExecutions", 0L
                ));
        payload.put("causalTreeSearch", causalTreeSearchGovernor != null
                ? causalTreeSearchGovernor.getTelemetryStats()
                : Map.of(
                        "totalSearches", 0L,
                        "totalExpandedBranches", 0L,
                        "totalPrunedQuarantinedBranches", 0L,
                        "totalEpistemicEarlyStops", 0L,
                        "totalErrorBookWrites", 0L
                ));
        payload.put("frsvSpecAudit", consistencyAuditor != null
                ? consistencyAuditor.getSpecTelemetryStats()
                : Map.of(
                        "totalSpecAudits", 0L,
                        "totalHardSpecBlocks", 0L,
                        "totalCodeActBindingsChecked", 0L,
                        "totalCodeActMismatchIntercepted", 0L
                ));
        payload.put("stepHeterogeneousEffort", stepComputePolicy != null
                ? stepComputePolicy.getTelemetryStats()
                : Map.of(
                        "totalStepDecisions", 0L,
                        "conformalCalibrated", false,
                        "noneSteps", 0L,
                        "lowSteps", 0L,
                        "mediumSteps", 0L,
                        "highSteps", 0L
                ));
        payload.put("typedSubgraphEngine", durableStateGraphEngine != null
                ? durableStateGraphEngine.getTelemetryStats()
                : Map.of(
                        "totalSuperstepsExecuted", 0L,
                        "totalNodeInvocations", 0L,
                        "totalFanoutInstances", 0L,
                        "totalTimeoutOrExceptions", 0L
                ));
        payload.put("episodicRelationalView", activeSlotLedgerIndex != null
                ? activeSlotLedgerIndex.getTelemetryStats()
                : Map.of(
                        "activeFunctionalSlotCount", 0,
                        "totalUpserts", 0L,
                        "totalSupersedes", 0L,
                        "totalDeduplicated", 0L
                ));
        payload.put("verifiableSkillEvolution", sleepTimeEvolutionCoordinator != null
                ? sleepTimeEvolutionCoordinator.getTelemetryStats()
                : Map.of(
                        "totalSleepCycles", 0L,
                        "totalPromotedSkills", 0L,
                        "totalQuarantinedSkills", 0L,
                        "totalRadiusMergedSkills", 0L
                ));
        Map<String, Object> msShscrTelemetry = new LinkedHashMap<>();
        if (stepComputePolicy != null) {
            msShscrTelemetry.putAll(stepComputePolicy.getTelemetryStats());
            msShscrTelemetry.put("calibratedThresholds", stepComputePolicy.getCalibratedThresholds());
        } else {
            msShscrTelemetry.put("v5ManifoldDecisions", 0L);
            msShscrTelemetry.put("conformalCalibrated", false);
        }
        payload.put("msShscrManifoldControl", Collections.unmodifiableMap(msShscrTelemetry));
        payload.put("maBcbtTournament", monotonicBranchAcceptanceArbiter != null
                ? monotonicBranchAcceptanceArbiter.getTelemetryStats()
                : Map.of(
                        "totalArbitrations", 0L,
                        "acceptedRefinements", 0L,
                        "rejectedFlipProtections", 0L
                ));
        Map<String, Object> ccAndPremiseTelemetry = new LinkedHashMap<>();
        ccAndPremiseTelemetry.put("completenessAuditor", episodicViewCompletenessAuditor != null
                ? episodicViewCompletenessAuditor.getTelemetryStats()
                : Map.of(
                        "totalCertifiedViews", 0L,
                        "completeViews", 0L,
                        "partialWarningViews", 0L,
                        "suppressedUnreliableViews", 0L
                ));
        ccAndPremiseTelemetry.put("premiseGrounding", premiseGroundingAuditor != null
                ? premiseGroundingAuditor.getTelemetryStats()
                : Map.of(
                        "totalPremiseAudits", 0L,
                        "topicShiftIntercepted", 0L,
                        "justifiedAbstentionsProtected", 0L
                ));
        payload.put("ccE2rAndPremiseGate", Collections.unmodifiableMap(ccAndPremiseTelemetry));
        Map<String, Object> pVpsTelemetry = new LinkedHashMap<>();
        pVpsTelemetry.put("synthesizer", programmaticSkillSynthesizer != null
                ? programmaticSkillSynthesizer.getTelemetryStats()
                : Map.of(
                        "totalSynthesisAttempts", 0L,
                        "totalSynthesizedCandidates", 0L,
                        "totalSynthesisRejected", 0L
                ));
        if (sleepTimeEvolutionCoordinator != null) {
            pVpsTelemetry.putAll(sleepTimeEvolutionCoordinator.getTelemetryStats());
        }
        payload.put("pVpsSkillEvolution", Collections.unmodifiableMap(pVpsTelemetry));

        // Phase 11 (H-NSCDS v6) 四大专项遥测节点
        payload.put("edScrDampingControl", stepComputePolicy != null
                ? stepComputePolicy.getDampingTelemetryStats()
                : Map.of(
                        "v6DampedDecisions", 0L,
                        "dampingDownshifts", 0L,
                        "totalBudgetCapTokensSaved", 0L,
                        "innovationThreshold", StepComputePolicy.DEFAULT_INNOVATION_THRESHOLD,
                        "gammaMin", StepComputePolicy.DEFAULT_GAMMA_MIN
                ));
        payload.put("idgCoObservationDedup", incrementalDeltaObservationDeduplicator != null
                ? incrementalDeltaObservationDeduplicator.getTelemetryStats()
                : Map.of(
                        "totalObservationsFiltered", 0L,
                        "totalDedupBlocksSuppressed", 0L,
                        "totalMetaSectionsSuppressed", 0L,
                        "totalCharsSaved", 0L,
                        "estimatedTokensSaved", 0L,
                        "prefixMutations", 0L
                ));
        Map<String, Object> bprLmTelemetry = new LinkedHashMap<>();
        if (bm25HypersphericalHybridIndex != null) {
            bprLmTelemetry.putAll(bm25HypersphericalHybridIndex.getTelemetryStats());
        } else {
            bprLmTelemetry.put("totalHybridSearches", 0L);
            bprLmTelemetry.put("totalIndexedSlices", 0);
        }
        if (batchParallelReadCoordinator != null) {
            bprLmTelemetry.putAll(batchParallelReadCoordinator.getTelemetryStats());
        } else {
            bprLmTelemetry.put("totalBatches", 0L);
            bprLmTelemetry.put("totalParallelReadCalls", 0L);
            bprLmTelemetry.put("totalSerialGuardedCalls", 0L);
            bprLmTelemetry.put("cumulativeWallTimeSavedMs", 0L);
        }
        payload.put("bprLmHybridRetrieval", Collections.unmodifiableMap(bprLmTelemetry));
        payload.put("v6SynergyGovernance", Map.of(
                "architectureVersion", "H-NSCDS v6 (Phase 11)",
                "ablationDimensions", 27,
                "profilesSupported", "G0..G22"
        ));

        payload.put("nativeFfmLoaded", NativeHippocampalSimdBridge.isNativeFfmLoaded());
        payload.put("embeddingManifold", "S^1023 (Qwen text-embedding-v4, L2=1.0)");
        payload.put("llmBaseline", "DeepSeek API (deepseek-flash, 64-token prefix invariant)");
        payload.put("timestampMs", System.currentTimeMillis());
        return payload;
    }

    private JSONObject fetchRemoteTelemetry(String baseUrl) {
        try {
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/hermes/phd-studio/telemetry"))
                    .timeout(Duration.ofSeconds(2))
                    .header(PROXY_HEADER, "1")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JSONObject root = JSON.parseObject(resp.body());
                if (root != null && root.getIntValue("code") == 200) {
                    return root.getJSONObject("data");
                }
            }
        } catch (Exception e) {
            log.debug("[PhdStudio] 联邦遥测拉取 hermes 远端跳过 ({}): {}", baseUrl, e.getMessage());
        }
        return null;
    }

    private Boolean forwardFrontierDecision(String baseUrl, FrontierDecisionRequest request) {
        try {
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/hermes/phd-studio/frontier/decide"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header(PROXY_HEADER, "1")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(request)))
                    .build();
            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JSONObject root = JSON.parseObject(resp.body());
                return root != null && root.getIntValue("code") == 200 && Boolean.TRUE.equals(root.getBoolean("data"));
            }
        } catch (Exception e) {
            log.debug("[PhdStudio] 远端审批决策转发失败: {}", e.getMessage());
        }
        return false;
    }

    private void mergeFederatedTelemetry(Map<String, Object> local, JSONObject remote) {
        String[] hermesPrimaryKeys = {
                "supernet", "frontierTx", "hippocampal", "formalGuard", "prefixCache", "codeModeSandbox",
                "temporalCausal", "verifiedCodeAct", "causalTreeSearch",
                "frsvSpecAudit", "stepHeterogeneousEffort", "typedSubgraphEngine",
                "episodicRelationalView", "verifiableSkillEvolution",
                "msShscrManifoldControl", "maBcbtTournament", "ccE2rAndPremiseGate", "pVpsSkillEvolution",
                "edScrDampingControl", "idgCoObservationDedup", "bprLmHybridRetrieval", "v6SynergyGovernance"
        };
        for (String key : hermesPrimaryKeys) {
            JSONObject rSection = remote.getJSONObject(key);
            if (rSection != null && !rSection.isEmpty()) {
                local.put(key, rSection);
            }
        }
        if (remote.containsKey("nativeFfmLoaded")) {
            local.put("nativeFfmLoaded", remote.getBooleanValue("nativeFfmLoaded"));
        }
        JSONObject rJournal = remote.getJSONObject("stepJournal");
        Object lJournalObj = local.get("stepJournal");
        if (rJournal != null && lJournalObj instanceof Map<?, ?> lJournal) {
            Map<String, Object> mergedJournal = new LinkedHashMap<>();
            mergedJournal.put("activeBranchScopeCount", Math.max(asInt(lJournal.get("activeBranchScopeCount")), rJournal.getIntValue("activeBranchScopeCount")));
            mergedJournal.put("committedEntryCount", Math.max(asInt(lJournal.get("committedEntryCount")), rJournal.getIntValue("committedEntryCount")));
            mergedJournal.put("totalJournalEntries", Math.max(asInt(lJournal.get("totalJournalEntries")), rJournal.getIntValue("totalJournalEntries")));
            mergedJournal.put("baseSnapshotBranchCount", Math.max(asInt(lJournal.get("baseSnapshotBranchCount")), rJournal.getIntValue("baseSnapshotBranchCount")));
            mergedJournal.put("coldRingCount", Math.max(asInt(lJournal.get("coldRingCount")), rJournal.getIntValue("coldRingCount")));
            local.put("stepJournal", Collections.unmodifiableMap(mergedJournal));
        }
    }

    private static int asInt(Object val) {
        return val instanceof Number n ? n.intValue() : 0;
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
