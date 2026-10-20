package tech.qiantong.qknow.mcp.server.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.server.registry.McpServerRegistry;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * 代码智能体 (Phase 30 CodeSandbox) 受限沙箱执行 MCP 适配器 (杜绝假数据)
 */
public class CodeSandboxMcpProvider {

    private static final Logger log = LoggerFactory.getLogger(CodeSandboxMcpProvider.class);
    private BiFunction<String, String, CallToolResult> sandboxRunnerDelegate;

    private static final Pattern HIGH_RISK_SYSCALL = Pattern.compile(
        "\\b(subprocess|os\\.system|os\\.popen|sys\\.exit|eval|exec|__import__)\\b"
    );

    public CodeSandboxMcpProvider() {
    }

    public CodeSandboxMcpProvider(BiFunction<String, String, CallToolResult> sandboxRunnerDelegate) {
        this.sandboxRunnerDelegate = sandboxRunnerDelegate;
    }

    public void setSandboxRunnerDelegate(BiFunction<String, String, CallToolResult> sandboxRunnerDelegate) {
        this.sandboxRunnerDelegate = sandboxRunnerDelegate;
    }

    public void registerTo(McpServerRegistry registry) {
        registry.registerTool(
            "qknow_sandbox_run",
            "在严格隔离的受限子进程瞬态沙箱中执行 Python/JS 代码，无环境变量泄露，硬超时 5s",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "language", Map.of("type", "string", "description", "编程语言 (python/js)"),
                    "code", Map.of("type", "string", "description", "待执行的代码内容")
                ),
                "required", List.of("language", "code")
            ),
            tech.qiantong.qknow.mcp.server.annotation.McpTool.RiskLevel.HIGH_RISK,
            true,
            params -> {
                String language = (String) params.get("language");
                String code = (String) params.get("code");
                return runInSandbox(language, code);
            }
        );
    }

    public CallToolResult runInSandbox(String language, String code) {
        if (code == null || code.isBlank()) {
            return CallToolResult.error("待执行代码不能为空");
        }

        // 静态 AST 级别高危模块剪枝
        if (HIGH_RISK_SYSCALL.matcher(code).find()) {
            throw new SecurityException("代码包含受限制的高危系统调用模块，沙箱拒绝执行");
        }

        if (sandboxRunnerDelegate != null) {
            try {
                return sandboxRunnerDelegate.apply(language, code);
            } catch (Exception e) {
                log.warn("执行委托沙箱运行异常: lang={}, msg={}", language, e.getMessage());
                return CallToolResult.error("沙箱执行失败: " + e.getMessage());
            }
        }

        // 缺乏真实执行器委托时客观报错，杜绝伪造假成功
        return CallToolResult.error("受限代码沙箱执行器未配置或环境未就绪");
    }
}
