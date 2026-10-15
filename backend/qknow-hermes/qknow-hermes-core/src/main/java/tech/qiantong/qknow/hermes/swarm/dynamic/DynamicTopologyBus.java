package tech.qiantong.qknow.hermes.swarm.dynamic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 动态拓扑稀疏化通信网络与因果时序消息总线 (Dynamic Topology & Causal Event Bus)
 * 1. 基于阿里千问 1536 维超球面测地大圆弧散度只激活 Top-K 专家智能体，规避 O(N^2) 广播风暴；
 * 2. 结合夏普利边际贡献率平滑剪枝冗余智能体；
 * 3. 基于因果时序向量时钟 (Vector Clock) 保障跨智能体异步通信的偏序与无冲突；
 * 4. 签发带常量时间防伪验签的不可变审计凭单。
 */
public class DynamicTopologyBus {

    public static final int VECTOR_DIMENSION = 1536;
    private static final double NORM_TOLERANCE = 1e-3;

    public record AgentProfile(String agentId, String role, float[] capabilityVector) {
        public AgentProfile {
            Objects.requireNonNull(agentId, "agentId cannot be null");
            Objects.requireNonNull(role, "role cannot be null");
            Objects.requireNonNull(capabilityVector, "capabilityVector cannot be null");
            if (capabilityVector.length != VECTOR_DIMENSION) {
                throw new IllegalArgumentException("capabilityVector must be " + VECTOR_DIMENSION + " dims, got: " + capabilityVector.length);
            }
            double normSq = 0.0;
            for (float f : capabilityVector) {
                normSq += f * f;
            }
            double norm = Math.sqrt(normSq);
            if (Math.abs(norm - 1.0) > NORM_TOLERANCE) {
                throw new IllegalArgumentException("capabilityVector must be L2-normalized unit vector (||v||_2 = 1.0), got norm: " + norm);
            }
        }
    }

    private final String secretKey;
    private final Map<String, AgentProfile> agentRegistry = new ConcurrentHashMap<>();
    private final Map<String, SwarmVectorClock> agentClocks = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<SwarmCausalEvent>>> eventListeners = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> taskActiveAgents = new ConcurrentHashMap<>();

    public DynamicTopologyBus() {
        this("DEFAULT_DYNAMIC_TOPOLOGY_SECRET_" + System.currentTimeMillis());
    }

    public DynamicTopologyBus(String secretKey) {
        this.secretKey = secretKey == null ? "DEFAULT_TOPOLOGY_KEY" : secretKey;
    }

    public void registerAgent(AgentProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        agentRegistry.put(profile.agentId(), profile);
        agentClocks.put(profile.agentId(), SwarmVectorClock.initial());
    }

    public AgentProfile getAgentProfile(String agentId) {
        return agentRegistry.get(agentId);
    }

    /**
     * 基于超球面测地大圆弧散度自组织激活 Top-K 专家智能体拓扑
     *
     * @param taskId     任务唯一标识
     * @param taskVector 1536 维超球面任务需求向量
     * @param topK       最大激活专家数
     * @return 拓扑激活审计凭单
     */
    public DynamicTopologyReceipt activateSubTopology(String taskId, float[] taskVector, int topK) {
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(taskVector, "taskVector cannot be null");
        if (taskVector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException("taskVector must be " + VECTOR_DIMENSION + " dims");
        }

        long startNanos = System.nanoTime();
        long timestamp = System.currentTimeMillis();
        String receiptId = "topo_rcpt_" + UUID.randomUUID().toString().replace("-", "");

        record Candidate(String agentId, float geodesicDistance) {}
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Float> geodesicScores = new HashMap<>();

        for (AgentProfile profile : agentRegistry.values()) {
            float[] vec = profile.capabilityVector();
            float dot = 0.0f;
            for (int i = 0; i < VECTOR_DIMENSION; i++) {
                dot += taskVector[i] * vec[i];
            }
            float cosine = Math.max(-1.0f, Math.min(1.0f, dot));
            // 测地散度 d_g = arccos(u·v) / pi
            float geodesic = (float) (Math.acos(cosine) / Math.PI);
            candidates.add(new Candidate(profile.agentId(), geodesic));
            geodesicScores.put(profile.agentId(), geodesic);
        }

        // 按测地距离升序排列（散度越小越契合）
        candidates.sort(Comparator.comparingDouble(Candidate::geodesicDistance));

        int effectiveK = Math.min(topK, candidates.size());
        List<String> activated = new ArrayList<>();
        for (int i = 0; i < effectiveK; i++) {
            activated.add(candidates.get(i).agentId());
        }

        taskActiveAgents.put(taskId, ConcurrentHashMap.newKeySet());
        taskActiveAgents.get(taskId).addAll(activated);

        long durationMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);
        String sig = DynamicTopologyReceipt.calculateSignature(
                receiptId, taskId, activated.size(), 0, durationMicros, timestamp, secretKey);

