package tech.qiantong.qknow.hermes.agent.debate.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 拓展 VCG 机制分层任务拍卖协调器 (VCG Task Auction Coordinator)
 * <p>
 * 集成阿里千问 1536 维超球面单位向量测地线门禁（s >= 0.70 硬过滤），并执行 Vickrey-Clarke-Groves (VCG)
 * 二阶外部性计费，确保智能体真实汇报技能与延迟是弱占优策略，杜绝劣质智能体搭便车与恶意刷单。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class VcgTaskAuctionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(VcgTaskAuctionCoordinator.class);

    /**
     * 阿里千问 1536 维超球面门禁余弦相似度阈值 (s >= 0.70)
     */
    public static final double SPHERICAL_GATE_THRESHOLD = 0.70;

    /**
     * 竞标出价描述 Record
     */
    public record BidOffer(
            String agentId,
            float[] capabilityVector,
            double valuation,     // 智能体申报的执行价值/能力
            double declaredCost    // 智能体申报的成本/延迟消耗
    ) {}

    /**
     * 拍卖结算结果 Record
     */
    public record AuctionResult(
            String winnerAgentId,
            double winningScore,
            double vcgPayment,    // VCG 二阶外部性支付
            double socialWelfare, // 达成总社会福利
            List<String> eliminatedByGateAgents
    ) {}

    /**
     * 执行拓展 VCG 任务拍卖协调
     *
     * @param taskDemandVector 任务需求 1536 维超球面嵌入向量
     * @param bids             所有参与竞标的智能体出价
     * @return 拍卖裁定结果
     */
    public AuctionResult conductAuction(float[] taskDemandVector, List<BidOffer> bids) {
        Objects.requireNonNull(taskDemandVector, "taskDemandVector 不能为空");
        if (bids == null || bids.isEmpty()) {
            return new AuctionResult(null, 0.0, 0.0, 0.0, Collections.emptyList());
        }

        List<String> eliminated = new ArrayList<>();
        List<BidOffer> qualifiedBids = new ArrayList<>();

        // 1. 阿里千问 1536 维超球面门禁过滤
        for (BidOffer bid : bids) {
            double cosineSimilarity = computeSphericalCosine(taskDemandVector, bid.capabilityVector());
            if (cosineSimilarity < SPHERICAL_GATE_THRESHOLD) {
                eliminated.add(bid.agentId());
                log.debug("智能体 {} 因超球面内积门禁未达标 (s={:.4f} < 0.70) 被剔除", bid.agentId(), cosineSimilarity);
            } else {
                qualifiedBids.add(bid);
            }
        }

        if (qualifiedBids.isEmpty()) {
            return new AuctionResult(null, 0.0, 0.0, 0.0, eliminated);
        }

        // 2. 计算各合格竞标的净社会福利贡献: W_i = valuation - declaredCost
        BidOffer bestBid = null;
        double maxWelfare = -Double.MAX_VALUE;
        BidOffer secondBestBid = null;
        double secondMaxWelfare = -Double.MAX_VALUE;

        for (BidOffer bid : qualifiedBids) {
            double welfare = bid.valuation() - bid.declaredCost();
            if (welfare > maxWelfare) {
                secondMaxWelfare = maxWelfare;
                secondBestBid = bestBid;
                maxWelfare = welfare;
                bestBid = bid;
            } else if (welfare > secondMaxWelfare) {
                secondMaxWelfare = welfare;
                secondBestBid = bid;
            }
        }

        if (bestBid == null) {
            return new AuctionResult(null, 0.0, 0.0, 0.0, eliminated);
        }

        // 3. 计算 VCG 二阶外部性支付:
        // P(i*) = 次优出价带来的福利 + 胜出者的边际成本 (使得胜出者效用等于其对系统的边际贡献)
        double vcgPayment = (secondBestBid != null) ? Math.max(0.0, secondBestBid.valuation() - secondBestBid.declaredCost() + bestBid.declaredCost()) : bestBid.declaredCost();

        return new AuctionResult(
                bestBid.agentId(),
                maxWelfare,
                vcgPayment,
                maxWelfare,
                eliminated
        );
    }

    /**
     * 计算两向量在超球面上的余弦点积 (要求输入尽量为单位向量)
     */
    private double computeSphericalCosine(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += (double) v1[i] * v2[i];
            norm1 += (double) v1[i] * v1[i];
            norm2 += (double) v2[i] * v2[i];
        }
        if (norm1 <= 1e-9 || norm2 <= 1e-9) {
            return 0.0;
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
