package tech.qiantong.qknow.thirdparty.domain.dify.enums;

import lombok.Getter;

/**
 * 文档嵌入状态
 * @author wang
 */
@Getter
public enum DocIndexStatusEnum {
    // 索引完成
    COMPLETED("completed"),
    // 索引中
    SPLITTING("splitting"),
    // 排队中
    WAITING("waiting"),
    // 索引失败
    ERROR("error"),
    ;

    private final String type;

    DocIndexStatusEnum(String type) {
        this.type = type;
    }

    public boolean equals(String type) {
       return this.type.equals(type);
    }
}
