package tech.qiantong.qknow.hermes.a2a.dsl.exception;

/**
 * 门禁三：工作流 DSL 外部依赖（MCP 工具、AgentCard、超球面流形）存活或几何断言未通过异常
 */
public class DependencyUnsatisfiedException extends RuntimeException {
    public DependencyUnsatisfiedException(String message) {
        super(message);
    }

    public DependencyUnsatisfiedException(String message, Throwable cause) {
        super(message, cause);
    }
}
