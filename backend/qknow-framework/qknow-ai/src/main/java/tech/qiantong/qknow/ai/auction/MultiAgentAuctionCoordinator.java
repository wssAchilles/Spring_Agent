package tech.qiantong.qknow.ai.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * 多智能体分布式组合拍卖与结算总控中枢
 */
@Component
public class MultiAgentAuctionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentAuctionCoordinator.class);

    private final AntiSybilCreditLedger creditLedger;
    private final CombinatorialAuctionEngine auctionEngine;
    private final TruthfulIncentiveMechanism incentiveMechanism;
    private final ShapleyCreditAllocator shapleyAllocator;

    public MultiAgentAuctionCoordinator(
            AntiSybilCreditLedger creditLedger,
            CombinatorialAuctionEngine auctionEngine,
            TruthfulIncentiveMechanism incentiveMechanism,
            ShapleyCreditAllocator shapleyAllocator
    ) {
        this.creditLedger = creditLedger;
        this.auctionEngine = auctionEngine;
        this.incentiveMechanism = incentiveMechanism;
        this.shapleyAllocator = shapleyAllocator;
    }

    /**
     * 端到端执行任务拍卖、VCG 决标与沙普利清算 (耗时 <= 5ms)
     */
    public AuctionSettlementReceipt coordinateAuction(
            String taskId,
            List<CombinatorialAuctionEngine.TaskItem> tasks,
            List<CombinatorialAuctionEngine.AgentBid> incomingBids,
            Function<Set<String>, Double> coalitionValueFunc
    ) {
        long startTime = System.nanoTime();
        String auctionId = "AUC_" + UUID.randomUUID().toString().substring(0, 8);

        // 1. 抗女巫过滤：剔除恶意伪造节点 (定理 1.3)
        List<CombinatorialAuctionEngine.AgentBid> legitimateBids = incomingBids.stream()
                .filter(bid -> !creditLedger.isSybil(bid.agentId()))
                .toList();

        // 2. 求解最优社会成本划分 S*
        CombinatorialAuctionEngine.AuctionAllocation optimalAlloc = auctionEngine.solveWinnerDetermination(tasks, legitimateBids);

        // 3. 计算每个中标者的 VCG 外部性支付补偿 (定理 1.1)
        Map<String, Double> vcgPayments = new HashMap<>();
        Set<String> winningAgents = optimalAlloc.allocation().keySet();

        for (String winner : winningAgents) {
            CombinatorialAuctionEngine.AuctionAllocation withoutWinnerAlloc = auctionEngine.solveWithoutAgent(tasks, legitimateBids, winner);
            // 其余人在最优解中的成本
            double othersCostInOptimal = optimalAlloc.totalCost() - legitimateBids.stream()
                    .filter(b -> b.agentId().equals(winner))
                    .mapToDouble(CombinatorialAuctionEngine.AgentBid::claimedCost)
                    .findFirst().orElse(0.0);

            double payment = incentiveMechanism.calculateVcgPayment(
                    winner, withoutWinnerAlloc.totalCost(), othersCostInOptimal
            );
            vcgPayments.put(winner, payment);
        }

        // 4. 沙普利公理化信誉分配 (定理 1.2)
        List<String> winnersList = new ArrayList<>(winningAgents);
        Map<String, Double> shapleyShares = shapleyAllocator.calculateShapleyValues(winnersList, coalitionValueFunc);

        // 5. 生成不可变结算凭证
        AuctionSettlementReceipt receipt = AuctionSettlementReceipt.createReceipt(
                auctionId, taskId, optimalAlloc.allocation(), vcgPayments, shapleyShares, optimalAlloc.totalCost()
        );

        // 6. 回写信誉记录
        for (Map.Entry<String, Double> entry : shapleyShares.entrySet()) {
            creditLedger.recordExecutionSuccess(entry.getKey(), entry.getValue() * 0.1);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        log.info("Auction {} settled in {}ms, total winners: {}", auctionId, elapsedMs, winningAgents.size());

        return receipt;
    }
}
