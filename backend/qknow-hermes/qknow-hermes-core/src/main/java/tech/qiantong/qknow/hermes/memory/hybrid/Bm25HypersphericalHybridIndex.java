package tech.qiantong.qknow.hermes.memory.hybrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯 Java 21 线程安全 BM25 × {@code S^1023} 单位超球面混合分片检索索引（Phase 11.3 BPR-LM，定理 3）。
 * <p>
 * 综合学术代码法医审计修复开源基准五大陷阱：
 * 1. 采用 Lucene 严格非负 IDF 公式 {@code ln(1 + (N - df + 0.5)/(df + 0.5))} 与 {@code avgdl} 零保护，消除 {@code rank_bm25} 小语料负 IDF 反转；
 * 2. 索引侧与查询侧严格复用同一分词器与停用词表，强保留数字+单位（如 {@code 16gb}、{@code $150}、{@code 2017}）及 CJK 单字/双字特征；
 * 3. 严格执行 BM25 零分剪枝（{@code bm25Score > 0}），杜绝无关键词匹配文档污染下游 RRF；
 * 4. 真正启用加权 RRF + 双路保底席位（Guarded Dual-Channel Quota）并全程保留精确分片坐标 {@code (docTitle, startOffset, endOffset)}；
 * 5. 采用全分片覆盖滑窗（{@code window=900, stride=700}），对每个完整分片编码 {@code L_2 = 1} 单位向量，彻底消除后半段盲区。
 * </p>
 */
public class Bm25HypersphericalHybridIndex {

    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;
    public static final double DEFAULT_RRF_K = 20.0;
    public static final int DEFAULT_SLICE_WINDOW_CHARS = 900;
    public static final int DEFAULT_SLICE_STRIDE_CHARS = 700;

    public static final double DEFAULT_WEIGHT_BM25 = 1.15;
    public static final double DEFAULT_WEIGHT_COSINE = 1.00;
    public static final double DEFAULT_WEIGHT_PPR = 0.45;

