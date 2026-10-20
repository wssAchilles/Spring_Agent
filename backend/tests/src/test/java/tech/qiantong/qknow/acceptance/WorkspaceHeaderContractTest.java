package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tech.qiantong.qknow.common.core.controller.BaseController;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R6 回归防护：客户端 workspace-id Header 只能选择合法工作区，非法值必须回退服务端基线。
 */
class WorkspaceHeaderContractTest {

    private final BaseController controller = new BaseController();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("无 Header 时回退服务端基线 1001")
    void defaultWorkspaceWithoutHeader() {
        bindRequest(new MockHttpServletRequest());
        assertEquals(1001L, controller.getWorkSpaceId());
    }

    @Test
    @DisplayName("伪造 workspace-id 越权头被拒绝并回退 1001")
    void spoofedWorkspaceHeaderMustFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Workspace-Id", "888");
        bindRequest(request);
        assertEquals(1001L, controller.getWorkSpaceId(), "普通用户不允许通过 Header 切换到任意工作区");
    }

    @Test
    @DisplayName("合法基线 workspace-id 头允许透传")
    void legitimateWorkspaceHeaderAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("workspace-id", "1001");
        bindRequest(request);
        assertEquals(1001L, controller.getWorkSpaceId());
    }

    private void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
