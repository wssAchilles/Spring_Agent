package tech.qiantong.qknow.hermes.agent.debate.engine;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.agent.debate.dto.AgentRoleNicheType;
import tech.qiantong.qknow.hermes.agent.debate.engine.NashConfidenceWeightedJudge.ArgumentTurn;

import java.util.*;

/**
 * 回音室极化与交错循环自愈守卫 (DebateDeadlockSelfHealingGovernor)
 * <p>
 * 1. 毫秒级监控多智能体观点重复率与谄媚合谋度 (Sycophancy Score)；
 * 2. 在探测到伪共识合谋时，主动注入反事实魔鬼代言人视角 (Devil's Advocate Injection)；
 * 3. 在交错复读死循环时，触发 ε-Nash 熔断提前安全退出，阻断无界自旋与 Token 账单雪崩。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
public class DebateDeadlockSelfHealingGovernor {

    public static final double SYCOPHANCY_COLLUSION_THRESHOLD = 0.85;
    public static final double DEADLOCK_REPETITION_THRESHOLD = 0.90;

    /**
     * 辩论健康度与死锁合谋审计报告
     */
    public record HealthReport(
            boolean isCollusionDetected,
            boolean isDeadlockDetected,
            double sycophancyScore,
            double repetitionRate,
            String diagnosticMessage
    ) {}

    /**
     * 审计辩论健康度与死锁风险
     */
    public HealthReport inspectDebateHealth(List<ArgumentTurn> history, int currentRound) {
        if (history == null || history.size() < 3) {
            return new HealthReport(false, false, 0.0, 0.0, "初始轮次样本不足，健康度正常");
        }

        double sycophancy = calculateSycophancyScore(history, currentRound);
        double repetition = calculateRepetitionRate(history);

        boolean collusion = sycophancy >= SYCOPHANCY_COLLUSION_THRESHOLD;
        boolean deadlock = repetition >= DEADLOCK_REPETITION_THRESHOLD;

        String msg;
        if (deadlock) {
            msg = String.format("警告：探测到论据高度重复死锁 (重复率 %.2f >= %.2f)，触发熔断自愈！",
                    repetition, DEADLOCK_REPETITION_THRESHOLD);
        } else if (collusion) {
            msg = String.format("警告：探测到全员迎合伪共识合谋 (谄媚度 %.2f >= %.2f)，触发反事实对抗注入！",
                    sycophancy, SYCOPHANCY_COLLUSION_THRESHOLD);
        } else {
            msg = "健康度正常，多方对抗性探索活跃。";
        }

        return new HealthReport(collusion, deadlock, sycophancy, repetition, msg);
    }

    /**
     * 注入反事实魔鬼代言人 (CRITIC) 视角以打破合谋温室
     */
    public ArgumentTurn injectDevilsAdvocate(String debateId, String topic, int round, NashConfidenceWeightedJudge judge) {
        log.info("[SelfHealingGovernor] 正在为 debateId={} 注入反事实挑战边界与红蓝反例", debateId);
        String counterArgument = String.format("【魔鬼代言人反事实质询】针对决策议题 '%s'：假定当前全员认同的方案存在严重的未预期合规极端漏洞与供应链单点故障，"
                + "若对手方或外部监管恶意触发此边界，系统将面临何种毁灭性资损后果？请各方必须给出可量化的形式化防御边界，严禁泛泛妥协！", topic);

        double[] emb = judge != null ? judge.embedTextWithQwen(counterArgument) : new double[NashConfidenceWeightedJudge.EMBEDDING_DIM];

        return new ArgumentTurn(
                "agent-critic-devils-advocate",
                AgentRoleNicheType.CRITIC,
                round,
                counterArgument,
                emb,
                System.currentTimeMillis()
        );
    }

    /**
     * 计算谄媚趋同度 (Sycophancy Score)
     */
    private double calculateSycophancyScore(List<ArgumentTurn> history, int currentRound) {
        // 提取最新一轮所有发言文本
        List<String> latestTexts = history.stream()
                .filter(t -> t.roundIndex() == currentRound || (currentRound > 1 && t.roundIndex() == currentRound - 1))
                .map(ArgumentTurn::argumentText)
                .toList();

        if (latestTexts.size() < 2) {
            return 0.10;
        }

        // 检查关键词共识度（如“同意”、“赞同”、“附和”、“一致”、“采纳业务”等词频占比）
        long agreeCount = latestTexts.stream()
                .filter(t -> t.contains("同意") || t.contains("赞成") || t.contains("附和") || t.contains("妥协") || t.contains("支持"))
                .count();

        double agreementRatio = (double) agreeCount / latestTexts.size();
        if (agreementRatio >= 0.65) {
            return Math.min(1.0, 0.70 + agreementRatio * 0.25);
        }
        return 0.15;
    }

    /**
     * 计算论据重复率 (Repetition Rate)
     */
    private double calculateRepetitionRate(List<ArgumentTurn> history) {
        if (history.size() < 4) {
            return 0.05;
        }
        // 检查倒数两条发言的文本相似度
        ArgumentTurn last = history.get(history.size() - 1);
        ArgumentTurn secondLast = history.get(history.size() - 2);

        if (last.argumentText().equals(secondLast.argumentText())) {
            return 1.0;
        }

        // Jaccard 字符集重复度
        Set<Character> s1 = new HashSet<>();
        for (char c : last.argumentText().toCharArray()) s1.add(c);
        Set<Character> s2 = new HashSet<>();
        for (char c : secondLast.argumentText().toCharArray()) s2.add(c);

        Set<Character> intersection = new HashSet<>(s1);
        intersection.retainAll(s2);
        Set<Character> union = new HashSet<>(s1);
        union.addAll(s2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }
}
