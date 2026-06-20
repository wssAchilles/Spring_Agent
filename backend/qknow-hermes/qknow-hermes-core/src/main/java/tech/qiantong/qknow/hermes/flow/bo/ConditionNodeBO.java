package tech.qiantong.qknow.hermes.flow.bo;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.enums.RuntimeStatusEnums;

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

        // 获取条件列表
        JSONArray conditions = configJson.getJSONArray("conditions");
        if (conditions == null || conditions.isEmpty()) {
            // 没有条件配置，默认走所有分支
            return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), inputData);
        }

        // 评估条件
        String matchedHandle = null;
        for (int i = 0; i < conditions.size(); i++) {
            JSONObject condition = conditions.getJSONObject(i);
            String expression = condition.getString("expression");

            if ("default".equals(expression)) {
                matchedHandle = condition.getString("targetHandle");
                continue;
            }

            if (evaluateExpression(expression, context)) {
                matchedHandle = condition.getString("targetHandle");
                log.info("条件匹配: {} -> {}", expression, matchedHandle);
                break;
            }
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
