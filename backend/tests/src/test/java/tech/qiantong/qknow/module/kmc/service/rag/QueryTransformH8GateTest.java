package tech.qiantong.qknow.module.kmc.service.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.service.IChatClientService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryTransformH8GateTest {

    @Test
    @DisplayName("默认 enabled=false：rewriteQuery 不调用 LLM")
    void defaultDisabledSkipsRewriteLlm() {
        IChatClientService chatClientService = mock(IChatClientService.class);
        QueryTransformService.QueryTransformConfig cfg =
                new QueryTransformService.QueryTransformConfig();
        assertFalse(cfg.isEnabled());
        QueryTransformService service = new QueryTransformService(chatClientService, cfg);
        assertEquals("原始问题", service.rewriteQuery("原始问题"));
        verifyNoInteractions(chatClientService);
    }

    @Test
    @DisplayName("strategy=none：即使 enabled 也不改写")
    void strategyNoneSkipsRewrite() {
        IChatClientService chatClientService = mock(IChatClientService.class);
        QueryTransformService.QueryTransformConfig cfg =
                new QueryTransformService.QueryTransformConfig();
        cfg.setEnabled(true);
        cfg.setStrategy("none");
        QueryTransformService service = new QueryTransformService(chatClientService, cfg);
        assertEquals("q", service.rewriteQuery("q"));
        verifyNoInteractions(chatClientService);
    }

    @Test
    @DisplayName("strategy=hyde：入口 rewriteQuery 不改写")
    void strategyHydeSkipsEntryRewrite() {
        IChatClientService chatClientService = mock(IChatClientService.class);
        QueryTransformService.QueryTransformConfig cfg =
                new QueryTransformService.QueryTransformConfig();
        cfg.setEnabled(true);
        cfg.setStrategy("hyde");
        QueryTransformService service = new QueryTransformService(chatClientService, cfg);
        assertEquals("q", service.rewriteQuery("q"));
        verifyNoInteractions(chatClientService);
    }

    @Test
    @DisplayName("config 额外字段：compressQuery 在 disabled 时原样返回")
    void compressSkippedWhenDisabled() {
        IChatClientService chatClientService = mock(IChatClientService.class);
        QueryTransformService.QueryTransformConfig cfg =
                new QueryTransformService.QueryTransformConfig();
        assertFalse(cfg.isEnabled());
        QueryTransformService service = new QueryTransformService(chatClientService, cfg);
        assertEquals("follow-up", service.compressQuery("follow-up", java.util.List.of()));
        verifyNoInteractions(chatClientService);
    }
}
