package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import tech.qiantong.qknow.ai.service.IChatModelService;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CragGateAndEntityDefaultTest {

    @Test
    @DisplayName("CRAG gate-mode=always 会调用 LLM")
    void cragAlwaysInvokesLlm() {
        IChatModelService chatModelService = mock(IChatModelService.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelService.getChatModel(any(), any(), any(), any(), any())).thenReturn(chatModel);
        ChatResponse out = mock(ChatResponse.class);
        Generation gen = mock(Generation.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(out);
        when(out.getResult()).thenReturn(gen);
        when(gen.getOutput()).thenReturn(new AssistantMessage(
                "{\"label\":\"CORRECT\",\"confidence\":0.9,\"reason\":\"ok\",\"rewrittenQuery\":\"q\"}"));

        CragRetrievalEvaluator.CragConfig cfg = new CragRetrievalEvaluator.CragConfig();
        cfg.setEnabled(true);
        cfg.setGateMode("always");
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(chatModelService, cfg);
        var evaluation = evaluator.evaluate("测试查询", RagResult.builder().context("some context").build());
        assertEquals(CragRetrievalEvaluation.Label.CORRECT, evaluation.getLabel());
        verify(chatModel, atLeastOnce()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("CRAG gate-mode=sample rate=0 跳过 LLM")
    void cragSampleZeroSkipsLlm() {
        IChatModelService chatModelService = mock(IChatModelService.class);
        CragRetrievalEvaluator.CragConfig cfg = new CragRetrievalEvaluator.CragConfig();
        cfg.setEnabled(true);
        cfg.setGateMode("sample");
        cfg.setSampleRate(0.0D);
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(chatModelService, cfg);
        var evaluation = evaluator.evaluate("任意查询", RagResult.builder().context("ctx").build());
        assertEquals(CragRetrievalEvaluation.Label.CORRECT, evaluation.getLabel());
        assertNotNull(evaluation.getReason());
        assertTrue(evaluation.getReason().contains("gated"));
        verifyNoInteractions(chatModelService);
    }

    @Test
    @DisplayName("CRAG sample 10% 在 200 条上落在合理带宽（确定性 hash）")
    void cragSampleRateRoughlyMatches() {
        CragRetrievalEvaluator.CragConfig cfg = new CragRetrievalEvaluator.CragConfig();
        cfg.setGateMode("sample");
        cfg.setSampleRate(0.10D);
        CragRetrievalEvaluator evaluator = new CragRetrievalEvaluator(mock(IChatModelService.class), cfg);
        int hit = 0;
        for (int i = 0; i < 200; i++) {
            if (evaluator.shouldEvaluate("query-" + i)) {
                hit++;
            }
        }
        assertTrue(hit > 0 && hit < 60, "sample rate out of expected band: " + hit + "/200");
    }

    @Test
    @DisplayName("实体抽取默认关闭：不调用 LLM，返回 fallback")
    void entityDefaultOffUsesFallback() {
        IChatModelService chatModelService = mock(IChatModelService.class);
        QueryEntityExtractionService.QueryEntityConfig cfg =
                new QueryEntityExtractionService.QueryEntityConfig();
        assertFalse(cfg.isEnabled());
        QueryEntityExtractionService service =
                new QueryEntityExtractionService(chatModelService, cfg);
        List<String> entities = service.extract("人工智能", List.of("人工", "智能"));
        assertEquals(List.of("人工", "智能"), entities);
        verifyNoInteractions(chatModelService);
    }

    @Test
    @DisplayName("实体抽取开启时会调用 LLM")
    void entityEnabledInvokesLlm() {
        IChatModelService chatModelService = mock(IChatModelService.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModelService.getChatModel(any(), any(), any(), any(), any())).thenReturn(chatModel);
        ChatResponse out = mock(ChatResponse.class);
        Generation gen = mock(Generation.class);
        when(chatModel.call(any(Prompt.class))).thenReturn(out);
        when(out.getResult()).thenReturn(gen);
        when(gen.getOutput()).thenReturn(new AssistantMessage("{\"entities\":[\"Foo\"]}"));
        QueryEntityExtractionService.QueryEntityConfig cfg =
                new QueryEntityExtractionService.QueryEntityConfig();
        cfg.setEnabled(true);
        QueryEntityExtractionService service =
                new QueryEntityExtractionService(chatModelService, cfg);
        List<String> entities = service.extract("Foo bar", List.of());
        assertEquals(List.of("Foo"), entities);
        verify(chatModel, atLeastOnce()).call(any(Prompt.class));
    }
}
