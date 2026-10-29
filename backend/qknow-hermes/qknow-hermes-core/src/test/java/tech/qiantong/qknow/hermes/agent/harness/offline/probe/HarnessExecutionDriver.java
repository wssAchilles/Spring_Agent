package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import reactor.core.publisher.Flux;

/**
 * 离线执行底层驱动契约。
 *
 * <p>用于解耦离线调度器与底层执行实现，支持真实大模型生产驱动与确定性测试替身物理分流。</p>
 */
public interface HarnessExecutionDriver {

    /**
     * 执行智能体对话并返回事件流。
     */
    Flux<ChatEvent> execute(ChatRequest request);

    /**
     * 获取单次请求生成的最新证据凭单。
     */
    HarnessEvidenceRecord getLatestEvidence(String requestId);

    /**
     * 驱动模式。
     */
    DriverMode getMode();

    enum DriverMode {
        REAL_PRODUCTION,        // 真实大模型调用（DeepSeek 官方 API）
        DETERMINISTIC_DOUBLE    // 确定性单测替身
    }
}
