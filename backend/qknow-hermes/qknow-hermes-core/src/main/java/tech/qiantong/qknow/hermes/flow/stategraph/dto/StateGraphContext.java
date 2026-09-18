package tech.qiantong.qknow.hermes.flow.stategraph.dto;

import lombok.Getter;
import lombok.Setter;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 状态图单次运行上下文
 */
@Getter
public class StateGraphContext {

    private final String executionId;
    private final String flowId;
    private final AtomicInteger currentSuperstep = new AtomicInteger(0);
    private final Map<String, AtomicInteger> loopCounters = new ConcurrentHashMap<>();
    private final Map<String, NodeRunResultBO> nodeResults = new ConcurrentHashMap<>();
    private final AtomicInteger remainingRetryBudget;
    private final AtomicBoolean degradedBreakOccurred = new AtomicBoolean(false);
    private final AtomicBoolean selfHealedOccurred = new AtomicBoolean(false);
    private final Map<String, Object> sharedState = new ConcurrentHashMap<>();

    @Setter
    private RuntimeContextBO runtimeContext;

    @Setter
    private double[] hypersphereStateVector;

    public StateGraphContext(String executionId, String flowId, int totalRetryBudget) {
        this.executionId = executionId;
        this.flowId = flowId;
        this.remainingRetryBudget = new AtomicInteger(Math.max(totalRetryBudget, 0));
    }

    public int incrementSuperstep() {
        return currentSuperstep.incrementAndGet();
    }

    public int getLoopCount(String edgeId) {
        AtomicInteger counter = loopCounters.get(edgeId);
        return counter != null ? counter.get() : 0;
    }

    public int incrementLoopCount(String edgeId) {
        return loopCounters.computeIfAbsent(edgeId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void putNodeResult(String nodeUuid, NodeRunResultBO result) {
        nodeResults.put(nodeUuid, result);
    }

    public NodeRunResultBO getNodeResult(String nodeUuid) {
        return nodeResults.get(nodeUuid);
    }

    public boolean tryConsumeRetryBudget() {
        while (true) {
            int current = remainingRetryBudget.get();
            if (current <= 0) {
                return false;
            }
            if (remainingRetryBudget.compareAndSet(current, current - 1)) {
                return true;
            }
        }
    }
}
