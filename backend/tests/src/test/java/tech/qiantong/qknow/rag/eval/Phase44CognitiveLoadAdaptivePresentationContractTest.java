package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.presentation.dto.MultimodalPresentationBlockDTO;
import tech.qiantong.qknow.ai.presentation.dto.PresentationPlanVO;
import tech.qiantong.qknow.ai.presentation.dto.UserCognitiveStateDTO;
import tech.qiantong.qknow.ai.presentation.engine.AdaptivePresentationGovernor;
import tech.qiantong.qknow.ai.presentation.engine.CognitiveLoadEstimator;
import tech.qiantong.qknow.ai.presentation.engine.DynamicMultimodalComposer;
import tech.qiantong.qknow.ai.presentation.engine.PresentationStreamCoordinator;
import tech.qiantong.qknow.ai.presentation.enums.CognitiveLoadLevel;
import tech.qiantong.qknow.ai.presentation.enums.PresentationBlockType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 44 契约测试：认知负荷感知自适应交互与动态多模态信息呈现中枢
 *
 * 验证 10 项严苛契约：
 * 1. 认知负荷连续得分计算精度与单调性契约
 * 2. 认知负荷四态区间映射精确无缝契约
 * 3. Cowan 工作记忆容量硬阻断不变量契约 (定理 1.1)
 * 4. CRITICAL 状态下唯一核心行动单阻断契约 (定理 1.1)
 * 5. HIGH 负荷下复杂图表抑制与指标卡帕累托占优契约 (定理 2.1)
 * 6. LOW 负荷下全量多模态与深度文本释放契约 (定理 2.1)
 * 7. 多模态展示块属性与 ECharts 配置完整性契约
 * 8. 端到端流式呈现调度总控流程闭环契约
 * 9. 异常输入下 Fail-Open 优雅降级契约
 * 10. 单次呈现评估与卡片组装耗时 MTTC <= 50ms 性能契约
 */
public class Phase44CognitiveLoadAdaptivePresentationContractTest {

    private CognitiveLoadEstimator estimator;
    private AdaptivePresentationGovernor governor;
    private DynamicMultimodalComposer composer;
    private PresentationStreamCoordinator coordinator;

    @BeforeEach
    public void setUp() {
        this.estimator = new CognitiveLoadEstimator();
        this.governor = new AdaptivePresentationGovernor();
        this.composer = new DynamicMultimodalComposer();
        this.coordinator = new PresentationStreamCoordinator(estimator, governor, composer);
    }

    @Test
    @DisplayName("契约 1: 认知负荷连续得分计算精度与单调性")
    public void test01_CognitiveLoadEstimatorContinuousScoreAccuracy() {
        // 低压场景: 简短文本、充裕停顿 (5000ms)、低信息熵、低紧急度
        UserCognitiveStateDTO lowState = new UserCognitiveStateDTO(200, 5000L, 0.10, 0.10, 0);
        double lowScore = estimator.estimateCognitiveScore(lowState);

        // 高压场景: 超长文本 (3800 tokens)、急促停顿 (100ms)、高信息熵、高紧急度、多次纠错
        UserCognitiveStateDTO highScore = new UserCognitiveStateDTO(3800, 100L, 0.90, 0.95, 3);
        double criticalScore = estimator.estimateCognitiveScore(highScore);

        assertTrue(lowScore >= 0.0 && lowScore <= 1.0, "负荷得分必须在 [0.0, 1.0] 范围内，实际=" + lowScore);
        assertTrue(criticalScore >= 0.0 && criticalScore <= 1.0, "负荷得分必须在 [0.0, 1.0] 范围内，实际=" + criticalScore);
        assertTrue(criticalScore > lowScore, "高压高紧急度场景得分必须严格高于低压从容场景");
        assertTrue(lowScore < 0.35, "从容场景负荷得分应处于低位，实际=" + lowScore);
        assertTrue(criticalScore >= 0.85, "极端紧急场景负荷得分应处于极高位，实际=" + criticalScore);
    }

