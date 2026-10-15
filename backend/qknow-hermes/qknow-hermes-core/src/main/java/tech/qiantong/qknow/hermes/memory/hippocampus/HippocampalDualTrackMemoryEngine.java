package tech.qiantong.qknow.hermes.memory.hippocampus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海马体双轨情境长程记忆协调引擎 (Hippocampal Dual-Track Memory Engine)
 * 统一调度在线快轨 (Working Buffer) 与离线慢轨 (Sleep Consolidator)，
 * 实现情境经验的低延迟在线存取与睡眠期高阶图谱法则不变量沉淀。
 */
public class HippocampalDualTrackMemoryEngine {

    private final String secretKey;
    private final Map<String, HippocampalWorkingBuffer> sessionBuffers = new ConcurrentHashMap<>();
    private final HippocampalSleepConsolidator sleepConsolidator;

    public HippocampalDualTrackMemoryEngine() {
        this("DEFAULT_DUAL_TRACK_SECRET_" + System.currentTimeMillis());
    }

    public HippocampalDualTrackMemoryEngine(String secretKey) {
        this.secretKey = secretKey == null ? "DEFAULT_DUAL_KEY" : secretKey;
        this.sleepConsolidator = new HippocampalSleepConsolidator(this.secretKey);
    }

    /**
     * 记录一轮情境经验，压入在线快轨工作缓冲区
     */
    public EpisodicTurnRecord recordTurn(String sessionId, String role, String content,
                                         float[] sphericalVector, Map<String, String> keyFacts) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
        Objects.requireNonNull(sphericalVector, "sphericalVector cannot be null");

        String turnId = "turn_" + UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();
        String sig = EpisodicTurnRecord.calculateSignature(turnId, sessionId, role, content, timestamp, secretKey);

        EpisodicTurnRecord record = new EpisodicTurnRecord(
                turnId, sessionId, role, content, sphericalVector, keyFacts, timestamp, sig);

        HippocampalWorkingBuffer buffer = sessionBuffers.computeIfAbsent(sessionId, k -> new HippocampalWorkingBuffer());
        buffer.pushTurn(record);
        return record;
    }

    /**
     * 在线快轨测地大圆弧相似度召回
     */
    public List<HippocampalWorkingBuffer.RetrievalResult> retrieveSimilarMemories(
            String sessionId, float[] queryVector, int topK, float minSimilarity) {
        HippocampalWorkingBuffer buffer = sessionBuffers.get(sessionId);
        if (buffer == null) {
            return List.of();
        }
        return buffer.retrieveSimilar(queryVector, topK, minSimilarity);
    }

    /**
     * 触发指定会话的离线睡眠整合
     */
    public SleepConsolidationReceipt triggerSleepConsolidation(String sessionId) {
        HippocampalWorkingBuffer buffer = sessionBuffers.get(sessionId);
        List<EpisodicTurnRecord> turns = buffer != null ? buffer.snapshotActiveTurns() : List.of();
        return sleepConsolidator.consolidateSession(sessionId, turns);
    }

    /**
     * 获取离线睡眠整合历史记录
     */
    public List<SleepConsolidationReceipt> getConsolidationHistory(String sessionId) {
        return sleepConsolidator.getHistory(sessionId);
    }

    public int getWorkingBufferSize(String sessionId) {
        HippocampalWorkingBuffer buffer = sessionBuffers.get(sessionId);
        return buffer != null ? buffer.snapshotActiveTurns().size() : 0;
    }

    public int getColdTierBufferSize(String sessionId) {
        HippocampalWorkingBuffer buffer = sessionBuffers.get(sessionId);
        return buffer != null ? buffer.getColdTierCount() : 0;
    }
}
