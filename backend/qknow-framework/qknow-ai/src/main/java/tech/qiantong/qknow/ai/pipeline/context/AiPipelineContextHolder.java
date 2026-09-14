package tech.qiantong.qknow.ai.pipeline.context;

import java.util.concurrent.Callable;

/**
 * 跨线程安全的 AI 管道上下文持有者 (AiPipelineContextHolder)
 *
 * 结合 InheritableThreadLocal 与 AutoCloseableScope 强制作用域清理，
 * 并提供 wrap 装饰器支持在线程池异步任务中无损透传上下文，彻底杜绝高并发线程池复用下的跨租户串标与内存泄漏隐患。
 *
 * @author qknow
 */
public final class AiPipelineContextHolder {

    private static final InheritableThreadLocal<AiPipelineContext> CONTEXT_HOLDER = new InheritableThreadLocal<>();

    private AiPipelineContextHolder() {}

    /**
     * 开启一个受保护的上下文作用域 (建议使用 try-with-resources)
     *
     * @param context 当前请求上下文
     * @return 作用域句柄，close 时自动复位
     */
    public static AutoCloseableScope open(AiPipelineContext context) {
        AiPipelineContext previous = CONTEXT_HOLDER.get();
        CONTEXT_HOLDER.set(context);
        return new AutoCloseableScope(previous);
    }

    /**
     * 获取当前线程绑定的上下文
     *
     * @return 当前上下文，未绑定时返回 null
     */
    public static AiPipelineContext get() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 显式清空当前线程上下文
     */
    public static void remove() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 将当前线程的上下文捕获并包装至 Callable，用于提交给线程池异步执行
     *
     * @param task 原始任务
     * @param <T> 返回类型
     * @return 携带上下文的包装任务
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        final AiPipelineContext captured = get();
        return () -> {
            try (var scope = open(captured)) {
                return task.call();
            }
        };
    }

    /**
     * 将当前线程的上下文捕获并包装至 Runnable，用于提交给线程池异步执行
     *
     * @param task 原始任务
     * @return 携带上下文的包装任务
     */
    public static Runnable wrap(Runnable task) {
        final AiPipelineContext captured = get();
        return () -> {
            try (var scope = open(captured)) {
                task.run();
            }
        };
    }

    /**
     * 自动关闭作用域契约类
     */
    public static class AutoCloseableScope implements AutoCloseable {
        private final AiPipelineContext previousContext;

        private AutoCloseableScope(AiPipelineContext previousContext) {
            this.previousContext = previousContext;
        }

        @Override
        public void close() {
            if (previousContext != null) {
                CONTEXT_HOLDER.set(previousContext);
            } else {
                CONTEXT_HOLDER.remove();
            }
        }
    }
}
