package tech.qiantong.qknow.hermes.tool.mcp;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * MCP 工具输入参数 JSON Schema 严格校验器 (McpSchemaValidator)
 * <p>
 * 提供零重量级依赖的高性能前置参数契约校验：
 * 1. 严格拦截模型幻觉导致的缺少必需字段 (required fields)；
 * 2. 严格校验字段数据类型 (string, integer, number, boolean, array, object)；
 * 3. 产出纯 Java 21 Record 不可变校验结果，防止错误参数向外部微服务与远程网络穿透。
 * </p>
 */
@Slf4j
public class McpSchemaValidator {

    /**
     * 参数校验结果不可变凭单 Record
     */
    public record ValidationResult(boolean valid, String errorMessage) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }

    /**
     * 根据 MCP 工具的 inputSchema 校验模型生成的输入参数
     *
     * @param inputSchema 工具定义的 JSON Schema (含 type, properties, required)
     * @param arguments   大模型或客户端传入的实参字典
     * @return 校验凭单
     */
    public static ValidationResult validate(JSONObject inputSchema, Map<String, Object> arguments) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return ValidationResult.success();
        }

        Map<String, Object> safeArgs = (arguments != null) ? arguments : Collections.emptyMap();

        // 1. 校验必需字段列表 (required)
        JSONArray requiredList = inputSchema.getJSONArray("required");
        if (requiredList != null && !requiredList.isEmpty()) {
            for (int i = 0; i < requiredList.size(); i++) {
                String reqField = requiredList.getString(i);
                if (!safeArgs.containsKey(reqField) || safeArgs.get(reqField) == null) {
                    return ValidationResult.failure("缺少必需参数: '" + reqField + "'");
                }
                Object val = safeArgs.get(reqField);
                if (val instanceof String str && str.trim().isEmpty()) {
                    return ValidationResult.failure("必需参数 '" + reqField + "' 不能为空字符串");
                }
            }
        }

        // 2. 校验字段类型契约 (properties)
        JSONObject properties = inputSchema.getJSONObject("properties");
        if (properties != null && !properties.isEmpty() && !safeArgs.isEmpty()) {
            for (Map.Entry<String, Object> entry : safeArgs.entrySet()) {
                String argKey = entry.getKey();
                Object argVal = entry.getValue();

                if (argVal == null) {
                    continue;
                }

                JSONObject fieldSchema = properties.getJSONObject(argKey);
                if (fieldSchema == null) {
                    // 非已知字段，默认宽松放行（亦可扩展严格模式）
                    continue;
                }

                String expectedType = fieldSchema.getString("type");
                if (expectedType == null || expectedType.isBlank()) {
                    continue;
                }

                if (!isTypeMatching(expectedType.toLowerCase(), argVal)) {
                    return ValidationResult.failure(String.format(
                            "参数 '%s' 类型不匹配: 期望 %s, 实际为 %s (值为 %s)",
                            argKey, expectedType, argVal.getClass().getSimpleName(), argVal
                    ));
                }
            }
        }

        return ValidationResult.success();
    }

    /**
     * 类型断言匹配
     */
    private static boolean isTypeMatching(String expectedType, Object val) {
        return switch (expectedType) {
            case "string" -> val instanceof CharSequence;
            case "integer" -> val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Byte;
            case "number" -> val instanceof Number;
            case "boolean" -> val instanceof Boolean;
            case "array" -> val instanceof Collection<?> || val instanceof Object[] || val instanceof JSONArray;
            case "object" -> val instanceof Map<?, ?> || val instanceof JSONObject;
            default -> true;
        };
    }
}
