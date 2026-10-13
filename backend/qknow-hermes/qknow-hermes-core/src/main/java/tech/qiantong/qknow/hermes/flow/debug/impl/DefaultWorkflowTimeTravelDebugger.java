package tech.qiantong.qknow.hermes.flow.debug.impl;

import tech.qiantong.qknow.hermes.flow.debug.DebugBreakpointRecord;
import tech.qiantong.qknow.hermes.flow.debug.TimeTravelStepRecord;
import tech.qiantong.qknow.hermes.flow.debug.WorkflowTimeTravelDebugger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认工作流交互式断点调试与时间旅行引擎实现
 * 结合 Redux 增量局部重算逻辑与 maxAge=30 滑动窗口内存防护
 */
public class DefaultWorkflowTimeTravelDebugger implements WorkflowTimeTravelDebugger {

    private static final int MAX_AGE_WINDOW = 30;

    private final ConcurrentHashMap<String, DebugBreakpointRecord> breakpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> hitCounters = new ConcurrentHashMap<>();

    // 运行时 ID -> 历史步骤列表
    private final ConcurrentHashMap<String, List<TimeTravelStepRecord>> runtimeHistories = new ConcurrentHashMap<>();

    // 挂起状态屏障: runtimeId -> 挂起 Future
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pausedFutures = new ConcurrentHashMap<>();

    @Override
    public void registerBreakpoint(DebugBreakpointRecord breakpoint) {
        if (breakpoint != null) {
            breakpoints.put(breakpoint.breakpointId(), breakpoint);
            hitCounters.putIfAbsent(breakpoint.breakpointId(), new AtomicInteger(0));
        }
    }

    @Override
    public void removeBreakpoint(String breakpointId) {
        if (breakpointId != null) {
            breakpoints.remove(breakpointId);
            hitCounters.remove(breakpointId);
        }
    }

    @Override
    public boolean shouldPauseAtNode(String flowId, String nodeId, Map<String, Object> currentVariables) {
        if (flowId == null || nodeId == null) {
            return false;
        }

        for (DebugBreakpointRecord bp : breakpoints.values()) {
            if (bp.enabled() && bp.flowId().equals(flowId) && bp.targetNodeId().equals(nodeId)) {
                AtomicInteger counter = hitCounters.get(bp.breakpointId());
                int hits = (counter != null) ? counter.incrementAndGet() : 1;
                if (bp.hitCountLimit() <= 0 || hits <= bp.hitCountLimit()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void pauseExecution(String runtimeId, String nodeId, Map<String, Object> contextSnapshot) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pausedFutures.put(runtimeId, future);
    }

    @Override
    public void stepOver(String runtimeId) {
        CompletableFuture<Void> future = pausedFutures.remove(runtimeId);
        if (future != null) {
            future.complete(null);
        }
    }

    @Override
    public void resumeExecution(String runtimeId) {
        CompletableFuture<Void> future = pausedFutures.remove(runtimeId);
        if (future != null) {
            future.complete(null);
        }
    }

    @Override
    public void recordStepSnapshot(TimeTravelStepRecord stepRecord) {
        if (stepRecord == null) return;

        runtimeHistories.compute(stepRecord.runtimeId(), (k, list) -> {
            List<TimeTravelStepRecord> steps = (list == null) ? Collections.synchronizedList(new ArrayList<>()) : list;
            steps.add(stepRecord);

            // 滑动窗口保护：超过 MAX_AGE_WINDOW 时移出最老步骤，防止浏览器与后端内存爆炸
            while (steps.size() > MAX_AGE_WINDOW) {
                steps.remove(0);
            }
            return steps;
        });
    }

    @Override
    public List<TimeTravelStepRecord> getTimeTravelHistory(String runtimeId) {
        List<TimeTravelStepRecord> steps = runtimeHistories.get(runtimeId);
        if (steps == null) {
            return List.of();
        }
        synchronized (steps) {
            return List.copyOf(steps);
        }
    }

    @Override
    public String forkAndReplay(String runtimeId, long forkStepSequence, Map<String, Object> modifiedVariables) {
        List<TimeTravelStepRecord> originalSteps = runtimeHistories.get(runtimeId);
        if (originalSteps == null || originalSteps.isEmpty()) {
            throw new IllegalArgumentException("未找到历史执行步序记录: " + runtimeId);
        }

        String forkedRuntimeId = runtimeId + "_fork_" + UUID.randomUUID().toString().substring(0, 8);
        List<TimeTravelStepRecord> forkedSteps = Collections.synchronizedList(new ArrayList<>());

        synchronized (originalSteps) {
            for (TimeTravelStepRecord step : originalSteps) {
                if (step.stepSequence() <= forkStepSequence) {
                    Map<String, Object> mergedContext = new HashMap<>(step.fullContextSnapshot());
                    if (step.stepSequence() == forkStepSequence && modifiedVariables != null) {
                        mergedContext.putAll(modifiedVariables);
                    }
                    forkedSteps.add(new TimeTravelStepRecord(
                            step.stepSequence(),
                            forkedRuntimeId,
                            step.nodeId(),
                            step.nodeName() + " (Forked)",
                            step.status(),
                            step.inputVariablesSnapshot(),
                            step.outputVariablesSnapshot(),
                            mergedContext,
                            step.executionDurationMs(),
                            System.currentTimeMillis()
                    ));
                }
            }
        }

        runtimeHistories.put(forkedRuntimeId, forkedSteps);
        return forkedRuntimeId;
    }
}
