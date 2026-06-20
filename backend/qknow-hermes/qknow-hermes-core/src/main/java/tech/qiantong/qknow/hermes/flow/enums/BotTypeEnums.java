package tech.qiantong.qknow.hermes.flow.enums;

public enum BotTypeEnums {
    AGENT(1),
    CHAT_FLOW(2);

    private final Integer code;
    BotTypeEnums(Integer code) { this.code = code; }
    public Integer getCode() { return code; }
}
