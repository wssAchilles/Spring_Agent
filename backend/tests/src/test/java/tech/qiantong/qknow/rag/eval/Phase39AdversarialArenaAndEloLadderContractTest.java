package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.*;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaLeaderboardDO;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO.JudgementResult;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO.MatchOutcome;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaPolicyDO;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 39: 智能体策略自博弈对抗竞技场与自动化 Elo 评测天梯 10 项严苛契约测试
 *
 * 核心指标验证：
 * 1. BTL 理论胜率与期望积分单调对称性契约 (Kolmogorov 公理)；
 * 2. 动态自适应 K-factor 退火衰减与严格零和积分守恒契约 (Theorem 1.1)；
 * 3. 双盲匿名对局打乱与策略元信息绝对隔离契约；
 * 4. 对偶双盲交换对称打分与一阶位置偏差 100% 代数消除契约 (Theorem 2.1)；
 * 5. 阶梯式长度偏差校准器与注水作弊惩罚契约 (Theorem 2.2)；
 * 6. DeepSeek-R1 链式深度思考 (<think>) 与结构化 JSON 裁判契约；
 * 7. 自适应信息增益极大化瑞士轮匹配调度契约 (Theorem 3.1)；
 * 8. 动态 Elo 天梯排行榜排序稳定性与防震荡收敛契约；
 * 9. 策略自动晋级双重门禁与自进化闭环流转契约；
 * 10. 结构化竞技场对决审计报告 Markdown 导出契约。
 *
 * @author qknow
 */
public class Phase39AdversarialArenaAndEloLadderContractTest {

    private PolicyRegistry policyRegistry;
    private VerbosityRegularizer verbosityRegularizer;
    private DeepSeekR1JudgeEngine judgeEngine;
    private DynamicEloRatingLadder eloLadder;
    private DoubleBlindMatchRunner matchRunner;
    private PromotionGateService promotionGate;
    private AdversarialArenaCoordinator arenaCoordinator;

    @BeforeEach
    void setUp() {
        policyRegistry = new PolicyRegistry();
        verbosityRegularizer = new VerbosityRegularizer();
        judgeEngine = new DeepSeekR1JudgeEngine();
        eloLadder = new DynamicEloRatingLadder();
        matchRunner = new DoubleBlindMatchRunner(judgeEngine, verbosityRegularizer);
        promotionGate = new PromotionGateService();
        arenaCoordinator = new AdversarialArenaCoordinator(policyRegistry, matchRunner, eloLadder, promotionGate);
    }

    @Test
    @DisplayName("Contract 01: BTL 理论胜率与期望积分单调对称性契约")
    void contract01_btlExpectedWinRateMonotonicityAndSymmetry() {
        // 1. 同分对弈期望胜率严格为 0.50
        double eEqual = eloLadder.calculateExpectedScore(1200.0, 1200.0);
        assertEquals(0.50, eEqual, 1e-6, "同积分对决期望胜率必须严格为 0.50");

        // 2. 柯尔莫哥洛夫公理: E_A + E_B == 1.0
        double eA = eloLadder.calculateExpectedScore(1350.0, 1150.0);
        double eB = eloLadder.calculateExpectedScore(1150.0, 1350.0);
        assertEquals(1.0, eA + eB, 1e-6, "对弈双方期望胜率和必须严格为 1.0");

        // 3. 400 分优势期望胜率约为 10/11 ≈ 0.90909
        double e400 = eloLadder.calculateExpectedScore(1600.0, 1200.0);
        assertEquals(1.0 / (1.0 + 0.1), e400, 1e-4, "400分差距下优势方期望胜率约 90.9%");

        // 4. 单调性检验
        double e100 = eloLadder.calculateExpectedScore(1300.0, 1200.0);
        double e200 = eloLadder.calculateExpectedScore(1400.0, 1200.0);
        assertTrue(e200 > e100 && e100 > eEqual, "积分差越大期望胜率必须严格单调递增");
    }

