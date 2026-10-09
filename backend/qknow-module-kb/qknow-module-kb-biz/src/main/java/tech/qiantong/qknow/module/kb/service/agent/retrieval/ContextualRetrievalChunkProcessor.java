package tech.qiantong.qknow.module.kb.service.agent.retrieval;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anthropic 风格 Contextual Retrieval 语境切片前缀增强处理器
 * <p>
 * 解决传统切片脱离上下文导致的孤岛失真问题：
 * 1. 结构化元数据面包屑增强 (Metadata Breadcrumb)：自动将文档名、章节标题与层级路径注入切片前缀；
 * 2. 文档级语境摘要前缀 (Document Context Header)：将整篇文档的核心主旨凝缩为 50-80 字符的全局前缀，
 *    解决“代词无指代”、“关键主体缺失”导致的召回与大模型理解偏差；
 * 3. 幂等防护与零损耗双向转换：自动检测已有前缀，防止多次富化膨胀，支持提取纯净正文；
 * 4. 纯 Java 21 Record 格式返回增强凭单。
 * </p>
 */
@Slf4j
@Component
public class ContextualRetrievalChunkProcessor {

    public static final String CONTEXT_PREFIX_START = "【语境前缀】";
    public static final String CONTEXT_PREFIX_END = "【正文内容】\n";

    private final IAiModelApiService aiModelService;

    // 内存语境摘要缓存 (docId -> summary)
    private final Map<String, String> contextCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public ContextualRetrievalChunkProcessor(IAiModelApiService aiModelService) {
        this.aiModelService = aiModelService;
    }

    public ContextualRetrievalChunkProcessor() {
        this(null);
    }

    /**
     * 单个切片语境化增强结果凭单
     */
    public record ContextualChunk(
            String chunkId,
            String originalContent,
            String contextPrefix,
            String enrichedContent,
            int prefixTokens,
            int originalTokens,
            int totalTokens,
            String documentName
    ) {}

    /**
     * 批量切片增强结果凭单
     */
    public record ContextualBatchResult(
            List<ContextualChunk> enrichedChunks,
            int totalProcessed,
            int cacheHits,
            long elapsedMillis
    ) {
        public List<RetrieveResult> toEnrichedRetrieveResults() {
            if (enrichedChunks == null) return Collections.emptyList();
            List<RetrieveResult> list = new ArrayList<>();
            for (ContextualChunk cc : enrichedChunks) {
                RetrieveResult r = new RetrieveResult();
                r.setId(cc.chunkId());
                r.setContent(cc.enrichedContent());
                r.setDocumentName(cc.documentName());
                r.setTokens(cc.totalTokens());
                list.add(r);
            }
            return Collections.unmodifiableList(list);
        }
    }

