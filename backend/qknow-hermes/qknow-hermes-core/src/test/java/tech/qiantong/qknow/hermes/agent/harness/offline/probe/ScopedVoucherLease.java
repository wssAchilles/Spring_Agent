package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 不可变请求凭单租约管理与自动卸载器 (AutoCloseable Voucher Lease)。
 *
 * <p>吸收生命周期安全模式，确保离线探针执行中的临时请求凭单能够准确注入，
 * 并在响应式流消费结束或异常退出时强制幂等卸载，彻底杜绝提前注销竞争与堆内存悬挂泄漏。</p>
 */
public final class ScopedVoucherLease implements AutoCloseable {

    private final AgentOrchestrator orchestrator;
    private final String probeRequestId;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ScopedVoucherLease(AgentOrchestrator orchestrator, String probeRequestId, HermesHarnessVoucher voucher) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator 不能为空");
        this.probeRequestId = Objects.requireNonNull(probeRequestId, "probeRequestId 不能为空");
        Objects.requireNonNull(voucher, "voucher 不能为空");
        this.orchestrator.registerRequestVoucher(probeRequestId, voucher);
    }

    public String getProbeRequestId() {
        return probeRequestId;
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            orchestrator.unregisterRequestVoucher(probeRequestId);
        }
    }
}