    @Test
    @DisplayName("Contract 02: 动态自适应 K-factor 衰减与零和积分守恒契约 (Theorem 1.1)")
    void contract02_adaptiveKFactorAnnealingAndZeroSumConservation() {
        // 1. K-factor 随对战轮次单调退火收敛至 K_min
        double k0 = eloLadder.computeAdaptiveK(0);
        double k5 = eloLadder.computeAdaptiveK(5);
        double k20 = eloLadder.computeAdaptiveK(20);
        double k100 = eloLadder.computeAdaptiveK(100);

        assertEquals(DynamicEloRatingLadder.BASE_K_FACTOR, k0, 1e-6, "初始 K 因子为 32.0");
        assertTrue(k5 < k0, "5场后 K 因子退火下降");
        assertTrue(k20 < k5, "20场后 K 因子进一步退火");
        assertEquals(DynamicEloRatingLadder.MIN_K_FACTOR, k100, 1e-6, "高场次后 K 因子收敛于下限 12.0");

        // 2. 零和积分守恒检验: Delta R_A + Delta R_B 严格为 0
        DynamicEloRatingLadder.RatingUpdateResult updateWin = eloLadder.updateRatings(1250.0, 1180.0, 2, 8, 1.0, 0.0);
        assertEquals(0.0, updateWin.getDeltaA() + updateWin.getDeltaB(), 1e-9, "胜负对局必须严格满足零和守恒 Delta A + Delta B = 0");

        DynamicEloRatingLadder.RatingUpdateResult updateTie = eloLadder.updateRatings(1300.0, 1100.0, 10, 10, 0.5, 0.5);
        assertEquals(0.0, updateTie.getDeltaA() + updateTie.getDeltaB(), 1e-9, "平局对局同样必须严格零和守恒");
        assertTrue(updateTie.getDeltaA() < 0, "高分方被低分方逼平必须扣分");
        assertTrue(updateTie.getDeltaB() > 0, "低分方逼平高分方必须加分");
    }

    @Test
    @DisplayName("Contract 03: 双盲匿名对局打乱与策略元信息绝对隔离契约")
    void contract03_doubleBlindAnonymizationAndMetadataIsolation() {
        ArenaPolicyDO pA = ArenaPolicyDO.builder()
                .policyId("POL_INTERNAL_DEEPSEEK_V3_001")
                .name("内部核心生产基线策略")
                .version("v3.2.0")
                .build();

        String anonA = matchRunner.anonymizePolicy(pA, "Model_A");
        assertNotNull(anonA);
        assertTrue(anonA.startsWith("Model_A_"), "匿名代号必须带有规整前缀");
        assertFalse(anonA.contains("DEEPSEEK"), "匿名代号严禁泄露底层模型真实名称");
        assertFalse(anonA.contains("内部核心"), "匿名代号严禁包含元数据名称");

        // 相同策略哈希代号具备稳定可重现性
        String anonA2 = matchRunner.anonymizePolicy(pA, "Model_A");
        assertEquals(anonA, anonA2, "相同策略元信息哈希化结果可重现");
    }

    @Test
    @DisplayName("Contract 04: 对偶双盲交换对称打分与一阶位置偏差 100% 消除契约 (Theorem 2.1)")
    void contract04_dualBlindExchangeSymmetricEvaluationCancelsPositionBias() {
        // 构造两个实力/答案完全一致的策略 (空探针对称实验)
        ArenaPolicyDO pA = ArenaPolicyDO.builder().policyId("POL_A").version("1.0").build();
        ArenaPolicyDO pB = ArenaPolicyDO.builder().policyId("POL_B").version("1.0").build();
        String ans = "年假未休完经由部门主管审批后可按照日工资收入的 300% 折算经济补偿。";

        // 模拟一个具有严重位置偏好（100% 偏爱位置 1）的不公正单向裁判
        DoubleBlindMatchRunner biasedRunner = new DoubleBlindMatchRunner(judgeEngine, verbosityRegularizer) {
            @Override
            protected String executeJudgeCall(String prompt, String ans1, String ans2) {
                // 不管内容如何，单向裁判总是盲目判位置 1 获胜 ("winner": "A")
                return "```json\n{\"winner\": \"A\", \"confidence\": 0.99, \"rationale\": \"偏爱位置1\"}\n```";
            }
        };

        // 执行对偶双盲交换
        ArenaMatchDO match = biasedRunner.runDoubleBlindMatch("如何补偿年假", pA, ans, pB, ans, Collections.emptySet());

        // 验证定理 2.1：经过正反双向对偶交换，位置偏见被数学对消，最终胜负判定为平局 TIE，双方得分均为 0.50
        assertEquals(MatchOutcome.TIE, match.getFinalOutcome(), "对等策略下，对偶双盲必须对消裁判的一阶位置偏好，判定平局");
        assertEquals(0.50, match.getPolicyAScore(), 1e-6, "A 得分必须严格为 0.50");
        assertEquals(0.50, match.getPolicyBScore(), 1e-6, "B 得分必须严格为 0.50");
    }

