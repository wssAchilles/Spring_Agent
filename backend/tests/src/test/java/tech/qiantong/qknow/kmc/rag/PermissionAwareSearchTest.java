package tech.qiantong.qknow.kmc.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.filter.Filter;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.common.core.domain.entity.SysRole;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeRoleService;
import tech.qiantong.qknow.module.kmc.service.rag.PermissionFilter;
import tech.qiantong.qknow.module.system.service.ISysRoleService;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionAwareSearchTest {

    @Mock
    private IKmcKnowledgeRoleService kmcKnowledgeRoleService;

    @Mock
    private ISysRoleService sysRoleService;

    @InjectMocks
    private PermissionFilter permissionFilter;

    private SysRole createRole(Long roleId, String roleKey) {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setRoleKey(roleKey);
        return role;
    }

    private KmcKnowledgeRoleDO createKnowledgeRole(Long roleId, Long knowledgeId) {
        return KmcKnowledgeRoleDO.builder()
                .roleId(roleId)
                .knowledgeId(knowledgeId)
                .validFlag(true)
                .build();
    }

    @Test
    void adminUserReturnsNullFilter() {
        when(sysRoleService.selectRoleByUserId(1L))
                .thenReturn(List.of(createRole(1L, "admin")));

        Filter.Expression result = permissionFilter.buildPermissionFilter(1L);

        assertNull(result);
    }

    @Test
    void systemRoleReturnsNullFilter() {
        when(sysRoleService.selectRoleByUserId(2L))
                .thenReturn(List.of(createRole(5L, "system")));

        Filter.Expression result = permissionFilter.buildPermissionFilter(2L);

        assertNull(result);
    }

    @Test
    void salesRoleReturnsNullFilter() {
        when(sysRoleService.selectRoleByUserId(3L))
                .thenReturn(List.of(createRole(6L, "sales")));

        Filter.Expression result = permissionFilter.buildPermissionFilter(3L);

        assertNull(result);
    }

    @Test
    void userWithKnowledgeBasesReturnsFilterWithIds() {
        SysRole userRole = createRole(10L, "user");
        when(sysRoleService.selectRoleByUserId(4L))
                .thenReturn(List.of(userRole));

        List<KmcKnowledgeRoleDO> knowledgeRoles = List.of(
                createKnowledgeRole(10L, 100L),
                createKnowledgeRole(10L, 200L)
        );
        when(kmcKnowledgeRoleService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(knowledgeRoles);

        Filter.Expression result = permissionFilter.buildPermissionFilter(4L);

        assertNotNull(result);
        assertEquals(Filter.ExpressionType.IN, result.type());
        assertEquals(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, ((Filter.Key) result.left()).key());
        List<?> ids = (List<?>) ((Filter.Value) result.right()).value();
        assertTrue(ids.contains(100L));
        assertTrue(ids.contains(200L));
        assertEquals(2, ids.size());
    }

    @Test
    void userWithNoRolesReturnsEmptyFilter() {
        when(sysRoleService.selectRoleByUserId(5L))
                .thenReturn(Collections.emptyList());

        Filter.Expression result = permissionFilter.buildPermissionFilter(5L);

        assertNotNull(result);
        assertEquals(Filter.ExpressionType.IN, result.type());
    }

    @Test
    void userWithRolesButNoKnowledgeAccessReturnsEmptyFilter() {
        SysRole userRole = createRole(10L, "user");
        when(sysRoleService.selectRoleByUserId(6L))
                .thenReturn(List.of(userRole));

        when(kmcKnowledgeRoleService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(Collections.emptyList());

        Filter.Expression result = permissionFilter.buildPermissionFilter(6L);

        assertNotNull(result);
        assertEquals(Filter.ExpressionType.IN, result.type());
    }
}
