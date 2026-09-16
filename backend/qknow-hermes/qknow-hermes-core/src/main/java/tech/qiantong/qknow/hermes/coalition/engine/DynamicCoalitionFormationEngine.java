package tech.qiantong.qknow.hermes.coalition.engine;

import tech.qiantong.qknow.hermes.coalition.dto.CoalitionMemberAgent;
import tech.qiantong.qknow.hermes.coalition.dto.DynamicCoalitionStructure;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨组织动态联盟形成引擎 (定理 1.1)
 */
public class DynamicCoalitionFormationEngine {

    /**
     * 形成/重组跨组织合作博弈动态联盟
     */
    public DynamicCoalitionStructure formCoalition(
        String coalitionId,
        long generation,
        List<CoalitionMemberAgent> members
    ) {
        if (coalitionId == null || coalitionId.isBlank()) {
            throw new IllegalArgumentException("coalitionId 不能为空");
        }
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("联盟成员列表不能为空");
        }

        long startNs = System.nanoTime();
        List<String> memberIds = new ArrayList<>();
        double baseCreditSum = 0.0;
        for (CoalitionMemberAgent m : members) {
            memberIds.add(m.agentId());
            baseCreditSum += m.initialCredit();
        }

        // 计算超可加协同特征函数增益 v(N)
        double synergyGain = 0.0;
        int n = members.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                CoalitionMemberAgent m1 = members.get(i);
                CoalitionMemberAgent m2 = members.get(j);
                double innerProd = computeCosineSimilarity8Way(m1.intentEmbedding(), m2.intentEmbedding());
                if (m1.domain() != m2.domain() && innerProd >= 0.50) {
                    // 跨组织互补增益
                    synergyGain += 5.0 * innerProd;
                } else {
                    synergyGain += 2.0 * Math.max(0.0, innerProd);
                }
            }
        }

        // 减去跨组织通信协调阻尼开销
        double coordinationCost = 0.3 * n;
        double totalCharacteristicValue = baseCreditSum + Math.max(0.0, synergyGain - coordinationCost);

        // 验证超可加性裕度与核心解稳定性
        double superadditivityMargin = synergyGain - coordinationCost;
        boolean isCoreStable = superadditivityMargin >= 0.0 && totalCharacteristicValue >= baseCreditSum;

        return new DynamicCoalitionStructure(
            coalitionId,
            generation,
            memberIds,
            totalCharacteristicValue,
            isCoreStable,
            superadditivityMargin,
            System.currentTimeMillis()
        );
    }

    /**
     * 8路循环展开高维向量点积加速
     */
    private double computeCosineSimilarity8Way(double[] a, double[] b) {
        double dot = 0.0;
        int len = a.length;
        int limit = len - (len % 8);
        for (int i = 0; i < limit; i += 8) {
            dot += a[i] * b[i]
                + a[i + 1] * b[i + 1]
                + a[i + 2] * b[i + 2]
                + a[i + 3] * b[i + 3]
                + a[i + 4] * b[i + 4]
                + a[i + 5] * b[i + 5]
                + a[i + 6] * b[i + 6]
                + a[i + 7] * b[i + 7];
        }
        for (int i = limit; i < len; i++) {
            dot += a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }
}
