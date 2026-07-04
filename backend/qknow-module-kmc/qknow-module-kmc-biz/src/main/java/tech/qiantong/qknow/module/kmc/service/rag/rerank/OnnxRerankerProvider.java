package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import ai.onnxruntime.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.LongBuffer;
import java.util.*;

/**
 * ONNX Cross-Encoder Reranker Provider
 * 使用 ONNX Runtime 加载 Cross-Encoder 模型进行本地精排
 * [溯源] 算法优化指南 Phase 1: Cross-Encoder ONNX Runtime
 */
@Slf4j
@Component
public class OnnxRerankerProvider implements RerankerProvider {

    private final OnnxRerankerConfig config;
    private OrtEnvironment env;
    private OrtSession session;
    private boolean available = false;

    public OnnxRerankerProvider(OnnxRerankerConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("ONNX Reranker disabled");
            return;
        }
        try {
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(4);
            session = env.createSession(config.getModelPath(), opts);
            available = true;
            log.info("ONNX Reranker model loaded: {}", config.getModelPath());
        } catch (Exception e) {
            log.warn("ONNX Reranker init failed: {}", e.getMessage());
            available = false;
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
        } catch (Exception ignored) {}
    }

    @Override
    public String name() { return "onnx-cross-encoder"; }

    @Override
    public boolean supports(RerankRequestContext context) {
        return config.isEnabled() && available;
    }

    @Override
    public List<RetrievalResult> rerank(RerankRequestContext context,
                                         List<RetrievalResult> candidates,
                                         QueryIntent queryIntent, int topK) {
        if (!available || candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("ONNX Reranker not available");
        }

        String query = context.getQuery();
        long start = System.currentTimeMillis();

        try {
            List<ScoredIndex> scored = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                String text = truncate(candidates.get(i).getContent(), config.getMaxDocTokens());
                float score = scorePair(query, text);
                scored.add(new ScoredIndex(i, score));
            }

            scored.sort((a, b) -> Float.compare(b.score, a.score));

            List<RetrievalResult> results = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, scored.size()); i++) {
                ScoredIndex si = scored.get(i);
                RetrievalResult orig = candidates.get(si.index);
                results.add(RetrievalResult.builder()
                        .segmentId(orig.getSegmentId())
                        .qmSegmentId(orig.getQmSegmentId())
                        .parentSegmentId(orig.getParentSegmentId())
                        .documentId(orig.getDocumentId())
                        .documentName(orig.getDocumentName())
                        .content(orig.getContent())
                        .answer(orig.getAnswer())
                        .score(si.score)
                        .source(orig.getSource())
                        .metadata(orig.getMetadata())
                        .build());
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("ONNX Rerank: {} -> {} docs in {}ms", candidates.size(), results.size(), elapsed);
            return results;

        } catch (Exception e) {
            log.warn("ONNX Rerank failed: {}", e.getMessage());
            throw new RuntimeException("ONNX Rerank failed", e);
        }
    }

    private float scorePair(String query, String document) throws OrtException {
        // 简化的 tokenizer：按空格分割，取前 maxTokens 个 token
        // 生产环境应使用 BPE/WordPiece tokenizer
        long[] inputIds = tokenize(query, document);
        long[] attentionMask = new long[inputIds.length];
        Arrays.fill(attentionMask, 1);
        long[] tokenTypeIds = new long[inputIds.length];
        // query 部分 token_type=0, document 部分 token_type=1
        int sepIndex = findSep(inputIds);
        if (sepIndex >= 0) {
            Arrays.fill(tokenTypeIds, sepIndex + 1, inputIds.length, 1L);
        }

        try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[]{1, inputIds.length});
             OnnxTensor attMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), new long[]{1, inputIds.length});
             OnnxTensor tokenTypeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), new long[]{1, inputIds.length})) {

            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attMaskTensor);
            inputs.put("token_type_ids", tokenTypeTensor);

            try (OrtSession.Result result = session.run(inputs)) {
                float[][] logits = (float[][]) result.get(0).getValue();
                // sigmoid 将 logits 转为 0-1 分数
                float logit = logits[0][0];
                return 1.0f / (1.0f + (float) Math.exp(-logit));
            }
        }
    }

    private long[] tokenize(String query, String document) {
        // 简化 tokenizer：[CLS] query_tokens [SEP] doc_tokens [SEP]
        // 实际应使用 BPE/WordPiece tokenizer
        String[] qTokens = query.split("\\s+");
        String[] dTokens = document.split("\\s+");
        
        int maxLen = config.getMaxQueryTokens() + config.getMaxDocTokens() + 3; // [CLS], 2x[SEP]
        long[] ids = new long[Math.min(maxLen, 512)];
        
        int idx = 0;
        ids[idx++] = 101; // [CLS]
        for (String t : qTokens) {
            if (idx >= maxLen - 1) break;
            ids[idx++] = Math.abs(t.hashCode() % 30000) + 1; // 简化 hash-based ID
        }
        ids[idx++] = 102; // [SEP]
        for (String t : dTokens) {
            if (idx >= maxLen - 1) break;
            ids[idx++] = Math.abs(t.hashCode() % 30000) + 1;
        }
        ids[idx++] = 102; // [SEP]
        
        return Arrays.copyOf(ids, idx);
    }

    private int findSep(long[] ids) {
        for (int i = ids.length - 1; i >= 0; i--) {
            if (ids[i] == 102) return i;
        }
        return -1;
    }

    private String truncate(String text, int maxTokens) {
        if (text == null) return "";
        int maxChars = maxTokens * 2;
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    private record ScoredIndex(int index, float score) {}

    @Data
    @Component
    @ConfigurationProperties(prefix = "qknow.rag.onnx-reranker")
    public static class OnnxRerankerConfig {
        private boolean enabled = false;
        private String modelPath = "models/cross-encoder-ms-marco-MiniLM-L-6-v2.onnx";
        private int maxQueryTokens = 64;
        private int maxDocTokens = 256;
    }
}
