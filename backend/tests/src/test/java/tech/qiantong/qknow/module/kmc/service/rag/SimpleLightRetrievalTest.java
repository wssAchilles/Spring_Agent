package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.core.utils.SecurityUtils;
import tech.qiantong.qknow.module.kmc.service.rag.model.RagResult;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimpleLightRetrievalTest {

    private RagRetrievalService service;
    private QueryRouter queryRouter;
    private KeywordRetriever keywordRetriever;
    private PermissionFilter permissionFilter;
    private RagContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        service = new RagRetrievalService();
        queryRouter = mock(QueryRouter.class);
        keywordRetriever = mock(KeywordRetriever.class);
        permissionFilter = mock(PermissionFilter.class);
        contextBuilder = mock(RagContextBuilder.class);
        ReflectionTestUtils.setField(service, "queryRouter", queryRouter);
        ReflectionTestUtils.setField(service, "keywordRetriever", keywordRetriever);
        ReflectionTestUtils.setField(service, "permissionFilter", permissionFilter);
        ReflectionTestUtils.setField(service, "ragContextBuilder", contextBuilder);
        ReflectionTestUtils.setField(service, "simpleLightTopK", 5);
        when(permissionFilter.getAccessibleKnowledgeBaseIds(any())).thenReturn(null);
        when(contextBuilder.buildContext(anyList(), anyBoolean())).thenReturn("ctx");
        when(queryRouter.classify(anyString())).thenReturn(QueryRouter.QueryRoute.SIMPLE);
    }

    @Test
    @DisplayName("默认 light-retrieval=false：SIMPLE 仍返回空上下文")
    void simpleRoute_defaultReturnsEmpty() {
        ReflectionTestUtils.setField(service, "simpleLightRetrieval", false);

        RagResult result = service.retrieve(1L, "人工智能", 10, false);

        assertEquals("", result.getContext());
        assertTrue(result.getSources().isEmpty());
        verifyNoInteractions(keywordRetriever);
    }

    @Test
    @DisplayName("light-retrieval=true：SIMPLE 走 keyword 且返回 context")
    void simpleRoute_lightEnabledUsesKeyword() {
        ReflectionTestUtils.setField(service, "simpleLightRetrieval", true);
        RetrievalResult hit = RetrievalResult.builder()
                .segmentId(1L)
                .documentName("人工智能.pdf")
                .content("相关片段")
                .score(1.0)
                .build();
        when(keywordRetriever.retrieve(1L, "人工智能", 5)).thenReturn(List.of(hit));

        RagResult result;
        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::getUserId).thenReturn(1L);
            result = service.retrieve(1L, "人工智能", 10, false);
        }

        assertEquals("ctx", result.getContext());
        assertEquals(1, result.getSources().size());
        assertEquals("人工智能.pdf", result.getSources().get(0).getDocumentName());
        verify(keywordRetriever, times(1)).retrieve(1L, "人工智能", 5);
        assertTrue(Boolean.TRUE.equals(result.getDebugInfo().get("simpleLightRetrieval")));
    }
}
