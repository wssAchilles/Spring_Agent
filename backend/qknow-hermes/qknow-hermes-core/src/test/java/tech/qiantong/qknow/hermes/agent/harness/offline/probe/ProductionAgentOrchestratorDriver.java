package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import tech.qiantong.qknow.hermes.agent.AgentOrchestrator;
import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * 生产级真实执行驱动器 (Production AgentOrchestrator Driver)。
 *
 * <p>直接桥接 Hermes 真实 AgentOrchestrator，发起底层真实大模型（DeepSeek 官方 API）
 * 与本地安全工具链的真实调用，捕获真实的物理执行轨迹与资源消耗。</p>
 */
public class ProductionAgentOrchestratorDriver implements HarnessExecutionDriver {

    private final AgentOrchestrator orchestrator;

    public ProductionAgentOrchestratorDriver(AgentOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator 不能为空");
    }

    @Override
    public Flux<ChatEvent> execute(ChatRequest request) {
        return orchestrator.chat(request);
    }

    @Override
    public HarnessEvidenceRecord getLatestEvidence(String requestId) {
        return orchestrator.getLatestEvidence(requestId);
    }

    @Override
    public DriverMode getMode() {
        return DriverMode.REAL_PRODUCTION;
    }
}
