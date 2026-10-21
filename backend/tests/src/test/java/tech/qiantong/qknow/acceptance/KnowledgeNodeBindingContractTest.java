package tech.qiantong.qknow.acceptance;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.flow.bo.KnowledgeNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-12 回归防护：知识库节点未绑定知识库时必须 fail-close 提示绑定，
 * 禁止用硬编码知识库 id 兜底（跨环境/跨租户错误召回）。
 */
class KnowledgeNodeBindingContractTest {

    @Test
    @DisplayName("未绑定知识库时节点失败并给出可操作提示，禁止回退硬编码 id")
    void missingKnowledgeBaseMustFailWithActionableMessage() {
        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid("knowledge_1");
        node.setName("知识库检索");
        node.setInput("[]");
        node.setConfig("{\"query\":\"测试问题\"}");

        KnowledgeNodeBO knowledgeNode = new KnowledgeNodeBO(node, List.of(), null);
        RuntimeContextBO context = new RuntimeContextBO();
        context.setVariables(new JSONObject());

        NodeRunResultBO result = knowledgeNode.execute(context);

        assertEquals(RuntimeStatusEnums.ERROR.getCode(), result.getStatus(),
                "缺少知识库绑定必须失败，禁止回退到硬编码知识库 id");
        assertTrue(result.getErrorMessage() != null && result.getErrorMessage().contains("未绑定知识库"));
    }
}
