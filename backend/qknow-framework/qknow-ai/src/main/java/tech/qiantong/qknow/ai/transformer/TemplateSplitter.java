package tech.qiantong.qknow.ai.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 模板分块器 — 按文档类型自动选择最优分块策略
 * 参考：RAGFlow DeepDoc 模板分块（83.7k⭐）
 */
@Slf4j
public class TemplateSplitter {

    private final String fileName;
    private final int maxChunkSize;
    private final int chunkOverlap;

    public TemplateSplitter(String fileName, int maxChunkSize, int chunkOverlap) {
        this.fileName = fileName;
        this.maxChunkSize = maxChunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 按文档类型选择分块策略并执行分块
     */
    public List<Document> split(List<Document> documents) {
        TextSplitter delegate = selectStrategy(fileName);
        log.debug("模板分块: fileName={}, strategy={}", fileName, delegate.getClass().getSimpleName());
        return delegate.apply(documents);
    }

    private TextSplitter selectStrategy(String name) {
        if (name == null) {
            return new RecursiveSplitter(maxChunkSize, chunkOverlap);
        }
        String lower = name.toLowerCase();

        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return new RecursiveSplitter(maxChunkSize, chunkOverlap, new String[]{"\n## ", "\n### ", "\n\n", "\n", "。", ". "});
        }
        if (isCodeFile(lower)) {
            return new RecursiveSplitter(maxChunkSize, chunkOverlap, new String[]{"\n\n", "\ndef ", "\nfunction ", "\npublic ", "\nprivate ", "\nprotected ", "\nclass ", "\n", " "});
        }
        if (lower.endsWith(".pdf")) {
            return new RecursiveSplitter(Math.max(maxChunkSize, 1024), chunkOverlap);
        }
        if (lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".xlsx")) {
            return new GeneralSplitter("\n", maxChunkSize, chunkOverlap);
        }
        return new RecursiveSplitter(maxChunkSize, chunkOverlap);
    }

    private static boolean isCodeFile(String lower) {
        return lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".go") || lower.endsWith(".rs")
                || lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".cs");
    }
}
