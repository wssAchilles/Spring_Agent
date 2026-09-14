package tech.qiantong.qknow.ai.auction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * VCG 组合拍卖调度引擎 (求解社会总成本最小化胜者决定问题 WDP)
 */
@Component
public class CombinatorialAuctionEngine {

    private static final Logger log = LoggerFactory.getLogger(CombinatorialAuctionEngine.class);

    public record TaskItem(
            String taskId,
            String skillRequired,
            double baseComplexity
    ) {}

    public record AgentBid(
            String bidId,
            String agentId,
            List<String> targetTaskIds,
            double claimedCost
    ) {}

    public record AuctionAllocation(
            Map<String, List<String>> allocation, // agentId -> List<taskId>
            double totalCost,
            boolean isFeasible
    ) {}

    /**
     * 求解全局社会成本最小化任务划分 S*
     */
    public AuctionAllocation solveWinnerDetermination(
            List<TaskItem> tasks,
            List<AgentBid> bids
    ) {
        if (tasks == null || tasks.isEmpty() || bids == null || bids.isEmpty()) {
            return new AuctionAllocation(Map.of(), 0.0, false);
        }

        // 针对每个任务选择合法报价中成本最低的智能体 (单任务与组合划分)
        Map<String, List<String>> winnerMap = new HashMap<>();
        double totalSocialCost = 0.0;
        Set<String> assignedTasks = new HashSet<>();

        // 按声明成本升序排序
        List<AgentBid> sortedBids = new ArrayList<>(bids);
        sortedBids.sort(Comparator.comparingDouble(AgentBid::claimedCost));

        for (TaskItem task : tasks) {
            AgentBid bestBid = null;
            for (AgentBid bid : sortedBids) {
                if (bid.targetTaskIds().contains(task.taskId())) {
                    bestBid = bid;
                    break;
                }
            }

            if (bestBid != null) {
                winnerMap.computeIfAbsent(bestBid.agentId(), k -> new ArrayList<>()).add(task.taskId());
                totalSocialCost += bestBid.claimedCost();
                assignedTasks.add(task.taskId());
            }
        }

        boolean allAssigned = (assignedTasks.size() == tasks.size());
        return new AuctionAllocation(Collections.unmodifiableMap(winnerMap), totalSocialCost, allAssigned);
    }

    /**
     * 求解排除指定智能体 i 后的虚拟系统最优划分 S^-i (用于核算 VCG 外部性)
     */
    public AuctionAllocation solveWithoutAgent(
            List<TaskItem> tasks,
            List<AgentBid> bids,
            String excludedAgentId
    ) {
        List<AgentBid> filteredBids = bids.stream()
                .filter(b -> !b.agentId().equals(excludedAgentId))
                .toList();

        return solveWinnerDetermination(tasks, filteredBids);
    }
}
