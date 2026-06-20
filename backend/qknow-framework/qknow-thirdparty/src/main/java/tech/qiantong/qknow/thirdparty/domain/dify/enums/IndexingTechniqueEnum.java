package tech.qiantong.qknow.thirdparty.domain.dify.enums;

import lombok.Getter;

/**
 * 索引类型枚举
 */
@Getter
public enum IndexingTechniqueEnum {
    HIGH_QUALITY("high_quality"), // 高质量：使用 embedding 模型进行嵌入，构建为向量数据库索引
    ECONOMY("economy"); //  经济：使用 keyword table index 的倒排索引进行构建

    private final String type;

    IndexingTechniqueEnum(String type) {
        this.type = type;
    }
}