    /**
     * 增强单个切片 (默认使用元数据与文档摘要)
     *
     * @param chunk           原始召回切片
     * @param documentSummary 文档全局背景摘要 (可为空)
     * @return 增强切片凭单
     */
    public ContextualChunk enrichChunk(RetrieveResult chunk, String documentSummary) {
        if (chunk == null) {
            return new ContextualChunk("", "", "", "", 0, 0, 0, "");
        }

        String rawContent = chunk.getContent() != null ? chunk.getContent().trim() : "";
        String docName = StrUtil.isNotBlank(chunk.getDocumentName()) ? chunk.getDocumentName() : "未知知识文档";

        // 幂等性检查: 如果切片已经带有前缀，避免重复追加
        if (rawContent.startsWith(CONTEXT_PREFIX_START) && rawContent.contains(CONTEXT_PREFIX_END)) {
            int originalTok = estimateTokens(rawContent);
            return new ContextualChunk(chunk.getId(), rawContent, "", rawContent, 0, originalTok, originalTok, docName);
        }

        // 1. 构建紧凑元数据语境前缀
        StringBuilder prefixBuilder = new StringBuilder();
        prefixBuilder.append(CONTEXT_PREFIX_START).append("文档:《").append(docName).append("》");

        // 提取章节与元数据信息 (若 docMetadata 存在)
        String metadataJson = chunk.getDocMetadata();
        if (StrUtil.isNotBlank(metadataJson)) {
            try {
                JSONObject meta = JSONObject.parseObject(metadataJson);
                if (meta.containsKey("section")) {
                    prefixBuilder.append(" | 章节: ").append(meta.getString("section"));
                } else if (meta.containsKey("title")) {
                    prefixBuilder.append(" | 标题: ").append(meta.getString("title"));
                }
            } catch (Exception ignored) {
            }
        }

        // 2. 注入文档级摘要背景 (若提供)
        if (StrUtil.isNotBlank(documentSummary)) {
            prefixBuilder.append(" | 主旨: ").append(documentSummary.trim());
        }

        prefixBuilder.append("\n").append(CONTEXT_PREFIX_END);

        String contextPrefix = prefixBuilder.toString();
        String enrichedContent = contextPrefix + rawContent;

        int prefixTokens = estimateTokens(contextPrefix);
        int originalTokens = estimateTokens(rawContent);
        int totalTokens = prefixTokens + originalTokens;

        return new ContextualChunk(
                chunk.getId(),
                rawContent,
                contextPrefix,
                enrichedContent,
                prefixTokens,
                originalTokens,
                totalTokens,
                docName
        );
    }

    /**
     * 批量增强切片
     *
     * @param chunks       原始切片列表
     * @param docSummaries 文档摘要映射 (docId 或 docName -> summary)
     * @return 批量增强凭单
     */
    public ContextualBatchResult enrichBatch(List<RetrieveResult> chunks, Map<String, String> docSummaries) {
        long start = System.currentTimeMillis();
        if (chunks == null || chunks.isEmpty()) {
            return new ContextualBatchResult(Collections.emptyList(), 0, 0, 0L);
        }

        List<ContextualChunk> list = new ArrayList<>();
        int cacheHitCount = 0;

        for (RetrieveResult chunk : chunks) {
            if (chunk == null) continue;
            String docKey = chunk.getDocumentId() != null ? chunk.getDocumentId() : chunk.getDocumentName();
            String summary = null;
            if (docSummaries != null && docKey != null && docSummaries.containsKey(docKey)) {
                summary = docSummaries.get(docKey);
            } else if (docKey != null && contextCache.containsKey(docKey)) {
                summary = contextCache.get(docKey);
                cacheHitCount++;
            }

            ContextualChunk enriched = enrichChunk(chunk, summary);
            list.add(enriched);
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[语境切片增强] 批量增强完成: 处理切片数={}, 缓存命中={}, 耗时={}ms", list.size(), cacheHitCount, elapsed);

        return new ContextualBatchResult(
                Collections.unmodifiableList(list),
                list.size(),
                cacheHitCount,
                elapsed
        );
    }

    /**
     * 注册/缓存文档全局语境摘要
     */
    public void registerDocumentSummary(String documentIdOrName, String summary) {
        if (StrUtil.isNotBlank(documentIdOrName) && StrUtil.isNotBlank(summary)) {
            contextCache.put(documentIdOrName, summary.trim());
        }
    }

    /**
     * 剥离语境前缀，还原切片纯净正文
     */
    public static String stripContextPrefix(String enrichedContent) {
        if (StrUtil.isBlank(enrichedContent)) return "";
        int endIdx = enrichedContent.indexOf(CONTEXT_PREFIX_END);
        if (endIdx >= 0) {
            return enrichedContent.substring(endIdx + CONTEXT_PREFIX_END.length()).trim();
        }
        return enrichedContent.trim();
    }

    private int estimateTokens(String text) {
        if (StrUtil.isBlank(text)) return 0;
        return Math.max(1, (int) Math.ceil(text.length() / 1.7));
    }
}
