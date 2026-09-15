package tech.qiantong.qknow.ai.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保守性价值惩罚治理器 (基于 Conservative Q-Learning 理论, 定理 1.2)
 */
@Component
public class ConservativePolicyGovernor {

    private static final Logger log = LoggerFactory.getLogger(ConservativePolicyGovernor.class);

    // 保守性超参数 alpha (加大对 OOD 动作的悲观惩罚权重)
    private final double alpha;

    // 状态下各动作的经验出现频次缓存: state -> (action -> count)
    private final ConcurrentHashMap<String, Map<String, Integer>> actionCountsByState = new ConcurrentHashMap<>();

    public ConservativePolicyGovernor() {
        this(1.0);
    }

    public ConservativePolicyGovernor(double alpha) {
        this.alpha = alpha;
    }

    /**
     * 摄取并登记历史轨迹数据集
     */
    public void recordTrajectories(List<DoublyRobustOpeEvaluator.TrajectoryStep> trajectories) {
        if (trajectories == null) return;
        for (DoublyRobustOpeEvaluator.TrajectoryStep step : trajectories) {
            actionCountsByState.compute(step.state(), (k, v) -> {
                Map<String, Integer> map = (v != null) ? v : new HashMap<>();
                map.put(step.action(), map.getOrDefault(step.action(), 0) + 1);
                return map;
            });
        }
    }

    /**
     * 计算特定状态下的 CQL 悲观散度惩罚 (定理 1.2)
     * D_CQL = sum_a pi(a|s) * (pi(a|s) / pi_hat(a|s) - 1)
     */
    public double computeCqlPenalty(String state, DoublyRobustOpeEvaluator.Policy targetPolicy) {
        if (targetPolicy == null) return 0.0;
        Map<String, Integer> counts = actionCountsByState.getOrDefault(state, Collections.emptyMap());
        int totalVisits = counts.values().stream().mapToInt(Integer::intValue).sum();

        List<String> actions = targetPolicy.getAvailableActions(state);
        if (actions == null || actions.isEmpty()) return 0.0;

        double cqlDivergence = 0.0;
        for (String a : actions) {
            double piProb = targetPolicy.getActionProb(state, a);
            if (piProb <= 0.0) continue;

            int aCount = counts.getOrDefault(a, 0);
            if (totalVisits == 0 || aCount == 0) {
                // 分布外动作 (OOD): 经验概率趋近于 0，施加重度惩罚
                cqlDivergence += piProb * 10.0;
            } else {
                double empiricalProb = (double) aCount / totalVisits;
                double ratio = piProb / empiricalProb;
                cqlDivergence += piProb * Math.max(0.0, ratio - 1.0);
            }
        }

        return alpha * cqlDivergence;
    }

    /**
     * 判断特定动作是否属于分布外 (OOD)
     */
    public boolean isOodAction(String state, String action) {
        Map<String, Integer> counts = actionCountsByState.get(state);
        return counts == null || !counts.containsKey(action) || counts.get(action) <= 0;
    }

    /**
     * 校验并校准悲观有效价值 (保证保守下界)
     */
    public double calibrateConservativeValue(double rawValue, double penalty) {
        return rawValue - penalty;
    }

    public void clear() {
        actionCountsByState.clear();
    }
}