    private static final Pattern EN_NUM_TOKEN_PATTERN = Pattern.compile("\\$?\\d+(?:[.,:/-]\\d+)*(?:gb|mb|tb|kb|kg|lb|%|hours?|days?|weeks?|months?|years?)?|[a-z0-9][a-z0-9_'-]*");
    private static final Pattern NUMERIC_OR_UNIT_PATTERN = Pattern.compile(".*\\d.*");

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "is", "are", "was", "were", "be", "been", "being", "a", "an", "and", "or", "but",
            "in", "on", "at", "to", "for", "of", "with", "by", "from", "as", "into", "about", "like",
            "through", "after", "over", "between", "out", "against", "during", "without", "before",
            "under", "around", "among", "this", "that", "these", "those", "it", "its", "they", "their",
            "them", "he", "she", "his", "her", "we", "our", "you", "your", "i", "me", "my",
            "what", "which", "who", "whom", "whose", "where", "when", "why", "how",
            "do", "does", "did", "doing", "done", "have", "has", "had", "having",
            "will", "would", "could", "should", "may", "might", "can", "must",
            "not", "no", "nor", "very", "just", "than", "then", "there", "here", "all", "any", "both",
            "each", "few", "more", "most", "other", "some", "such", "only", "own", "same", "so", "too"
    );

    /**
     * 混合检出结果与不可变凭单。
     */
    public record HybridSearchOutcome(
            List<HybridScoredChunk> hits,
            HybridRetrievalReceipt receipt
    ) {}

    private record IndexedSlice(
            HybridChunkEntry entry,
            Map<String, Integer> termFreqs,
            int docLength
    ) {}

    private final double k1;
    private final double b;
    private final double rrfK;
    private final int sliceWindowChars;
    private final int sliceStrideChars;

    private final List<IndexedSlice> slices = new CopyOnWriteArrayList<>();
    private final Map<String, Map<Integer, Integer>> postingsByTerm = new ConcurrentHashMap<>();
    private final Map<String, Integer> dfByTerm = new ConcurrentHashMap<>();
    private final AtomicLong totalTokenLength = new AtomicLong(0L);
    private final AtomicInteger sliceSequence = new AtomicInteger(0);

    private final AtomicLong totalHybridSearches = new AtomicLong(0L);
    private final AtomicLong topBm25SumMilli = new AtomicLong(0L);
    private final AtomicLong topCosineSumMilli = new AtomicLong(0L);

    public Bm25HypersphericalHybridIndex() {
        this(DEFAULT_K1, DEFAULT_B, DEFAULT_RRF_K, DEFAULT_SLICE_WINDOW_CHARS, DEFAULT_SLICE_STRIDE_CHARS);
    }

    public Bm25HypersphericalHybridIndex(double k1, double b, double rrfK, int sliceWindowChars, int sliceStrideChars) {
        this.k1 = Math.max(0.1, k1);
        this.b = Math.max(0.0, Math.min(1.0, b));
        this.rrfK = Math.max(1.0, rrfK);
        this.sliceWindowChars = Math.max(200, sliceWindowChars);
        this.sliceStrideChars = Math.max(100, Math.min(this.sliceWindowChars, sliceStrideChars));
    }

    /**
     * 对完整文档/会话按滑窗全覆盖切片，并同步写入动态 BM25 倒排表与 S^1023 单位向量库。
     */
    public synchronized void indexDocument(
            String docTitle,
            String fullContent,
            String timestampStr,
            Function<String, float[]> unit1024Encoder
    ) {
        if (fullContent == null || fullContent.isBlank()) {
            return;
        }
        String safeTitle = docTitle != null ? docTitle : "untitled";
        String safeTs = timestampStr != null ? timestampStr : "";
        int len = fullContent.length();

        int pos = 0;
        int localIdx = 0;
        while (pos < len) {
            int end = Math.min(len, pos + sliceWindowChars);
            String sliceText = fullContent.substring(pos, end);
            float[] rawVec = unit1024Encoder != null ? unit1024Encoder.apply(sliceText) : new float[0];
            float[] unitVec = normalizeL2(rawVec);

            int globalSliceIdx = slices.size();
            String chunkId = safeTitle + "#s" + localIdx + "@" + sliceSequence.getAndIncrement();
            HybridChunkEntry entry = new HybridChunkEntry(
                    chunkId, safeTitle, localIdx, pos, end, sliceText, safeTs, unitVec
            );

            List<String> tokens = tokenize(safeTitle + " " + sliceText);
            Map<String, Integer> tf = new HashMap<>();
            for (String tok : tokens) {
                tf.merge(tok, 1, Integer::sum);
            }
            IndexedSlice slice = new IndexedSlice(entry, tf, tokens.size());
            slices.add(slice);
            totalTokenLength.addAndGet(tokens.size());

            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                String term = e.getKey();
                postingsByTerm.computeIfAbsent(term, k -> new ConcurrentHashMap<>()).put(globalSliceIdx, e.getValue());
                dfByTerm.merge(term, 1, Integer::sum);
            }

            if (end >= len) {
                break;
            }
            pos += sliceStrideChars;
            localIdx++;
        }
    }

    /**
     * 执行加权 RRF + 双路保底席位（Guarded Dual-Channel Quota）分片级混合检索。
     */
    public HybridSearchOutcome search(
            String requestId,
            String query,
            float[] queryUnit1024,
            Map<String, Double> optionalPprBoostByTitle,
            int topK
    ) {
        long t0 = System.nanoTime();
        String safeReq = (requestId == null || requestId.isBlank()) ? "req-hybrid" : requestId;
        String safeQuery = query != null ? query : "";
        int safeTopK = Math.max(1, topK);

        List<IndexedSlice> snapshot = List.copyOf(slices);
        int totalN = snapshot.size();
        if (totalN == 0) {
            HybridRetrievalReceipt receipt = new HybridRetrievalReceipt(
                    safeReq, safeQuery, 0, 0, 0.0, 0.0,
                    Math.max(1L, (System.nanoTime() - t0) / 1000L), System.currentTimeMillis()
            );
            totalHybridSearches.incrementAndGet();
            return new HybridSearchOutcome(List.of(), receipt);
        }

        List<String> queryTerms = tokenize(safeQuery);
        double avgdl = Math.max(1.0, (double) totalTokenLength.get() / totalN);
        double[] bm25Scores = new double[totalN];

        // 1. 稀疏 BM25 通道（Lucene 严格非负 IDF + 文档长度归一化）
        Set<String> uniqueQueryTerms = new LinkedHashSet<>(queryTerms);
        for (String term : uniqueQueryTerms) {
            Map<Integer, Integer> postings = postingsByTerm.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }
            int df = dfByTerm.getOrDefault(term, postings.size());
            double idf = Math.log(1.0 + (totalN - df + 0.5) / (df + 0.5));
            double termBoost = NUMERIC_OR_UNIT_PATTERN.matcher(term).matches() ? 1.45 : 1.0;

            for (Map.Entry<Integer, Integer> pe : postings.entrySet()) {
                int docIdx = pe.getKey();
                if (docIdx < 0 || docIdx >= totalN) {
                    continue;
                }
                int tf = pe.getValue();
                int dl = Math.max(1, snapshot.get(docIdx).docLength());
                double denom = tf + k1 * (1.0 - b + b * (dl / avgdl));
                double score = idf * ((tf * (k1 + 1.0)) / Math.max(1e-9, denom)) * termBoost;
                bm25Scores[docIdx] += score;
            }
        }

        // 严格零分剪枝后构建 BM25 降序榜
        List<Integer> bm25RankedIndices = new ArrayList<>();
        for (int i = 0; i < totalN; i++) {
            if (bm25Scores[i] > 1e-12) {
                bm25RankedIndices.add(i);
            }
        }
        bm25RankedIndices.sort((a, c) -> {
            int cmp = Double.compare(bm25Scores[c], bm25Scores[a]);
            return cmp != 0 ? cmp : Integer.compare(a, c);
        });
        int[] bm25Ranks = new int[totalN];
        for (int r = 0; r < bm25RankedIndices.size(); r++) {
            bm25Ranks[bm25RankedIndices.get(r)] = r + 1;
        }

        // 2. S^1023 单位超球面稠密余弦通道
        float[] normQueryVec = normalizeL2(queryUnit1024);
        double[] cosineScores = new double[totalN];
        List<Integer> cosineRankedIndices = new ArrayList<>(totalN);
        for (int i = 0; i < totalN; i++) {
            cosineScores[i] = cosine(normQueryVec, snapshot.get(i).entry().unitVector1024());
            cosineRankedIndices.add(i);
        }
        cosineRankedIndices.sort((a, c) -> {
            int cmp = Double.compare(cosineScores[c], cosineScores[a]);
            return cmp != 0 ? cmp : Integer.compare(a, c);
        });
        int[] cosineRanks = new int[totalN];
        for (int r = 0; r < cosineRankedIndices.size(); r++) {
            cosineRanks[cosineRankedIndices.get(r)] = r + 1;
        }

        // 3. 可选 PPR 图扩散加成通道
        double[] pprScores = new double[totalN];
        List<Integer> pprRankedIndices = new ArrayList<>();
        if (optionalPprBoostByTitle != null && !optionalPprBoostByTitle.isEmpty()) {
            for (int i = 0; i < totalN; i++) {
                double pScore = optionalPprBoostByTitle.getOrDefault(snapshot.get(i).entry().docTitle(), 0.0);
                pprScores[i] = Math.max(0.0, pScore);
                if (pprScores[i] > 1e-12) {
                    pprRankedIndices.add(i);
                }
            }
            pprRankedIndices.sort((a, c) -> {
                int cmp = Double.compare(pprScores[c], pprScores[a]);
                return cmp != 0 ? cmp : Integer.compare(a, c);
            });
        }
        int[] pprRanks = new int[totalN];
        for (int r = 0; r < pprRankedIndices.size(); r++) {
            pprRanks[pprRankedIndices.get(r)] = r + 1;
        }

        // 4. 加权 Guarded RRF 融合（真正应用权重，绝不丢失 BM25 唯一强锚点或 Dense 语义锚点）
        double[] fusedScores = new double[totalN];
        for (int i = 0; i < totalN; i++) {
            double s = 0.0;
            if (bm25Ranks[i] > 0) {
                s += DEFAULT_WEIGHT_BM25 / (rrfK + bm25Ranks[i]);
            }
            if (cosineRanks[i] > 0) {
                s += DEFAULT_WEIGHT_COSINE / (rrfK + cosineRanks[i]);
            }
            if (pprRanks[i] > 0) {
                s += DEFAULT_WEIGHT_PPR / (rrfK + pprRanks[i]);
            }
            fusedScores[i] = s;
        }

        // 双路保底席位机制 + Per-Document Diversity Pass（避免单篇多切分高命中霸占 Top-K 挤出其它独立会话）
        Set<Integer> selectedSet = new LinkedHashSet<>();
        Set<String> coveredDocTitles = new HashSet<>();
        int limit = Math.min(safeTopK, totalN);

        if (!bm25RankedIndices.isEmpty() && selectedSet.size() < limit) {
            int bm25TopIdx = bm25RankedIndices.get(0);
            selectedSet.add(bm25TopIdx);
            coveredDocTitles.add(snapshot.get(bm25TopIdx).entry().docTitle());
        }
        if (!cosineRankedIndices.isEmpty() && selectedSet.size() < limit) {
            for (Integer cosIdx : cosineRankedIndices) {
                String cosTitle = snapshot.get(cosIdx).entry().docTitle();
                if (!coveredDocTitles.contains(cosTitle) || limit < 3) {
                    selectedSet.add(cosIdx);
                    coveredDocTitles.add(cosTitle);
                    break;
                }
            }
        }
        if (limit >= 6 && bm25RankedIndices.size() > 1) {
            for (Integer bmIdx : bm25RankedIndices) {
                if (selectedSet.size() >= limit) {
                    break;
                }
                String bmTitle = snapshot.get(bmIdx).entry().docTitle();
                if (!coveredDocTitles.contains(bmTitle)) {
                    selectedSet.add(bmIdx);
                    coveredDocTitles.add(bmTitle);
                    break;
                }
            }
        }

        List<Integer> allByRrf = new ArrayList<>(totalN);
        for (int i = 0; i < totalN; i++) {
            allByRrf.add(i);
        }
        allByRrf.sort((a, c) -> {
            int cmp = Double.compare(fusedScores[c], fusedScores[a]);
            if (cmp != 0) {
                return cmp;
            }
            cmp = Double.compare(bm25Scores[c], bm25Scores[a]);
            if (cmp != 0) {
                return cmp;
            }
            return Double.compare(cosineScores[c], cosineScores[a]);
        });

        // 第一轮多样性填充：每个独立文档最多取其最佳分片
        for (Integer idx : allByRrf) {
            if (selectedSet.size() >= limit) {
                break;
            }
            String title = snapshot.get(idx).entry().docTitle();
            if (!coveredDocTitles.contains(title)) {
                selectedSet.add(idx);
                coveredDocTitles.add(title);
            }
        }
        // 第二轮回退填充：若独立文档总数不足 limit，再补充同文档次高分分片
        for (Integer idx : allByRrf) {
            if (selectedSet.size() >= limit) {
                break;
            }
            selectedSet.add(idx);
        }

        List<HybridScoredChunk> hits = new ArrayList<>(selectedSet.size());
        for (Integer idx : selectedSet) {
            hits.add(new HybridScoredChunk(
                    snapshot.get(idx).entry(),
                    bm25Scores[idx],
                    cosineScores[idx],
                    pprScores[idx],
                    fusedScores[idx],
                    bm25Ranks[idx],
                    cosineRanks[idx]
            ));
        }
        hits.sort(Comparator.comparingDouble(HybridScoredChunk::fusedRrfScore).reversed()
                .thenComparing(Comparator.comparingDouble(HybridScoredChunk::bm25Score).reversed()));

        double topBm25 = bm25RankedIndices.isEmpty() ? 0.0 : bm25Scores[bm25RankedIndices.get(0)];
        double topCosine = cosineRankedIndices.isEmpty() ? 0.0 : cosineScores[cosineRankedIndices.get(0)];
        long latencyUs = Math.max(1L, (System.nanoTime() - t0) / 1000L);

        totalHybridSearches.incrementAndGet();
        topBm25SumMilli.addAndGet(Math.round(topBm25 * 1000.0));
        topCosineSumMilli.addAndGet(Math.round(Math.max(0.0, topCosine) * 1000.0));

        HybridRetrievalReceipt receipt = new HybridRetrievalReceipt(
                safeReq, safeQuery, totalN, hits.size(), topBm25, topCosine, latencyUs, System.currentTimeMillis()
        );
        return new HybridSearchOutcome(Collections.unmodifiableList(hits), receipt);
    }

    /**
     * 统一高精度双语分词器（索引与查询严格共用同一逻辑）：
     * - 保留数字+单位复合词（如 {@code 16gb}、{@code $150} 同时产出 {@code $150} 与 {@code 150}）；
     * - 剔除高频英文停用词；
     * - 保留 CJK 单字与连续双字 bigram，消除正则丢弃短标识符/汉字的陷阱。
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);

        Matcher m = EN_NUM_TOKEN_PATTERN.matcher(lower);
        while (m.find()) {
            String tok = m.group();
            if (tok.startsWith("$") && tok.length() > 1) {
                tokens.add(tok);
                tokens.add(tok.substring(1));
                continue;
            }
            if (NUMERIC_OR_UNIT_PATTERN.matcher(tok).matches()) {
                tokens.add(tok);
                // 对 "16gb" 类数字单位复合词同时产出 "16" 与 "gb" 提升跨写法召回
                Matcher splitNumUnit = Pattern.compile("^(\\d+(?:\\.\\d+)?)([a-z]+)$").matcher(tok);
                if (splitNumUnit.matches()) {
                    tokens.add(splitNumUnit.group(1));
                    tokens.add(splitNumUnit.group(2));
                }
                continue;
            }
            if (tok.length() >= 2 && !STOP_WORDS.contains(tok)) {
                tokens.add(tok);
            }
        }

        // CJK 单字 + 双字 bigram
        List<Character> cjkRun = new ArrayList<>();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (isCjk(ch)) {
                tokens.add(String.valueOf(ch));
                cjkRun.add(ch);
            } else {
                emitCjkBigrams(cjkRun, tokens);
                cjkRun.clear();
            }
        }
        emitCjkBigrams(cjkRun, tokens);
        return tokens;
    }

    public int getIndexedSliceCount() {
        return slices.size();
    }

    public synchronized void clear() {
        slices.clear();
        postingsByTerm.clear();
        dfByTerm.clear();
        totalTokenLength.set(0L);
        sliceSequence.set(0);
    }

    public Map<String, Object> getTelemetryStats() {
        long searches = totalHybridSearches.get();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalHybridSearches", searches);
        map.put("totalIndexedSlices", slices.size());
        map.put("distinctIndexedTerms", dfByTerm.size());
        map.put("avgTopBm25Score", searches == 0 ? 0.0 : (topBm25SumMilli.get() / 1000.0) / searches);
        map.put("avgTopCosineScore", searches == 0 ? 0.0 : (topCosineSumMilli.get() / 1000.0) / searches);
        return Collections.unmodifiableMap(map);
    }

    private static void emitCjkBigrams(List<Character> run, List<String> out) {
        for (int i = 0; i + 1 < run.size(); i++) {
            out.add(new String(new char[]{run.get(i), run.get(i + 1)}));
        }
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private static float[] normalizeL2(float[] vec) {
        if (vec == null || vec.length == 0) {
            return new float[0];
        }
        double normSq = 0.0;
        for (float v : vec) {
            normSq += (double) v * v;
        }
        if (normSq <= 1e-12) {
            return vec.clone();
        }
        double inv = 1.0 / Math.sqrt(normSq);
        float[] out = new float[vec.length];
        for (int i = 0; i < vec.length; i++) {
            out[i] = (float) (vec[i] * inv);
        }
        return out;
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }
}
