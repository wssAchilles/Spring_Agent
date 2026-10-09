package tech.qiantong.qknow.module.kb.service.agent.retrieval;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.*;

/**
 * 阿里千问 1536 维超球面 RRF 融合重排与 Token 预算截断算子 (Hyperspherical RRF Reranker)
 * <p>
 * 遵循系统工程总纲 (AGENTS.md)：
 * 1. 唯一向量模型基线：千问 1536 维超球面单位向量空间 (||v||_2 = 1.0)，相似度度量为余弦测度 cos\theta = u \cdot v；
 * 2. 倒数排名融合 (RRF: Reciprocal Rank Fusion)：融合 Dense 向量、Sparse 关键字与多知识库多通道召回结果；
 * 3. 动态 Token 预算截断机制：从源头严控上下文膨胀，防止 DeepSeek API 首字延迟暴涨与 Token 费用失控；
 * 4. 产出纯 Java 21 Record 不可变凭单，具备完整审计可追溯性。
 * </p>
 */
@Slf4j
@Component
public class HypersphericalRrfReranker {

    /**
     * 千问 1536 维空间维度基线
     */
    public static final int EMBEDDING_DIM = 1536;

    /**
     * RRF 默认平滑常数 k
     */
    public static final double DEFAULT_RRF_K = 60.0;

    /**
     * 默认最大 Token 预算 (防止 Prompt 窗口溢出)
     */
    public static final int DEFAULT_MAX_TOKEN_BUDGET = 2048;

    /**
     * 默认超球面几何调制系数 alpha (0.0: 纯RRF, 1.0: 纯余弦)
     */
    public static final double DEFAULT_ALPHA = 0.35;

    /**
     * 单个重排切片不可变凭单
     */
    public record RerankedChunk(
            RetrieveResult chunk,
            double finalScore,
            double rrfScore,
            Double cosineSimilarity,
            int estimatedTokens,
            boolean truncated
    ) {}

    /**
     * 重排与预算截断不可变结果凭单
     */
    public record RrfRerankResult(
            List<RerankedChunk> selectedChunks,
            List<RerankedChunk> droppedChunks,
            int totalInputChunks,
            int selectedCount,
            int consumedTokens,
            int budgetLimit,
            boolean budgetExceeded,
            long elapsedMillis
    ) {
        public List<RetrieveResult> getSelectedRetrieveResults() {
            if (selectedChunks == null) return Collections.emptyList();
            return selectedChunks.stream().map(RerankedChunk::chunk).toList();
        }
    }

    /**
     * 通道排名候选切片
     */
    public record ChannelRankList(
            String channelName,
            double weight,
            List<RetrieveResult> rankedChunks
    ) {}

    /**
     * 单通道简单 RRF 重排与 Token 截断 (默认预算 2048)
     */
    public RrfRerankResult rerankAndTruncate(List<RetrieveResult> chunks) {
        return rerankAndTruncate(chunks, DEFAULT_MAX_TOKEN_BUDGET);
    }

    /**
     * 单通道按得分简单 RRF 重排与指定 Token 预算截断
     */
    public RrfRerankResult rerankAndTruncate(List<RetrieveResult> chunks, int maxTokenBudget) {
        if (chunks == null || chunks.isEmpty()) {
            return new RrfRerankResult(Collections.emptyList(), Collections.emptyList(), 0, 0, 0, maxTokenBudget, false, 0L);
        }
        ChannelRankList defaultChannel = new ChannelRankList("default_channel", 1.0, chunks);
        return rerankMultiChannels(List.of(defaultChannel), null, Collections.emptyMap(), DEFAULT_RRF_K, DEFAULT_ALPHA, maxTokenBudget);
    }

