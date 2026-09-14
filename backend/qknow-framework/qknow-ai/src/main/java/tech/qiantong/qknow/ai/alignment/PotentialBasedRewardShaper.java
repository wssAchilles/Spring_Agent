package tech.qiantong.qknow.ai.alignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ng 1999 势能保持奖励塑形器 (定理 1.1: 策略无偏保持不变量定理 Pi*_M' = Pi*_M)
 */
@Component
public class PotentialBasedRewardShaper {

    private static final Logger log = LoggerFactory.getLogger(PotentialBasedRewardShaper.class);

    /**
     * 智能体协作状态指标
     */
    public record PotentialState(
            double taskProgress,       // 任务完成度 [0.0, 1.0]
            double safetyAdherence,     // 安全遵循度 [0.0, 1.0]
            double latencyPenalty       // 延迟惩罚 [0.0, 1.0]
    ) {}

    /**
     * 计算状态势能函数 Phi(s)
     */
    public double calculatePotential(PotentialState state) {
        if (state == null) return 0.0;
        // 势能由真实业务进度与安全合规加权驱动，抑制高延迟与空转
        return 0.55 * state.taskProgress() + 0.45 * state.safetyAdherence() - 0.10 * state.latencyPenalty();
    }

    /**
     * 计算势能奖励塑形: F(s, a, s') = gamma * Phi(s') - Phi(s)
     * 总奖励 R' = baseReward + F
     */
    public double computeShapedReward(
            double baseReward,
            double currentPotential,
            double nextPotential,
            double gamma
    ) {
        double shapingReward = gamma * nextPotential - currentPotential;
        return baseReward + shapingReward;
    }

    /**
     * 验证闭环环路势能奖励恒等性 (定理 1.1: 环路累计塑形收益为 0，彻底杜绝刷分作弊)
     */
    public double calculateCycleCumulativeShapingReward(List<Double> cyclePotentials, double gamma) {
        if (cyclePotentials == null || cyclePotentials.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < cyclePotentials.size() - 1; i++) {
            double s_cur = cyclePotentials.get(i);
            double s_next = cyclePotentials.get(i + 1);
            sum += (gamma * s_next - s_cur);
        }
        // 当 gamma=1.0 时，环路收尾相同 (s_N = s_0)，伸缩求和必为 0
        return sum;
    }
}
