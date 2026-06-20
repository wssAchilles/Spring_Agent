package tech.qiantong.qknow.thirdparty.domain.dify.enums;

import lombok.Getter;

/**
 * 分段模式
 * @author: qknow
 */
@Getter
public enum SegmentEnum {

    HIERARCHICAL("hierarchical"),
    CUSTOM("custom");

    private final String type;

    SegmentEnum(String type) {
        this.type = type;
    }


}
