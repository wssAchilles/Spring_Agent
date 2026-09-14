package tech.qiantong.qknow.ai.code.sandbox;

import tech.qiantong.qknow.ai.code.model.CodeExecutionRequest;
import tech.qiantong.qknow.ai.code.model.CodeExecutionResult;

/**
 * 代码沙箱顶层抽象 SPI 接口
 * 屏蔽本地轻量子进程、WASM 或远端微容器实现差异
 */
public interface CodeSandbox {

    /**
     * 执行指定语言与源码
     *
     * @param language  语言类型 (python, sql, javascript, bash)
     * @param code      代码内容
     * @param timeoutMs 超时毫秒数
     * @return 结构化执行结果
     */
    CodeExecutionResult execute(String language, String code, long timeoutMs);

    /**
     * 使用默认超时执行
     */
    default CodeExecutionResult execute(String language, String code) {
        return execute(language, code, 5000L);
    }

    /**
     * 基于完整请求对象执行
     */
    default CodeExecutionResult execute(CodeExecutionRequest request) {
        if (request == null) {
            return CodeExecutionResult.securityViolation("CodeExecutionRequest must not be null");
        }
        return execute(request.getLanguage(), request.getCode(), request.getTimeoutMs());
    }
}
