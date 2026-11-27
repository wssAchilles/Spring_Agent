package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.agent.skill.LifelongSkillBankEngine;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.evolution.CounterfactualSkillRegressionGate;
import tech.qiantong.qknow.hermes.cognitive.evolution.ProgrammaticSkillSynthesizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.RhoSelfPreferenceOptimizer;
import tech.qiantong.qknow.hermes.cognitive.evolution.SleepTimeEvolutionCoordinator;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.treesearch.MonotonicBranchAcceptanceArbiter;
import tech.qiantong.qknow.hermes.cognitive.verification.PremiseGroundingAuditor;
import tech.qiantong.qknow.hermes.config.HermesPhdEnginesAutoConfiguration;
import tech.qiantong.qknow.hermes.controller.PhdStudioObservabilityController;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.SleepTimeSpectralDistiller;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicViewCompletenessAuditor;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityAuditor;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10.5 H-NSCDS v5 全栈协同、Spring Bean 装配与博士观测控制面遥测契约单测。
 */
class Phase10_5HnscdsV5SynergyAndWiringContractTest {

    @Test
    void springAutoConfigRegistersAllFourPhase10Engines() {
        Set<String> beanMethodNames = new HashSet<>();
        for (Method m : HermesPhdEnginesAutoConfiguration.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Bean.class)) {
                beanMethodNames.add(m.getName());
            }
        }
        assertTrue(beanMethodNames.contains("monotonicBranchAcceptanceArbiter"));
        assertTrue(beanMethodNames.contains("episodicViewCompletenessAuditor"));
        assertTrue(beanMethodNames.contains("premiseGroundingAuditor"));
        assertTrue(beanMethodNames.contains("programmaticSkillSynthesizer"));
    }

    @Test
    void controllerExposesAllFourPhase10TelemetrySections() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        StepComputePolicy stepPolicy = new StepComputePolicy();
        stepPolicy.calibrateFromDefaultMultiHopPrior();
        MonotonicBranchAcceptanceArbiter arbiter = new MonotonicBranchAcceptanceArbiter();
        EpisodicViewCompletenessAuditor completenessAuditor = new EpisodicViewCompletenessAuditor();
        PremiseGroundingAuditor premiseAuditor = new PremiseGroundingAuditor(resolver);
        ProgrammaticSkillSynthesizer synthesizer = new ProgrammaticSkillSynthesizer(new AstSecurityInspector(), resolver);
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("v5-ctrl-hippo");
        HypersphericalSupernetRouter router = new HypersphericalSupernetRouter("v5-ctrl-router", 42L);
        LifelongSkillBankEngine skillBank = new LifelongSkillBankEngine("v5-ctrl-skill");
        SleepTimeEvolutionCoordinator sleepCoordinator = new SleepTimeEvolutionCoordinator(
                new SleepTimeSpectralDistiller(hippo),
                skillBank,
                new CounterfactualSkillRegressionGate(),
                new RhoSelfPreferenceOptimizer(router, resolver, hippo),
                new AstSecurityInspector(),
                null
        );

        PhdStudioObservabilityController controller = new PhdStudioObservabilityController(
                new PgStepJournalStateCheckpointer(),
                new FrontierGatedToolTransactionManager(null),
                hippo,
                new HoareToolContractGateway("v5-ctrl-hoare"),
                router,
                new PrefixCacheStabilityAuditor(null),
                null
        );
        controller.setStepComputePolicy(stepPolicy);
        controller.setMonotonicBranchAcceptanceArbiter(arbiter);
        controller.setEpisodicViewCompletenessAuditor(completenessAuditor);
        controller.setPremiseGroundingAuditor(premiseAuditor);
        controller.setProgrammaticSkillSynthesizer(synthesizer);
        controller.setSleepTimeEvolutionCoordinator(sleepCoordinator);

        var res = controller.getTelemetry();
        assertEquals(200, res.getCode());
        Map<String, Object> data = res.getData();
        assertNotNull(data);
        assertTrue(data.containsKey("msShscrManifoldControl"));
        assertTrue(data.containsKey("maBcbtTournament"));
        assertTrue(data.containsKey("ccE2rAndPremiseGate"));
        assertTrue(data.containsKey("pVpsSkillEvolution"));

        @SuppressWarnings("unchecked")
        Map<String, Object> msSec = (Map<String, Object>) data.get("msShscrManifoldControl");
        assertTrue(Boolean.TRUE.equals(msSec.get("conformalCalibrated")));
        assertTrue(msSec.containsKey("calibratedThresholds"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ccSec = (Map<String, Object>) data.get("ccE2rAndPremiseGate");
        assertTrue(ccSec.containsKey("completenessAuditor"));
        assertTrue(ccSec.containsKey("premiseGrounding"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pvpsSec = (Map<String, Object>) data.get("pVpsSkillEvolution");
        assertTrue(pvpsSec.containsKey("synthesizer"));
        assertTrue(pvpsSec.containsKey("totalScopedSleepCycles"));
    }

    @Test
    void g18UltimateProfileEnablesAllV5FlagsWithoutDisablingPriorGenerations() {
        HnscdsAblationConfig g18 = HnscdsAblationConfig.g18HnscdsV5Ultimate(42L);
        assertTrue(g18.supernetEnabled());
        assertTrue(g18.hippocampalBitemporalEnabled());
        assertTrue(g18.temporalCausalEvolutionEnabled());
        assertTrue(g18.verifiedCodeActEnabled());
        assertTrue(g18.causalReflexiveTreeSearchEnabled());
        assertTrue(g18.requirementSpecVerificationEnabled());
        assertTrue(g18.stepHeterogeneousEffortEnabled());
        assertTrue(g18.typedParallelSubgraphEnabled());
        assertTrue(g18.episodicRelationalViewEnabled());
        assertTrue(g18.verifiedProgrammaticSkillEvolutionEnabled());
        assertTrue(g18.manifoldSpreadStepEffortEnabled());
        assertTrue(g18.monotonicTournamentBcbtEnabled());
        assertTrue(g18.completenessAndPremiseGateEnabled());
        assertTrue(g18.parameterizedScopedSkillEvoEnabled());
    }
}
