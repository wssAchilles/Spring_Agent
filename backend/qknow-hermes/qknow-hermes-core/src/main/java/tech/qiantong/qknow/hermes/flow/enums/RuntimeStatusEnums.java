package tech.qiantong.qknow.hermes.flow.enums;

public enum RuntimeStatusEnums {
    RUNNING(0),
    SUCCESS(1),
    ERROR(2),
    SUSPENDED(3),
    SKIPPED(4),
    COMPENSATING(5),
    COMPENSATED(6),
    REJECTED(7);

    private final Integer code;

    RuntimeStatusEnums(Integer code) { this.code = code; }
    public Integer getCode() { return code; }

    public static RuntimeStatusEnums getByCode(Integer code) {
        if (code == null) return null;
        for (RuntimeStatusEnums status : values()) {
            if (status.code.equals(code)) return status;
        }
        return null;
    }
}
