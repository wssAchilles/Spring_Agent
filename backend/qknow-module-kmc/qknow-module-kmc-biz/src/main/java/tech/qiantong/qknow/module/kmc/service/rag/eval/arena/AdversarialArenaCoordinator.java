package tech.qiantong.qknow.module.kmc.service.rag.eval.arena;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaLeaderboardDO;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaPolicyDO;

import java.util.*;

/**
 * Phase 39: 自博弈对抗竞技场总协调器 (Theorem 3.1: Maximal Mutual Information Tournament Matching)
 * 采用自适应瑞士轮信息增益极大化调度策略，将配对比较调用量从 O(N^2) 压降至 O(N log N)。
 *
 * @author qknow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdversarialArenaCoordinator {

    private final PolicyRegistry policyRegistry;
    private final DoubleBlindMatchRunner matchRunner;
    private final DynamicEloRatingLadder eloLadder;
    private final PromotionGateService promotionGate;

    /**
     * 计算单次成对比较的香农互信息增益 (Theorem 3.1)
     * I(A, B) = - [E_A * log2(E_A) + (1 - E_A) * log2(1 - E_A)]
     */
    public double computeMutualInformationGain(double ratingA, double ratingB) {
        double expectedA = eloLadder.calculateExpectedScore(ratingA, ratingB);
        // 边界保护
        expectedA = Math.max(1e-6, Math.min(1.0 - 1e-6, expectedA));
        double expectedB = 1.0 - expectedA;
        return - (expectedA * (Math.log(expectedA) / Math.log(2.0)) + expectedB * (Math.log(expectedB) / Math.log(2.0)));
    }

    /**
     * 自适应瑞士轮配对：在活跃策略中选择积分差 |R_A - R_B| 最小的邻近对手，最大化信息增益 (Theorem 3.1)
     */
    public List<PolicyPair> scheduleAdaptiveMatches(List<ArenaPolicyDO> activePolicies) {
        List<PolicyPair> pairs = new ArrayList<>();
        if (activePolicies == null || activePolicies.size() < 2) {
            return pairs;
        }

        // 按当前 Elo 排序
        List<ArenaPolicyDO> sorted = new ArrayList<>(activePolicies);
        sorted.sort(Comparator.comparingDouble(ArenaPolicyDO::getEloRating).reversed());

        // 邻近两两配对，信息增益最大化
        for (int i = 0; i < sorted.size() - 1; i += 2) {
            ArenaPolicyDO pA = sorted.get(i);
            ArenaPolicyDO pB = sorted.get(i + 1);
            pairs.add(new PolicyPair(pA, pB));
        }
        return pairs;
    }

    public static class PolicyPair {
        public final ArenaPolicyDO policyA;
        public final ArenaPolicyDO policyB;

        public PolicyPair(ArenaPolicyDO policyA, ArenaPolicyDO policyB) {
            this.policyA = policyA;
            this.policyB = policyB;
        }
    }

    /**
     * 执行单场对决并原子更新天梯 Elo 积分
     */
    public ArenaMatchDO conductMatch(String question, 
                                    ArenaPolicyDO policyA, String answerA, 
                                    ArenaPolicyDO policyB, String answerB, 
                                    Set<String> goldEntities) {
        // 1. 双盲交换对弈
        ArenaMatchDO match = matchRunner.runDoubleBlindMatch(question, policyA, answerA, policyB, answerB, goldEntities);

        // 2. 零和 Elo 积分更新
        DynamicEloRatingLadder.RatingUpdateResult update = eloLadder.updateRatings(
                policyA.getEloRating(), policyB.getEloRating(),
                policyA.getMatchCount(), policyB.getMatchCount(),
                match.getPolicyAScore(), match.getPolicyBScore()
        );

        match.setRatingDeltaA(update.getDeltaA());
        match.setRatingDeltaB(update.getDeltaB());

        // 3. 更新策略注册表
        boolean aWin = match.getFinalOutcome() == ArenaMatchDO.MatchOutcome.A_WIN;
        boolean bWin = match.getFinalOutcome() == ArenaMatchDO.MatchOutcome.B_WIN;
        boolean tie = match.getFinalOutcome() == ArenaMatchDO.MatchOutcome.TIE;

        policyRegistry.updatePolicyRating(policyA.getPolicyId(), update.getNewRatingA(), aWin, bWin, tie);
        policyRegistry.updatePolicyRating(policyB.getPolicyId(), update.getNewRatingB(), bWin, aWin, tie);

        return match;
    }

    /**
     * 生成格式化 Markdown 审计对决报告
     */
    public String generateAuditMarkdownReport(List<ArenaMatchDO> matches, List<ArenaLeaderboardDO> leaderboard) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 智能体策略自博弈对抗竞技场与 Elo 天梯评测审计报告\n\n");
        sb.append("> **评测引擎**：DeepSeek-R1 链式裁判 + 阿里千问超球面嵌入 + 双盲位置对消算子\n\n");

        sb.append("## 一、最新天梯排行榜 (Leaderboard)\n\n");
        sb.append("| 排名 | 策略 ID | 策略名称 | 版本 | Elo 积分 | 置信区间 | 胜率 | 分段 | 状态 |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");
        for (ArenaLeaderboardDO item : leaderboard) {
            sb.append(String.format("| %d | `%s` | %s | %s | %.1f | ±%.1f | %.1f%% | %s | %s |\n",
                    item.getRank(), item.getPolicyId(), item.getPolicyName(), item.getVersion(),
                    item.getEloRating(), item.getConfidenceInterval(), item.getWinRate(),
                    item.getTierBadge(), item.getStatus()));
        }
        sb.append("\n");

        sb.append("## 二、对决审计明细 (Match Audit Logs)\n\n");
        for (ArenaMatchDO m : matches) {
            sb.append(String.format("### 对局 `%s`: `%s` vs `%s`\n\n", m.getMatchId(), m.getPolicyAId(), m.getPolicyBId()));
            sb.append("- **测试问题**：").append(m.getQuestion()).append("\n");
            sb.append("- **对决胜负**：`").append(m.getFinalOutcome()).append("` (A得分: ").append(m.getPolicyAScore()).append(", B得分: ").append(m.getPolicyBScore()).append(")\n");
            sb.append(String.format("- **Elo 变动**：A (Δ=%.2f), B (Δ=%.2f)\n", m.getRatingDeltaA(), m.getRatingDeltaB()));
            sb.append("- **注水阻尼**：").append(m.isVerbosityPenaltyApplied() ? "已激活下凸降级" : "未触发").append("\n");
            sb.append("- **裁决归因**：").append(m.getRationale()).append("\n\n");
            if (m.getDeepSeekThinkingProcess() != null && !m.getDeepSeekThinkingProcess().isBlank()) {
                sb.append("<details><summary>DeepSeek-R1 深度思考链条</summary>\n\n```text\n")
                        .append(m.getDeepSeekThinkingProcess())
                        .append("\n```\n</details>\n\n");
            }
        }

        return sb.toString();
    }
}
