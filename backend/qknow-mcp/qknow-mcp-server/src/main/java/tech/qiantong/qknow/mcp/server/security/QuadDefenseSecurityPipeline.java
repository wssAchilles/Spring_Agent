package tech.qiantong.qknow.mcp.server.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.model.CallToolResult;
import tech.qiantong.qknow.mcp.core.model.ToolContentItem;
import tech.qiantong.qknow.mcp.core.protocol.JsonRpcError;
import tech.qiantong.qknow.mcp.server.annotation.McpTool.RiskLevel;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 四级纵深防御工程引擎 (Quad-Defense Security Pipeline)
 * 1. 模式防线：16KB 物理截断与强 Schema 校验
 * 2. 租约防线：60 秒瞬态 LeaseToken CAS 原子核销
 * 3. 免疫防线：对抗指令图谱双向审查与敏感 Key 脱敏掩码
 * 4. 环境防线：沙箱子进程清空环境变量
 */
public class QuadDefenseSecurityPipeline {

    private static final Logger log = LoggerFactory.getLogger(QuadDefenseSecurityPipeline.class);

    public static final int MAX_PAYLOAD_BYTES = 16 * 1024; // 16KB 严格物理上限

    private static final Pattern SENSITIVE_KEY_PATTERN =
            Pattern.compile("(?i)(sk-[a-zA-Z0-9]{20,}|bearer\\s+[a-zA-Z0-9_\\-\\.]+|password\\s*=\\s*['\"]?[^'\"\\s]+)");

    private static final List<String> ADVERSARIAL_PATTERNS = List.of(
            "ignore previous instructions",
            "system override",
            "you are now in mode",
            "print system prompt",
            "exfiltrate",
            "disregard all prior directives",
            "bypass security filter"
    );

    private final McpTransientLeaseManager leaseManager;

    public QuadDefenseSecurityPipeline(McpTransientLeaseManager leaseManager) {
        this.leaseManager = Objects.requireNonNull(leaseManager, "leaseManager 不得为空");
    }

    /**
     * 防线一：物理载荷长度强校验 (16KB 上限)
     */
    public void validatePayloadSize(byte[] rawBytes) {
        if (rawBytes == null) {
            return;
        }
        if (rawBytes.length > MAX_PAYLOAD_BYTES) {
            log.warn("[防线 1: 模式防御] 拦截到超大载荷请求, 体积: {} 字节 (上限: 16KB)", rawBytes.length);
            throw new SecurityPipelineException(JsonRpcError.INVALID_PARAMS,
                    "Payload exceeds physical security limit of 16KB (" + rawBytes.length + " bytes)");
        }
    }

    /**
     * 防线二：时效租约强核验 (高危操作要求 60s 瞬态租约)
     */
    public void verifyLeaseIfRequired(String toolName, RiskLevel riskLevel, boolean requiresLease, Map<String, Object> arguments) {
        if (riskLevel == RiskLevel.HIGH_RISK || requiresLease) {
            String leaseToken = arguments != null ? (String) arguments.get("leaseToken") : null;
            if (leaseToken == null || leaseToken.isBlank()) {
                log.warn("[防线 2: 时效租约] 高危工具 {} 缺少必填 leaseToken", toolName);
                throw new SecurityPipelineException(-32001, "High-risk tool requires a valid transient leaseToken");
            }

            boolean verified = leaseManager.consumeLease(toolName, leaseToken);
            if (!verified) {
                log.warn("[防线 2: 时效租约] 高危工具 {} 的 leaseToken 无效或已过期 (>60s)", toolName);
                throw new SecurityPipelineException(-32001, "Transient leaseToken is expired, invalid or already consumed");
            }
            log.info("[防线 2: 时效租约] 工具 {} 成功核销有效租约: {}", toolName, leaseToken);
        }
    }

    /**
     * 防线三：入参对抗性特征主动免疫审查
     */
    public void sanitizeInboundArguments(String toolName, Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        String argsString = arguments.toString().toLowerCase(Locale.ROOT);
        for (String pattern : ADVERSARIAL_PATTERNS) {
            if (argsString.contains(pattern)) {
                log.warn("[防线 3: 入参免疫] 捕获到潜在提示词注入对抗特征: '{}', 目标工具: {}", pattern, toolName);
                throw new SecurityPipelineException(-32002, "Inbound arguments contain adversarial prompt injection pattern: " + pattern);
            }
        }
    }

    /**
     * 防线三：出参深度清洗与敏感信息脱敏 (OWASP LLM07)
     */
    public CallToolResult sanitizeOutboundResult(String toolName, CallToolResult rawResult) {
        if (rawResult == null || rawResult.content() == null) {
            return rawResult;
        }

        List<ToolContentItem> sanitizedList = new ArrayList<>();
        for (ToolContentItem item : rawResult.content()) {
            if ("text".equalsIgnoreCase(item.type()) && item.text() != null) {
                String text = item.text();
                // 1. 敏感密钥与密码模式脱敏
                text = SENSITIVE_KEY_PATTERN.matcher(text).replaceAll("[REDACTED-CREDENTIAL-***]");

                // 2. 检查输出中是否存在潜伏间接提示词劫持指令
                String lower = text.toLowerCase(Locale.ROOT);
                for (String pattern : ADVERSARIAL_PATTERNS) {
                    if (lower.contains(pattern)) {
                        log.warn("[防线 3: 出参免疫] 工具 {} 返回内容中潜伏对抗性注入指令: '{}', 执行强制清除", toolName, pattern);
                        text = text.replace(pattern, "[BLOCKED-INDIRECT-INJECTION]");
                    }
                }
                sanitizedList.add(new ToolContentItem("text", text));
            } else {
                sanitizedList.add(item);
            }
        }
        return new CallToolResult(sanitizedList, rawResult.isError());
    }

    /**
     * 防线四：环境无干扰沙箱安全执行器配置
     */
    public ProcessBuilder configureSanitizedSandboxProcess(List<String> command) {
        ProcessBuilder pb = new ProcessBuilder(command);
        // 彻底清空全部环境变量，严禁继承宿主 JVM 的 DEEPSEEK_API_KEY 等密钥
        pb.environment().clear();
        // 注入仅维持基础系统运行的绝对安全白名单参数
        pb.environment().put("PATH", "/usr/bin:/bin");
        pb.environment().put("LANG", "en_US.UTF-8");
        log.info("[防线 4: 环境无干扰] 沙箱进程已彻底清空环境变量, 阻断密钥向子进程逃逸");
        return pb;
    }

    public static class SecurityPipelineException extends RuntimeException {
        private final int errorCode;

        public SecurityPipelineException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }
}
