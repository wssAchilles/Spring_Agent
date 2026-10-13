package tech.qiantong.qknow.hermes.flow.stategraph.reducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateReductionReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 超步并发规约治理器 (SuperstepStateReducerGovernor)
 * 在 Bulk Synchronous Parallel (BSP) 屏障处原子汇聚多分支增量，消除覆写竞态，生成不可变密码学凭单。
 */
public class SuperstepStateReducerGovernor {

    private static final Logger log = LoggerFactory.getLogger(SuperstepStateReducerGovernor.class);

    private final StateGraphSchema schema;
    private final StateReductionColdRingBuffer coldRingBuffer;
    private final AtomicLong versionGenerator = new AtomicLong(1L);

    public record ReductionOutcome(
            Map<String, Object> reducedState,
            StateReductionReceipt receipt,
            int conflictCount
    ) {}

    public SuperstepStateReducerGovernor(StateGraphSchema schema, StateReductionColdRingBuffer coldRingBuffer) {
        this.schema = schema != null ? schema : StateGraphSchema.defaultSchema();
        this.coldRingBuffer = coldRingBuffer != null ? coldRingBuffer : new StateReductionColdRingBuffer(50);
    }

    public SuperstepStateReducerGovernor(StateGraphSchema schema) {
        this(schema, new StateReductionColdRingBuffer(50));
    }

    public SuperstepStateReducerGovernor() {
        this(StateGraphSchema.defaultSchema(), new StateReductionColdRingBuffer(50));
    }

    public StateReductionColdRingBuffer getColdRingBuffer() {
        return coldRingBuffer;
    }

    /**
     * 重载方法：支持以 NodeExecutionEnvelope 列表进行超步屏障原子规约
     */
    public ReductionOutcome reduceSuperstep(
            String executionId,
            int superstep,
            Map<String, Object> currentState,
            List<NodeExecutionEnvelope> envelopes,
            long fencingToken
    ) {
        List<BranchStateDelta> deltas = new ArrayList<>();
        if (envelopes != null) {
            long seq = 1;
            for (NodeExecutionEnvelope env : envelopes) {
                if (env.stateUpdates() != null && !env.stateUpdates().isEmpty()) {
                    deltas.add(BranchStateDelta.of(
                            env.nodeId(),
                            superstep,
                            seq++,
                            env.stateUpdates()
                    ));
                }
                if (env.explicitDeltas() != null) {
                    deltas.addAll(env.explicitDeltas());
                }
            }
        }
        return reduceSuperstepDeltas(executionId, superstep, currentState, deltas, fencingToken);
    }

    /**
     * 兼容传统直接传入 BranchStateDelta 列表的规约调用
     */
    public ReductionOutcome reduceSuperstep(
            String executionId,
            int superstep,
            Map<String, Object> currentState,
            List<BranchStateDelta> branchDeltas
    ) {
        return reduceSuperstepDeltas(executionId, superstep, currentState, branchDeltas, versionGenerator.incrementAndGet());
    }

    /**
     * 超步屏障汇聚点执行确定性归约核心算法
     */
    @SuppressWarnings("unchecked")
    private ReductionOutcome reduceSuperstepDeltas(
            String executionId,
            int superstep,
            Map<String, Object> currentState,
            List<BranchStateDelta> branchDeltas,
            long versionToken
    ) {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        Map<String, Object> workingState = new LinkedHashMap<>(currentState != null ? currentState : Map.of());
        String beforeHash = computeStateDigest(workingState);

        if (branchDeltas == null || branchDeltas.isEmpty()) {
            String afterHash = beforeHash;
            StateReductionReceipt receipt = StateReductionReceipt.createSigned(
                    "rcpt-red-" + UUID.randomUUID().toString().substring(0, 8),
                    executionId, superstep, beforeHash, afterHash,
                    0, 0, 0, versionToken, System.currentTimeMillis()
            );
            coldRingBuffer.recordReduction(executionId, superstep, workingState, receipt);
            return new ReductionOutcome(Collections.unmodifiableMap(workingState), receipt, 0);
        }

        // 按时序标记 (branchSequenceToken) 进行严格拓扑全序排序 (Lemma 1.3)
        List<BranchStateDelta> orderedDeltas = new ArrayList<>(branchDeltas);
        orderedDeltas.sort(Comparator.comparingLong(BranchStateDelta::branchSequenceToken));

        // 收集各通道在当前超步的所有增量值
        Map<String, List<Object>> channelUpdates = new LinkedHashMap<>();
        int conflicts = 0;

        for (BranchStateDelta delta : orderedDeltas) {
            delta.deltaUpdates().forEach((channel, value) -> {
                if (value != null) {
                    channelUpdates.computeIfAbsent(channel, k -> new ArrayList<>()).add(value);
                }
            });
        }

        int reducedChannels = 0;
        for (Map.Entry<String, List<Object>> entry : channelUpdates.entrySet()) {
            String channel = entry.getKey();
            List<Object> updates = entry.getValue();

            StateChannelDefinition<Object> def = (StateChannelDefinition<Object>) schema.getChannel(channel);
            StateChannelReducer<Object> reducer = (def != null) ? def.reducer() : StandardStateReducers.overwrite();

            Object currentVal = workingState.get(channel);
            if (currentVal == null && def != null && def.defaultValue() != null) {
                currentVal = def.defaultValue();
            }

            for (Object updateVal : updates) {
                try {
                    currentVal = reducer.reduce(currentVal, updateVal);
                } catch (IllegalStateException e) {
                    log.warn("【规约治理器】检测到通道冲突: channel={}, error={}", channel, e.getMessage());
                    conflicts++;
                    // 冲突排他策略下保留原有值，阻断脏写
                }
            }
            if (currentVal != null) {
                workingState.put(channel, currentVal);
                reducedChannels++;
            }
        }

        String afterHash = computeStateDigest(workingState);
        StateReductionReceipt receipt = StateReductionReceipt.createSigned(
                "rcpt-red-" + UUID.randomUUID().toString().substring(0, 8),
                executionId,
                superstep,
                beforeHash,
                afterHash,
                reducedChannels,
                conflicts,
                branchDeltas.size(),
                versionToken,
                System.currentTimeMillis()
        );

        // 记录至定长冷备环形缓冲区 (无物理硬删除软隔离)
        coldRingBuffer.recordReduction(executionId, superstep, workingState, receipt);

        return new ReductionOutcome(Collections.unmodifiableMap(workingState), receipt, conflicts);
    }

    private static String computeStateDigest(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return "empty-state-digest";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getKey()).append("=").append(String.valueOf(e.getValue())).append(";"));
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "state-digest-fallback";
        }
    }
}
