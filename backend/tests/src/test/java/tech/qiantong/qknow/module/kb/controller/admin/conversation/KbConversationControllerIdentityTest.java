package tech.qiantong.qknow.module.kb.controller.admin.conversation;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbAgentConfigReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.conversation.vo.KbChatMessageSendReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.agent.KbAgentConfigDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbChatMessageDO;
import tech.qiantong.qknow.module.kb.service.agent.IKbAgentConfigService;
import tech.qiantong.qknow.module.kb.service.conversation.IKbChatMessageService;
import tech.qiantong.qknow.module.kb.service.conversation.IKbConversationService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KbConversationControllerIdentityTest {

    @Test
    void httpBoundaryBuildsAuthenticatedIdentityAndUniqueRequestId() throws Exception {
        KbConversationController controller = spy(new KbConversationController());
        IKbConversationService conversationService = mock(IKbConversationService.class);
        IKbChatMessageService chatMessageService = mock(IKbChatMessageService.class);
        IKbAgentConfigService agentConfigService = mock(IKbAgentConfigService.class);
        ReflectionTestUtils.setField(controller, "conversationService", conversationService);
        ReflectionTestUtils.setField(controller, "chatMessageService", chatMessageService);
        ReflectionTestUtils.setField(controller, "agentConfigService", agentConfigService);
        doReturn(40L).when(controller).getUserId();
        doReturn(10L).when(controller).getWorkSpaceId();

        when(chatMessageService.getMessagesByConversationId(30L)).thenReturn(List.of());
        when(chatMessageService.saveMessage(eq(30L), anyString(), anyString()))
                .thenReturn(new KbChatMessageDO());
        when(agentConfigService.getKbAgentConfigByBotId(20L)).thenReturn(new KbAgentConfigDO());
        when(agentConfigService.chatMessage(any())).thenReturn(Flux.empty());

        KbChatMessageSendReqVO request = new KbChatMessageSendReqVO();
        request.setConversationId(30L);
        request.setBotId(20L);
        request.setWorkspaceId(999L);
        request.setQuestion("question");

        controller.sendMessage(request);
        controller.sendMessage(request);

        ArgumentCaptor<KbAgentConfigReqVO> captor = ArgumentCaptor.forClass(KbAgentConfigReqVO.class);
        verify(agentConfigService, times(2)).chatMessage(captor.capture());
        List<KbAgentConfigReqVO> identities = captor.getAllValues();
        for (KbAgentConfigReqVO identity : identities) {
            assertEquals(10L, identity.getWorkspaceId());
            assertEquals(20L, identity.getBotId());
            assertEquals(30L, identity.getConversationId());
            assertEquals(40L, identity.getUserId());
            assertNotNull(UUID.fromString(identity.getRequestId()));
        }
        assertNotEquals(identities.get(0).getRequestId(), identities.get(1).getRequestId());
    }
}