    @Test
    @DisplayName("契约 2: 认知负荷四态区间映射精确无缝")
    public void test02_CognitiveLoadEstimatorDiscreteLevelMapping() {
        assertEquals(CognitiveLoadLevel.LOW, estimator.mapToLevel(0.15));
        assertEquals(CognitiveLoadLevel.LOW, estimator.mapToLevel(0.349));
        assertEquals(CognitiveLoadLevel.MEDIUM, estimator.mapToLevel(0.35));
        assertEquals(CognitiveLoadLevel.MEDIUM, estimator.mapToLevel(0.649));
        assertEquals(CognitiveLoadLevel.HIGH, estimator.mapToLevel(0.65));
        assertEquals(CognitiveLoadLevel.HIGH, estimator.mapToLevel(0.849));
        assertEquals(CognitiveLoadLevel.CRITICAL, estimator.mapToLevel(0.85));
        assertEquals(CognitiveLoadLevel.CRITICAL, estimator.mapToLevel(0.99));
    }

    @Test
    @DisplayName("契约 3: Cowan 工作记忆容量硬阻断不变量 (定理 1.1)")
    public void test03_CowanWorkingMemoryBoundInvariant() {
        // 准备 8 个候选展示块 (远超人类工作记忆 4±1 阈值)
        List<MultimodalPresentationBlockDTO> candidateBlocks = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            candidateBlocks.add(new MultimodalPresentationBlockDTO(
                    "block_" + i,
                    PresentationBlockType.TEXT_MARKDOWN,
                    "标题 " + i,
                    "内容摘要 " + i,
                    i
            ));
        }

