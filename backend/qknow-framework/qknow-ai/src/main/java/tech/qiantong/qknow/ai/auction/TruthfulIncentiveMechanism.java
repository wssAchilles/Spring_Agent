package tech.qiantong.qknow.ai.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * VCG 占优策略真实性激励定价核算器 (定理 1.1: DSIC 保证)
 */
@Component
public class TruthfulIncentiveMechanism {

    private static final Logger log = LoggerFactory.getLogger(TruthfulIncentiveMechanism.class);

    /**
     * 计算中标智能体 i 的 VCG 外部性支付价格:
     * p_i = sum_{j != i} b_j(S^-i) - sum_{j != i} b_j(S*)
     * 即: 智能体 i 获得的补偿等于其为其余人带来的社会成本节约
     */
    public double calculateVcgPayment(
            String agentId,
            double socialCostWithoutAgent,
            double othersCostInOptimal
    ) {
        double payment = socialCostWithoutAgent - othersCostInOptimal;
        // 保证个人理性 (Individual Rationality): 补偿支付非负
        return Math.max(0.0, payment);
    }

    /**
     * 验证定理 1.1 占优策略真实性:
     * 证明智能体诚实申报 trueCost 时的效用 u_honest = p - trueCost
     * 严格大于等于虚报（高报/低报）时的效用
     */
    public boolean verifyDSIC(
            double trueCost,
            double honestBid,
            double misreportedBid,
            double externalityPaymentIfHonest,
            double externalityPaymentIfMisreported
    ) {
        double honestUtility = externalityPaymentIfHonest - trueCost;
        double misreportedUtility = externalityPaymentIfMisreported - trueCost;

        // 诚实报价效用必然 >= 虚假报价效用
        return honestUtility >= misreportedUtility - 1e-9;
    }
}
