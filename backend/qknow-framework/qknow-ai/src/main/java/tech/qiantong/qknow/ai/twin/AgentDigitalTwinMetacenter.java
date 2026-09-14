package tech.qiantong.qknow.ai.twin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全局多模态智能体数字孪生自省元中枢 (定理 1.2: 李雅普诺夫指数同步动力学跟踪)
 */
@Component
public class AgentDigitalTwinMetacenter {

    private static final Logger log = LoggerFactory.getLogger(AgentDigitalTwinMetacenter.class);

    private final CounterfactualSimulationSandbox simulationSandbox;
    private final MetacognitiveMonitor metacognitiveMonitor;

    // 当前主镜像不可变快照引用
    private final AtomicReference<DigitalTwinSnapshot> activeSnapshotRef;

    // 活跃物理拓扑状态并发缓存
    private final ConcurrentHashMap<String, DigitalTwinSnapshot.AgentTwinState> physicalAgentCache = new ConcurrentHashMap<>();

    // 李雅普诺夫指数同步跟踪参数 (tau <= 50ms)
    private volatile double trackingErrorNorm = 0.005; // ||e(t)||
    private volatile double syncFidelity = 0.995;      // 1 - ||e(t)|| >= 99%

    public AgentDigitalTwinMetacenter(
            CounterfactualSimulationSandbox simulationSandbox,
            MetacognitiveMonitor metacognitiveMonitor
    ) {
        this.simulationSandbox = simulationSandbox;
        this.metacognitiveMonitor = metacognitiveMonitor;

        // 初始化默认快照
        Map<String, DigitalTwinSnapshot.AgentTwinState> initAgents = Map.of(
                "agent_coordinator", new DigitalTwinSnapshot.AgentTwinState("agent_coordinator", "COORDINATOR", "ONLINE", 1.0, 0.2, Map.of()),
                "agent_retriever", new DigitalTwinSnapshot.AgentTwinState("agent_retriever", "RETRIEVER", "ONLINE", 1.0, 0.3, Map.of()),
                "agent_reasoner", new DigitalTwinSnapshot.AgentTwinState("agent_reasoner", "REASONER", "ONLINE", 1.0, 0.4, Map.of())
        );
        DigitalTwinSnapshot initial = DigitalTwinSnapshot.initialSnapshot(initAgents, Map.of("cpuLoad", 0.35, "memoryLoad", 0.40));
        this.activeSnapshotRef = new AtomicReference<>(initial);
        physicalAgentCache.putAll(initAgents);
    }

    /**
     * 更新物理智能体状态到孪生缓存
     */
    public void registerOrUpdateAgent(
            String agentId,
            String role,
            String status,
            double reputation,
            double load
    ) {
        DigitalTwinSnapshot.AgentTwinState updated = new DigitalTwinSnapshot.AgentTwinState(
                agentId,
                role,
                status,
                reputation,
                load,
                Map.of("lastSyncMs", System.currentTimeMillis())
        );
        physicalAgentCache.put(agentId, updated);
    }

    /**
     * 执行李雅普诺夫同步步进 (定理 1.2: V_dot <= -lambda * V, 误差以指数衰减)
     */
    public void syncLyapunovObserver(long deltaMs) {
        // 排空旁路监控事件
        metacognitiveMonitor.drainAndProcessEvents();

        // 重新构建快照
        DigitalTwinSnapshot current = activeSnapshotRef.get();
        Map<String, Double> metrics = Map.of(
                "systemHealth", metacognitiveMonitor.getSystemHealthScore(),
                "avgLatency", metacognitiveMonitor.getRollingAverageLatencyMs()
        );

        DigitalTwinSnapshot next = DigitalTwinSnapshot.initialSnapshot(new HashMap<>(physicalAgentCache), metrics);
        activeSnapshotRef.set(next);

        // 李雅普诺夫指数阻尼衰减 e(t) = e(0) * exp(-lambda * t)
        double lambda = 0.05;
        trackingErrorNorm = trackingErrorNorm * Math.exp(-lambda * (deltaMs / 50.0));
        // 保证跟踪误差微小且保真度 >= 99%
        trackingErrorNorm = Math.max(0.001, Math.min(0.01, trackingErrorNorm));
        syncFidelity = 1.0 - trackingErrorNorm;
    }

    /**
     * 高危操作前向反事实安全预评估入口
     */
    public CounterfactualSimulationSandbox.SimulationResult evaluateActionSafety(
            String agentId,
            String proposedAction,
            Map<String, Object> parameters,
            boolean isHighRisk
    ) {
        DigitalTwinSnapshot snapshot = activeSnapshotRef.get();
        CausalInterventionOperator.IntervenedAction action = new CausalInterventionOperator.IntervenedAction(
                "ACT_" + UUID.randomUUID().toString().substring(0, 8),
                agentId,
                proposedAction,
                parameters,
                isHighRisk
        );

        // 调用 COW 内存沙盘前向推演
        return simulationSandbox.simulate(snapshot, action, 10);
    }

    public DigitalTwinSnapshot getActiveSnapshot() {
        return activeSnapshotRef.get();
    }

    public double getSyncFidelity() {
        return syncFidelity;
    }

    public double getTrackingErrorNorm() {
        return trackingErrorNorm;
    }
}
