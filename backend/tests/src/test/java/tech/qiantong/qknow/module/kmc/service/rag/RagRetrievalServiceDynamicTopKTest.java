package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagRetrievalServiceDynamicTopKTest {

    private RagRetrievalService service;
    private DynamicTopKConfig config;
    private Method resolveTopK;

    @BeforeEach
    void setUp() throws Exception {
        service = new RagRetrievalService();
        config = new DynamicTopKConfig();
        ReflectionTestUtils.setField(service, "dynamicTopKConfig", config);
        resolveTopK = RagRetrievalService.class.getDeclaredMethod(
                "resolveTopK", int.class, QueryRouter.QueryRoute.class, QueryIntent.class);
        resolveTopK.setAccessible(true);
    }

    @Test
    @DisplayName("复杂查询提升topK并满足下限")
    void complexRoute_increasesTopK() throws Exception {
        QueryIntent intent = QueryIntent.builder()
                .keywords(List.of("趋势", "影响"))
                .build();

        int topK = (int) resolveTopK.invoke(service, 10, QueryRouter.QueryRoute.COMPLEX, intent);

        assertEquals(20, topK);
    }

    @Test
    @DisplayName("时序查询应用时序倍数")
    void temporalIntent_appliesTemporalMultiplier() throws Exception {
        QueryIntent intent = QueryIntent.builder()
                .dayNo(5)
                .build();

        int topK = (int) resolveTopK.invoke(service, 10, QueryRouter.QueryRoute.MEDIUM, intent);

        assertEquals(13, topK);
    }

    @Test
    @DisplayName("禁用Dynamic topK时保留请求值")
    void disabledDynamicTopK_keepsRequestedTopK() throws Exception {
        config.setEnabled(false);

        int topK = (int) resolveTopK.invoke(service, 7, QueryRouter.QueryRoute.COMPLEX, QueryIntent.builder().build());

        assertEquals(7, topK);
    }
}
