package tech.qiantong.qknow.hermes.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.agent.skill.LifelongSkillBankEngine;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.benchmark.dataset.RealPublicBenchmarkLoader;
import tech.qiantong.qknow.hermes.benchmark.evaluation.AcademicBenchmarkEvaluationHarness;
import tech.qiantong.qknow.hermes.cognitive.context.HystereticDualZoneContextFolder;
import tech.qiantong.qknow.hermes.cognitive.context.IncrementalDeltaObservationDeduplicator;
import tech.qiantong.qknow.hermes.cognitive.control.HypersphericalEpistemicStoppingGovernor;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.control.StepEvidenceDriftTracker;
import tech.qiantong.qknow.hermes.cognitive.control.SupernetControlPlaneDispatcher;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.control.VariationalUtilityEvaluator;
import tech.qiantong.qknow.hermes.cognitive.tool.BatchParallelReadCoordinator;
import tech.qiantong.qknow.hermes.cognitive.evolution.CounterfactualSkillRegressionGate;
import tech.qiantong.qknow.hermes.cognitive.evolution.ProgrammaticSkillSynthesizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.RhoSelfPreferenceOptimizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.SleepTimeEvolutionCoordinator;
import tech.qiantong.qknow.hermes.cognitive.evolution.VerifiedSkillHarvester;
import tech.qiantong.qknow.hermes.cognitive.speculative.LatticeGatedSpeculativePrefetcher;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalFailureReflector;
import tech.qiantong.qknow.hermes.cognitive.treesearch.CausalReflexiveTreeSearchGovernor;
import tech.qiantong.qknow.hermes.cognitive.treesearch.MonotonicBranchAcceptanceArbiter;
import tech.qiantong.qknow.hermes.cognitive.treesearch.NeuroSymbolicBranchEvaluator;
import tech.qiantong.qknow.hermes.cognitive.verification.PremiseGroundingAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpecCompiler;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.BridgeContractGate;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.NativeHippocampalSimdBridge;
import tech.qiantong.qknow.hermes.memory.bitemporal.SleepTimeSpectralDistiller;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ActiveSlotLedgerIndex;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicDatasetViewMaterializer;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicRelationalStateExtractor;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicViewCompletenessAuditor;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.memory.hybrid.Bm25HypersphericalHybridIndex;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityAuditor;
import tech.qiantong.qknow.hermes.tool.formal.DbmLtlFormalMicroSolver;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActProgrammaticBridge;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.math.MathematicalCodeSandboxTool;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanSandboxExecutor;
import tech.qiantong.qknow.mcp.router.McpHierarchicalVoronoiRouter;

import javax.sql.DataSource;

/**
 * Hermes 独立微服务进程（qknow-hermes-starter）的 H-NSCDS 博士论文核心引擎自动装配类。
 * <p>
 * 确保独立运行的 Hermes 认知内核与控制面 qknow-server 对等实例化并共享 PostgreSQL 16 Step Journal 与全部算法引擎。
 * 所有 Bean 均使用 {@link ConditionalOnMissingBean} 保护，避免与主控制面配置冲突。
 * </p>
 */
