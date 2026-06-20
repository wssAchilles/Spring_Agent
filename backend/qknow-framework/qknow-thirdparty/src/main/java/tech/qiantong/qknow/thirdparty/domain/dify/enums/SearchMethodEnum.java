package tech.qiantong.qknow.thirdparty.domain.dify.enums;

import lombok.Getter;

/**
 *  检索方法
 */
@Getter
public enum SearchMethodEnum {
    KEYWORD_SEARCH("keyword_search"), // 关键字检索
    SEMANTIC_SEARCH("semantic_search"), // 语义检索
    FULL_TEXT_SEARCH("full_text_search"), // 全文检索
    HYBRID_SEARCH("hybrid_search"); // 混合检索

    private final String type;

    SearchMethodEnum(String type) {
        this.type = type;
    }
}
