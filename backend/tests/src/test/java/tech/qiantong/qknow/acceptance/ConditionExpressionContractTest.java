package tech.qiantong.qknow.acceptance;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.service.flow.bo.ConditionNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-12 回归防护：条件网关必须支持 xxx.length 伪属性与"兜底分支"，禁止退化为字典序恒真。
 */
class ConditionExpressionContractTest {

    @Test
    @DisplayName("query.length 条件：短问题走兜底分支，长问题走 deep 分支")
    void queryLengthConditionMustNotDegradeToLexicographic() {
        String config = """
                {"cases":[
                  {"condition":"query.length > 20","id":"deep"},
                  {"condition":"兜底分支","id":"fast"}
                ]}
                """;

        // 短问题（10 字符）：应走 fast
        NodeRunResultBO shortResult = execute(config, "短问题");
        assertEquals(List.of("fast_target"), shortResult.getNextNodeIds());

        // 长问题（30 字符）：应走 deep
        NodeRunResultBO longResult = execute(config, "这是一个超过二十个字符的较长用户问题用于验证条件分支正确性");
        assertEquals(List.of("deep_target"), longResult.getNextNodeIds());
    }

    private NodeRunResultBO execute(String config, String query) {
        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid("condition_1");
        node.setName("条件判断");
        node.setConfig(config);

        KbFlowEdgeDO deepEdge = new KbFlowEdgeDO();
        deepEdge.setSourceNodeUuid("condition_1");
        deepEdge.setSourceHandle("deep");
        deepEdge.setTargetNodeUuid("deep_target");

        KbFlowEdgeDO fastEdge = new KbFlowEdgeDO();
        fastEdge.setSourceNodeUuid("condition_1");
        fastEdge.setSourceHandle("fast");
        fastEdge.setTargetNodeUuid("fast_target");

        ConditionNodeBO conditionNode = new ConditionNodeBO(node, List.of(deepEdge, fastEdge));

        RuntimeContextBO context = new RuntimeContextBO();
        JSONObject variables = new JSONObject();
        variables.put("start_1.query", query);
        context.setVariables(variables);

        return conditionNode.execute(context);
    }
}
