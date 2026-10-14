package tech.qiantong.qknow.hermes.search.hybrid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.search.ColbertNative;
import tech.qiantong.qknow.hermes.search.ColbertScoreReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹性混合检索漏斗协调器 (Resilient Hybrid Retrieval Funnel)
 * 整合千问 1536 维超球面稠密向量 + 嵌入式 BM25 稀疏倒排 + 无偏 RRF 粗排漏斗 + 第一阶段 ColBERT MaxSim 原生 SIMD 精排
 */
public class ResilientHybridRetrievalFunnel {

    private static final Logger log = LoggerFactory.getLogger(ResilientHybridRetrievalFunnel.class);
    public static final int DEFAULT_ROUGH_POOL_SIZE = 500;
    public static final int EMBEDDING_DIM = 1536;

    private final EmbeddedInvertedIndexEngine invertedIndexEngine;
    private final ReciprocalRankFusionEngine rrfEngine;

    // 内存稠密向量库: docId -> 1536 维单位向量
    private final Map<String, float[]> denseVectorIndex = new ConcurrentHashMap<>();
    // 内存多 Token 嵌入库 (供 ColBERT MaxSim 精排使用): docId -> float[] (连续展平多 Token 向量)
    private final Map<String, float[]> multiTokenDocIndex = new ConcurrentHashMap<>();
    private final Map<String, Integer> multiTokenDocLens = new ConcurrentHashMap<>();

    public ResilientHybridRetrievalFunnel() {
        this(new EmbeddedInvertedIndexEngine(), new ReciprocalRankFusionEngine());
    }

    public ResilientHybridRetrievalFunnel(
            EmbeddedInvertedIndexEngine invertedIndexEngine,
            ReciprocalRankFusionEngine rrfEngine
    ) {
        this.invertedIndexEngine = Objects.requireNonNull(invertedIndexEngine, "invertedIndexEngine 不能为空");
        this.rrfEngine = Objects.requireNonNull(rrfEngine, "rrfEngine 不能为空");
    }

    /**
     * 注册或更新文档索引
     * @param docId 文档全局唯一标识
     * @param text 文档文本内容
     * @param dense1536Vector 阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
     * @param multiTokenVectors 文档多 Token 连续向量 (tokenCount * 1536 维)，若为 null 则自动使用 dense1536Vector 作为单 Token
     * @param tokenCount 多 Token 数量
     */
    public void indexDocument(
            String docId,
            String text,
            float[] dense1536Vector,
            float[] multiTokenVectors,
            int tokenCount
    ) {
        Objects.requireNonNull(docId, "docId 不能为空");
        Objects.requireNonNull(text, "text 不能为空");
        Objects.requireNonNull(dense1536Vector, "dense1536Vector 不能为空");

        if (dense1536Vector.length != EMBEDDING_DIM) {
            throw new IllegalArgumentException("千问稠密向量维度必须严格为 " + EMBEDDING_DIM + " 维");
        }

        // 1. 建立稀疏倒排索引
        invertedIndexEngine.indexDocument(docId, text);

        // 2. 建立稠密向量索引
        denseVectorIndex.put(docId, Arrays.copyOf(dense1536Vector, EMBEDDING_DIM));

        // 3. 建立多 Token 晚期交互向量索引
        if (multiTokenVectors != null && tokenCount > 0) {
            multiTokenDocIndex.put(docId, Arrays.copyOf(multiTokenVectors, multiTokenVectors.length));
            multiTokenDocLens.put(docId, tokenCount);
        } else {
            multiTokenDocIndex.put(docId, Arrays.copyOf(dense1536Vector, EMBEDDING_DIM));
            multiTokenDocLens.put(docId, 1);
        }
    }