        for (CognitiveLoadLevel level : CognitiveLoadLevel.values()) {
            List<List<MultimodalPresentationBlockDTO>> partitioned = governor.governPresentation(candidateBlocks, level);
            List<MultimodalPresentationBlockDTO> active = partitioned.get(0);
            List<MultimodalPresentationBlockDTO> collapsed = partitioned.get(1);

            // 严格断言：首屏活跃展示块数量 <= maxActiveChunks <= 4
            assertTrue(active.size() <= level.getMaxActiveChunks(),
                    level + " 等级下活跃块数量 (" + active.size() + ") 超出上限 (" + level.getMaxActiveChunks() + ")");
            assertTrue(active.size() <= 4, "活跃展示块数量在任何情况下不得超过 Cowan 4 组块硬上限");
            assertEquals(candidateBlocks.size(), active.size() + collapsed.size(), "展示块总数守恒");
        }
    }

    @Test
    @DisplayName("契约 4: CRITICAL 状态下唯一核心行动单阻断 (定理 1.1)")
    public void test05_CriticalStateStrictSingleActionInvariant() {
        List<MultimodalPresentationBlockDTO> blocks = Arrays.asList(
                composer.composeMarkdownBlock("b1", "分析详情", "详细文本内容...", 1),
                composer.composeEchartBlock("b2", "风险走势", "Line", List.of("10:00", "11:00"), List.of(20.0, 85.0), 2),
                composer.composeActionBannerBlock("b3", "风控紧急阻断", "检测到异常交易穿仓，请确认立即停机平仓！", "DANGER", 0),
                composer.composeMetricCardBlock("b4", "波动率", "92.5", "%", 15.2, 3)
        );

        List<List<MultimodalPresentationBlockDTO>> partitioned = governor.governPresentation(blocks, CognitiveLoadLevel.CRITICAL);
        List<MultimodalPresentationBlockDTO> active = partitioned.get(0);
        List<MultimodalPresentationBlockDTO> collapsed = partitioned.get(1);

        assertEquals(1, active.size(), "CRITICAL 极端状态下活跃块数量严格为 1");
        MultimodalPresentationBlockDTO soleBlock = active.get(0);
        assertEquals(PresentationBlockType.ACTION_BANNER, soleBlock.getBlockType(), "唯一活跃块必须为 ACTION_BANNER");
        assertFalse(soleBlock.isCollapsed(), "核心行动单不折叠");
        assertEquals(3, collapsed.size(), "其余所有次要分析与图表必须 100% 自动折叠");
    }

    @Test
    @DisplayName("契约 5: HIGH 负荷下复杂图表抑制与指标卡帕累托占优 (定理 2.1)")
    public void test05_HighLoadChartSuppressionParetoDominance() {
        List<MultimodalPresentationBlockDTO> blocks = Arrays.asList(
                composer.composeMetricCardBlock("m1", "当前延迟", "12", "ms", -5.0, 1),
                composer.composeEchartBlock("c1", "全链路耗时分布", "Bar", List.of("A", "B"), List.of(10.0, 20.0), 2),
                composer.composeMetricCardBlock("m2", "错误率", "0.01", "%", 0.0, 3),
                composer.composeMarkdownBlock("t1", "要点说明", "核心指标平稳", 4)
        );

        List<List<MultimodalPresentationBlockDTO>> partitioned = governor.governPresentation(blocks, CognitiveLoadLevel.HIGH);
        List<MultimodalPresentationBlockDTO> active = partitioned.get(0);
        List<MultimodalPresentationBlockDTO> collapsed = partitioned.get(1);

        assertEquals(2, active.size(), "HIGH 等级下活跃展示块上限为 2");
        // 复杂图表 c1 必须被折叠
        boolean chartInActive = active.stream().anyMatch(b -> b.getBlockType() == PresentationBlockType.ECHART_SPEC);
        assertFalse(chartInActive, "HIGH 负荷下复杂 ECharts 图表必须被抑制并折叠，帕累托占优给轻量指标卡");
        boolean chartInCollapsed = collapsed.stream().anyMatch(b -> b.getBlockType() == PresentationBlockType.ECHART_SPEC);
        assertTrue(chartInCollapsed, "图表应进入折叠列表");
    }

    @Test
    @DisplayName("契约 6: LOW 负荷下全量多模态与深度文本释放 (定理 2.1)")
    public void test06_LowLoadFullExplorationRichMultimodal() {
        List<MultimodalPresentationBlockDTO> blocks = Arrays.asList(
                composer.composeMetricCardBlock("m1", "TPS", "1500", "qps", 12.0, 1),
                composer.composeEchartBlock("c1", "QPS走势图", "Line", List.of("T1", "T2"), List.of(100.0, 150.0), 2),
                composer.composeMarkdownBlock("t1", "深度分析", "详细架构拓扑性能评估...", 3)
        );

        List<List<MultimodalPresentationBlockDTO>> partitioned = governor.governPresentation(blocks, CognitiveLoadLevel.LOW);
        List<MultimodalPresentationBlockDTO> active = partitioned.get(0);
        List<MultimodalPresentationBlockDTO> collapsed = partitioned.get(1);

        assertEquals(3, active.size(), "LOW 负荷下全部候选块均首屏活跃呈现");
        assertTrue(collapsed.isEmpty(), "LOW 负荷下无折叠项");
        assertTrue(active.stream().anyMatch(b -> b.getBlockType() == PresentationBlockType.ECHART_SPEC), "包含图表");
        assertTrue(active.stream().anyMatch(b -> b.getBlockType() == PresentationBlockType.TEXT_MARKDOWN), "包含文本");
    }

    @Test
    @DisplayName("契约 7: 多模态展示块属性与 ECharts 配置完整性")
    public void test07_DynamicMultimodalComposerCardIntegrity() {
        MultimodalPresentationBlockDTO metric = composer.composeMetricCardBlock("mc_1", "CPU利用率", "45.2", "%", 2.1, 1);
        assertNotNull(metric);
        assertEquals(PresentationBlockType.METRIC_CARD, metric.getBlockType());
        assertTrue(metric.getPayload().contains("45.2"));
        assertTrue(metric.getPayload().contains("monochromatic-titanium"));

        MultimodalPresentationBlockDTO chart = composer.composeEchartBlock("chart_1", "请求耗时", "Line", List.of("00:00", "01:00"), List.of(5.0, 6.2), 2);
        assertNotNull(chart);
        assertEquals(PresentationBlockType.ECHART_SPEC, chart.getBlockType());
        assertTrue(chart.getPayload().contains("xAxis"));
        assertTrue(chart.getPayload().contains("series"));

        MultimodalPresentationBlockDTO banner = composer.composeActionBannerBlock("act_1", "高危警报", "立即复位", "CRITICAL", 0);
        assertNotNull(banner);
        assertEquals(PresentationBlockType.ACTION_BANNER, banner.getBlockType());
        assertTrue(banner.getPayload().contains("requireConfirm"));
    }

    @Test
    @DisplayName("契约 8: 端到端流式呈现调度总控流程闭环")
    public void test08_PresentationStreamCoordinatorEndToEndWorkflow() {
        UserCognitiveStateDTO state = new UserCognitiveStateDTO(1500, 2000L, 0.45, 0.30, 0);
        List<MultimodalPresentationBlockDTO> blocks = Arrays.asList(
                composer.composeMetricCardBlock("b1", "内存", "65", "%", 1.0, 1),
                composer.composeMarkdownBlock("b2", "概要", "系统平稳运行中", 2),
                composer.composeEchartBlock("b3", "时延", "Bar", List.of("S1", "S2"), List.of(10.0, 15.0), 3)
        );

        PresentationPlanVO plan = coordinator.planPresentation("TASK-UX-001", state, blocks);

        assertNotNull(plan);
        assertEquals("TASK-UX-001", plan.getTaskId());
        assertNotNull(plan.getLoadLevel());
        assertFalse(plan.getActiveBlocks().isEmpty(), "活跃展示块不能为空");
        assertTrue(plan.getPlanningDurationMs() >= 0, "耗时非负");
    }

    @Test
    @DisplayName("契约 9: 异常输入下 Fail-Open 优雅降级")
    public void test09_FailOpenGracefulDegradationOnException() {
        // 传入 null 状态与 null 展示块
        PresentationPlanVO plan = coordinator.planPresentation("ERR-TASK-001", null, null);

        assertNotNull(plan, "Fail-Open 模式下必须输出有效降级 VO");
        assertEquals(CognitiveLoadLevel.MEDIUM, plan.getLoadLevel(), "异常默认平滑降级为 MEDIUM 平衡模式");
        assertNotNull(plan.getActiveBlocks());
        assertNotNull(plan.getCollapsedBlocks());
    }

    @Test
    @DisplayName("契约 10: 单次呈现评估与卡片组装耗时 MTTC <= 50ms")
    public void test10_PresentationEngineSubFiftyMillisLatency() {
        UserCognitiveStateDTO state = new UserCognitiveStateDTO(3000, 300L, 0.85, 0.80, 2);
        List<MultimodalPresentationBlockDTO> blocks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            blocks.add(composer.composeMetricCardBlock("card_" + i, "指标 " + i, "100", "ms", 1.0, i));
        }

        // 预热一次
        coordinator.planPresentation("WARMUP", state, blocks);

        // 运行 50 次压力测试
        int rounds = 50;
        long maxDuration = 0;
        for (int i = 0; i < rounds; i++) {
            long start = System.currentTimeMillis();
            PresentationPlanVO plan = coordinator.planPresentation("BENCH-" + i, state, blocks);
            long duration = System.currentTimeMillis() - start;
            if (duration > maxDuration) {
                maxDuration = duration;
            }
            assertTrue(plan.getPlanningDurationMs() <= 50, "单次规划内部耗时应 <= 50ms，实际=" + plan.getPlanningDurationMs());
        }

        assertTrue(maxDuration <= 50, "并发单次呈现规划最大耗时必须 <= 50ms，实际最大耗时=" + maxDuration + "ms");
    }
}