    @Test
    @DisplayName("Contract 05: 阶梯式长度偏差校准器与注水作弊惩罚契约 (Theorem 2.2)")
    void contract05_verbosityRegularizerPenalizesFluffWithoutInfoGain() {
        String baseAnswer = "年假补偿按日工资 300% 发放。";
        // 候选策略通过单纯重复、无意义客套话使长度暴增 2.5 倍 (注水作弊)
        String fluffCandidateAnswer = "年假补偿按日工资 300% 发放。尊敬的同事您好！非常荣幸能够为您解答关于年休假福利政策的相关疑问，公司一直非常注重员工身心健康与劳动权益保障，关于年休假的具体申报与核算流程请务必仔细阅读上述说明并在钉钉考勤系统中提交复核审批，祝您工作顺利生活愉快！";

        Set<String> goldEntities = Set.of("年假", "补偿", "300%");

        double ratio = verbosityRegularizer.calculateLengthRatio(fluffCandidateAnswer, baseAnswer);
        assertTrue(ratio > 1.25, "注水回答长度比显著超标 (> 1.25)");

        // 初始若判定注水策略胜出
        JudgementResult initialWin = JudgementResult.builder()
                .winner("A")
                .confidence(0.90)
                .rationale("回答详实丰富")
                .build();

        JudgementResult calibrated = verbosityRegularizer.calibrateJudgement(initialWin, fluffCandidateAnswer, baseAnswer, goldEntities);

        // 验证：触发下凸长度惩罚，胜局强制降级为平局 TIE，注水胜率收益截断为 0
        assertEquals("TIE", calibrated.getWinner(), "空洞注水胜局必须被强制降级为平局");
        assertTrue(calibrated.isPenaltyApplied(), "必须标记已应用注水阻尼惩罚");
        assertTrue(calibrated.getConfidence() < 0.90, "置信度必须受到对数惩罚衰减");
    }

    @Test
    @DisplayName("Contract 06: DeepSeek-R1 链式深度思考与结构化 JSON 裁判契约")
    void contract06_deepSeekR1JudgeChainOfThoughtAndStructuredJson() {
        String r1SampleResponse = "<think>\n" +
                "首先审视用户问题是查询差旅住宿补贴标准。\n" +
                "候选 1 给出了具体的一线城市 500 元、二线城市 350 元的精确分级标准。\n" +
                "候选 2 仅给出了模糊的实报实销描述，缺乏事实准确性。\n" +
                "因此候选 1 显著胜出，置信度高。\n" +
                "</think>\n" +
                "```json\n" +
                "{\n" +
                "  \"winner\": \"A\",\n" +
                "  \"confidence\": 0.92,\n" +
                "  \"rationale\": \"候选 1 提供了精确的城市分级补贴金额，事实完备度显著高于候选 2\"\n" +
                "}\n" +
                "```";

        JudgementResult parsed = judgeEngine.parseJudgeResponse(r1SampleResponse);
        assertEquals("A", parsed.getWinner(), "准确提取胜者 A");
        assertEquals(0.92, parsed.getConfidence(), 1e-4, "准确提取置信度 0.92");
        assertTrue(parsed.getRationale().contains("精确的城市分级"), "准确提取判据");
        assertTrue(parsed.getThinking().contains("审视用户问题是查询差旅住宿补贴"), "完整剥离提取 <think> 思考链内容");
    }