    /**
     * 执行双路召回 + RRF 融合 + 扩大至 Top-500 候选 + ColBERT MaxSim SIMD 硬件精排
     */
    public HybridRetrievalReceipt retrieve(
            String query,
            float[] queryDenseEmbedding,
            float[] queryTokenEmbeddings,
            int queryTokenCount,
            int finalTopK
    ) {
        Objects.requireNonNull(query, "query 不能为空");
        Objects.requireNonNull(queryDenseEmbedding, "queryDenseEmbedding 不能为空");
        long startNanos = System.nanoTime();

        // 1. 稀疏通道检索 (BM25)
        List<Map.Entry<String, Float>> sparseHits = invertedIndexEngine.search(query, DEFAULT_ROUGH_POOL_SIZE);

        // 2. 稠密通道检索 (千问 1536 维超球面余弦点积)
        List<Map.Entry<String, Float>> denseHits = searchDense(queryDenseEmbedding, DEFAULT_ROUGH_POOL_SIZE);

        // 3. 自适应推断两路通道权重并执行无偏 RRF 融合
        double[] weights = rrfEngine.inferAdaptiveWeights(query);
        double denseWeight = weights[0];
        double sparseWeight = weights[1];

        List<HybridRetrievalCandidate> rrfCandidates = rrfEngine.fuse(
                denseHits,
                sparseHits,
                invertedIndexEngine.getAllDocumentTexts(),
                denseWeight,
                sparseWeight
        );

        if (rrfCandidates.isEmpty()) {
            long duration = System.nanoTime() - startNanos;
            return HybridRetrievalReceipt.create(
                    query, denseHits.size(), sparseHits.size(), 0, Collections.emptyList(), duration, "NONE"
            );
        }

        // 4. 截取粗排候选池 (由 50 扩大 10 倍至最多 500)
        int poolSize = Math.min(DEFAULT_ROUGH_POOL_SIZE, rrfCandidates.size());
        List<HybridRetrievalCandidate> pool = rrfCandidates.subList(0, poolSize);

        // 5. 准备 ColBERT MaxSim 5ms 原生精排参数
        int numDocsToRerank = pool.size();
        int[] docLens = new int[numDocsToRerank];
        int totalDocTokens = 0;
        for (int i = 0; i < numDocsToRerank; i++) {
            String docId = pool.get(i).docId();
            int len = multiTokenDocLens.getOrDefault(docId, 1);
            docLens[i] = len;
            totalDocTokens += len;
        }

        float[] flattenedDocTokens = new float[totalDocTokens * EMBEDDING_DIM];
        int currentFloatOffset = 0;
        for (int i = 0; i < numDocsToRerank; i++) {
            String docId = pool.get(i).docId();
            float[] tokens = multiTokenDocIndex.get(docId);
            if (tokens != null) {
                System.arraycopy(tokens, 0, flattenedDocTokens, currentFloatOffset, tokens.length);
                currentFloatOffset += tokens.length;
            }
        }

        float[] actualQueryTokens = (queryTokenEmbeddings != null && queryTokenCount > 0)
                ? queryTokenEmbeddings
                : queryDenseEmbedding;
        int actualQueryLen = (queryTokenEmbeddings != null && queryTokenCount > 0)
                ? queryTokenCount
                : 1;

        // 6. 调用第一阶段落地的 ColBERT MaxSim 原生加速算子
        ColbertScoreReceipt colbertReceipt = ColbertNative.computeMaxSimWithReceipt(
                actualQueryTokens,
                actualQueryLen,
                flattenedDocTokens,
                docLens,
                numDocsToRerank,
                EMBEDDING_DIM
        );

        float[] rerankScores = colbertReceipt.getScoresArray();
        List<HybridRetrievalCandidate> rerankedList = new ArrayList<>(numDocsToRerank);
        for (int i = 0; i < numDocsToRerank; i++) {
            HybridRetrievalCandidate original = pool.get(i);
            rerankedList.add(original.withFinalRerankScore(rerankScores[i]));
        }

        // 7. 根据最终重排得分降序排序并截断至 finalTopK
        rerankedList.sort(Comparator.comparing(HybridRetrievalCandidate::finalRerankScore).reversed());

        int resultSize = Math.min(finalTopK, rerankedList.size());
        List<HybridRetrievalCandidate> finalTopKList = List.copyOf(rerankedList.subList(0, resultSize));

        long duration = System.nanoTime() - startNanos;
        return HybridRetrievalReceipt.create(
                query,
                denseHits.size(),
                sparseHits.size(),
                numDocsToRerank,
                finalTopKList,
                duration,
                colbertReceipt.executionMode()
        );
    }

    private List<Map.Entry<String, Float>> searchDense(float[] queryEmbedding, int topK) {
        List<Map.Entry<String, Float>> list = new ArrayList<>(denseVectorIndex.size());
        for (Map.Entry<String, float[]> entry : denseVectorIndex.entrySet()) {
            float dot = 0.0f;
            float[] docVec = entry.getValue();
            for (int i = 0; i < EMBEDDING_DIM; i++) {
                dot += queryEmbedding[i] * docVec[i];
            }
            list.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), dot));
        }
        list.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        if (list.size() > topK) {
            return List.copyOf(list.subList(0, topK));
        }
        return List.copyOf(list);
    }

    public EmbeddedInvertedIndexEngine getInvertedIndexEngine() {
        return invertedIndexEngine;
    }

    public int getIndexedDocumentCount() {
        return denseVectorIndex.size();
    }
}
