package tech.qiantong.qknow.hermes.flow.enums;

public enum RuntimeStatusEnums {
    RUNNING(0),
    SUCCESS(1),
    ERROR(2);

    private final Integer code;

    RuntimeStatusEnums(Integer code) { this.code = code; }
    public Integer getCode() { return code; }
}