        return new DynamicTopologyReceipt(
                receiptId, taskId, activated, Collections.emptyList(),
                geodesicScores, durationMicros, timestamp, sig);
    }

    /**
     * 基于夏普利边际贡献值动态淘汰沉睡节点
     */
    public DynamicTopologyReceipt pruneLowContributionAgents(String taskId, Map<String, Double> shapleyValues, double threshold) {
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Set<String> activeSet = taskActiveAgents.get(taskId);
        if (activeSet == null || shapleyValues == null) {
            return null;
        }

        long startNanos = System.nanoTime();
        long timestamp = System.currentTimeMillis();
        String receiptId = "prune_rcpt_" + UUID.randomUUID().toString().replace("-", "");

        List<String> pruned = new ArrayList<>();
        for (String agentId : new ArrayList<>(activeSet)) {
            double shapley = shapleyValues.getOrDefault(agentId, 0.0);
            if (shapley < threshold) {
                activeSet.remove(agentId);
                pruned.add(agentId);
            }
        }

        List<String> remaining = new ArrayList<>(activeSet);
        Collections.sort(remaining);
        Collections.sort(pruned);

        long durationMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);
        String sig = DynamicTopologyReceipt.calculateSignature(
                receiptId, taskId, remaining.size(), pruned.size(), durationMicros, timestamp, secretKey);

        return new DynamicTopologyReceipt(
                receiptId, taskId, remaining, pruned, Collections.emptyMap(), durationMicros, timestamp, sig);
    }

    public Set<String> getActiveAgents(String taskId) {
        return Collections.unmodifiableSet(taskActiveAgents.getOrDefault(taskId, Collections.emptySet()));
    }

    /**
     * 订阅指定智能体的因果消息事件
     */
    public void subscribe(String agentId, Consumer<SwarmCausalEvent> listener) {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        eventListeners.computeIfAbsent(agentId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 发布带有因果向量时钟的事件
     */
    public SwarmCausalEvent createAndPublishEvent(String senderId, String targetId, String eventType, String payload) {
        Objects.requireNonNull(senderId, "senderId cannot be null");
        Objects.requireNonNull(targetId, "targetId cannot be null");

        // 发送方时钟自增
        SwarmVectorClock senderClock = agentClocks.compute(senderId, (k, v) -> (v == null ? SwarmVectorClock.initial() : v).tick(senderId));

        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();
        String sig = SwarmCausalEvent.calculateSignature(eventId, senderId, targetId, eventType, payload, timestamp, secretKey);

        SwarmCausalEvent event = new SwarmCausalEvent(
                eventId, senderId, targetId, eventType, payload, senderClock, timestamp, sig);

        // 接收方合并向量时钟
        agentClocks.compute(targetId, (k, v) -> (v == null ? SwarmVectorClock.initial() : v).merge(senderClock).tick(targetId));

        // 触发监听器分发
        List<Consumer<SwarmCausalEvent>> listeners = eventListeners.get(targetId);
        if (listeners != null) {
            for (Consumer<SwarmCausalEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (Exception ignored) {}
            }
        }
        return event;
    }

    public SwarmVectorClock getAgentClock(String agentId) {
        return agentClocks.getOrDefault(agentId, SwarmVectorClock.initial());
    }
}
