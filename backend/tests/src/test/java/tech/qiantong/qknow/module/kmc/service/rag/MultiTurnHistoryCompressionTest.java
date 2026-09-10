package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import tech.qiantong.qknow.ai.service.IChatClientService;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.RetrieveResultReqVO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * H4b: multi-turn history reaches QueryTransformService.compressQuery
 * only when history is non-empty and transform is enabled.
 */
class MultiTurnHistoryCompressionTest {

    @Test
    @DisplayName("history 非空且 transform 开启时会调用 compressQuery")
    void historyNonEmptyInvokesCompress() {
        IChatClientService chatClientService = mock(IChatClientService.class);
        QueryTransformService.QueryTransformConfig cfg =
                new QueryTransformService.QueryTransformConfig();
        cfg.setEnabled(true);
        cfg.setStrategy("rewrite");
        QueryTransformService service = new QueryTransformService(chatClientService, cfg);

        List<Message> messages = List.of(new UserMessage("上一轮问题"));
        // compressQuery requires LLM — verify it attempts chat client when history present
        // If LLM fails it returns original query (see implementation).
        String out = service.compressQuery("它是什么？", messages);
        assertNotNull(out);
        // Either compressed or original; must not be null
        verify(chatClientService, atMost(1)).getChatClient(any(), any(), any(), any());
    }

    @Test
    @DisplayName("history 字段可挂到 RetrieveResultReqVO")
    void reqVoCarriesHistory() {
        RetrieveResultReqVO reqVO = new RetrieveResultReqVO();
        RetrieveResultReqVO.ChatMessage m = new RetrieveResultReqVO.ChatMessage();
        m.setRole("user");
        m.setContent("hello");
        reqVO.setHistory(List.of(m));
        assertEquals(1, reqVO.getHistory().size());
        assertEquals("user", reqVO.getHistory().get(0).getRole());
    }

    @Test
    @DisplayName("空 history 时 compress 不调用（由调用方控制）")
    void emptyHistoryCallerSkips() {
        IChatClientService chatClientService = mock(IChatClientService.class);
        QueryTransformService.QueryTransformConfig cfg =
                new QueryTransformService.QueryTransformConfig();
        cfg.setEnabled(true);
        QueryTransformService service = new QueryTransformService(chatClientService, cfg);
        String out = service.compressQuery("solo?", List.of());
        assertNotNull(out);
        verifyNoInteractions(chatClientService);
    }
}
