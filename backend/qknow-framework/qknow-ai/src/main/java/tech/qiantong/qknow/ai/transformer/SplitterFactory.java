package tech.qiantong.qknow.ai.transformer;

import org.springframework.ai.transformer.splitter.TextSplitter;

/**
 * 文本分块器工厂，根据 mode 创建对应的 Splitter
 *
 * @author qknow
 */
public class SplitterFactory {

    public static final String MODE_CUSTOM = "custom";
    public static final String MODE_RECURSIVE = "recursive";
    public static final String MODE_RECURSIVE_LEGACY = "RecursiveSplitter";
    public static final String MODE_SEMANTIC = "semantic";
    public static final String MODE_TEMPLATE = "template";

    /**
     * 创建分块器
     *
     * @param mode           分块模式：custom / recursive / RecursiveSplitter / semantic / template
     * @param separator      分隔符（custom 模式使用）
     * @param maxChunkSize   最大切片长度
     * @param chunkOverlap   重叠长度
     * @return TextSplitter 实例
     */
    public static TextSplitter create(String mode, String separator, int maxChunkSize, int chunkOverlap) {
        if (mode == null) {
            mode = MODE_RECURSIVE;
        }
        switch (mode) {
            case MODE_RECURSIVE:
            case MODE_RECURSIVE_LEGACY:
                return new RecursiveSplitter(maxChunkSize, chunkOverlap);
            case MODE_TEMPLATE:
                // TemplateSplitter 需要文件名和内容，使用 RecursiveSplitter 作为 fallback
                return new RecursiveSplitter(maxChunkSize, chunkOverlap);
            case MODE_SEMANTIC:
                throw new UnsupportedOperationException(
                        "SemanticSplitter 需要 EmbeddingModel，请通过 KmcSyncServiceImpl 直接创建");
            case MODE_CUSTOM:
            default:
                String sep = separator != null ? separator : "\n\n";
                return new GeneralSplitter(sep, maxChunkSize, chunkOverlap);
        }
    }

    /**
     * 创建模板分块器（按文件类型自动选择策略）
     * 返回的 TextSplitter 会根据文件扩展名自动选择最优分隔符
     */
    public static TextSplitter createTemplate(String fileName, int maxChunkSize, int chunkOverlap) {
        if (fileName == null) {
            return new RecursiveSplitter(maxChunkSize, chunkOverlap);
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return new RecursiveSplitter(maxChunkSize, chunkOverlap, new String[]{"\n## ", "\n### ", "\n\n", "\n", "。", ". "});
        }
        if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts")) {
            return new RecursiveSplitter(maxChunkSize, chunkOverlap, new String[]{"\n\n", "\ndef ", "\nfunction ", "\npublic ", "\nprivate ", "\nclass ", "\n", " "});
        }
        if (lower.endsWith(".pdf")) {
            return new RecursiveSplitter(Math.max(maxChunkSize, 1024), chunkOverlap);
        }
        if (lower.endsWith(".csv") || lower.endsWith(".tsv")) {
            return new GeneralSplitter("\n", maxChunkSize, chunkOverlap);
        }
        return new RecursiveSplitter(maxChunkSize, chunkOverlap);
    }
}
