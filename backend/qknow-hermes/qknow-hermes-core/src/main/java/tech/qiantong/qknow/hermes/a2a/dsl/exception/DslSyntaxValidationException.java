package tech.qiantong.qknow.hermes.a2a.dsl.exception;

/**
 * 门禁一：工作流 DSL 语法、结构与 Schema 校验失败异常
 */
public class DslSyntaxValidationException extends RuntimeException {
    public DslSyntaxValidationException(String message) {
        super(message);
    }

    public DslSyntaxValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
