package tech.qiantong.qknow.hermes.benchmark.ablation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * H-NSCDS 可编程正交消融配置（对应论文第 7/8/9/10/11 章实验控制总线，支持 27 维 {@code G0..G22}）。
 * <p>
 * 支持 Phase 6（{@code G0..G5}）、Phase 7 v2（{@code G6}）、Phase 8 v3（{@code G7..G10}）、
 * Phase 9 v4（{@code G11..G14}）、Phase 10 v5（{@code G15..G18}），并扩展 Phase 11 H-NSCDS v6 四大正交机制开关：
 * {@code epistemicDampedComputeEnabled}（ED-SCR 认知阻尼控算与动态思考预算帽）、
 * {@code incrementalDeltaObsDedupEnabled}（IDG-CO 前缀守恒增量观测去重）、
 * {@code bm25HypersphericalBatchReadEnabled}（BPR-LM 批量并发只读与 BM25-S^1023 混合分片检索）、
 * {@code v6PrefixConservingSynergyEnabled}（Phase 11 v6 全局前缀守恒协同控制）形成 {@code G19..G22}，
 * 严格保持既有 8 参、11 参、14 参、19 参及 23 参构造器向后兼容。
 * </p>
 */
public record HnscdsAblationConfig(
        String profileId,
        boolean supernetEnabled,
        boolean driftReplanEnabled,
        boolean ariadneConsisGuardEnabled,
        boolean hippocampalBitemporalEnabled,
        boolean codeModePrefixLockEnabled,
        boolean sleepTimeRhoEnabled,
        long randomSeed,
        boolean hystereticContextFoldingEnabled,
        boolean epistemicStoppingEnabled,
        boolean latticeSpeculativePrefetchEnabled,
        boolean temporalCausalEvolutionEnabled,
        boolean verifiedCodeActEnabled,
        boolean causalReflexiveTreeSearchEnabled,
        boolean requirementSpecVerificationEnabled,
        boolean stepHeterogeneousEffortEnabled,
        boolean typedParallelSubgraphEnabled,
        boolean episodicRelationalViewEnabled,
        boolean verifiedProgrammaticSkillEvolutionEnabled,
        boolean manifoldSpreadStepEffortEnabled,
        boolean monotonicTournamentBcbtEnabled,
        boolean completenessAndPremiseGateEnabled,
        boolean parameterizedScopedSkillEvoEnabled,
        boolean epistemicDampedComputeEnabled,
        boolean incrementalDeltaObsDedupEnabled,
        boolean bm25HypersphericalBatchReadEnabled,
        boolean v6PrefixConservingSynergyEnabled
) {

    public HnscdsAblationConfig(
            String profileId,
            boolean supernetEnabled,
            boolean driftReplanEnabled,
            boolean ariadneConsisGuardEnabled,
            boolean hippocampalBitemporalEnabled,
            boolean codeModePrefixLockEnabled,
            boolean sleepTimeRhoEnabled,
            long randomSeed
    ) {
        this(
                profileId, supernetEnabled, driftReplanEnabled, ariadneConsisGuardEnabled,
                hippocampalBitemporalEnabled, codeModePrefixLockEnabled, sleepTimeRhoEnabled,
                randomSeed, false, false, false, false, false, false,
                false, false, false, false, false,
                false, false, false, false,
                false, false, false, false
        );
    }

    public HnscdsAblationConfig(
            String profileId,
            boolean supernetEnabled,
            boolean driftReplanEnabled,
            boolean ariadneConsisGuardEnabled,
            boolean hippocampalBitemporalEnabled,
            boolean codeModePrefixLockEnabled,
            boolean sleepTimeRhoEnabled,
            long randomSeed,
            boolean hystereticContextFoldingEnabled,
            boolean epistemicStoppingEnabled,
            boolean latticeSpeculativePrefetchEnabled
    ) {
        this(
                profileId, supernetEnabled, driftReplanEnabled, ariadneConsisGuardEnabled,
                hippocampalBitemporalEnabled, codeModePrefixLockEnabled, sleepTimeRhoEnabled,
                randomSeed, hystereticContextFoldingEnabled, epistemicStoppingEnabled,
                latticeSpeculativePrefetchEnabled, false, false, false,
                false, false, false, false, false,
                false, false, false, false,
                false, false, false, false
        );
    }

    public HnscdsAblationConfig(
            String profileId,
            boolean supernetEnabled,
            boolean driftReplanEnabled,
            boolean ariadneConsisGuardEnabled,
            boolean hippocampalBitemporalEnabled,
            boolean codeModePrefixLockEnabled,
            boolean sleepTimeRhoEnabled,
            long randomSeed,
            boolean hystereticContextFoldingEnabled,
            boolean epistemicStoppingEnabled,
            boolean latticeSpeculativePrefetchEnabled,
            boolean temporalCausalEvolutionEnabled,
            boolean verifiedCodeActEnabled,
            boolean causalReflexiveTreeSearchEnabled
    ) {
        this(
                profileId, supernetEnabled, driftReplanEnabled, ariadneConsisGuardEnabled,
                hippocampalBitemporalEnabled, codeModePrefixLockEnabled, sleepTimeRhoEnabled,
                randomSeed, hystereticContextFoldingEnabled, epistemicStoppingEnabled,
                latticeSpeculativePrefetchEnabled, temporalCausalEvolutionEnabled,
                verifiedCodeActEnabled, causalReflexiveTreeSearchEnabled,
                false, false, false, false, false,
                false, false, false, false,
                false, false, false, false
        );
    }

    public HnscdsAblationConfig(
            String profileId,
            boolean supernetEnabled,
            boolean driftReplanEnabled,
            boolean ariadneConsisGuardEnabled,
            boolean hippocampalBitemporalEnabled,
            boolean codeModePrefixLockEnabled,
            boolean sleepTimeRhoEnabled,
            long randomSeed,
            boolean hystereticContextFoldingEnabled,
            boolean epistemicStoppingEnabled,
            boolean latticeSpeculativePrefetchEnabled,
            boolean temporalCausalEvolutionEnabled,
            boolean verifiedCodeActEnabled,
            boolean causalReflexiveTreeSearchEnabled,
            boolean requirementSpecVerificationEnabled,
            boolean stepHeterogeneousEffortEnabled,
            boolean typedParallelSubgraphEnabled,
            boolean episodicRelationalViewEnabled,
            boolean verifiedProgrammaticSkillEvolutionEnabled
    ) {
        this(
                profileId, supernetEnabled, driftReplanEnabled, ariadneConsisGuardEnabled,
                hippocampalBitemporalEnabled, codeModePrefixLockEnabled, sleepTimeRhoEnabled,
                randomSeed, hystereticContextFoldingEnabled, epistemicStoppingEnabled,
                latticeSpeculativePrefetchEnabled, temporalCausalEvolutionEnabled,
                verifiedCodeActEnabled, causalReflexiveTreeSearchEnabled,
                requirementSpecVerificationEnabled, stepHeterogeneousEffortEnabled,
                typedParallelSubgraphEnabled, episodicRelationalViewEnabled,
                verifiedProgrammaticSkillEvolutionEnabled,
                false, false, false, false,
                false, false, false, false
        );
    }

    public HnscdsAblationConfig(
            String profileId,
            boolean supernetEnabled,
            boolean driftReplanEnabled,
            boolean ariadneConsisGuardEnabled,
            boolean hippocampalBitemporalEnabled,
            boolean codeModePrefixLockEnabled,
            boolean sleepTimeRhoEnabled,
            long randomSeed,
            boolean hystereticContextFoldingEnabled,
            boolean epistemicStoppingEnabled,
            boolean latticeSpeculativePrefetchEnabled,
            boolean temporalCausalEvolutionEnabled,
            boolean verifiedCodeActEnabled,
            boolean causalReflexiveTreeSearchEnabled,
            boolean requirementSpecVerificationEnabled,
            boolean stepHeterogeneousEffortEnabled,
            boolean typedParallelSubgraphEnabled,
            boolean episodicRelationalViewEnabled,
            boolean verifiedProgrammaticSkillEvolutionEnabled,
            boolean manifoldSpreadStepEffortEnabled,
            boolean monotonicTournamentBcbtEnabled,
            boolean completenessAndPremiseGateEnabled,
            boolean parameterizedScopedSkillEvoEnabled
    ) {
        this(
                profileId, supernetEnabled, driftReplanEnabled, ariadneConsisGuardEnabled,
                hippocampalBitemporalEnabled, codeModePrefixLockEnabled, sleepTimeRhoEnabled,
                randomSeed, hystereticContextFoldingEnabled, epistemicStoppingEnabled,
                latticeSpeculativePrefetchEnabled, temporalCausalEvolutionEnabled,
                verifiedCodeActEnabled, causalReflexiveTreeSearchEnabled,
                requirementSpecVerificationEnabled, stepHeterogeneousEffortEnabled,
                typedParallelSubgraphEnabled, episodicRelationalViewEnabled,
                verifiedProgrammaticSkillEvolutionEnabled,
                manifoldSpreadStepEffortEnabled, monotonicTournamentBcbtEnabled,
                completenessAndPremiseGateEnabled, parameterizedScopedSkillEvoEnabled,
                false, false, false, false
        );
    }

    public HnscdsAblationConfig {
        profileId = (profileId == null || profileId.isBlank()) ? "G5_FULL_HNSCDS" : profileId.trim();
    }

    /** G0：基础 ReAct 基线（全部高级增强关闭，用于基准对比） */
    public static HnscdsAblationConfig g0VanillaReact(long seed) {
        return new HnscdsAblationConfig(
                "G0_VANILLA_REACT",
                false, false, false, false, false, false, seed,
                false, false, false,
                false, false, false
        );
    }

    /** B_CWL：CWL 原地上下文驱逐基线（不锁定双区前缀，用于对照 Prefix Cache 抖动） */
    public static HnscdsAblationConfig bCwlInPlace(long seed) {
        return new HnscdsAblationConfig(
                "B_CWL_INPLACE",
                true, true, true, true, false, false, seed,
                false, true, false,
                false, false, false
        );
    }

    /** G1：剥离超球面超网控算（仅保留其余闭环机制） */
    public static HnscdsAblationConfig g1WithoutSupernet(long seed) {
        return new HnscdsAblationConfig(
                "G1_WO_SUPERNET",
                false, true, true, true, true, true, seed,
                false, false, false,
                false, false, false
        );
    }

    /** G2：剥离平方弦漂移重规划与早退 */
    public static HnscdsAblationConfig g2WithoutDriftReplan(long seed) {
        return new HnscdsAblationConfig(
                "G2_WO_DRIFT_REPLAN",
                true, false, true, true, true, true, seed,
                false, false, false,
                false, false, false
        );
    }

    /** G3：剥离 Ariadne/ConsisGuard 双向推理一致性审计与前沿阻断 */
    public static HnscdsAblationConfig g3WithoutConsisGuard(long seed) {
        return new HnscdsAblationConfig(
                "G3_WO_CONSIS_GUARD",
                true, true, false, true, true, true, seed,
                false, false, false,
                false, false, false
        );
    }

    /** G4：剥离双时态海马体知识检索注入 */
    public static HnscdsAblationConfig g4WithoutHippocampal(long seed) {
        return new HnscdsAblationConfig(
                "G4_WO_HIPPOCAMPAL",
                true, true, true, false, true, true, seed,
                false, false, false,
                false, false, false
        );
    }

    /** G5：完整 Phase 6 H-NSCDS v1 全闭环架构 */
    public static HnscdsAblationConfig g5FullHnscds(long seed) {
        return new HnscdsAblationConfig(
                "G5_FULL_HNSCDS",
                true, true, true, true, true, true, seed,
                false, false, false,
                false, false, false
        );
    }

    /** G6：Phase 7 H-NSCDS v2 终极完整版（全闭环 + HDZ-PCF + HEIG-BAS + LG-STP） */
    public static HnscdsAblationConfig g6HnscdsV2Full(long seed) {
        return new HnscdsAblationConfig(
                "G6_HNSCDS_V2_FULL",
                true, true, true, true, true, true, seed,
                true, true, true,
                false, false, false
        );
    }

    /** G7：Phase 8.1 增量验证版（H-NSCDS v2 + 双时态因果演化与只读视角） */
    public static HnscdsAblationConfig g7V3Temporal(long seed) {
        return new HnscdsAblationConfig(
                "G7_V3_TEMPORAL",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, false, false
        );
    }

    /** G8：Phase 8.1+8.2 增量验证版（+ 安全格验证型 CodeAct 沙盒） */
    public static HnscdsAblationConfig g8V3CodeAct(long seed) {
        return new HnscdsAblationConfig(
                "G8_V3_CODEACT",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, false
        );
    }

    /** G9：Phase 8.1+8.3 对照验证版（双时态因果记忆 + 有界因果反思树搜索，关闭 CodeAct） */
    public static HnscdsAblationConfig g9V3CausalTree(long seed) {
        return new HnscdsAblationConfig(
                "G9_V3_CAUSAL_TREE",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, false, true
        );
    }

    /** G10：Phase 8 终极完整版 H-NSCDS v3（14 维全开） */
    public static HnscdsAblationConfig g10HnscdsV3Ultimate(long seed) {
        return new HnscdsAblationConfig(
                "G10_HNSCDS_V3_ULTIMATE",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                false, false, false, false, false
        );
    }

    /** G11：Phase 9.1 增量版（v3 + FRSV 需求规格校验 + ZCEA-SCR 步级异构控算） */
    public static HnscdsAblationConfig g11V4FrsvShscr(long seed) {
        return new HnscdsAblationConfig(
                "G11_V4_FRSV_SHSCR",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, false, false, false
        );
    }

    /** G12：Phase 9.2 增量版（G11 + PA-TSG 前缀锚定隔离上下文类型化并行子图） */
    public static HnscdsAblationConfig g12V4Subgraph(long seed) {
        return new HnscdsAblationConfig(
                "G12_V4_SUBGRAPH",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, false, false
        );
    }

    /** G13：Phase 9.3 增量版（G12 + BTER-MV 双时态情景-关系动态物化视图） */
    public static HnscdsAblationConfig g13V4E2r(long seed) {
        return new HnscdsAblationConfig(
                "G13_V4_E2R",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, false
        );
    }

    /** G14：Phase 9 终极完整版 H-NSCDS v4（全 19 维正交机制全开） */
    public static HnscdsAblationConfig g14HnscdsV4Ultimate(long seed) {
        return new HnscdsAblationConfig(
                "G14_HNSCDS_V4_ULTIMATE",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true
        );
    }

    public static List<HnscdsAblationConfig> v4ExtendedMatrix(long seed) {
        return List.of(
                g0VanillaReact(seed),
                g6HnscdsV2Full(seed),
                g10HnscdsV3Ultimate(seed),
                g11V4FrsvShscr(seed),
                g12V4Subgraph(seed),
                g13V4E2r(seed),
                g14HnscdsV4Ultimate(seed)
        );
    }

    /** G15：Phase 10.1 增量版（v4 + MS-SHSCR 多候选流形散度与跳深感知步级控算） */
    public static HnscdsAblationConfig g15V5ManifoldEffort(long seed) {
        return new HnscdsAblationConfig(
                "G15_V5_MANIFOLD_EFFORT",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, false, false, false
        );
    }

    /** G16：Phase 10.2 增量版（G15 + MA-BCBT 单调保守反谄媚竞赛树搜索） */
    public static HnscdsAblationConfig g16V5MonotonicBcbt(long seed) {
        return new HnscdsAblationConfig(
                "G16_V5_MONOTONIC_BCBT",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, true, false, false
        );
    }

    /** G17：Phase 10.3 增量版（G16 + CC-E2R 完备证书视图与 PG-FRSV 前提锚定拒答门控） */
    public static HnscdsAblationConfig g17V5CompletenessPremise(long seed) {
        return new HnscdsAblationConfig(
                "G17_V5_COMPLETENESS_PREMISE",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, true, true, false
        );
    }

    /** G18：Phase 10 终极完整版 H-NSCDS v5（前 23 维正交机制全开） */
    public static HnscdsAblationConfig g18HnscdsV5Ultimate(long seed) {
        return new HnscdsAblationConfig(
                "G18_HNSCDS_V5_ULTIMATE",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, true, true, true,
                false, false, false, false
        );
    }

    /** G19：Phase 11.1 增量版（v5 + ED-SCR 认知阻尼步级控算与动态思考预算帽） */
    public static HnscdsAblationConfig g19V6EpistemicDamping(long seed) {
        return new HnscdsAblationConfig(
                "G19_V6_ED_SCR",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, true, true, true,
                true, false, false, false
        );
    }

    /** G20：Phase 11.2 增量版（G19 + IDG-CO 前缀守恒增量观测去重） */
    public static HnscdsAblationConfig g20V6IncrementalDedup(long seed) {
        return new HnscdsAblationConfig(
                "G20_V6_IDG_CO",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, true, true, true,
                true, true, false, false
        );
    }

    /** G21：Phase 11.3 增量版（G20 + BPR-LM 批量并发只读检索与 BM25-超球面混合分片索引） */
    public static HnscdsAblationConfig g21V6Bm25BatchRead(long seed) {
        return new HnscdsAblationConfig(
                "G21_V6_BPR_LM",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, true, true, true,
                true, true, true, false
        );
    }

    /** G22：Phase 11 终极完整版 H-NSCDS v6（全 27 维正交机制全开） */
    public static HnscdsAblationConfig g22HnscdsV6Ultimate(long seed) {
        return new HnscdsAblationConfig(
                "G22_HNSCDS_V6_ULTIMATE",
                true, true, true, true, true, true, seed,
                true, true, true,
                true, true, true,
                true, true, true, true, true,
                true, true, true, true,
                true, true, true, true
        );
    }

    public static List<HnscdsAblationConfig> v6ExtendedMatrix(long seed) {
        return List.of(
                g0VanillaReact(seed),
                g6HnscdsV2Full(seed),
                g10HnscdsV3Ultimate(seed),
                g14HnscdsV4Ultimate(seed),
                g18HnscdsV5Ultimate(seed),
                g19V6EpistemicDamping(seed),
                g20V6IncrementalDedup(seed),
                g21V6Bm25BatchRead(seed),
                g22HnscdsV6Ultimate(seed)
        );
    }

    public static List<HnscdsAblationConfig> v5ExtendedMatrix(long seed) {
        return List.of(
                g0VanillaReact(seed),
                g6HnscdsV2Full(seed),
                g10HnscdsV3Ultimate(seed),
                g14HnscdsV4Ultimate(seed),
                g15V5ManifoldEffort(seed),
                g16V5MonotonicBcbt(seed),
                g17V5CompletenessPremise(seed),
                g18HnscdsV5Ultimate(seed)
        );
    }

    public static HnscdsAblationConfig defaults() {
        return g5FullHnscds(42L);
    }

    public static List<HnscdsAblationConfig> standardMatrix(long seed) {
        return List.of(
                g0VanillaReact(seed),
                g1WithoutSupernet(seed),
                g2WithoutDriftReplan(seed),
                g3WithoutConsisGuard(seed),
                g4WithoutHippocampal(seed),
                g5FullHnscds(seed)
        );
    }

    public static List<HnscdsAblationConfig> v2ExtendedMatrix(long seed) {
        return List.of(
                g0VanillaReact(seed),
                bCwlInPlace(seed),
                g1WithoutSupernet(seed),
                g3WithoutConsisGuard(seed),
                g4WithoutHippocampal(seed),
                g5FullHnscds(seed),
                g6HnscdsV2Full(seed)
        );
    }

    public static List<HnscdsAblationConfig> v3ExtendedMatrix(long seed) {
        return List.of(
                g0VanillaReact(seed),
                bCwlInPlace(seed),
                g5FullHnscds(seed),
                g6HnscdsV2Full(seed),
                g7V3Temporal(seed),
                g8V3CodeAct(seed),
                g9V3CausalTree(seed),
                g10HnscdsV3Ultimate(seed)
        );
    }

    public static HnscdsAblationConfig fromProfileId(String profileId, long seed) {
        if (profileId == null || profileId.isBlank()) {
            return g5FullHnscds(seed);
        }
        String normalized = profileId.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "G0", "G0_VANILLA_REACT" -> g0VanillaReact(seed);
            case "B_CWL", "B_CWL_INPLACE" -> bCwlInPlace(seed);
            case "G1", "G1_WO_SUPERNET" -> g1WithoutSupernet(seed);
            case "G2", "G2_WO_DRIFT_REPLAN" -> g2WithoutDriftReplan(seed);
            case "G3", "G3_WO_CONSIS_GUARD" -> g3WithoutConsisGuard(seed);
            case "G4", "G4_WO_HIPPOCAMPAL" -> g4WithoutHippocampal(seed);
            case "G5", "G5_FULL_HNSCDS" -> g5FullHnscds(seed);
            case "G6", "G6_HNSCDS_V2_FULL" -> g6HnscdsV2Full(seed);
            case "G7", "G7_V3_TEMPORAL" -> g7V3Temporal(seed);
            case "G8", "G8_V3_CODEACT" -> g8V3CodeAct(seed);
            case "G9", "G9_V3_CAUSAL_TREE" -> g9V3CausalTree(seed);
            case "G10", "G10_HNSCDS_V3_ULTIMATE" -> g10HnscdsV3Ultimate(seed);
            case "G11", "G11_V4_FRSV_SHSCR" -> g11V4FrsvShscr(seed);
            case "G12", "G12_V4_SUBGRAPH" -> g12V4Subgraph(seed);
            case "G13", "G13_V4_E2R" -> g13V4E2r(seed);
            case "G14", "G14_HNSCDS_V4_ULTIMATE" -> g14HnscdsV4Ultimate(seed);
            case "G15", "G15_V5_MANIFOLD_EFFORT" -> g15V5ManifoldEffort(seed);
            case "G16", "G16_V5_MONOTONIC_BCBT" -> g16V5MonotonicBcbt(seed);
            case "G17", "G17_V5_COMPLETENESS_PREMISE" -> g17V5CompletenessPremise(seed);
            case "G18", "G18_HNSCDS_V5_ULTIMATE" -> g18HnscdsV5Ultimate(seed);
            case "G19", "G19_V6_ED_SCR" -> g19V6EpistemicDamping(seed);
            case "G20", "G20_V6_IDG_CO" -> g20V6IncrementalDedup(seed);
            case "G21", "G21_V6_BPR_LM" -> g21V6Bm25BatchRead(seed);
            case "G22", "G22_HNSCDS_V6_ULTIMATE" -> g22HnscdsV6Ultimate(seed);
            default -> throw new IllegalArgumentException("未知的 H-NSCDS 消融档位: " + Objects.toString(profileId));
        };
    }
}
