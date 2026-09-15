package tech.qiantong.qknow.ai.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自适应探索调度器 (基于汤普森采样 Thompson Sampling 与动态指数退火衰减)
 */
@Component
public class AdaptiveExplorationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveExplorationScheduler.class);

    // 动作臂先验状态
    public static class ActionArmStat {
        private double alpha = 1.0; // 成功伪计数先验
        private double beta = 1.0;  // 失败伪计数先验
        private long pullCount = 0;

        public synchronized void update(double reward) {
            pullCount++;
            if (reward >= 0.5) {
                alpha += reward;
            } else {
                beta += (1.0 - reward);
            }
        }

        public synchronized double sampleScore(Random random) {
            // Beta 分布采样近似 (基于均匀随机变量与伪反变换)
            double mean = alpha / (alpha + beta);
            double variance = (alpha * beta) / (Math.pow(alpha + beta, 2) * (alpha + beta + 1));
            double stdDev = Math.sqrt(variance);
            double gaussianSample = mean + random.nextGaussian() * stdDev;
            return Math.max(0.001, Math.min(0.999, gaussianSample));
        }

        public synchronized double getExpectedValue() {
            return alpha / (alpha + beta);
        }

        public long getPullCount() {
            return pullCount;
        }
    }

    private final ConcurrentHashMap<String, ActionArmStat> armStats = new ConcurrentHashMap<>();
    private final AtomicLong totalSteps = new AtomicLong(0);

    // 探索参数：受控探索上限严格 <= 10%，最小探索率 1%
    private static final double INITIAL_EPSILON = 0.10;
    private static final double MIN_EPSILON = 0.01;
    private static final double DECAY_RATE = 0.005;

    private final Random random = new Random(42);

    /**
     * 自适应动作选择 (平衡汤普森利用与退火探索)
     */
    public String selectAction(List<String> candidateActions) {
        if (candidateActions == null || candidateActions.isEmpty()) {
            throw new IllegalArgumentException("候选动作列表不能为空");
        }
        long step = totalSteps.incrementAndGet();
        double currentEpsilon = getCurrentExplorationRate();

        // 受控探索决策
        if (random.nextDouble() < currentEpsilon) {
            // 汤普森后验采样探索
            String bestExplorationAction = candidateActions.get(0);
            double maxSampleScore = -1.0;
            for (String action : candidateActions) {
                ActionArmStat stat = armStats.computeIfAbsent(action, k -> new ActionArmStat());
                double score = stat.sampleScore(random);
                if (score > maxSampleScore) {
                    maxSampleScore = score;
                    bestExplorationAction = action;
                }
            }
            log.debug("自适应探索选择动作: {}, 步骤: {}, 探索率: {}", bestExplorationAction, step, currentEpsilon);
            return bestExplorationAction;
        } else {
            // 贪心确定性利用 (选择后验期望最大者)
            String bestAction = candidateActions.get(0);
            double maxExpected = -1.0;
            for (String action : candidateActions) {
                ActionArmStat stat = armStats.computeIfAbsent(action, k -> new ActionArmStat());
                double expected = stat.getExpectedValue();
                if (expected > maxExpected) {
                    maxExpected = expected;
                    bestAction = action;
                }
            }
            return bestAction;
        }
    }

    /**
     * 观测奖励反馈更新
     */
    public void updateFeedback(String actionId, double reward) {
        if (actionId != null) {
            ActionArmStat stat = armStats.computeIfAbsent(actionId, k -> new ActionArmStat());
            stat.update(Math.max(0.0, Math.min(1.0, reward)));
        }
    }

    /**
     * 计算当前自适应探索率 (指数退火)
     */
    public double getCurrentExplorationRate() {
        long t = totalSteps.get();
        return Math.max(MIN_EPSILON, INITIAL_EPSILON * Math.exp(-DECAY_RATE * t));
    }

    public long getTotalSteps() {
        return totalSteps.get();
    }

    public ActionArmStat getArmStat(String actionId) {
        return armStats.get(actionId);
    }

    public void reset() {
        armStats.clear();
        totalSteps.set(0);
    }
}
