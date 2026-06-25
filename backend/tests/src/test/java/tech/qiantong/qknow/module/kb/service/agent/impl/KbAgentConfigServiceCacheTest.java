package tech.qiantong.qknow.module.kb.service.agent.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.module.ai.api.dto.AiModelRespDTO;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbAgentConfigReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbChatMessageSendRespVO;
import tech.qiantong.qknow.module.kb.service.agent.HermesGrpcClient;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.SemanticCacheHitDTO;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KbAgentConfigServiceCacheTest {

    @Test
    void semanticCacheHitReturnsAnswerAndSourcesWithoutCallingHermes() throws Exception {
        KbAgentConfigServiceImpl service = new KbAgentConfigServiceImpl();
        IKmcApiService kmcApiService = mock(IKmcApiService.class);
        HermesGrpcClient hermesGrpcClient = mock(HermesGrpcClient.class);
        IAiModelApiService aiModelService = mock(IAiModelApiService.class);
        when(aiModelService.getAiModel(1L)).thenReturn(aiModel());

        when(kmcApiService.findSemanticAnswer(any())).thenReturn(Optional.of(SemanticCacheHitDTO.builder()
                .id(1L)
                .answer("cached answer")
                .similarity(0.98D)
                .sourcesJson("""
                        [{"documentId":"101","documentName":"Day01.md"},{"documentId":"102","documentName":"Day02.md"}]
                        """)
                .build()));

        ReflectionTestUtils.setField(service, "kmcApiService", kmcApiService);
        ReflectionTestUtils.setField(service, "hermesGrpcClient", hermesGrpcClient);
        ReflectionTestUtils.setField(service, "aiModelService", aiModelService);

        KbAgentConfigReqVO req = new KbAgentConfigReqVO();
        req.setWorkspaceId(10L);
        req.setBotId(20L);
        req.setQuestion("问题");
        req.setKnowledgeIds("1,2");
        req.setModelConfig("{\"modelId\":\"1\",\"modelName\":\"deepseek-chat\"}");

        List<KbChatMessageSendRespVO> responses = service.chatMessage(req).collectList().block();

        assertEquals(1, responses.size());
        KbChatMessageSendRespVO.Message receive = responses.get(0).getReceive();
        assertEquals("cached answer", receive.getContent());
        assertEquals(List.of("101", "102"), receive.getDocumentIdList());
        assertEquals(List.of("Day01.md", "Day02.md"), receive.getDocumentNameList());
        verify(hermesGrpcClient, never()).chat(any());
    }

    @Test
    void cacheMissCallsHermes() throws Exception {
        KbAgentConfigServiceImpl service = new KbAgentConfigServiceImpl();
        IKmcApiService kmcApiService = mock(IKmcApiService.class);
        HermesGrpcClient hermesGrpcClient = mock(HermesGrpcClient.class);
        IAiModelApiService aiModelService = mock(IAiModelApiService.class);
        when(aiModelService.getAiModel(1L)).thenReturn(aiModel());

        when(kmcApiService.findSemanticAnswer(any())).thenReturn(Optional.empty());
        when(kmcApiService.getKnowledgeBaseByIds(any())).thenReturn(List.of());
        when(hermesGrpcClient.chat(any())).thenReturn(Flux.empty());

        ReflectionTestUtils.setField(service, "kmcApiService", kmcApiService);
        ReflectionTestUtils.setField(service, "hermesGrpcClient", hermesGrpcClient);
        ReflectionTestUtils.setField(service, "aiModelService", aiModelService);

        KbAgentConfigReqVO req = new KbAgentConfigReqVO();
        req.setWorkspaceId(10L);
        req.setBotId(20L);
        req.setQuestion("问题");
        req.setKnowledgeIds("1");
        req.setModelConfig("{\"modelId\":\"1\",\"modelName\":\"deepseek-chat\"}");

        service.chatMessage(req).collectList().block();

        verify(hermesGrpcClient).chat(any());
    }

    private AiModelRespDTO aiModel() {
        AiModelRespDTO dto = new AiModelRespDTO();
        dto.setId(1L);
        dto.setModel("deepseek-chat");
        dto.setPlatform("DeepSeek");
        return dto;
    }
}
