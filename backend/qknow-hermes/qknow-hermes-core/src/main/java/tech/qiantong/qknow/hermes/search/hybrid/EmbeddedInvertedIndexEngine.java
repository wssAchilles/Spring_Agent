package tech.qiantong.qknow.hermes.search.hybrid;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 嵌入式内存倒排索引引擎 (Embedded Inverted Index Engine)
 * 采用经典 Okapi BM25 算法标准实现，兼顾中文分词与代码/错误码精确标识符切分
 */
public class EmbeddedInvertedIndexEngine {

    private static final Pattern CODE_TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9_]+");
    private static final float DEFAULT_K1 = 1.2f;
    private static final float DEFAULT_B = 0.75f;

    private final float k1;
    private final float b;

    // 倒排表: term -> (docId -> termFrequency)
    private final Map<String, Map<String, Integer>> invertedIndex = new ConcurrentHashMap<>();
    // 文档长度记录: docId -> tokenCount
    private final Map<String, Integer> docLengths = new ConcurrentHashMap<>();
    // 文档原始文本: docId -> textContent
    private final Map<String, String> docTexts = new ConcurrentHashMap<>();

    private long totalTokenCount = 0;

    public EmbeddedInvertedIndexEngine() {
        this(DEFAULT_K1, DEFAULT_B);
    }

    public EmbeddedInvertedIndexEngine(float k1, float b) {
        this.k1 = k1;
        this.b = b;
    }

    /**
     * 写入或更新文档倒排索引
     */
    public synchronized void indexDocument(String docId, String text) {
        Objects.requireNonNull(docId, "docId 不能为空");
        Objects.requireNonNull(text, "text 不能为空");

        // 若已存在，先扣除既有词数统计
        if (docLengths.containsKey(docId)) {
            totalTokenCount -= docLengths.get(docId);
        }

        List<String> tokens = tokenize(text);
        int docLen = tokens.size();
        docLengths.put(docId, docLen);
        docTexts.put(docId, text);
        totalTokenCount += docLen;

        Map<String, Integer> freqs = new HashMap<>();
        for (String token : tokens) {
            freqs.put(token, freqs.getOrDefault(token, 0) + 1);
        }

        for (Map.Entry<String, Integer> entry : freqs.entrySet()) {
            invertedIndex.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>())
                    .put(docId, entry.getValue());
        }
    }

    /**
     * 批量构建文档倒排索引
     */
    public void indexDocuments(Map<String, String> documents) {
        Objects.requireNonNull(documents, "documents 不能为空");
        for (Map.Entry<String, String> entry : documents.entrySet()) {
            indexDocument(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 执行 BM25 关键词检索，返回按相关性得分降序的 (docId, bm25Score) 列表
     */
    public List<Map.Entry<String, Float>> search(String query, int topK) {
        if (query == null || query.isBlank() || docLengths.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        int totalDocs = docLengths.size();
        float avgDocLength = (float) totalTokenCount / totalDocs;

        Map<String, Float> scoreMap = new HashMap<>();

        for (String qToken : queryTokens) {
            Map<String, Integer> postings = invertedIndex.get(qToken);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            int docFreq = postings.size();
            // Lucene/Okapi 平滑 IDF 公式: ln(1 + (N - n + 0.5) / (n + 0.5))
            float idf = (float) Math.log(1.0 + (totalDocs - docFreq + 0.5) / (docFreq + 0.5));

            for (Map.Entry<String, Integer> posting : postings.entrySet()) {
                String docId = posting.getKey();
                int tf = posting.getValue();
                int docLen = docLengths.getOrDefault(docId, (int) avgDocLength);

                // TF 饱和度打分项
                float tfNorm = (tf * (k1 + 1.0f)) / (tf + k1 * (1.0f - b + b * (docLen / avgDocLength)));
                float termScore = idf * tfNorm;

                scoreMap.put(docId, scoreMap.getOrDefault(docId, 0.0f) + termScore);
            }
        }

        List<Map.Entry<String, Float>> results = new ArrayList<>(scoreMap.entrySet());
        results.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        if (results.size() > topK) {
            return List.copyOf(results.subList(0, topK));
        }
        return List.copyOf(results);
    }

    public String getDocumentText(String docId) {
        return docTexts.get(docId);
    }

    public Map<String, String> getAllDocumentTexts() {
        return Collections.unmodifiableMap(docTexts);
    }

    public int getDocumentCount() {
        return docLengths.size();
    }

    /**
     * 智能混合分词器：
     * 1. 优先提取连续英文、数字、下划线构成的代码标识符与错误码；
     * 2. 中文字符提取单字与双字滑动切分（Unigram + Bigram）。
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        // 1. 提取英文字符、数字与标识符
        Matcher matcher = CODE_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }

        // 2. 提取中文字符及其二元切分
        List<Character> cjkChars = new ArrayList<>();
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjkChars.add(c);
            }
        }
        for (int i = 0; i < cjkChars.size(); i++) {
            tokens.add(String.valueOf(cjkChars.get(i)));
            if (i + 1 < cjkChars.size()) {
                tokens.add(new String(new char[]{cjkChars.get(i), cjkChars.get(i + 1)}));
            }
        }

        return tokens;
    }
}
