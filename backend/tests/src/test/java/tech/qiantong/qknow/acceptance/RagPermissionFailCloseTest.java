package tech.qiantong.qknow.acceptance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tech.qiantong.qknow.common.core.domain.model.LoginUser;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.RetrieveResultReqVO;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.impl.KmcKnowledgeBaseServiceImpl;
import tech.qiantong.qknow.module.kmc.service.rag.PermissionFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-13 回归防护：存在用户上下文时，知识库权限查询异常必须 fail-close，
 * 禁止"跳过权限检查"后继续召回其它租户知识库。
 */
class RagPermissionFailCloseTest {

    private final KmcKnowledgeBaseServiceImpl service = new KmcKnowledgeBaseServiceImpl();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("权限过滤异常时召回返回空，禁止 fail-open 泄露其它知识库")
    void permissionFailureMustReturnEmpty() {
        authenticateUser(123L);
        PermissionFilter permissionFilter = mock(PermissionFilter.class);
        when(permissionFilter.getAccessibleKnowledgeBaseIds(any()))
                .thenThrow(new IllegalStateException("permission store unavailable"));
        ReflectionTestUtils.setField(service, "permissionFilter", permissionFilter);

        RetrieveResultReqVO request = new RetrieveResultReqVO();
        request.setId(88L);

        assertTrue(service.recallTest(request).isEmpty(),
                "权限查询失败必须按拒绝处理，不能返回未授权知识库召回结果");
    }

    @Test
    @DisplayName("用户无权访问知识库时召回返回空")
    void inaccessibleKnowledgeBaseReturnsEmpty() {
        authenticateUser(123L);
        PermissionFilter permissionFilter = mock(PermissionFilter.class);
        when(permissionFilter.getAccessibleKnowledgeBaseIds(any())).thenReturn(List.of(1L, 2L));
        ReflectionTestUtils.setField(service, "permissionFilter", permissionFilter);

        RetrieveResultReqVO request = new RetrieveResultReqVO();
        request.setId(88L);

        assertTrue(service.recallTest(request).isEmpty());
    }

    private void authenticateUser(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }
}
