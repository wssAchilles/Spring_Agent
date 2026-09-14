package tech.qiantong.qknow.ai.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * 沙普利值合作博弈边际贡献公理化分配器 (定理 1.2: 完备效率性与虚设性)
 */
@Component
public class ShapleyCreditAllocator {

    private static final Logger log = LoggerFactory.getLogger(ShapleyCreditAllocator.class);

    /**
     * 计算多个协同 Agent 的公理化沙普利分配值
     *
     * @param agents               参与协作的中标智能体集合 (N <= 8)
     * @param coalitionValueFunc   特征函数 v(S): 联盟能够达成的预期收益
     */
    public Map<String, Double> calculateShapleyValues(
            List<String> agents,
            Function<Set<String>, Double> coalitionValueFunc
    ) {
        if (agents == null || agents.isEmpty()) {
            return Map.of();
        }

        int n = agents.size();
        Map<String, Double> shapleyMap = new LinkedHashMap<>();
        for (String a : agents) {
            shapleyMap.put(a, 0.0);
        }

        // 阶乘权重表
        double[] factorials = new double[n + 1];
        factorials[0] = 1.0;
        for (int i = 1; i <= n; i++) {
            factorials[i] = factorials[i - 1] * i;
        }
        double totalPermutations = factorials[n];

        // 状态位掩码枚举 2^n 个子联盟
        int totalSubsets = 1 << n;
        for (int mask = 0; mask < totalSubsets; mask++) {
            Set<String> subset = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(agents.get(i));
                }
            }
            int subsetSize = subset.size();
            double vSubset = coalitionValueFunc.apply(subset);

            // 对于不在当前子集中的智能体 i，计算边际贡献
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) == 0) {
                    String agent = agents.get(i);
                    Set<String> withAgent = new HashSet<>(subset);
                    withAgent.add(agent);
                    double vWith = coalitionValueFunc.apply(withAgent);
                    double marginalContribution = vWith - vSubset;

                    double weight = (factorials[subsetSize] * factorials[n - subsetSize - 1]) / totalPermutations;
                    shapleyMap.merge(agent, weight * marginalContribution, Double::sum);
                }
            }
        }

        return Collections.unmodifiableMap(shapleyMap);
    }
}
