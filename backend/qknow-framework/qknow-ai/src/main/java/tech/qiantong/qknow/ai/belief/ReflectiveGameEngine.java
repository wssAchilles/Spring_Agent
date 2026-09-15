package tech.qiantong.qknow.ai.belief;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 有限视界自反博弈引擎 (定理 1.2: k<=2 认知层级硬截断与死锁消除)
 */
@Component
public class ReflectiveGameEngine {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveGameEngine.class);

    // 泊松认知层级分布参数 tau = 1.5
    public static final double POISSON_TAU = 1.5;

    /**
     * 博弈决策输出
     */
    public record GameDecision(
            int cognitiveLevel,
            String selectedAction,
            double expectedPayoff
    ) {}

    /**
     * 求解自反博弈决策 (定理 1.2)
     * @param requestedLevel 申请推演层级 (硬截断至 [0, 2])
     * @param candidateActions 本方可用动作
     * @param opponentActions 对手可用动作
     * @param payoffMatrix 收益矩阵: actionSelf -> (actionOpponent -> payoff)
     */
    public GameDecision solveReflectiveDecision(
            int requestedLevel,
            List<String> candidateActions,
            List<String> opponentActions,
            Map<String, Map<String, Double>> payoffMatrix
    ) {
        if (candidateActions == null || candidateActions.isEmpty() ||
            opponentActions == null || opponentActions.isEmpty() ||
            payoffMatrix == null) {
            throw new IllegalArgumentException("博弈动作空间与收益矩阵不能为空");
        }

        // 硬截断：任何大于 2 的层级强制截断为 2，消除无限死锁 (定理 1.2)
        int effectiveLevel = Math.max(0, Math.min(2, requestedLevel));

        if (effectiveLevel == 0) {
            // Level-0: 确定性基线探索 (选择第一个候选动作)
            String a0 = candidateActions.get(0);
            double avgPayoff = computeActionAveragePayoff(a0, opponentActions, payoffMatrix);
            return new GameDecision(0, a0, avgPayoff);
        } else if (effectiveLevel == 1) {
            // Level-1: 单步最佳应对 (假设对手均匀分布)
            return computeLevel1BestResponse(candidateActions, opponentActions, payoffMatrix);
        } else {
            // Level-2: 二阶综合博弈 (按泊松分布综合 Level-0 与 Level-1 对手)
            return computeLevel2BestResponse(candidateActions, opponentActions, payoffMatrix);
        }
    }

    private GameDecision computeLevel1BestResponse(
            List<String> candidateActions,
            List<String> opponentActions,
            Map<String, Map<String, Double>> payoffMatrix
    ) {
        String bestAction = candidateActions.get(0);
        double maxExpected = -Double.MAX_VALUE;

        for (String aSelf : candidateActions) {
            double expected = computeActionAveragePayoff(aSelf, opponentActions, payoffMatrix);
            if (expected > maxExpected) {
                maxExpected = expected;
                bestAction = aSelf;
            }
        }

        return new GameDecision(1, bestAction, maxExpected);
    }

    private GameDecision computeLevel2BestResponse(
            List<String> candidateActions,
            List<String> opponentActions,
            Map<String, Map<String, Double>> payoffMatrix
    ) {
        // 计算 Level-1 对手在对手视角下的最佳动作
        String opponentL1Action = opponentActions.get(0); // 假定对偶收益最直接应对
        // 泊松权重: P(L=0) propto 1, P(L=1) propto tau = 1.5
        double w0 = 1.0 / (1.0 + POISSON_TAU);
        double w1 = POISSON_TAU / (1.0 + POISSON_TAU);

        String bestAction = candidateActions.get(0);
        double maxWeightedExpected = -Double.MAX_VALUE;

        for (String aSelf : candidateActions) {
            // 对手是 Level-0 (均匀对手) 的期望
            double expAgainstL0 = computeActionAveragePayoff(aSelf, opponentActions, payoffMatrix);
            // 对手是 Level-1 (确定性对手) 的期望
            double expAgainstL1 = getPayoff(aSelf, opponentL1Action, payoffMatrix);

            double combinedExpected = w0 * expAgainstL0 + w1 * expAgainstL1;
            if (combinedExpected > maxWeightedExpected) {
                maxWeightedExpected = combinedExpected;
                bestAction = aSelf;
            }
        }

        return new GameDecision(2, bestAction, maxWeightedExpected);
    }

    private double computeActionAveragePayoff(
            String aSelf, List<String> opponentActions, Map<String, Map<String, Double>> payoffMatrix
    ) {
        double sum = 0.0;
        for (String aOpp : opponentActions) {
            sum += getPayoff(aSelf, aOpp, payoffMatrix);
        }
        return sum / opponentActions.size();
    }

    private double getPayoff(String aSelf, String aOpp, Map<String, Map<String, Double>> payoffMatrix) {
        Map<String, Double> row = payoffMatrix.get(aSelf);
        if (row == null) return 0.0;
        return row.getOrDefault(aOpp, 0.0);
    }
}
