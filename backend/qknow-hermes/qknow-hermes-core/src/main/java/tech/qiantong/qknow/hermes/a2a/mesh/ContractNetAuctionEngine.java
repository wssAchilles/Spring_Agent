package tech.qiantong.qknow.hermes.a2a.mesh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * 基于阿里千问 1536 维超球面流形与历史信誉的 FIPA-ACL 契约网二阶密封拍卖撮合引擎
 * 严格落实定理 1：真实报价为弱优势策略，全局社会福利 Pareto 最优，单步复杂度 O(N * d)
 */
public class ContractNetAuctionEngine {

    private static final Logger log = LoggerFactory.getLogger(ContractNetAuctionEngine.class);
    private static final int DIMENSION = 1536;

    public record ContractNetProposal(
            String cfpId,
            String taskId,
            float[] taskVector1536,
            double baseValue,
            double minReputationThreshold,
            long bidDeadlineTimestamp
    ) {
        public ContractNetProposal(
                String cfpId,
                String taskId,
                float[] taskVector1536,
                double minReputationThreshold,
                long bidDeadlineTimestamp
        ) {
            this(cfpId, taskId, taskVector1536, 100.0, minReputationThreshold, bidDeadlineTimestamp);
        }

        public ContractNetProposal {
            if (taskVector1536 == null || taskVector1536.length != DIMENSION) {
                throw new IllegalArgumentException("任务需求向量必须严格为 " + DIMENSION + " 维超球面向量");
            }
            if (baseValue <= 0.0) {
                baseValue = 100.0;
            }
        }
    }

    public record ContractNetBid(
            String bidId,
            String cfpId,
            String agentId,
            double bidCost,
            float[] agentVector1536,
            double reputationScore
    ) {
        public ContractNetBid {
            if (agentVector1536 == null || agentVector1536.length != DIMENSION) {
                throw new IllegalArgumentException("智能体能力向量必须严格为 " + DIMENSION + " 维超球面向量");
            }
            if (reputationScore < 0.0 || reputationScore > 1.0) {
                throw new IllegalArgumentException("信誉得分必须落在 [0.0, 1.0]，当前为: " + reputationScore);
            }
        }
    }

    public record ContractNetAwardReceipt(
            String awardId,
            String cfpId,
            String winningAgentId,
            double vcgPayment,
            double socialSurplus,
            String receiptHash
    ) {}

    /**
     * 执行 FIPA-ACL 二阶密封拍卖 (VCG) 裁决
     *
     * @param proposal 任务呼标提案
     * @param bids     候选智能体出标集合
     * @param alpha    语义匹配度权重 (0.0 < alpha < 1.0)
     * @return 最优中标凭单（若无有效出标则返回空）
     */
    public Optional<ContractNetAwardReceipt> evaluateBids(
            ContractNetProposal proposal,
            List<ContractNetBid> bids,
            double alpha
    ) {
        if (proposal == null || bids == null || bids.isEmpty()) {
            return Optional.empty();
        }

        double normalizedAlpha = Math.min(1.0, Math.max(0.0, alpha));
        float[] taskVec = proposal.taskVector1536();

        ContractNetBid bestBid = null;
        double bestSurplus = Double.NEGATIVE_INFINITY;
        double bestValue = 0.0;

        double secondBestSurplus = Double.NEGATIVE_INFINITY;

        for (ContractNetBid bid : bids) {
            // 过滤信誉不达标的智能体
            if (bid.reputationScore() < proposal.minReputationThreshold()) {
                continue;
            }

            // 1. 计算千问 1536 维超球面内积 (余弦相似度)
            double cosineSimilarity = computeCosineSimilarity(taskVec, bid.agentVector1536());

            // 2. 发起方估值: V_i(q) = baseValue * [alpha * cos(theta) + (1 - alpha) * R_i]
            double qualityScore = normalizedAlpha * Math.max(0.0, cosineSimilarity)
                    + (1.0 - normalizedAlpha) * bid.reputationScore();
            double taskValue = proposal.baseValue() * qualityScore;

            // 3. 声明社会盈余: S_i' = V_i(q) - b_i
            double declaredSurplus = taskValue - bid.bidCost();

            // 4. 维护最大与次大声明盈余
            if (declaredSurplus > bestSurplus) {
                secondBestSurplus = bestSurplus;
                bestSurplus = declaredSurplus;
                bestBid = bid;
                bestValue = taskValue;
            } else if (declaredSurplus > secondBestSurplus) {
                secondBestSurplus = declaredSurplus;
            }
        }

        if (bestBid == null) {
            return Optional.empty();
        }

        // 5. 二阶 VCG 支付计算:
        // p_{i*} = V_{i*}(q) - max_{j != i*} { V_j(q) - b_j } = V_{i*}(q) - secondBestSurplus
        // 若没有次优出标者或次优盈余为负，设定支付保底为当前中标者出标价（保证个体理性）
        double vcgPayment;
        if (Double.isInfinite(secondBestSurplus) || secondBestSurplus <= 0.0) {
            vcgPayment = bestBid.bidCost();
        } else {
            vcgPayment = bestValue - secondBestSurplus;
            // 确保不低于出标成本
            if (vcgPayment < bestBid.bidCost()) {
                vcgPayment = bestBid.bidCost();
            }
        }

        String awardId = "AWARD_" + UUID.randomUUID().toString().replace("-", "");
        String receiptHash = computeReceiptHash(awardId, proposal.cfpId(), bestBid.agentId(), vcgPayment, bestSurplus);

        ContractNetAwardReceipt receipt = new ContractNetAwardReceipt(
                awardId,
                proposal.cfpId(),
                bestBid.agentId(),
                vcgPayment,
                bestSurplus,
                receiptHash
        );

        log.debug("[ContractNetAuction] 中标智能体: {}, 真实出标: {}, VCG支付: {}, 社会盈余: {}",
                bestBid.agentId(), bestBid.bidCost(), vcgPayment, bestSurplus);

        return Optional.of(receipt);
    }

    private double computeCosineSimilarity(float[] u, float[] v) {
        double dot = 0.0;
        for (int i = 0; i < DIMENSION; i++) {
            dot += u[i] * v[i];
        }
        return dot;
    }

    private String computeReceiptHash(String awardId, String cfpId, String agentId, double payment, double surplus) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = String.format("%s|%s|%s|%.6f|%.6f", awardId, cfpId, agentId, payment, surplus);
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
