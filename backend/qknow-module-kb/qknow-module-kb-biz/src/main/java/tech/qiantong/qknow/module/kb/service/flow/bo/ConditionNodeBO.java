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
            if (resolved.contains(".length")) {
                JSONObject vars = context != null ? context.getVariables() : null;
                if (vars != null) {
                    for (String key : vars.keySet()) {
                        if (resolved.contains(key + ".length")) {
                            String val = vars.getString(key);
                            int len = (val != null) ? val.length() : 0;
                            resolved = resolved.replace(key + ".length", String.valueOf(len));
                        }
                    }
                }
            }

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

    private boolean compare(String left, String right, String operator) {
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
}
