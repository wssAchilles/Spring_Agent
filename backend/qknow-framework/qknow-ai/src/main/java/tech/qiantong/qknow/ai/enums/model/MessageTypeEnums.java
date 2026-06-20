
package tech.qiantong.qknow.ai.enums.model;

import java.util.Objects;

public enum MessageTypeEnums {
    USER(0, "用户"),
    ROBOT(1, "机器人");

    public final Integer code;
    public final String info;

    MessageTypeEnums(Integer code, String info) {
        this.code = code;
        this.info = info;
    }

    public static MessageTypeEnums get(Integer code) {
        for (MessageTypeEnums v : values()) {
            if (v.eq(code)) {
                return v;
            }
        }
        return null;
    }

    // 根据code返回县市名称
    public static MessageTypeEnums getName(String info) {
        for (MessageTypeEnums v : values()) {
            if (v.like(info)) {
                return v;
            }
        }
        return null;
    }

    public boolean eq(Integer code) {
        return this.code.equals(code);
    }

    public boolean like(String info) {
        return this.info.equals(info);
    }

    public static String getInfo(Integer code) {
        return Objects.requireNonNull(MessageTypeEnums.get(code)).getInfo();
    }

    public static Integer getCode(String info) {
        return Objects.requireNonNull(MessageTypeEnums.getName(info)).getCode();
    }

    public Integer getCode() {
        return code;
    }

    public String getInfo() {
        return info;
    }
}
