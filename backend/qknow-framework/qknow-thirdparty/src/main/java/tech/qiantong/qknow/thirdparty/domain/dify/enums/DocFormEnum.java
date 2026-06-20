package tech.qiantong.qknow.thirdparty.domain.dify.enums;

import lombok.Getter;

/**
 * 索引内容形式
 */
@Getter
public enum DocFormEnum {
    TEXT_MODEL("text_model"), // text 文档直接 embedding，经济模式默认为该模式
    HIERARCHICAL_MODEL("hierarchical_model"), // parent-child 模式
    QA_MODEL("qa_model"); // Q&A 模式：为分片文档生成 Q&A 对，然后对问题进行 embedding

    private final String type;

    DocFormEnum(String type) {
        this.type = type;
    }
}