    @Test
    @DisplayName("Contract 07: 自适应信息增益极大化锦标赛匹配调度契约 (Theorem 3.1)")
    void contract07_adaptiveMutualInformationMaximizationTournamentMatching() {
        // 1. 信息增益在分差为 0 时达到理论上界 1.0 bit
        double infoGainMax = arenaCoordinator.computeMutualInformationGain(1250.0, 1250.0);
        assertEquals(1.0, infoGainMax, 1e-4, "实力对等对局香农互信息增益达到最大值 1.0 bit");

        // 积分差距扩大时信息增益衰减
        double infoGainLarge = arenaCoordinator.computeMutualInformationGain(1600.0, 1200.0);
        assertTrue(infoGainLarge < 0.60, "积分悬殊对局信息增益大幅衰减 (< 0.60 bit)");

        // 2. 调度器优先为邻近战力策略配对
        ArenaPolicyDO p1 = ArenaPolicyDO.builder().policyId("P1").eloRating(1450.0).build();
        ArenaPolicyDO p2 = ArenaPolicyDO.builder().policyId("P2").eloRating(1440.0).build();
        ArenaPolicyDO p3 = ArenaPolicyDO.builder().policyId("P3").eloRating(1200.0).build();
        ArenaPolicyDO p4 = ArenaPolicyDO.builder().policyId("P4").eloRating(1190.0).build();

        List<AdversarialArenaCoordinator.PolicyPair> pairs = arenaCoordinator.scheduleAdaptiveMatches(List.of(p1, p2, p3, p4));
        assertEquals(2, pairs.size(), "4个策略配出 2 对对局");
        assertEquals("P1", pairs.get(0).policyA.getPolicyId());
        assertEquals("P2", pairs.get(0).policyB.getPolicyId(), "高分区 P1 与 P2 配对");
        assertEquals("P3", pairs.get(1).policyA.getPolicyId());
        assertEquals("P4", pairs.get(1).policyB.getPolicyId(), "低分区 P3 与 P4 配对");
    }

    @Test
    @DisplayName("Contract 08: 动态 Elo 天梯排行榜排序稳定性与防震荡契约")
    void contract08_dynamicEloLeaderboardSortingStabilityAndConvergence() {
        ArenaPolicyDO strong = ArenaPolicyDO.builder().policyId("P_STRONG").name("高阶推理策略").version("2.0").eloRating(1300.0).build();
        ArenaPolicyDO medium = ArenaPolicyDO.builder().policyId("P_MEDIUM").name("基线问答策略").version("1.0").eloRating(1200.0).build();
        ArenaPolicyDO weak = ArenaPolicyDO.builder().policyId("P_WEAK").name("简易问答策略").version("0.5").eloRating(1100.0).build();

        policyRegistry.registerPolicy(strong);
        policyRegistry.registerPolicy(medium);
        policyRegistry.registerPolicy(weak);

        // 连续进行若干场实力符合预期的对弈
        for (int i = 0; i < 5; i++) {
            // strong vs medium, strong 胜
            DynamicEloRatingLadder.RatingUpdateResult up1 = eloLadder.updateRatings(
                    strong.getEloRating(), medium.getEloRating(), strong.getMatchCount(), medium.getMatchCount(), 1.0, 0.0);
            policyRegistry.updatePolicyRating(strong.getPolicyId(), up1.getNewRatingA(), true, false, false);
            policyRegistry.updatePolicyRating(medium.getPolicyId(), up1.getNewRatingB(), false, true, false);

            // medium vs weak, medium 胜
            DynamicEloRatingLadder.RatingUpdateResult up2 = eloLadder.updateRatings(
                    medium.getEloRating(), weak.getEloRating(), medium.getMatchCount(), weak.getMatchCount(), 1.0, 0.0);
            policyRegistry.updatePolicyRating(medium.getPolicyId(), up2.getNewRatingA(), true, false, false);
            policyRegistry.updatePolicyRating(weak.getPolicyId(), up2.getNewRatingB(), false, true, false);
        }

        List<ArenaLeaderboardDO> leaderboard = eloLadder.generateLeaderboard(policyRegistry.listPolicies());
        assertEquals(3, leaderboard.size());
        assertEquals("P_STRONG", leaderboard.get(0).getPolicyId(), "实力最强者高居榜首");
        assertEquals("P_MEDIUM", leaderboard.get(1).getPolicyId(), "中等策略居中");
        assertEquals("P_WEAK", leaderboard.get(2).getPolicyId(), "较弱者位列末尾");
        assertTrue(leaderboard.get(0).getEloRating() > leaderboard.get(1).getEloRating());
        assertTrue(leaderboard.get(1).getEloRating() > leaderboard.get(2).getEloRating());
    }