@Configuration
public class HermesPhdEnginesAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public PgStepJournalStateCheckpointer pgStepJournalStateCheckpointer(ObjectProvider<DataSource> dataSourceProvider) {
        return new PgStepJournalStateCheckpointer(dataSourceProvider.getIfAvailable(), null, 8);
    }

    @Bean
    @ConditionalOnMissingBean
    public FrontierGatedToolTransactionManager frontierGatedToolTransactionManager() {
        return new FrontierGatedToolTransactionManager(null);
    }

    @Bean
    @ConditionalOnMissingBean
    public BiTemporalHippocampalGraphEngine biTemporalHippocampalGraphEngine(
            ObjectProvider<DataSource> dataSourceProvider
    ) {
        // 启动期预热探测 Rust FFM SIMD 句柄，确保加载状态即刻进入启动日志，并激活 PG 16 双写与热恢复
        NativeHippocampalSimdBridge.isNativeFfmLoaded();
        return new BiTemporalHippocampalGraphEngine(
                dataSourceProvider.getIfAvailable(),
                "hermes-hippocampal-bitemporal-secret"
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public DbmLtlFormalMicroSolver dbmLtlFormalMicroSolver() {
        return new DbmLtlFormalMicroSolver(null);
    }

    @Bean
    @ConditionalOnMissingBean
    public HoareToolContractGateway hoareToolContractGateway() {
        return new HoareToolContractGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public HypersphericalSupernetRouter hypersphericalSupernetRouter() {
        return new HypersphericalSupernetRouter();
    }

    @Bean
    @ConditionalOnMissingBean
    public PrefixCacheStabilityAuditor prefixCacheStabilityAuditor() {
        return new PrefixCacheStabilityAuditor(null);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpHierarchicalVoronoiRouter mcpHierarchicalVoronoiRouter() {
        return new McpHierarchicalVoronoiRouter("default-tenant");
    }

    @Bean
    @ConditionalOnMissingBean
    public VerifiedCodeActSandboxRuntime verifiedCodeActSandboxRuntime(
            ObjectProvider<AstSecurityInspector> inspectorProvider,
            ObjectProvider<MathematicalCodeSandboxTool> mathToolProvider
    ) {
        AstSecurityInspector inspector = inspectorProvider.getIfAvailable(AstSecurityInspector::new);
        MathematicalCodeSandboxTool mathTool = mathToolProvider.getIfAvailable(
                MathematicalCodeSandboxTool.DefaultMathematicalCodeSandboxTool::new);
        return new VerifiedCodeActSandboxRuntime(inspector, mathTool, "hermes-codeact-sandbox-secret");
    }

    @Bean
    @ConditionalOnMissingBean
    public CodeActProgrammaticBridge codeActProgrammaticBridge(
            VerifiedCodeActSandboxRuntime sandboxRuntime,
            BiTemporalCausalEvolutionRetriever causalRetriever,
            HoareToolContractGateway hoareGateway
    ) {
        return new CodeActProgrammaticBridge(sandboxRuntime, causalRetriever, hoareGateway);
    }

    @Bean
    @ConditionalOnMissingBean
    public CodeModePlanSandboxExecutor codeModePlanSandboxExecutor(
            McpHierarchicalVoronoiRouter voronoiRouter,
            ObjectProvider<AstSecurityInspector> inspectorProvider,
            ObjectProvider<VerifiedCodeActSandboxRuntime> codeActRuntimeProvider
    ) {
        AstSecurityInspector inspector = inspectorProvider.getIfAvailable(AstSecurityInspector::new);
        VerifiedCodeActSandboxRuntime runtime = codeActRuntimeProvider.getIfAvailable();
        return new CodeModePlanSandboxExecutor(
                voronoiRouter,
                inspector::inspect,
                runtime != null ? runtime.toRawScriptStepCallback() : null
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public UnitSphereEmbeddingResolver unitSphereEmbeddingResolver(
            ObjectProvider<EmbeddingModel> embeddingModelProvider
    ) {
        return new UnitSphereEmbeddingResolver(embeddingModelProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SupernetControlPlaneDispatcher supernetControlPlaneDispatcher(
            HypersphericalSupernetRouter supernetRouter,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new SupernetControlPlaneDispatcher(supernetRouter, embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public VariationalUtilityEvaluator variationalUtilityEvaluator() {
        return new VariationalUtilityEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean
    public StepEvidenceDriftTracker stepEvidenceDriftTracker(
            HypersphericalSupernetRouter supernetRouter,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new StepEvidenceDriftTracker(supernetRouter, embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public SleepTimeSpectralDistiller sleepTimeSpectralDistiller(
            BiTemporalHippocampalGraphEngine hippocampalGraphEngine
    ) {
        return new SleepTimeSpectralDistiller(hippocampalGraphEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public RhoSelfPreferenceOptimizer rhoSelfPreferenceOptimizer(
            HypersphericalSupernetRouter supernetRouter,
            UnitSphereEmbeddingResolver embeddingResolver,
            BiTemporalHippocampalGraphEngine hippocampalGraphEngine
    ) {
        return new RhoSelfPreferenceOptimizer(supernetRouter, embeddingResolver, hippocampalGraphEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public PremiseGroundingAuditor premiseGroundingAuditor(
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new PremiseGroundingAuditor(embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReasoningChainConsistencyAuditor reasoningChainConsistencyAuditor(
            UnitSphereEmbeddingResolver embeddingResolver,
            ObjectProvider<PremiseGroundingAuditor> premiseGroundingAuditorProvider
    ) {
        ReasoningChainConsistencyAuditor auditor = new ReasoningChainConsistencyAuditor(embeddingResolver);
        if (premiseGroundingAuditorProvider != null) {
            auditor.setPremiseGroundingAuditor(premiseGroundingAuditorProvider.getIfAvailable());
        }
        return auditor;
    }

    @Bean
    @ConditionalOnMissingBean
    public HnscdsAblationConfig hnscdsAblationConfig() {
        return HnscdsAblationConfig.defaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public AcademicBenchmarkEvaluationHarness academicBenchmarkEvaluationHarness(
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new AcademicBenchmarkEvaluationHarness(embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public HystereticDualZoneContextFolder hystereticDualZoneContextFolder() {
        return new HystereticDualZoneContextFolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public HypersphericalEpistemicStoppingGovernor hypersphericalEpistemicStoppingGovernor(
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new HypersphericalEpistemicStoppingGovernor(embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public LatticeGatedSpeculativePrefetcher latticeGatedSpeculativePrefetcher() {
        return new LatticeGatedSpeculativePrefetcher();
    }

    @Bean
    @ConditionalOnMissingBean
    public RealPublicBenchmarkLoader realPublicBenchmarkLoader() {
        return new RealPublicBenchmarkLoader();
    }

    @Bean
    @ConditionalOnMissingBean
    public TemporalAnchorQueryParser temporalAnchorQueryParser() {
        return new TemporalAnchorQueryParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public BiTemporalCausalEvolutionRetriever biTemporalCausalEvolutionRetriever(
            BiTemporalHippocampalGraphEngine hippocampalGraphEngine,
            UnitSphereEmbeddingResolver embeddingResolver,
            TemporalAnchorQueryParser anchorParser
    ) {
        return new BiTemporalCausalEvolutionRetriever(hippocampalGraphEngine, embeddingResolver, anchorParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public NeuroSymbolicBranchEvaluator neuroSymbolicBranchEvaluator(
            ReasoningChainConsistencyAuditor consistencyAuditor,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new NeuroSymbolicBranchEvaluator(consistencyAuditor, embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public CausalFailureReflector causalFailureReflector(
            BiTemporalHippocampalGraphEngine hippocampalGraphEngine,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new CausalFailureReflector(hippocampalGraphEngine, embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public CausalReflexiveTreeSearchGovernor causalReflexiveTreeSearchGovernor(
            PgStepJournalStateCheckpointer stepJournal,
            NeuroSymbolicBranchEvaluator branchEvaluator,
            CausalFailureReflector failureReflector,
            HypersphericalEpistemicStoppingGovernor epistemicGovernor,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new CausalReflexiveTreeSearchGovernor(
                stepJournal, branchEvaluator, failureReflector, epistemicGovernor, embeddingResolver
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public QuestionRequirementSpecCompiler questionRequirementSpecCompiler(
            TemporalAnchorQueryParser anchorParser
    ) {
        return new QuestionRequirementSpecCompiler(anchorParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public StepComputePolicy stepComputePolicy() {
        return new StepComputePolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    public BridgeContractGate bridgeContractGate() {
        return new BridgeContractGate();
    }

    @Bean
    @ConditionalOnMissingBean
    public ActiveSlotLedgerIndex activeSlotLedgerIndex() {
        return new ActiveSlotLedgerIndex();
    }

    @Bean
    @ConditionalOnMissingBean
    public EpisodicRelationalStateExtractor episodicRelationalStateExtractor(
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new EpisodicRelationalStateExtractor(embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public EpisodicDatasetViewMaterializer episodicDatasetViewMaterializer(
            TemporalAnchorQueryParser anchorParser,
            ActiveSlotLedgerIndex activeSlotLedgerIndex,
            EpisodicRelationalStateExtractor extractor,
            BiTemporalHippocampalGraphEngine hippocampalGraphEngine
    ) {
        return new EpisodicDatasetViewMaterializer(anchorParser, activeSlotLedgerIndex, extractor, hippocampalGraphEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public LifelongSkillBankEngine lifelongSkillBankEngine(
            ObjectProvider<AstSecurityInspector> inspectorProvider,
            ObjectProvider<VerifiedCodeActSandboxRuntime> sandboxProvider
    ) {
        LifelongSkillBankEngine engine = new LifelongSkillBankEngine("hermes-skill-bank-secret");
        engine.setSecurityAndSandbox(
                inspectorProvider != null ? inspectorProvider.getIfAvailable(AstSecurityInspector::new) : new AstSecurityInspector(),
                sandboxProvider != null ? sandboxProvider.getIfAvailable() : null
        );
        return engine;
    }

    @Bean
    @ConditionalOnMissingBean
    public MonotonicBranchAcceptanceArbiter monotonicBranchAcceptanceArbiter() {
        return new MonotonicBranchAcceptanceArbiter();
    }

    @Bean
    @ConditionalOnMissingBean
    public EpisodicViewCompletenessAuditor episodicViewCompletenessAuditor() {
        return new EpisodicViewCompletenessAuditor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProgrammaticSkillSynthesizer programmaticSkillSynthesizer(
            ObjectProvider<AstSecurityInspector> inspectorProvider,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        AstSecurityInspector inspector = inspectorProvider != null
                ? inspectorProvider.getIfAvailable(AstSecurityInspector::new)
                : new AstSecurityInspector();
        return new ProgrammaticSkillSynthesizer(inspector, embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public CounterfactualSkillRegressionGate counterfactualSkillRegressionGate() {
        return new CounterfactualSkillRegressionGate();
    }

    @Bean
    @ConditionalOnMissingBean
    public VerifiedSkillHarvester verifiedSkillHarvester(
            LifelongSkillBankEngine skillBankEngine,
            UnitSphereEmbeddingResolver embeddingResolver
    ) {
        return new VerifiedSkillHarvester(skillBankEngine, embeddingResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public SleepTimeEvolutionCoordinator sleepTimeEvolutionCoordinator(
            SleepTimeSpectralDistiller spectralDistiller,
            LifelongSkillBankEngine skillBankEngine,
            CounterfactualSkillRegressionGate regressionGate,
            RhoSelfPreferenceOptimizer rhoOptimizer,
            ObjectProvider<AstSecurityInspector> inspectorProvider,
            ObjectProvider<VerifiedCodeActSandboxRuntime> sandboxProvider
    ) {
        return new SleepTimeEvolutionCoordinator(
                spectralDistiller,
                skillBankEngine,
                regressionGate,
                rhoOptimizer,
                inspectorProvider != null ? inspectorProvider.getIfAvailable(AstSecurityInspector::new) : new AstSecurityInspector(),
                sandboxProvider != null ? sandboxProvider.getIfAvailable() : null
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public IncrementalDeltaObservationDeduplicator incrementalDeltaObservationDeduplicator() {
        return new IncrementalDeltaObservationDeduplicator();
    }

    @Bean
    @ConditionalOnMissingBean
    public BatchParallelReadCoordinator batchParallelReadCoordinator() {
        return new BatchParallelReadCoordinator();
    }

    @Bean
    @ConditionalOnMissingBean
    public Bm25HypersphericalHybridIndex bm25HypersphericalHybridIndex() {
        return new Bm25HypersphericalHybridIndex();
    }
}
