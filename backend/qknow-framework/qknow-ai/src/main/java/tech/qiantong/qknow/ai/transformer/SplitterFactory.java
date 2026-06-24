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

    /**
     * 创建分块器
     *
     * @param mode           分块模式：custom / recursive / RecursiveSplitter / semantic
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
            case MODE_SEMANTIC:
                // SemanticSplitter 需要 EmbeddingModel，无法在此静态工厂中创建
                // 实际使用时由 KmcSyncServiceImpl 直接构造
                throw new UnsupportedOperationException(
                        "SemanticSplitter 需要 EmbeddingModel，请通过 KmcSyncServiceImpl 直接创建");
            case MODE_CUSTOM:
            default:
                String sep = separator != null ? separator : "\n\n";
                return new GeneralSplitter(sep, maxChunkSize, chunkOverlap);
        }
    }
}
