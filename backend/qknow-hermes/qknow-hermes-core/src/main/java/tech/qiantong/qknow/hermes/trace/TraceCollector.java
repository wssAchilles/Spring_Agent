package tech.qiantong.qknow.hermes.trace;

import java.util.UUID;

/**
 * 追踪收集器，使用 ThreadLocal 支持多线程隔离。
 */
public class TraceCollector {

    private final ThreadLocal<TraceContext> currentContext = new ThreadLocal<>();

    public TraceCollector() {
    }

    /**
     * 开始一次新的追踪。
     */
    public TraceContext startTrace(String requestId) {
        TraceContext ctx = new TraceContext(requestId, requestId);
        currentContext.set(ctx);
        return ctx;
    }

    /**
     * 创建一个根 span。
     */
    public TraceSpan startSpan(String name) {
        TraceContext ctx = currentContext.get();
        TraceSpan span = new TraceSpan(name);
        ctx.addSpan(span);
        ctx.getRootSpans().add(span);
        return span;
    }

    /**
     * 创建一个子 span。
     */
    public TraceSpan startSpan(String name, TraceSpan parent) {
        TraceContext ctx = currentContext.get();
        TraceSpan span = new TraceSpan(name);
        ctx.addSpan(span);
        parent.addChild(span);
        return span;
    }

    /**
     * 结束一个 span（默认 status = "success"）。
     */
    public void endSpan(String spanId, String output, int tokenCount, double cost) {
        endSpan(spanId, output, "success", tokenCount, cost);
    }

    /**
     * 结束一个 span（自定义 status）。
     */
    public void endSpan(String spanId, String output, String status, int tokenCount, double cost) {
        TraceContext ctx = currentContext.get();
        TraceSpan span = ctx.getSpan(spanId);
        span.setEndTime(System.currentTimeMillis());
        span.setStatus(status);
        span.setOutput(output);
        span.setTokenCount(tokenCount);
        span.setCost(cost);
    }

    /**
     * 结束追踪，清除 ThreadLocal，返回上下文。
     */
    public TraceContext endTrace() {
        TraceContext ctx = currentContext.get();
        currentContext.remove();
        return ctx;
    }
}
