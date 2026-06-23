package tech.qiantong.qknow.hermes.trace;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的追踪上下文。
 */
public class TraceContext {

    private final String traceId;
    private final String requestId;
    private final Map<String, TraceSpan> spans = new ConcurrentHashMap<>();
    private final List<TraceSpan> rootSpans = new java.util.ArrayList<>();

    public TraceContext(String traceId, String requestId) {
        this.traceId = traceId;
        this.requestId = requestId;
    }

    public void addSpan(TraceSpan span) {
        spans.put(span.getSpanId(), span);
    }

    public TraceSpan getSpan(String spanId) {
        return spans.get(spanId);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public List<TraceSpan> getRootSpans() {
        return rootSpans;
    }

    public int getTotalTokens() {
        int total = 0;
        for (TraceSpan span : spans.values()) {
            total += span.getTokenCount();
        }
        return total;
    }

    public double getTotalCost() {
        double total = 0.0;
        for (TraceSpan span : spans.values()) {
            total += span.getCost();
        }
        return total;
    }

    public String getStatus() {
        for (TraceSpan span : spans.values()) {
            if (!"success".equals(span.getStatus())) {
                return "error";
            }
        }
        return "success";
    }

    public long getTotalDuration() {
        long minStart = Long.MAX_VALUE;
        long maxEnd = Long.MIN_VALUE;
        for (TraceSpan span : spans.values()) {
            if (span.getStartTime() < minStart) {
                minStart = span.getStartTime();
            }
            if (span.getEndTime() > maxEnd) {
                maxEnd = span.getEndTime();
            }
        }
        if (spans.isEmpty()) {
            return 0;
        }
        return maxEnd - minStart;
    }
}