    @Test
    @DisplayName("Contract 09: 策略自动晋级双重门禁与自进化闭环流转契约")
    void contract09_promotionGateDualThresholdAndEvolutionTransition() {
        ArenaPolicyDO baseline = ArenaPolicyDO.builder()
                .policyId("BASE_PROMPT")
                .eloRating(1200.0)
                .build();

        // 1. 场次不足 5 场，保持 CANDIDATE
        ArenaPolicyDO cand1 = ArenaPolicyDO.builder()
                .policyId("CAND_1")
                .eloRating(1250.0)
                .matchCount(3)
                .winCount(3)
                .status(ArenaPolicyDO.PolicyStatus.CANDIDATE)
                .build();
        PromotionGateService.PromotionDecision dec1 = promotionGate.evaluatePromotion(cand1, baseline);
        assertFalse(dec1.isPromoted(), "场次不足 5 场不得提前晋级");
        assertEquals(ArenaPolicyDO.PolicyStatus.CANDIDATE, dec1.getFinalStatus());

        // 2. 胜率 >= 60% 且 Delta Elo >= +30 且场次 >= 5 -> 成功晋级 PROMOTED
        ArenaPolicyDO cand2 = ArenaPolicyDO.builder()
                .policyId("CAND_2")
                .eloRating(1245.0) // Delta = +45 >= +30
                .matchCount(6)
                .winCount(4)
                .tieCount(1) // score rate = (4 + 0.5)/6 = 75% >= 60%
                .status(ArenaPolicyDO.PolicyStatus.CANDIDATE)
                .build();
        PromotionGateService.PromotionDecision dec2 = promotionGate.evaluatePromotion(cand2, baseline);
        assertTrue(dec2.isPromoted(), "满足双重门禁指标，判定晋级");
        assertEquals(ArenaPolicyDO.PolicyStatus.PROMOTED, dec2.getFinalStatus());
        promotionGate.applyPromotionDecision(cand2, dec2);
        assertEquals(ArenaPolicyDO.PolicyStatus.PROMOTED, cand2.getStatus());

        // 3. 场次满足但胜率未达标 -> 淘汰 REJECTED
        ArenaPolicyDO cand3 = ArenaPolicyDO.builder()
                .policyId("CAND_3")
                .eloRating(1210.0) // Delta = +10 < +30
                .matchCount(6)
                .winCount(2) // 33% < 60%
                .status(ArenaPolicyDO.PolicyStatus.CANDIDATE)
                .build();
        PromotionGateService.PromotionDecision dec3 = promotionGate.evaluatePromotion(cand3, baseline);
        assertFalse(dec3.isPromoted());
        assertEquals(ArenaPolicyDO.PolicyStatus.REJECTED, dec3.getFinalStatus());
    }

    @Test
    @DisplayName("Contract 10: 结构化竞技场对决审计报告导出契约")
    void contract10_structuredArenaAuditReportGeneration() {
        ArenaPolicyDO pA = ArenaPolicyDO.builder().policyId("POL_DS_V3").name("DeepSeek V3 Baseline").version("1.0").eloRating(1220.0).matchCount(4).winCount(3).build();
        ArenaPolicyDO pB = ArenaPolicyDO.builder().policyId("POL_EVOLVED").name("Evolved Strategy").version("1.1").eloRating(1260.0).matchCount(5).winCount(4).build();

        policyRegistry.registerPolicy(pA);
        policyRegistry.registerPolicy(pB);

        ArenaMatchDO match = ArenaMatchDO.builder()
                .matchId("MATCH_TEST_888")
                .question("知识库切片最大重叠长度推荐值是多少？")
                .policyAId(pA.getPolicyId())
                .policyBId(pB.getPolicyId())
                .finalOutcome(MatchOutcome.B_WIN)
                .policyAScore(0.0)
                .policyBScore(1.0)
                .ratingDeltaA(-12.5)
                .ratingDeltaB(12.5)
                .deepSeekThinkingProcess("R1 链式分析：策略 B 提供了 10%~20% 动态重叠窗口与保真度计算，更优。")
                .rationale("策略 B 逻辑更严密")
                .verbosityPenaltyApplied(false)
                .build();

        List<ArenaLeaderboardDO> leaderboard = eloLadder.generateLeaderboard(List.of(pA, pB));
        String report = arenaCoordinator.generateAuditMarkdownReport(List.of(match), leaderboard);

        assertNotNull(report);
        assertTrue(report.contains("# 智能体策略自博弈对抗竞技场与 Elo 天梯评测审计报告"), "报告包含一级大标题");
        assertTrue(report.contains("POL_EVOLVED"), "包含晋级策略信息");
        assertTrue(report.contains("MATCH_TEST_888"), "包含对局详细审计 ID");
        assertTrue(report.contains("R1 链式分析"), "包含 DeepSeek-R1 思考链细节");
    }
}
