
package tech.qiantong.qknow.ai.enums;

/**
 * 聊天消息用户类型
 */
public enum ChatMessageTypeEnum {
    USER(0), // 用户
    AI(1);// 机器人

    private final Integer type;

    // 构造方法
    private ChatMessageTypeEnum(Integer type) {
        this.type = type;
    }

    public Integer getValue() {
        return this.type;
    }
}
