package tech.qiantong.qknow.module.ai.api.enums;

import lombok.Getter;

/**
 * 提示词角色枚举
 *
 * @author asd
 */
@Getter
public enum AiMessageRoleEnums {

    // SYSTEM
    SYSTEM(1),
    // USER
    USER(2),
    // ASSISTANT
    ASSISTANT(3);

    private final Integer code;

    AiMessageRoleEnums(Integer code) {
        this.code = code;
    }

    public static AiMessageRoleEnums getByCode(Integer code) {
        for (AiMessageRoleEnums value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
