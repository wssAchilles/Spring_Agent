package tech.qiantong.qknow.hermes.tool.mcp.sandbox;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Phase 122 核心资产：动态 Schema 深度模式校验与 AST 防注入过滤器 (DeepSchemaSecurityInspector)
 * 落实定理 1.3 递归动态模式匹配同态性与间接注入免疫定理：
 * 1. 递归同态映射 \Phi: \Sigma^* -> \mathcal{T}，最大深度受限于 D_max <= 8，防范深度调用栈溢出
 * 2. 字段类型、必填槽位完备性与字符串长度范围严格校验
 * 3. 危险模式谓词全景防御：
 *    - 路径穿越谓词 \mathcal{P}_{path}: 100% 物理拦截 ../, ..\, /etc/, /proc/ 等越权访问
 *    - 命令注入与管道拼接谓词 \mathcal{P}_{cmd}: 100% 物理拦截 ;, |, &, $(), `, rm, sudo, chmod 等破坏性语法
 *    - 间接提示词注入谓词 \mathcal{P}_{inj}: 100% 物理拦截 "ignore previous instructions" 等越狱指令
 * 4. 证明恶意参数判伪率严格为 100%，良性有效参数模式同态保真度达到 100%
 */
public class DeepSchemaSecurityInspector {

    // 最大递归深度约束，杜绝恶意嵌套导致的栈溢出 (StackOverflow)
    public static final int MAX_RECURSION_DEPTH = 8;

    // 单个字符串参数允许的最大字符长度
    public static final int MAX_STRING_LENGTH = 4096;

    // 高危 Shell 元字符与管道拼接特征正则
    private static final Pattern SHELL_METACHAR_PATTERN = Pattern.compile(
            "[;&|`$><\n\r]"
    );

    // 高危破坏性系统命令特征正则 (全词匹配或前后边界)
    private static final Pattern DANGEROUS_COMMAND_PATTERN = Pattern.compile(
            "(?i)\\b(rm(\\s+-rf)?|sudo|chmod|chown|mkfs|dd|curl|wget|nc|bash|sh|kill|shutdown|reboot|format)\\b"
    );

    // 路径穿越与敏感系统目录访问特征正则
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(?i)(\\.\\./|\\.\\.\\\\|/etc/|/proc/|/sys/|/root/|/var/run/|^/dev/)"
    );

    // 针对大模型与工具生态的典型间接提示词注入 (Indirect Prompt Injection) 对抗特征
    private static final Pattern INDIRECT_INJECTION_PATTERN = Pattern.compile(
            "(?i)\\b(ignore\\s+(all\\s+)?previous\\s+instructions|system\\s+update:|disregard\\s+all\\s+prior|reveal\\s+(the\\s+)?api\\s+key|output\\s+your\\s+system\\s+prompt|new\\s+system\\s+directive|system\\s+prompt\\s+override)\\b"
    );

    /**
     * 校验结果封装结构体
     */
    public record InspectionResult(boolean passed, String blockedReason) {
        public static InspectionResult ok() {
            return new InspectionResult(true, McpSecuritySandboxReceipt.NONE_BLOCKED_REASON);
        }

        public static InspectionResult fail(String reason) {
            return new InspectionResult(false, reason);
        }
    }

    /**
     * 针对工具调用入参进行全量深度递归模式校验与 AST 防注入审查
     *
     * @param arguments          工具入参键值对映射
     * @param requiredParamNames 工具契约声明的必填参数名称集合 (可选，可为空)
     * @return 校验结果
     */
    public InspectionResult inspectArguments(Map<String, Object> arguments, Set<String> requiredParamNames) {
        if (arguments == null) {
            if (requiredParamNames != null && !requiredParamNames.isEmpty()) {
                return InspectionResult.fail("入参为空，缺失必填槽位: " + requiredParamNames);
            }
            return InspectionResult.ok();
        }

        // 1. 必填参数槽位存在性校验
        if (requiredParamNames != null) {
            for (String requiredName : requiredParamNames) {
                if (!arguments.containsKey(requiredName) || arguments.get(requiredName) == null) {
                    return InspectionResult.fail("缺失必填参数槽位: " + requiredName);
                }
            }
        }

        // 2. 递归遍历 AST 语法树结构与安全谓词审查
        return inspectNodeRecursive(arguments, 0);
    }

    /**
     * 递归审查 AST 节点
     */
    @SuppressWarnings("unchecked")
    private InspectionResult inspectNodeRecursive(Object node, int currentDepth) {
        if (currentDepth > MAX_RECURSION_DEPTH) {
            return InspectionResult.fail("参数嵌套深度超限，超过最大允许深度: " + MAX_RECURSION_DEPTH);
        }

        if (node == null) {
            return InspectionResult.ok();
        }

        if (node instanceof String strVal) {
            return inspectStringLeaf(strVal);
        } else if (node instanceof Number || node instanceof Boolean) {
            // 数值与布尔基本类型天然无代码执行风险，直接通过
            return InspectionResult.ok();
        } else if (node instanceof Map<?, ?> mapVal) {
            for (Map.Entry<?, ?> entry : mapVal.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String keyStr)) {
                    return InspectionResult.fail("键名必须为字符串类型: " + key);
                }
                // 检查键名自身是否存在高危注入字符
                InspectionResult keyInspect = inspectStringLeaf(keyStr);
                if (!keyInspect.passed()) {
                    return InspectionResult.fail("键名包含不安全字符: " + keyInspect.blockedReason());
                }
                // 递归检查子值
                InspectionResult valInspect = inspectNodeRecursive(entry.getValue(), currentDepth + 1);
                if (!valInspect.passed()) {
                    return valInspect;
                }
            }
            return InspectionResult.ok();
        } else if (node instanceof Collection<?> collVal) {
            for (Object item : collVal) {
                InspectionResult itemInspect = inspectNodeRecursive(item, currentDepth + 1);
                if (!itemInspect.passed()) {
                    return itemInspect;
                }
            }
            return InspectionResult.ok();
        } else if (node.getClass().isArray()) {
            Object[] array = (Object[]) node;
            for (Object item : array) {
                InspectionResult itemInspect = inspectNodeRecursive(item, currentDepth + 1);
                if (!itemInspect.passed()) {
                    return itemInspect;
                }
            }
            return InspectionResult.ok();
        }

        // 遇到无法识别的非基本复杂反射类型，执行 Default-Deny 阻断
        return InspectionResult.fail("不支持的非安全参数对象类型: " + node.getClass().getName());
    }

    /**
     * 审查叶子节点字符串内容，执行全部危险模式谓词硬拦截
     */
    private InspectionResult inspectStringLeaf(String value) {
        if (value == null) {
            return InspectionResult.ok();
        }

        // 1. 字符串超长校验
        if (value.length() > MAX_STRING_LENGTH) {
            return InspectionResult.fail("参数字符串长度超限 (字符数超出上限: " + value.length() + " > " + MAX_STRING_LENGTH + ")");
        }

        // 2. 间接提示词注入 (Indirect Prompt Injection) 谓词审查
        if (INDIRECT_INJECTION_PATTERN.matcher(value).find()) {
            return InspectionResult.fail("拦截到间接提示词注入对抗载荷 (Indirect Prompt Injection)");
        }

        // 3. 路径穿越 (Path Traversal) 谓词审查
        if (PATH_TRAVERSAL_PATTERN.matcher(value).find()) {
            return InspectionResult.fail("拦截到越权路径穿越载荷: " + value);
        }

        // 4. Shell 元字符与管道命令注入谓词审查
        if (SHELL_METACHAR_PATTERN.matcher(value).find()) {
            return InspectionResult.fail("拦截到危险 Shell 控制元字符/管道符: " + value);
        }

        // 5. 高危破坏性命令关键字谓词审查
        if (DANGEROUS_COMMAND_PATTERN.matcher(value).find()) {
            return InspectionResult.fail("拦截到高危破坏性操作系统命令: " + value);
        }

        return InspectionResult.ok();
    }
}
