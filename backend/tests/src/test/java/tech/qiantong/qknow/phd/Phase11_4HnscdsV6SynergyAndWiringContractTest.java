package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.context.IncrementalDeltaObservationDeduplicator;
import tech.qiantong.qknow.hermes.cognitive.control.StepComputePolicy;
import tech.qiantong.qknow.hermes.cognitive.supernet.HypersphericalSupernetRouter;
import tech.qiantong.qknow.hermes.cognitive.tool.BatchParallelReadCoordinator;
import tech.qiantong.qknow.hermes.config.HermesPhdEnginesAutoConfiguration;
import tech.qiantong.qknow.hermes.controller.PhdStudioObservabilityController;
import tech.qiantong.qknow.hermes.flow.stategraph.journal.PgStepJournalStateCheckpointer;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.hybrid.Bm25HypersphericalHybridIndex;
import tech.qiantong.qknow.hermes.prefix.PrefixCacheStabilityAuditor;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;
import tech.qiantong.qknow.hermes.transaction.frontier.FrontierGatedToolTransactionManager;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11.4 (H-NSCDS v6) 27 维消融配置向后兼容、双端 Spring Bean 装配与联邦遥测契约单测。
 */
class Phase11_4HnscdsV6SynergyAndWiringContractTest {

    @Test
    void ablationConfigPreservesAllLegacyConstructorsAndSupportsG19ToG22() {
        // 8 / 11 / 14 / 19 / 23 参历史构造器必须全部零改动编译通过且默认关闭 v6 开关
        var c8 = new HnscdsAblationConfig("C8", true, true, true, true, true, true, 42L);
        var c11 = new HnscdsAblationConfig("C11", true, true, true, true, true, true, 42L,
                true, true, true);
        var c14 = new HnscdsAblationConfig("C14", true, true, true, true, true, true, 42L,
                true, true, true, true, true, true);
        var c19 = new HnscdsAblationConfig("C19", true, true, true, true, true, true, 42L,
                true, true, true, true, true, true, true, true, true, true, true);
        var c23 = new HnscdsAblationConfig("C23", true, true, true, true, true, true, 42L,
                true, true, true, true, true, true, true, true, true, true, true,
                true, true, true, true);

        for (HnscdsAblationConfig legacy : List.of(c8, c11, c14, c19, c23)) {
            assertFalse(legacy.epistemicDampedComputeEnabled());
            assertFalse(legacy.incrementalDeltaObsDedupEnabled());
            assertFalse(legacy.bm25HypersphericalBatchReadEnabled());
            assertFalse(legacy.v6PrefixConservingSynergyEnabled());
        }

        var g19 = HnscdsAblationConfig.fromProfileId("G19", 42L);
        assertTrue(g19.epistemicDampedComputeEnabled());
        assertFalse(g19.incrementalDeltaObsDedupEnabled());

        var g20 = HnscdsAblationConfig.fromProfileId("G20_V6_IDG_CO", 42L);
        assertTrue(g20.epistemicDampedComputeEnabled());
        assertTrue(g20.incrementalDeltaObsDedupEnabled());
        assertFalse(g20.bm25HypersphericalBatchReadEnabled());

        var g21 = HnscdsAblationConfig.fromProfileId("G21", 42L);
        assertTrue(g21.bm25HypersphericalBatchReadEnabled());
        assertFalse(g21.v6PrefixConservingSynergyEnabled());

        var g22 = HnscdsAblationConfig.fromProfileId("G22_HNSCDS_V6_ULTIMATE", 42L);
        assertTrue(g22.parameterizedScopedSkillEvoEnabled());
        assertTrue(g22.epistemicDampedComputeEnabled());
        assertTrue(g22.incrementalDeltaObsDedupEnabled());
        assertTrue(g22.bm25HypersphericalBatchReadEnabled());
        assertTrue(g22.v6PrefixConservingSynergyEnabled());

        var matrix = HnscdsAblationConfig.v6ExtendedMatrix(42L);
        assertEquals(9, matrix.size());
        assertEquals("G22_HNSCDS_V6_ULTIMATE", matrix.get(matrix.size() - 1).profileId());
    }

