package tech.qiantong.qknow.module.kb.service.flow.bo;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;

import java.util.*;

/**
 * 条件网关节点
 * 根据条件表达式决定走哪条分支
 *
 * config 格式:
 * {
 *   "conditions": [
 *     { "expression": "{{ variable }} == 'value'", "targetHandle": "handle_1" },
 *     { "expression": "default", "targetHandle": "default" }
 *   ]
 * }
 */
@Slf4j
public class ConditionNodeBO extends BaseNodeBO {

    public ConditionNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList) {
        super(nodeDefinition, edgeList);
    }

    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        KbFlowNodeDO nodeDefinition = getNodeDefinition();
        JSONObject configJson = JSONObject.parseObject(nodeDefinition.getConfig());

        log.info("执行条件节点: {}", nodeDefinition.getName());

        // 获取条件列表 (兼容 conditions 与 cases 数组)
        JSONArray conditions = configJson.getJSONArray("conditions");
        if (conditions == null || conditions.isEmpty()) {
            conditions = configJson.getJSONArray("cases");
        }
        if (conditions == null || conditions.isEmpty()) {
            // 没有条件配置，默认走所有分支
            return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), inputData);
        }

        // 评估条件
        String matchedHandle = null;
        String defaultHandle = null;
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject condition = conditions.getJSONObject(i);
            if (condition == null) {
                continue;
            }
            String expression = condition.getString("expression");
            if (cn.hutool.core.util.StrUtil.isBlank(expression)) {
                expression = condition.getString("condition");
            }
            String targetHandle = condition.getString("targetHandle");
            if (cn.hutool.core.util.StrUtil.isBlank(targetHandle)) {
                targetHandle = condition.getString("id");
            }

            boolean isElse = condition.getBooleanValue("isElse", false)
                    || "default".equalsIgnoreCase(expression)
                    || "else".equalsIgnoreCase(expression)
                    || "兜底分支".equals(expression);

            if (isElse) {
                defaultHandle = targetHandle;
                continue;
            }

            if (evaluateExpression(expression, context)) {
                matchedHandle = targetHandle;
                log.info("条件匹配成功: {} -> {}", expression, matchedHandle);
                break;
            }
        }
        if (matchedHandle == null) {
            matchedHandle = defaultHandle;
        }

        // 根据 matchedHandle 找到对应的下一个节点
        List<String> nextNodeIds = new ArrayList<>();
        if (matchedHandle != null) {
            for (KbFlowEdgeDO edge : getEdgeList()) {
                if (edge.getSourceNodeUuid().equals(nodeDefinition.getUuid())) {
                    if (matchedHandle.equals(edge.getSourceHandle()) ||
                        "default".equals(matchedHandle) && edge.getSourceHandle() == null) {
                        nextNodeIds.add(edge.getTargetNodeUuid());
                    }
                }
            }
        }

        // 如果没有匹配到任何条件，走默认分支（无 sourceHandle 的边）
        if (nextNodeIds.isEmpty()) {
            for (KbFlowEdgeDO edge : getEdgeList()) {
                if (edge.getSourceNodeUuid().equals(nodeDefinition.getUuid()) &&
                    edge.getSourceHandle() == null) {
                    nextNodeIds.add(edge.getTargetNodeUuid());
                }
            }
        }

        NodeRunResultBO result = NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), inputData);
        result.setNextNodeIds(nextNodeIds);
        return result;
    }

    /**
     * 评估条件表达式
     * 支持简单的比较操作: ==, !=, >, <, >=, <=
     * 支持变量替换: {{ variableName }}
     */
    private boolean evaluateExpression(String expression, RuntimeContextBO context) {
        try {
            // 替换变量
            String resolved = format(expression, context);
            // 支持 xxx.length 伪属性（如种子 SQL 的 query.length > 20），变量名允许省略 start_1. 前缀
            resolved = resolveLengthOperands(resolved, context);

            // 解析比较操作
            if (resolved.contains("==")) {
                String[] parts = resolved.split("==", 2);
                return compare(parts[0].trim(), parts[1].trim(), "==");
            } else if (resolved.contains("!=")) {
                String[] parts = resolved.split("!=", 2);
                return compare(parts[0].trim(), parts[1].trim(), "!=");
            } else if (resolved.contains(">=")) {
                String[] parts = resolved.split(">=", 2);
                return compare(parts[0].trim(), parts[1].trim(), ">=");
            } else if (resolved.contains("<=")) {
                String[] parts = resolved.split("<=", 2);
                return compare(parts[0].trim(), parts[1].trim(), "<=");
            } else if (resolved.contains(">")) {
                String[] parts = resolved.split(">", 2);
                return compare(parts[0].trim(), parts[1].trim(), ">");
            } else if (resolved.contains("<")) {
                String[] parts = resolved.split("<", 2);
                return compare(parts[0].trim(), parts[1].trim(), "<");
            }

            // 布尔值判断
            return Boolean.parseBoolean(resolved);
        } catch (Exception e) {
            log.warn("条件表达式评估失败: {}", expression, e);
            return false;
        }
    }

    private static final java.util.regex.Pattern LENGTH_OPERAND_PATTERN =
            java.util.regex.Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\.length");

    /**
     * 将 {@code query.length} 解析为真实变量值长度；变量名支持 fullName 与后缀匹配两种形式。
     * 未解析到变量时按 0 处理（宁可走 else 分支，也不做字典序误判）。
     */
    private String resolveLengthOperands(String resolved, RuntimeContextBO context) {
        if (resolved == null || context == null || context.getVariables() == null) {
            return resolved;
        }
        JSONObject vars = context.getVariables();
        java.util.regex.Matcher matcher = LENGTH_OPERAND_PATTERN.matcher(resolved);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String value = resolveVariableValue(vars, variableName);
            matcher.appendReplacement(buffer,
                    java.util.regex.Matcher.quoteReplacement(String.valueOf(value == null ? 0 : value.length())));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolveVariableValue(JSONObject vars, String name) {
        if (vars.containsKey(name)) {
            return vars.getString(name);
        }
        for (String key : vars.keySet()) {
            if (key.endsWith("." + name)) {
                return vars.getString(key);
            }
        }
        return null;
    }

    private boolean compare(String leftRaw, String rightRaw, String operator) {
        // 去除表达式中的引号字面量，避免把 'value' 当成含引号的字符串比较
        String left = stripQuotes(leftRaw);
        String right = stripQuotes(rightRaw);
        // 尝试数值比较
        try {
            double leftNum = Double.parseDouble(left);
            double rightNum = Double.parseDouble(right);
            return switch (operator) {
                case "==" -> leftNum == rightNum;
                case "!=" -> leftNum != rightNum;
                case ">" -> leftNum > rightNum;
                case "<" -> leftNum < rightNum;
                case ">=" -> leftNum >= rightNum;
                case "<=" -> leftNum <= rightNum;
                default -> false;
            };
        } catch (NumberFormatException e) {
            // 字符串比较
            int cmp = left.compareTo(right);
            return switch (operator) {
                case "==" -> cmp == 0;
                case "!=" -> cmp != 0;
                case ">" -> cmp > 0;
                case "<" -> cmp < 0;
                case ">=" -> cmp >= 0;
                case "<=" -> cmp <= 0;
                default -> false;
            };
        }
    }

    private String stripQuotes(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
