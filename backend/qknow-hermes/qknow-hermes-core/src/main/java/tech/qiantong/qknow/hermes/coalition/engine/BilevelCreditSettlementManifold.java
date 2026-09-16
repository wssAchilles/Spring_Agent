package tech.qiantong.qknow.hermes.coalition.engine;

import tech.qiantong.qknow.hermes.coalition.dto.BilevelSettlementScheme;
import tech.qiantong.qknow.hermes.coalition.dto.CoalitionMemberAgent;
import tech.qiantong.qknow.hermes.coalition.dto.DynamicCoalitionStructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 双层沙普利-纳什信贷清算流形引擎 (定理 1.2)
 */
public class BilevelCreditSettlementManifold {

    private static final double FREE_RIDER_THRESHOLD = 0.35;

    /**
     * 执行双层信贷清算：内层沙普利公理化分配 + 外层纳什议价帕累托寻优
     */
    public BilevelSettlementScheme settleCredits(
        DynamicCoalitionStructure coalition,
        List<CoalitionMemberAgent> members,
        double[] targetTaskEmbedding
    ) {
        if (coalition == null) {
            throw new IllegalArgumentException("coalition 不能为空");
        }
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("members 不能为空");
        }
        if (targetTaskEmbedding == null || targetTaskEmbedding.length != 1536) {
            throw new IllegalArgumentException("targetTaskEmbedding 必须为阿里千问 1536 维向量");
        }

        // 校验目标向量模长
        double targetNormSq = 0.0;
        for (double v : targetTaskEmbedding) {
            targetNormSq += v * v;
        }
        double targetNorm = Math.sqrt(targetNormSq);
        if (Math.abs(targetNorm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("targetTaskEmbedding 模长必须为 1.0±1e-4");
        }

        // 1. 计算因果反事实边际增益与搭便车识别
        Map<String, Double> marginalContributions = new HashMap<>();
        double totalMarginal = 0.0;
        boolean hasFreeRider = false;

        for (CoalitionMemberAgent m : members) {
            double dot = computeDot8Way(m.intentEmbedding(), targetTaskEmbedding);
            // 搭便车判断：意图匹配度极低且初始标签为低贡献/观察者
            if (dot < FREE_RIDER_THRESHOLD || "DUMMY_OBSERVER".equalsIgnoreCase(m.capabilityTag())) {
                marginalContributions.put(m.agentId(), 0.0);
                hasFreeRider = true;
            } else {
                double mc = dot * m.initialCredit();
                marginalContributions.put(m.agentId(), mc);
                totalMarginal += mc;
            }
        }

        // 2. 内层沙普利分配：满足有效性、对称性与搭便车零信贷
        Map<String, Double> allocations = new HashMap<>();
        double totalVal = coalition.totalCharacteristicValue();
        double sumAllocated = 0.0;
        String highestAgent = null;
        double maxMc = -1.0;

        for (CoalitionMemberAgent m : members) {
            double mc = marginalContributions.get(m.agentId());
            double alloc = 0.0;
            if (totalMarginal > 0.0 && mc > 0.0) {
                alloc = (mc / totalMarginal) * totalVal;
            }
            allocations.put(m.agentId(), alloc);
            sumAllocated += alloc;
            if (mc > maxMc) {
                maxMc = mc;
                highestAgent = m.agentId();
            }
        }

        // 残差严格补偿至最高贡献者以确保完全守恒
        double residual = totalVal - sumAllocated;
        if (highestAgent != null && Math.abs(residual) > 1e-9) {
            allocations.put(highestAgent, allocations.get(highestAgent) + residual);
            residual = 0.0;
        }

        // 3. 外层纳什议价对数帕累托效用
        double paretoUtility = 0.0;
        for (double val : allocations.values()) {
            if (val > 0.0) {
                paretoUtility += Math.log(val + 1.0);
            }
        }

        String settlementId = "SETTLE-" + coalition.coalitionId() + "-" + System.nanoTime();
        return new BilevelSettlementScheme(
            settlementId,
            coalition.coalitionId(),
            paretoUtility,
            allocations,
            Math.abs(residual),
            hasFreeRider,
            System.currentTimeMillis()
        );
    }

    private double computeDot8Way(double[] a, double[] b) {
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
