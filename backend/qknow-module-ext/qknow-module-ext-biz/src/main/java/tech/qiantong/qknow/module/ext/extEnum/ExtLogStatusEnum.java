package tech.qiantong.qknow.module.ext.extEnum;

/**
 * 抽取任务日志，结果状态
 *
 * @author Administrator
 */
public enum ExtLogStatusEnum {
    /**
     * 失败
     */
    FAIL(0),
    /**
     * 成功
     */
    SUCCESS(1);

    private final Integer code;

    ExtLogStatusEnum(Integer code) {
        this.code = code;
    }

    public Integer getValue() {
        return this.code;
    }
}
