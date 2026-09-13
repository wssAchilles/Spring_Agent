package tech.qiantong.qknow.module.kmc.service.rag.model;

import lombok.Getter;

/**
 * 知识库冷启动与零命中状态契约 (Phase 13 Zero-State Fallback)
 *
 * @author qknow
 */
@Getter
public enum RagZeroState {

    /**
     * 正常召回 (包含有效命中文档)
     */
    NORMAL("正常召回", 200),

    /**
     * 知识库为空 (未上传任何文档)
     */
    EMPTY_KNOWLEDGE_BASE("知识库未上传任何文档，请先添加资料", 4001),

    /**
     * 正在构建中 (文档正在切片和向量化中)
     */
    INDEXING_IN_PROGRESS("知识库文档正在切片和向量化中，请稍候重试", 4002),

    /**
     * 未解析出有效分段
     */
    UNINDEXED_EMPTY("知识库文档未解析出有效文本分段", 4003),

    /**
     * 零相似度命中 (多路召回后均无有效匹配)
     */
    ZERO_SIMILARITY_HIT("未在知识库中检索到与问题相关的上下文内容", 4004);

    private final String description;
    private final int code;

    RagZeroState(String description, int code) {
        this.description = description;
        this.code = code;
    }
}
