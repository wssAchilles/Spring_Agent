package tech.qiantong.qknow.hermes.flow.enums;

public enum FlowNodeTypeEnums {
    START(1, "start"),
    LLM(2, "llm"),
    REPLY(3, "reply"),
    CONDITION(4, "condition"),
    HTTP(5, "http"),
    KNOWLEDGE(6, "knowledge"),
    AGGREGATOR(7, "aggregator");

    private final Integer code;
    private final String name;

    FlowNodeTypeEnums(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    public Integer getCode() { return code; }
    public String getName() { return name; }

    public static FlowNodeTypeEnums getByCode(Integer code) {
        for (FlowNodeTypeEnums e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public static FlowNodeTypeEnums getByName(String name) {
        for (FlowNodeTypeEnums e : values()) {
            if (e.name.equals(name)) return e;
        }
        return null;
    }
}
