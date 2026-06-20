package tech.qiantong.qknow.module.ext.extEnum;

/**
 * 抽取任务，任务类型
 *
 * @author Administrator
 */
public enum ExtTaskTypeEnum {
    /**
     * 结构化抽取
     */
    STRUCT(0),
    /**
     * 非结构化抽取
     */
    UN_STRUCT(1);

    private final Integer code;

    ExtTaskTypeEnum(Integer code) {
        this.code = code;
    }

    public Integer getValue() {
        return this.code;
    }
}
