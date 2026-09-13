package tech.qiantong.qknow.kb.biz.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StateGraphExecutorTest {

    private CascadeRouter cascadeRouter;
    private StateGraphExecutor executor;

    @BeforeEach
    public void setup() {
        cascadeRouter = mock(CascadeRouter.class);
        executor = new StateGraphExecutor(cascadeRouter);
    }

    @Test
    public void testNormalExecution() {
        when(cascadeRouter.route(anyString()))
                .thenReturn(new CascadeRouter.RouteResult("knowledge", CascadeRouter.RouteLayer.L1_SEMANTIC, 0.95));

        String result = executor.executeGraph("正常的明确问题");
        assertTrue(result.contains("content"));
        assertTrue(result.contains("knowledge"));
    }

    @Test
    public void testAmbiguousSuspension() {
        when(cascadeRouter.route(anyString()))
                .thenThrow(new ClarificationRequiredException("意图不明确", List.of("业务A", "业务B")));

        String result = executor.executeGraph("模棱两可的问题");
        
        // 断言返回了控制型 JSON，而不是 markdown 流
        assertTrue(result.contains("clarification_required"));
        assertTrue(result.contains("业务A"));
        assertTrue(result.contains("业务B"));
        assertTrue(result.contains("thread_id"));
    }
}
