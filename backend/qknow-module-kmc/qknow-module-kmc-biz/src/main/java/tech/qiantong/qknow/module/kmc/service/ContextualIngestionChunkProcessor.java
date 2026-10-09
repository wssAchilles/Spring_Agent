package tech.qiantong.qknow.module.kmc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Contextual RAG 入库期前置语境化处理器 (Contextual Ingestion Chunk Processor)
 * <p>
 * 借鉴 Anthropic 官方 Contextual Retrieval 成果与 DeepSeek 官方 64-token 对齐机制：
 * 在文档切片入库及向量化之前，结合全篇文档背景为孤立切片生成 50~100 字的语境说明前缀，
 * 彻底消除代词指代不明与孤立上下文语义断裂缺陷，使向量与 BM25 双路召回失败率大幅降低。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Service
public class ContextualIngestionChunkProcessor {

    /**
     * 针对 DeepSeek Context Caching 机制固定对齐的系统前缀指令 (固定 64 tokens 边界)
     */
    public static final String CACHE_ALIGNED_SYSTEM_PROMPT =
            "You are a professional enterprise document retrieval contextualizer. " +
            "Your task is to provide a brief 50-100 character context situating a chunk within the overall document. " +
            "Resolve all ambiguous pronouns such as 'it', 'the company', or 'they'. " +
            "Output ONLY the concise contextual sentence without conversational filler or quotation marks.";

    /**
     * 富语境化切片模型 Record (纯不可变对象)
     */
    public record ContextualChunk(
            String chunkId,
            String originalText,
            String contextualPrefix,
            String enrichedText,
            boolean cachingAligned
    ) {}

    /**
     * 单切片语境化同步生成处理
     *
     * @param chunkId                  切片唯一 ID
     * @param chunkText                切片正文
     * @param documentSummaryOrContext 整个文档的摘要或核心背景
     * @param chatModel                大模型客户端 (可为空触发降级)
     * @return 增强语境后的切片不可变对象
     */
    public ContextualChunk processChunk(String chunkId, String chunkText, String documentSummaryOrContext, ChatModel chatModel) {
        if (chunkText == null || chunkText.isBlank()) {
            return new ContextualChunk(chunkId, "", "", "", false);
        }

        // 若无可用大模型或无文档背景，Fail-open 降级返回原切片
        if (chatModel == null || documentSummaryOrContext == null || documentSummaryOrContext.isBlank()) {
            return new ContextualChunk(chunkId, chunkText, "", chunkText, false);
        }

        try {
            String userPrompt = String.format(
                    "<document_context>\n%s\n</document_context>\n\n<chunk_to_contextualize>\n%s\n</chunk_to_contextualize>\n\n" +
                    "Please provide the succinct contextual prefix for this chunk:",
                    documentSummaryOrContext, chunkText
            );

            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(CACHE_ALIGNED_SYSTEM_PROMPT),
                    new UserMessage(userPrompt)
            ));

            String prefix = chatModel.call(prompt).getResult().getOutput().getText();
            if (prefix != null) {
                prefix = prefix.trim();
                // 过滤可能包含的多余包裹标记
                if (prefix.startsWith("\"") && prefix.endsWith("\"") && prefix.length() > 2) {
                    prefix = prefix.substring(1, prefix.length() - 1).trim();
                }
            }

            String enrichedText = (prefix != null && !prefix.isBlank())
                    ? prefix + "\n\n" + chunkText
                    : chunkText;

            return new ContextualChunk(chunkId, chunkText, prefix != null ? prefix : "", enrichedText, true);
        } catch (Exception e) {
            log.warn("生成切片语境化前缀失败，自动降级为原生切片: chunkId={}, err={}", chunkId, e.getMessage());
            return new ContextualChunk(chunkId, chunkText, "", chunkText, false);
        }
    }

    /**
     * 基于 Java 21 虚拟线程的高并发批量切片语境化并行处理
     *
     * @param chunks                   切片集合 (Pair<ID, Text>)
     * @param documentSummaryOrContext 文档上下文
     * @param chatModel                模型接口
     * @return 语境化处理后的切片集合
     */
    public List<ContextualChunk> batchProcessChunks(
            List<org.apache.commons.lang3.tuple.Pair<String, String>> chunks,
            String documentSummaryOrContext,
            ChatModel chatModel
    ) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ContextualChunk>> futures = new ArrayList<>(chunks.size());
            for (var chunk : chunks) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> processChunk(chunk.getLeft(), chunk.getRight(), documentSummaryOrContext, chatModel),
                        virtualExecutor
                ));
            }

            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        }
    }
}
