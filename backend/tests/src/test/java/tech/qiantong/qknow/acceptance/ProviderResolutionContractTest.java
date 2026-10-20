package tech.qiantong.qknow.acceptance;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.BotExecuteModeEnum;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.service.flow.bo.LLMNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.RuntimeContextBO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * P0-12 回归防护：LLM 节点未显式配置 provider 时必须按平台动态解析，
 * 禁止再回退到跨环境不一致的 ai_api_key.id=1 魔数。
 */
class ProviderResolutionContractTest {

    @Test
    @DisplayName("未配置 provider 时按 platform 动态解析模型，绝不调用 keyId 分支")
    void missingProviderMustResolveByPlatform() {
        IAiModelApiService aiModelService = mock(IAiModelApiService.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(aiModelService.getChatModelByPlatform("DeepSeek", "deepseek-flash")).thenReturn(chatModel);
        when(chatModel.call(any(Message[].class))).thenReturn("模型答复");

        KbFlowNodeDO node = new KbFlowNodeDO();
        node.setUuid("llm_test");
        node.setName("DeepSeek 推理");
        node.setInput("[]");
        node.setConfig("{\"platform\":\"DeepSeek\",\"model\":\"deepseek-flash\",\"prompt\":\"你好\"}");

        LLMNodeBO llmNode = new LLMNodeBO(node, List.of(), aiModelService);
        RuntimeContextBO context = new RuntimeContextBO();
        context.setVariables(new JSONObject());
        // 阻塞模式强制走 chatModel.call 分支，避免无后继节点时进入流式分支
        context.setExecuteMode(BotExecuteModeEnum.BLOCK);

        NodeRunResultBO result = llmNode.execute(context);

        assertEquals(RuntimeStatusEnums.SUCCESS.getCode(), result.getStatus());
        assertEquals("模型答复", result.getOutput().get("llm_test.text"));
        verify(aiModelService).getChatModelByPlatform("DeepSeek", "deepseek-flash");
        verify(aiModelService, never()).getChatModel(anyLong(), anyString());
    }
}
