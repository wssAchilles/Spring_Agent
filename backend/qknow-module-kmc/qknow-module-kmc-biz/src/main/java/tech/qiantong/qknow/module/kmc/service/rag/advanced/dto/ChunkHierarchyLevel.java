package tech.qiantong.qknow.module.kmc.service.rag.advanced.dto;

/**
 * 文档切片树层次级别枚举类 (Chunk Hierarchy Level)
 * <p>
 * 定义文档树四级拓扑层级：DOCUMENT -> SECTION -> PARAGRAPH -> SENTENCE。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public enum ChunkHierarchyLevel {

    /**
     * 根文档层级 (Root Document)
     */
    DOCUMENT(0, "根文档"),

    /**
     * 章节/篇目层级 (Section)
     */
    SECTION(1, "章节/篇目"),

    /**
     * 段落层级 (Paragraph)
     */
    PARAGRAPH(2, "自然段落"),

    /**
     * 句子/断句层级 (Sentence)
     */
    SENTENCE(3, "具体语句");

    private final int depth;
    private final String description;

    ChunkHierarchyLevel(int depth, String description) {
        this.depth = depth;
        this.description = description;
    }

    public int getDepth() {
        return depth;
    }

    public String getDescription() {
        return description;
    }
}