    @Test
    void bothServerAndHermesConfigsRegisterAllThreePhase11Beans() throws Exception {
        Set<String> hermesBeans = extractBeanMethodNames(HermesPhdEnginesAutoConfiguration.class);

        // 读取 qknow-server 主装配类源码核验对等 @Bean 注册与观测控制器 Setter 注入
        Path serverConfigPath = resolveServerConfigPath();
        String serverConfigSource = Files.readString(serverConfigPath, StandardCharsets.UTF_8);

        for (String requiredBean : List.of(
                "incrementalDeltaObservationDeduplicator",
                "batchParallelReadCoordinator",
                "bm25HypersphericalHybridIndex")) {
            assertTrue(hermesBeans.contains(requiredBean),
                    "HermesPhdEnginesAutoConfiguration 缺少 Bean: " + requiredBean);
            assertTrue(serverConfigSource.contains(requiredBean + "()"),
                    "HermesConfiguration 缺少对等 @Bean: " + requiredBean);
        }
        assertTrue(serverConfigSource.contains("setIncrementalDeltaObservationDeduplicator"));
        assertTrue(serverConfigSource.contains("setBatchParallelReadCoordinator"));
        assertTrue(serverConfigSource.contains("setBm25HypersphericalHybridIndex"));
    }

    private static Path resolveServerConfigPath() {
        for (Path candidate : List.of(
                Path.of("qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java"),
                Path.of("backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java"),
                Path.of("../qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java")
        )) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("未定位到 HermesConfiguration.java");
    }

    @Test
    void controllerExposesAllFourPhase11TelemetrySectionsPreservingLegacySections() {
        StepComputePolicy stepPolicy = new StepComputePolicy();
        stepPolicy.calibrateFromDefaultMultiHopPrior();
        IncrementalDeltaObservationDeduplicator dedup = new IncrementalDeltaObservationDeduplicator();
        BatchParallelReadCoordinator coordinator = new BatchParallelReadCoordinator();
        Bm25HypersphericalHybridIndex hybridIndex = new Bm25HypersphericalHybridIndex();

        PhdStudioObservabilityController controller = new PhdStudioObservabilityController(
                new PgStepJournalStateCheckpointer(),
                new FrontierGatedToolTransactionManager(null),
                new BiTemporalHippocampalGraphEngine("v6-ctrl-hippo"),
                new HoareToolContractGateway("v6-ctrl-hoare"),
                new HypersphericalSupernetRouter("v6-ctrl-router", 42L),
                new PrefixCacheStabilityAuditor(null),
                null
        );
        controller.setStepComputePolicy(stepPolicy);
        controller.setIncrementalDeltaObservationDeduplicator(dedup);
        controller.setBatchParallelReadCoordinator(coordinator);
        controller.setBm25HypersphericalHybridIndex(hybridIndex);

        var res = controller.getTelemetry();
        assertEquals(200, res.getCode());
        Map<String, Object> data = res.getData();
        assertNotNull(data);

        // 保留 Phase 10 与此前既有节点的同时，暴露全部 4 个 Phase 11 遥测节
        assertTrue(data.containsKey("msShscrManifoldControl"));
        assertTrue(data.containsKey("edScrDampingControl"));
        assertTrue(data.containsKey("idgCoObservationDedup"));
        assertTrue(data.containsKey("bprLmHybridRetrieval"));
        assertTrue(data.containsKey("v6SynergyGovernance"));

        @SuppressWarnings("unchecked")
        Map<String, Object> v6Gov = (Map<String, Object>) data.get("v6SynergyGovernance");
        assertEquals(27, v6Gov.get("ablationDimensions"));
        assertEquals("G0..G22", v6Gov.get("profilesSupported"));
    }

    private static Set<String> extractBeanMethodNames(Class<?> configClass) {
        Set<String> names = new HashSet<>();
        for (Method m : configClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Bean.class)) {
                names.add(m.getName());
            }
        }
        return names;
    }
}
