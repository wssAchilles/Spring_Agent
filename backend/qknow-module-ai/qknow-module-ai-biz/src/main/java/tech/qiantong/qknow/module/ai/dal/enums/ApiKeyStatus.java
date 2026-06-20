package tech.qiantong.qknow.module.ai.dal.enums;

/**
 * apiKey 状态
 *
 * @author fabian
 */
public enum ApiKeyStatus {
    /**
     * 未配置
     */
    NOT_CONFIG(0),
    /**
     * 已配置
     */
    CONFIG(1),
    /**
     * 已同步模型
     */
    SYNC(2);

    private final Integer code;

    ApiKeyStatus(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
