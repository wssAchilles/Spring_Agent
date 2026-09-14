package tech.qiantong.qknow.ai.dataagent.security;

/**
 * 抽象语法树 (AST) 安全违规拦截强类型异常
 */
public class AstSecurityException extends RuntimeException {

    private final String violationType;
    private final String rawQuery;

    public AstSecurityException(String violationType, String message, String rawQuery) {
        super(message);
        this.violationType = violationType;
        this.rawQuery = rawQuery;
    }

    public String getViolationType() {
        return violationType;
    }

    public String getErrorCode() {
        return violationType;
    }

    public String getRawQuery() {
        return rawQuery;
    }
}
