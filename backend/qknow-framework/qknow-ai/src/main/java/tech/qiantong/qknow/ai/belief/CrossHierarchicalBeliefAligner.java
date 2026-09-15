package tech.qiantong.qknow.ai.belief;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 跨层级信念状态对齐器 (定理 1.3: 杰弗里斯对称散度与几何平均测地流形投影)
 */
@Component
public class CrossHierarchicalBeliefAligner {

    private static final Logger log = LoggerFactory.getLogger(CrossHierarchicalBeliefAligner.class);

    // 最大容许信念散度门限
    public static final double MAX_TOLERABLE_DIVERGENCE = 1.5;
    private static final double EPSILON = 1e-6; // 拉普拉斯极小平滑

    /**
     * 对齐结果
     */
    public record AlignmentResult(
            double jeffreysDivergence,
            Map<String, Double> alignedDistribution,
            boolean aligned
    ) {}

    /**
     * 计算对称杰弗里斯散度与几何平均投影 (定理 1.3)
     */
    public AlignmentResult alignBeliefs(Map<String, Double> macroBelief, Map<String, Double> microBelief) {
        if (macroBelief == null || microBelief == null || macroBelief.isEmpty() || microBelief.isEmpty()) {
            throw new IllegalArgumentException("宏观信念与微观信念均不能为空");
        }

        Set<String> allStates = new HashSet<>();
        allStates.addAll(macroBelief.keySet());
        allStates.addAll(microBelief.keySet());

        // 平滑并归一化两个分布
        Map<String, Double> p = smoothAndNormalize(macroBelief, allStates);
        Map<String, Double> q = smoothAndNormalize(microBelief, allStates);

        // 1. 计算对称杰弗里斯散度 D_J = 0.5 * (KL(P||Q) + KL(Q||P))
        double klPq = 0.0;
        double klQp = 0.0;
        for (String s : allStates) {
            double pi = p.get(s);
            double qi = q.get(s);
            klPq += pi * Math.log(pi / qi);
            klQp += qi * Math.log(qi / pi);
        }
        double jeffreysDivergence = 0.5 * (klPq + klQp);

        // 2. 几何平均无偏最优投影 P_aligned(s) propto sqrt(P(s) * Q(s)) (定理 1.3)
        Map<String, Double> aligned = new HashMap<>();
        double sumSqrt = 0.0;
        for (String s : allStates) {
            double val = Math.sqrt(p.get(s) * q.get(s));
            aligned.put(s, val);
            sumSqrt += val;
        }

        // 归一化对齐分布
        for (String s : allStates) {
            aligned.put(s, aligned.get(s) / sumSqrt);
        }

        boolean alignedStatus = (jeffreysDivergence <= MAX_TOLERABLE_DIVERGENCE);

        log.debug("跨层级信念对齐计算完成: DJ={}, isAligned={}",
                String.format("%.4f", jeffreysDivergence), alignedStatus);

        return new AlignmentResult(jeffreysDivergence, Collections.unmodifiableMap(aligned), alignedStatus);
    }

    private Map<String, Double> smoothAndNormalize(Map<String, Double> input, Set<String> allKeys) {
        Map<String, Double> result = new HashMap<>();
        double sum = 0.0;
        for (String k : allKeys) {
            double raw = input.getOrDefault(k, 0.0);
            double smoothed = Math.max(EPSILON, raw);
            result.put(k, smoothed);
            sum += smoothed;
        }
        for (String k : allKeys) {
            result.put(k, result.get(k) / sum);
        }
        return result;
    }
}
