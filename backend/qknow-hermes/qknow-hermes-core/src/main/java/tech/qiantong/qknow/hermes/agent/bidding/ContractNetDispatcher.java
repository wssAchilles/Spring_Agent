package tech.qiantong.qknow.hermes.agent.bidding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.agent.EnhancedBaseAgent;
import tech.qiantong.qknow.hermes.agent.dag.DagTaskNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级合同网竞标调度引擎
 */
@Slf4j
@Component
public class ContractNetDispatcher {

    private final Map<String, EnhancedBaseAgent> registeredWorkers = new ConcurrentHashMap<>();
    private EnhancedBaseAgent defaultFallbackWorker;

    public void registerWorker(EnhancedBaseAgent worker) {
        if (worker != null) {
            registeredWorkers.put(worker.getName(), worker);
            log.info("[ContractNet] 成功注册 Worker 智能体: {}", worker.getName());
        }
    }

    public void setDefaultFallbackWorker(EnhancedBaseAgent worker) {
        this.defaultFallbackWorker = worker;
    }

    /**
     * 执行四步合同网握手：CFP发布 -> 投标收集 -> 选标授标 -> 兜底降级
     */
    public String bidAndExecute(String sessionId, DagTaskNode task, String contextPayload) {
        TaskCfp cfp = new TaskCfp(
                UUID.randomUUID().toString(),
                task.taskId(),
                task.objective(),
                task.requiredCapability(),
                contextPayload,
                task.timeoutSeconds()
        );

        // 1. 收集所有可用 Worker 的投标
        List<WorkerBid> bids = new ArrayList<>();
        for (EnhancedBaseAgent worker : registeredWorkers.values()) {
            Optional<WorkerBid> bidOpt = worker.evaluateAndBid(cfp);
            bidOpt.ifPresent(bids::add);
        }

        // 2. 评选最佳投标（按综合得分倒序排序）
        bids.sort(Comparator.comparingDouble(WorkerBid::bidScore).reversed());

        EnhancedBaseAgent selectedWorker = null;
        if (!bids.isEmpty() && bids.get(0).bidScore() >= 0.60) {
            WorkerBid winner = bids.get(0);
            selectedWorker = registeredWorkers.get(winner.workerName());
            log.info("[ContractNet] 任务 [{}] 中标 Worker: {}, 得分: {}",
                    task.taskId(), winner.workerName(), winner.bidScore());
        }

        // 3. 自适应回退降级策略（Fallback Default Worker）
        if (selectedWorker == null) {
            log.warn("[ContractNet] 任务 [{}] 无有效投标或得分低于阈值 0.60，触发降级至 DefaultFallbackWorker", task.taskId());
            selectedWorker = defaultFallbackWorker;
        }

        if (selectedWorker == null) {
            return "【执行错误】：无可用 Worker 且默认兜底降级 Worker 未配置。";
        }

        // 4. 授标并执行任务
        return selectedWorker.executeTask(task.objective(), contextPayload);
    }
}
