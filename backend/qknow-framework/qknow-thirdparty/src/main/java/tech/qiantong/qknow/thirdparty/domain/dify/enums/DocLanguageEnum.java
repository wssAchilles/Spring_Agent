package tech.qiantong.qknow.thirdparty.domain.dify.enums;

import lombok.Getter;

/**
 * 文档语言枚举
 */
@Getter
public enum DocLanguageEnum {
    ENGLISH("English"),
    CHINESE("Chinese");

    private final String type;

    DocLanguageEnum(String type) {
        this.type = type;
    }
}
