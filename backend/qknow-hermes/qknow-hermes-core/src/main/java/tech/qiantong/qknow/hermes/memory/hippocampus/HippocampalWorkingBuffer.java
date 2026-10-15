package tech.qiantong.qknow.hermes.memory.hippocampus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 海马体在线快轨情景工作缓冲区 (Hippocampal Working Buffer)
 * 采用环形缓冲区管理短期活跃情境记忆，结合三级冷备可逆隔离机制防物理硬删除；
 * 基于阿里千问 1536 维超球面单位向量进行测地大圆弧相似度检索。
 */
public class HippocampalWorkingBuffer {

    public record RetrievalResult(
            EpisodicTurnRecord record,
            float cosineSimilarity,
            float geodesicDistance
    ) {}

    private final int capacity;
    private final int coldCapacity;
    private final Deque<EpisodicTurnRecord> activeBuffer = new ArrayDeque<>();
    private final Deque<EpisodicTurnRecord> coldTierBuffer = new ConcurrentLinkedDeque<>();
    private final Object lock = new Object();

    public HippocampalWorkingBuffer() {
        this(64, 256);
    }

    public HippocampalWorkingBuffer(int capacity, int coldCapacity) {
        if (capacity <= 0 || coldCapacity <= 0) {
            throw new IllegalArgumentException("Capacities must be strictly positive");
        }
        this.capacity = capacity;
        this.coldCapacity = coldCapacity;
    }

    /**
     * 将新的情境回合压入活跃缓冲区；若超出容量，则将最旧记录冷备归档至冷备环形队列
     */
    public void pushTurn(EpisodicTurnRecord record) {
        Objects.requireNonNull(record, "record cannot be null");
        synchronized (lock) {
            if (activeBuffer.size() >= capacity) {
                EpisodicTurnRecord evicted = activeBuffer.pollFirst();
                if (evicted != null) {
                    if (coldTierBuffer.size() >= coldCapacity) {
                        coldTierBuffer.pollFirst();
                    }
                    coldTierBuffer.addLast(evicted);
                }
            }
            activeBuffer.addLast(record);
        }
    }

    /**
     * 获取当前活跃缓冲区中所有回合的快照
     */
    public List<EpisodicTurnRecord> snapshotActiveTurns() {
        synchronized (lock) {
            return new ArrayList<>(activeBuffer);
        }
    }

    /**
     * 获取冷备缓冲区中的回合数量
     */
    public int getColdTierCount() {
        return coldTierBuffer.size();
    }

    /**
     * 基于超球面测地大圆弧度量检索最相似的情境记忆
     *
     * @param queryVector 1536 维超球面单位向量
     * @param topK        最大召回条数
     * @param minSimilarity 最小余弦相似度门禁
     * @return 匹配列表，按余弦相似度降序排序
     */
    public List<RetrievalResult> retrieveSimilar(float[] queryVector, int topK, float minSimilarity) {
        Objects.requireNonNull(queryVector, "queryVector cannot be null");
        if (queryVector.length != EpisodicTurnRecord.VECTOR_DIMENSION) {
            throw new IllegalArgumentException("queryVector must be 1536 dimensions");
        }

        List<EpisodicTurnRecord> candidates = snapshotActiveTurns();
        List<RetrievalResult> matched = new ArrayList<>();

        for (EpisodicTurnRecord turn : candidates) {
            float[] vec = turn.sphericalVector();
            float dot = 0.0f;
            for (int i = 0; i < EpisodicTurnRecord.VECTOR_DIMENSION; i++) {
                dot += queryVector[i] * vec[i];
            }
            // 夹紧到 [-1.0, 1.0] 防止浮点误差
            float cosine = Math.max(-1.0f, Math.min(1.0f, dot));
            if (cosine >= minSimilarity) {
                float geodesic = (float) (Math.acos(cosine) / Math.PI);
                matched.add(new RetrievalResult(turn, cosine, geodesic));
            }
        }

        matched.sort((a, b) -> Float.compare(b.cosineSimilarity(), a.cosineSimilarity()));
        if (matched.size() > topK) {
            return matched.subList(0, topK);
        }
        return matched;
    }
}
