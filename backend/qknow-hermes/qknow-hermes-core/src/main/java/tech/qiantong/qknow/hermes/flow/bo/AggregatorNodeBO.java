package tech.qiantong.qknow.hermes.flow.bo;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多流并发交汇聚合节点
 * <p>
 * 将多个上游分支的输出聚合为单一结果
 * <p>
 * config 格式:
 * {
 *   "strategy": "concat",
 *   "inputKeys": ["node_a.text", "node_b.text"]
 * }
 * <p>
 * strategy 取值:
 * - "concat": 将所有输入值拼接为字符串
 * - "highest_score": 选择 score 最高的输入值
 */
@Slf4j
public class AggregatorNodeBO extends BaseNodeBO {

    public AggregatorNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList) {
        super(nodeDefinition, edgeList);
    }

    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        KbFlowNodeDO nodeDefinition = getNodeDefinition();
        JSONObject configJson = JSONObject.parseObject(nodeDefinition.getConfig());
        JSONObject contextVariables = context.getVariables();

        String strategy = configJson.getString("strategy");
        if (StrUtil.isBlank(strategy)) {
            strategy = "concat";
        }

        JSONArray inputKeysArray = configJson.getJSONArray("inputKeys");
        if (inputKeysArray == null || inputKeysArray.isEmpty()) {
            return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                    "aggregator 节点 inputKeys 不能为空");
        }

        log.info("执行聚合节点: {}, strategy: {}, inputKeys: {}",
                nodeDefinition.getName(), strategy, inputKeysArray);

        String mergedValue;
        switch (strategy) {
            case "concat" -> mergedValue = doConcat(inputKeysArray, contextVariables);
            case "highest_score" -> mergedValue = doHighestScore(inputKeysArray, contextVariables);
            default -> {
                return NodeRunResultBO.failure(nodeDefinition.getUuid(), nodeDefinition.getName(),
                        "不支持的聚合策略: " + strategy);
            }
        }

        Map<String, Object> outputData = new HashMap<>();
        outputData.put(nodeDefinition.getUuid() + ".merged", mergedValue);

        log.info("聚合完成: {}，结果长度: {}", nodeDefinition.getName(),
                mergedValue != null ? mergedValue.length() : 0);
        return NodeRunResultBO.success(nodeDefinition.getUuid(), nodeDefinition.getName(), outputData);
    }

    /**
     * 拼接策略：将所有输入值连接成字符串
     */
    private String doConcat(JSONArray inputKeysArray, JSONObject contextVariables) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inputKeysArray.size(); i++) {
            String key = inputKeysArray.getString(i);
            String value = contextVariables.getString(key);
            if (StrUtil.isNotBlank(value)) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(value);
            }
        }
        return sb.toString();
    }

    /**
     * 最高分策略：找到 score 最高的输入 key，返回其值
     * 评分 key 格式为: inputKey + ".score"
     */
    private String doHighestScore(JSONArray inputKeysArray, JSONObject contextVariables) {
        String bestKey = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < inputKeysArray.size(); i++) {
            String key = inputKeysArray.getString(i);
            String scoreKey = key + ".score";
            Object scoreObj = contextVariables.get(scoreKey);

            double score = 0.0;
            if (scoreObj instanceof Number num) {
                score = num.doubleValue();
            } else if (scoreObj instanceof String str && StrUtil.isNotBlank(str)) {
                try {
                    score = Double.parseDouble(str);
                } catch (NumberFormatException ignored) {
                    // 保持默认分数 0.0
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }

        if (bestKey != null) {
            String value = contextVariables.getString(bestKey);
            return StrUtil.isNotBlank(value) ? value : "";
        }

        return "";
    }
}
