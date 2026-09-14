package tech.qiantong.qknow.ai.twin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * 旁路无侵入健康与异动监控器 (无锁单向发布，物理主链路零等待)
 */
@Component
public class MetacognitiveMonitor {

    private static final Logger log = LoggerFactory.getLogger(MetacognitiveMonitor.class);

    /**
     * 生产智能体物理执行事件
     */
    public record PhysicalAgentEvent(
            String eventId,
            String agentId,
            String eventType,
            double latencyMs,
            boolean isError,
            Map<String, Object> payload,
            long timestampMs
    ) {}

    // 无锁环形并发队列
    private final ConcurrentLinkedQueue<PhysicalAgentEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final LongAdder totalIngestedEvents = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();

    // 滑动窗口指标
    private volatile double rollingAverageLatencyMs = 25.0;
    private volatile double systemHealthScore = 1.0;
    private volatile boolean oscillationDetected = false;

    /**
     * 旁路无锁事件上报 (物理主链路执行，O(1) 耗时，绝不阻塞)
     */
    public void recordAgentEvent(PhysicalAgentEvent event) {
        if (event == null) return;
        eventQueue.offer(event);
        totalIngestedEvents.increment();
        if (event.isError()) {
            totalErrors.increment();
        }
    }

    /**
     * 周期性排空并分析事件队列
     */
    public int drainAndProcessEvents() {
        int processedCount = 0;
        double sumLatency = 0.0;
        int errorCountInBatch = 0;
        Map<String, Integer> agentActionFrequency = new HashMap<>();

        PhysicalAgentEvent evt;
        while ((evt = eventQueue.poll()) != null) {
            processedCount++;
            sumLatency += evt.latencyMs();
            if (evt.isError()) {
                errorCountInBatch++;
            }
            agentActionFrequency.merge(evt.agentId(), 1, Integer::sum);
        }

        if (processedCount > 0) {
            double batchAvg = sumLatency / processedCount;
            // 指数移动平均 (EMA) 平滑
            rollingAverageLatencyMs = 0.7 * rollingAverageLatencyMs + 0.3 * batchAvg;

            double errorRate = (double) errorCountInBatch / processedCount;
            systemHealthScore = Math.max(0.0, 1.0 - (errorRate * 0.8 + (rollingAverageLatencyMs > 500 ? 0.2 : 0.0)));

            // 振荡检测：单个智能体在批次内发生异常高频冲突重试或错误震荡 (如死锁重试循环)
            final int total = processedCount;
            final int errors = errorCountInBatch;
            boolean errorFlapping = errors > 10 && agentActionFrequency.values().stream().anyMatch(freq -> freq > 0.8 * total);
            boolean extremeFrequency = agentActionFrequency.values().stream().anyMatch(freq -> freq > 5000);
            oscillationDetected = errorFlapping || extremeFrequency;
        }

        return processedCount;
    }

    public double getSystemHealthScore() {
        return systemHealthScore;
    }

    public double getRollingAverageLatencyMs() {
        return rollingAverageLatencyMs;
    }

    public boolean isOscillationDetected() {
        return oscillationDetected;
    }

    public long getTotalIngestedEvents() {
        return totalIngestedEvents.sum();
    }

    public long getTotalErrors() {
        return totalErrors.sum();
    }
}