    /**
     * 多通道多路检索 RRF 融合重排与超球面几何调制及 Token 截断
     *
     * @param channels       多通道排名结果列表 (如 Dense 通道、Sparse 通道、各知识库通道)
     * @param queryVector    查询词千问 1536 维超球面单位向量 (可选，为空时不进行余弦调制)
     * @param chunkVectors   切片 ID/内容映射的千问 1536 维超球面向量表 (可选)
     * @param rrfK           RRF 平滑常数 (推荐 60.0)
     * @param alpha          超球面几何调制权重 (0.0~1.0)
     * @param maxTokenBudget 最大允许注入的 Token 预算门限
     * @return 包含入选切片与丢弃切片的不可变凭单
     */
    public RrfRerankResult rerankMultiChannels(
            List<ChannelRankList> channels,
            double[] queryVector,
            Map<String, double[]> chunkVectors,
            double rrfK,
            double alpha,
            int maxTokenBudget
    ) {
        long start = System.currentTimeMillis();
        if (channels == null || channels.isEmpty()) {
            return new RrfRerankResult(Collections.emptyList(), Collections.emptyList(), 0, 0, 0, maxTokenBudget, false, 0L);
        }

        // 1. 搜集所有独立切片并建立唯一索引
        Map<String, RetrieveResult> uniqueChunks = new LinkedHashMap<>();
        // 记录每个切片在各通道中的 rank: chunkKey -> (channelIdx -> 1-based rank)
        Map<String, Map<Integer, Integer>> chunkChannelRanks = new HashMap<>();

        for (int cIdx = 0; cIdx < channels.size(); cIdx++) {
            ChannelRankList channel = channels.get(cIdx);
            if (channel == null || channel.rankedChunks() == null) continue;

            List<RetrieveResult> list = channel.rankedChunks();
            for (int r = 0; r < list.size(); r++) {
                RetrieveResult chunk = list.get(r);
                if (chunk == null) continue;
                String key = getChunkKey(chunk);
                uniqueChunks.putIfAbsent(key, chunk);

                chunkChannelRanks.computeIfAbsent(key, k -> new HashMap<>()).put(cIdx, r + 1);
            }
        }

        if (uniqueChunks.isEmpty()) {
            return new RrfRerankResult(Collections.emptyList(), Collections.emptyList(), 0, 0, 0, maxTokenBudget, false, 0L);
        }

        // 2. 计算各切片的 RRF 倒数排名融合得分
        // RRF(d) = \sum_{m} \frac{w_m}{k + rank_m(d)}
        Map<String, Double> rrfScores = new HashMap<>();
        double maxRrf = 0.0;

        for (Map.Entry<String, RetrieveResult> entry : uniqueChunks.entrySet()) {
            String key = entry.getKey();
            Map<Integer, Integer> ranks = chunkChannelRanks.getOrDefault(key, Collections.emptyMap());

            double rrf = 0.0;
            for (int cIdx = 0; cIdx < channels.size(); cIdx++) {
                ChannelRankList channel = channels.get(cIdx);
                double w = channel.weight() > 0 ? channel.weight() : 1.0;
                Integer rank = ranks.get(cIdx);
                if (rank != null) {
                    rrf += w / (rrfK + rank);
                }
            }
            rrfScores.put(key, rrf);
            if (rrf > maxRrf) {
                maxRrf = rrf;
            }
        }

        // 3. 计算超球面余弦相似度与最终综合评分
        boolean hasQueryVector = isValidHypersphericalVector(queryVector);
        List<RerankedChunk> scoredChunks = new ArrayList<>();

        for (Map.Entry<String, RetrieveResult> entry : uniqueChunks.entrySet()) {
            String key = entry.getKey();
            RetrieveResult chunk = entry.getValue();
            double rawRrf = rrfScores.getOrDefault(key, 0.0);
            // 归一化 RRF 分数至 [0, 1]
            double normRrf = maxRrf > 0.0 ? rawRrf / maxRrf : 0.0;

            Double cosineSim = null;
            if (hasQueryVector && chunkVectors != null && chunkVectors.containsKey(key)) {
                double[] cVec = chunkVectors.get(key);
                if (isValidHypersphericalVector(cVec)) {
                    cosineSim = computeCosineSimilarity(queryVector, cVec);
                }
            }

            // 综合打分: (1 - alpha) * RRF_norm + alpha * CosineSim (若无向量则纯 RRF_norm)
            double finalScore;
            if (cosineSim != null) {
                double boundedCos = Math.max(0.0, Math.min(1.0, cosineSim));
                finalScore = (1.0 - alpha) * normRrf + alpha * boundedCos;
            } else {
                finalScore = normRrf;
            }

            int tokens = estimateTokens(chunk);
            scoredChunks.add(new RerankedChunk(chunk, finalScore, rawRrf, cosineSim, tokens, false));
        }

        // 4. 按综合得分降序排序
        scoredChunks.sort((a, b) -> Double.compare(b.finalScore(), a.finalScore()));

        // 5. 按照 Token 预算门限进行自适应截断
        List<RerankedChunk> selected = new ArrayList<>();
        List<RerankedChunk> dropped = new ArrayList<>();
        int currentTokens = 0;
        boolean exceeded = false;

        for (RerankedChunk rc : scoredChunks) {
            int chunkTok = rc.estimatedTokens();
            if (currentTokens + chunkTok <= maxTokenBudget) {
                selected.add(rc);
                currentTokens += chunkTok;
            } else {
                exceeded = true;
                // 标记被预算截断抛弃
                dropped.add(new RerankedChunk(rc.chunk(), rc.finalScore(), rc.rrfScore(),
                        rc.cosineSimilarity(), rc.estimatedTokens(), true));
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[RRF重排] 完成切片重排与Token预算截断: 输入切片={}, 入选={}, 丢弃={}, 消耗Token={}/{}, 耗时={}ms",
                uniqueChunks.size(), selected.size(), dropped.size(), currentTokens, maxTokenBudget, elapsed);

        return new RrfRerankResult(
                Collections.unmodifiableList(selected),
                Collections.unmodifiableList(dropped),
                uniqueChunks.size(),
                selected.size(),
                currentTokens,
                maxTokenBudget,
                exceeded,
                elapsed
        );
    }

    /**
     * 计算阿里千问 1536 维超球面单位向量的余弦相似度 (内积 \cos\theta = \mathbf{u} \cdot \mathbf{v})
     */
    public double computeCosineSimilarity(double[] u, double[] v) {
        if (u == null || v == null || u.length != EMBEDDING_DIM || v.length != EMBEDDING_DIM) {
            throw new IllegalArgumentException("向量必须严格符合千问 1536 维基线约束");
        }
        double dot = 0.0;
        // 循环累加内积
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            dot += u[i] * v[i];
        }
        // 限制在 [-1.0, 1.0]
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    /**
     * 校验是否为合法千问 1536 维超球面单位向量
     */
    public boolean isValidHypersphericalVector(double[] vec) {
        if (vec == null || vec.length != EMBEDDING_DIM) {
            return false;
        }
        double normSq = 0.0;
        for (double d : vec) {
            normSq += d * d;
        }
        // 单位范数容差 0.05
        return Math.abs(normSq - 1.0) < 0.05;
    }

    /**
     * 估算切片的 Token 消耗
     */
    public int estimateTokens(RetrieveResult chunk) {
        if (chunk == null) return 0;
        if (chunk.getTokens() != null && chunk.getTokens() > 0) {
            return chunk.getTokens();
        }
        String text = chunk.getContent();
        if (StrUtil.isBlank(text)) return 0;

        // 中文约 1.6-1.8 字符 / Token，保留安全上限
        return Math.max(1, (int) Math.ceil(text.length() / 1.7));
    }

    private String getChunkKey(RetrieveResult chunk) {
        if (chunk.getId() != null && !chunk.getId().isBlank()) {
            return chunk.getId();
        }
        return chunk.getContent() != null ? String.valueOf(chunk.getContent().hashCode()) : UUID.randomUUID().toString();
    }
}
