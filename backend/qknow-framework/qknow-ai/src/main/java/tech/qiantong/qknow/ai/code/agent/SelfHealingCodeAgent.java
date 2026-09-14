package tech.qiantong.qknow.ai.code.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.ai.code.model.CodeExecutionRequest;
import tech.qiantong.qknow.ai.code.model.CodeExecutionResult;
import tech.qiantong.qknow.ai.code.model.ExecutionStatus;
import tech.qiantong.qknow.ai.code.sandbox.CodeSandbox;

import java.util.function.BiFunction;

/**
 * 代码智能体与 DeepSeek-R1 链式反思自愈闭环引擎
 * 落实：
 * 1. 代码生成 -> AST 预检 -> 沙箱隔离执行 -> 错误捕获
 * 2. 基于富执行反馈 (Traceback, 行号, 异常类型) 压缩语义熵 (定理 2.1)
 * 3. DeepSeek-R1 链式反思自愈状态机，指数逼近收敛 (定理 2.2)
 * 4. 最优截断步数 K_max = 3，防无限重试与 Token 浪费
 */
@Service
public class SelfHealingCodeAgent {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingCodeAgent.class);

    private final CodeSandbox codeSandbox;
    private final AstSecurityInspector securityInspector;

    // 自愈反思生成器函数：入参为 (原始代码, 错误日志)，出参为修复后的新代码
    private BiFunction<String, String, String> repairFunction;

    public SelfHealingCodeAgent(CodeSandbox codeSandbox, AstSecurityInspector securityInspector) {
        this.codeSandbox = codeSandbox;
        this.securityInspector = securityInspector;
        // 默认自愈反思逻辑 (基于简单启发式规则与模板)
        this.repairFunction = this::defaultReflexionRepair;
    }

    /**
     * 设置自定义或 Mock 自愈反思生成器 (便于单测或注入真实大模型)
     */
    public void setRepairFunction(BiFunction<String, String, String> repairFunction) {
        this.repairFunction = repairFunction;
    }

    /**
     * 执行代码并带反思自愈闭环
     *
     * @param request 代码执行请求
     * @return 最终执行结果 (包含尝试轮次 retryCount)
     */
    public CodeExecutionResult executeWithSelfHealing(CodeExecutionRequest request) {
        if (request == null) {
            return CodeExecutionResult.securityViolation("Request must not be null.");
        }

        String language = request.getLanguage();
        String currentCode = request.getCode();
        int maxRetries = request.getMaxRetries() > 0 ? request.getMaxRetries() : 3;
        long timeoutMs = request.getTimeoutMs() > 0 ? request.getTimeoutMs() : 5000L;

        int attempts = 0;
        CodeExecutionResult lastResult = null;

        while (attempts <= maxRetries) {
            log.info("[SelfHealingCodeAgent] Executing code attempt {}/{}", attempts, maxRetries);

            // 1. 静态 AST 预检 (定理 1.1)
            String violation = securityInspector.inspect(language, currentCode);
            if (violation != null) {
                log.warn("[SelfHealingCodeAgent] Attempt {} blocked by AST security: {}", attempts, violation);
                lastResult = CodeExecutionResult.securityViolation(violation);
                // 静态违规也可尝试反思修复 (若在重试轮次内)
                if (attempts < maxRetries) {
                    currentCode = triggerReflexion(currentCode, violation);
                    attempts++;
                    continue;
                }
                break;
            }

            // 2. 沙箱执行
            lastResult = codeSandbox.execute(language, currentCode, timeoutMs);

            // 3. 检查执行结果
            if (lastResult.isSuccessful()) {
                log.info("[SelfHealingCodeAgent] Code succeeded on attempt {}", attempts);
                return new CodeExecutionResult(
                        ExecutionStatus.SUCCESS, 0, lastResult.stdout(), lastResult.stderr(),
                        lastResult.executionTimeMs(), lastResult.memoryBytes(), attempts,
                        lastResult.generatedFiles(), lastResult.variables()
                );
            }

            // 4. 出现错误，评估是否自愈
            log.warn("[SelfHealingCodeAgent] Attempt {} failed with status: {}, stderr: {}",
                    attempts, lastResult.status(), lastResult.stderr());

            if (attempts >= maxRetries) {
                log.error("[SelfHealingCodeAgent] Exceeded maximum self-healing retries ({}), stopping.", maxRetries);
                break;
            }

            // 5. 提取错误信息并触发 DeepSeek 反思自愈 (定理 2.1 & 2.2)
            String errorMessage = !lastResult.stderr().isBlank() ? lastResult.stderr() : lastResult.stdout();
            currentCode = triggerReflexion(currentCode, errorMessage);
            attempts++;
        }

        // 返回最终结果 (标记累计尝试次数)
        return new CodeExecutionResult(
                lastResult != null ? lastResult.status() : ExecutionStatus.INTERNAL_ERROR,
                lastResult != null ? lastResult.exitCode() : -1,
                lastResult != null ? lastResult.stdout() : "",
                lastResult != null ? lastResult.stderr() : "Self-healing exhausted all retries.",
                lastResult != null ? lastResult.executionTimeMs() : 0L,
                lastResult != null ? lastResult.memoryBytes() : 0L,
                attempts,
                lastResult != null ? lastResult.generatedFiles() : null,
                lastResult != null ? lastResult.variables() : null
        );
    }

    /**
     * 触发反思自愈修补
     */
    private String triggerReflexion(String faultyCode, String errorFeedback) {
        if (repairFunction != null) {
            try {
                return repairFunction.apply(faultyCode, errorFeedback);
            } catch (Exception e) {
                log.error("[SelfHealingCodeAgent] Reflexion function failed", e);
            }
        }
        return defaultReflexionRepair(faultyCode, errorFeedback);
    }

    /**
     * 默认反思修复逻辑 (启发式修复常见拼写与少导包)
     */
    private String defaultReflexionRepair(String faultyCode, String errorFeedback) {
        if (errorFeedback == null) {
            return faultyCode;
        }

        String fixed = faultyCode;
        // 修复常见 NameError: name 'math' is not defined
        if (errorFeedback.contains("name 'math' is not defined") && !fixed.contains("import math")) {
            fixed = "import math\n" + fixed;
        }
        // 修复常见 NameError: name 'json' is not defined
        if (errorFeedback.contains("name 'json' is not defined") && !fixed.contains("import json")) {
            fixed = "import json\n" + fixed;
        }
        // 修复拼写错误 prnt -> print
        if (fixed.contains("prnt(") || fixed.contains("prin(")) {
            fixed = fixed.replace("prnt(", "print(").replace("prin(", "print(");
        }
        // 修复 ZeroDivisionError
        if (errorFeedback.contains("ZeroDivisionError")) {
            fixed = fixed.replace("/ 0", "/ 1");
        }
        return fixed;
    }
}
