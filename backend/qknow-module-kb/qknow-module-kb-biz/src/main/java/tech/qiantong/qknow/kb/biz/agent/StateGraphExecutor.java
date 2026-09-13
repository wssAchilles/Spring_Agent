package tech.qiantong.qknow.kb.biz.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
public class StateGraphExecutor {

    private final CascadeRouter cascadeRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StateGraphExecutor(CascadeRouter cascadeRouter) {
        this.cascadeRouter = cascadeRouter;
    }

    @Data
    public static class SseControlFrame {
        private String type;
        private String thread_id;
        private Context context;

        @Data
        public static class Context {
            private String reason;
            private List<String> options;
        }
    }

    /**
     * 执行图状态机，支持被挂起
     * 返回的值可以直接通过 SSE Flush 到前端
     */
    public String executeGraph(String userQuery) {
        try {
            CascadeRouter.RouteResult result = cascadeRouter.route(userQuery);
            // 正常执行后续 RAG/Agent 逻辑 (简化返回)
            return "{\"type\": \"content\", \"data\": \"正常执行路由: " + result.getRouteName() + "\"}";
            
        } catch (ClarificationRequiredException ex) {
            log.warn("State Graph Suspended! Ambiguous intent detected.");
            
            SseControlFrame frame = new SseControlFrame();
            frame.setType("clarification_required");
            frame.setThread_id(UUID.randomUUID().toString());
            
            SseControlFrame.Context context = new SseControlFrame.Context();
            context.setReason(ex.getMessage());
            context.setOptions(ex.getOptions());
            frame.setContext(context);
            
            try {
                return objectMapper.writeValueAsString(frame);
            } catch (Exception e) {
                return "{\"type\": \"error\"}";
            }
        }
    }
}
