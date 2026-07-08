package tech.qiantong.qknow.kmc.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.RetrieveResultReqVO;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.impl.KmcKnowledgeBaseServiceImpl;
import tech.qiantong.qknow.module.kmc.service.rag.QueryTransformService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KmcKnowledgeBaseServiceLegacyQueryTest {

    @Mock
    private QueryTransformService queryTransformService;

    private KmcKnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new KmcKnowledgeBaseServiceImpl();
        Field field = KmcKnowledgeBaseServiceImpl.class.getDeclaredField("queryTransformService");
        field.setAccessible(true);
        field.set(service, queryTransformService);
    }

    @Test
    void legacyHydeAppliesOnlyToSemanticSearch() throws Exception {
        RetrieveResultReqVO req = req("Denmark capital?");
        when(queryTransformService.isEnabled()).thenReturn(true);
        when(queryTransformService.getStrategy()).thenReturn(" HYDE ");
        when(queryTransformService.generateHypotheticalDocument("Denmark capital?"))
                .thenReturn("Copenhagen is the capital of Denmark.");

        applyLegacyFallbackQuery(req, "semantic_search");

        assertEquals("Copenhagen is the capital of Denmark.", req.getQuery());
    }

    @Test
    void legacyHydeDoesNotRewriteHybridSearch() throws Exception {
        RetrieveResultReqVO req = req("Denmark capital?");
        when(queryTransformService.isEnabled()).thenReturn(true);
        when(queryTransformService.getStrategy()).thenReturn("hyde");

        applyLegacyFallbackQuery(req, "hybrid_search");

        assertEquals("Denmark capital?", req.getQuery());
        verify(queryTransformService, never()).generateHypotheticalDocument("Denmark capital?");
    }

    private RetrieveResultReqVO req(String query) {
        RetrieveResultReqVO req = new RetrieveResultReqVO();
        req.setQuery(query);
        return req;
    }

    private void applyLegacyFallbackQuery(RetrieveResultReqVO req, String searchMethod) throws Exception {
        Method method = KmcKnowledgeBaseServiceImpl.class
                .getDeclaredMethod("applyLegacyFallbackQuery", RetrieveResultReqVO.class, String.class);
        method.setAccessible(true);
        method.invoke(service, req, searchMethod);
    }
}
