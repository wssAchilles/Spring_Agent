package tech.qiantong.qknow.acceptance;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.flow.bo.KnowledgeNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 知识库节点输出契约：除 knowledge_x.text 外必须提供 {{chunks}} 别名，
 * 否则历史模板中的 {{chunks}} 不会被替换，LLM 会看到未解析占位符。
 */
class KnowledgeNodeOutputAliasTest {

    @Test
    @DisplayName("知识库节点输出 chunks 与 knowledge_x.text 同内容")
    void knowledgeNodeEmitsChunksAlias() {
        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid("knowledge_1");
        node.setName("知识库检索");
        node.setInput("[]");
        node.setConfig("{\"knowledgeBaseId\":8,\"query\":\"测试问题\"}");

        IKmcApiService kmcApiService = mock(IKmcApiService.class);
        RetrieveResult result = new RetrieveResult();
        result.setContent("真实切片内容");
        result.setDocumentName("测试文档");
        result.setScore(0.9);
        when(kmcApiService.recallTest(8L, "测试问题")).thenReturn(List.of(result));

        KnowledgeNodeBO knowledgeNode = new KnowledgeNodeBO(node, List.of(), kmcApiService);
        RuntimeContextBO context = new RuntimeContextBO();
        context.setVariables(new JSONObject());

        NodeRunResultBO run = knowledgeNode.execute(context);

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), run.getStatus());
        String text = String.valueOf(run.getOutput().get("knowledge_1.text"));
        String chunks = String.valueOf(run.getOutput().get("chunks"));
        assertTrue(text.contains("真实切片内容"));
        assertEquals(text, chunks, "chunks 别名必须与节点文本一致");
    }
}
