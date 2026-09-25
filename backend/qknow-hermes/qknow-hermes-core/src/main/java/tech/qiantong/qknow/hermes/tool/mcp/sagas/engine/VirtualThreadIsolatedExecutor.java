package tech.qiantong.qknow.hermes.tool.mcp.sagas.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 第一道防线：基于 Java 21 虚拟线程的高并发轻量隔离与硬超时熔断防线
 * 1. 采用 Executors.newVirtualThreadPerTaskExecutor() 纳秒级派发，单任务独享专用虚拟线程堆栈；
 * 2. 硬编码单步执行超时门禁 (默认 5000ms)，Future.get 超时立即强行执行 Thread.interrupt() 中断并清理；
 * 3. 彻底解除外部不可信 MCP 工具对宿主载体工作线程 (Carrier Threads) 的耗尽与饥饿威胁。
 */
public class VirtualThreadIsolatedExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadIsolatedExecutor.class);

    private final ExecutorService virtualExecutor;
    private final long defaultTimeoutMillis;

    public VirtualThreadIsolatedExecutor() {
        this(5000L); // 默认 5000ms 硬超时门禁
    }

    public VirtualThreadIsolatedExecutor(long defaultTimeoutMillis) {
        // 利用 Java 21 虚拟线程执行器，每个工具任务独享独立堆栈，阻塞时自动从 Carrier 线程卸载
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.defaultTimeoutMillis = defaultTimeoutMillis > 0 ? defaultTimeoutMillis : 5000L;
    }

    /**
     * 在隔离的虚拟线程中执行单步外部任务，附带硬超时熔断
     *
     * @param toolName      工具名称
     * @param task          任务逻辑 Supplier
     * @param timeoutMillis 超时时间 (毫秒，<=0 则使用默认值)
     * @param <T>           返回类型
     * @return 执行结果
     */
    public <T> T executeWithTimeout(String toolName, Supplier<T> task, long timeoutMillis)
            throws TimeoutException, ExecutionException, InterruptedException {
        long effectiveTimeout = timeoutMillis > 0 ? timeoutMillis : this.defaultTimeoutMillis;
        Future<T> future = virtualExecutor.submit(task::get);

        try {
            return future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            log.warn("[VirtualThreadIsolatedExecutor] 外部工具 {} 超过硬门禁 ({}ms)，立即强制发送物理中断信号！",
                    toolName, effectiveTimeout);
            // 物理中断目标虚拟线程，防止阻塞挂起
            future.cancel(true);
            throw te;
        } catch (InterruptedException | ExecutionException e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * 获取底层虚拟线程执行器实例 (用于并发 invokeAll 等高级操作)
     */
    public ExecutorService getVirtualExecutor() {
        return virtualExecutor;
    }

    @Override
    public void close() {
        try {
            virtualExecutor.shutdownNow();
        } catch (Exception e) {
            log.warn("[VirtualThreadIsolatedExecutor] 关闭虚拟线程执行器异常", e);
        }
    }
}
