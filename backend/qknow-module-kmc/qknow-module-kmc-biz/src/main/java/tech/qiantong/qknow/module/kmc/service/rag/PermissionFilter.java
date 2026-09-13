package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.common.core.domain.entity.SysRole;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeBaseDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeBaseService;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeRoleService;
import tech.qiantong.qknow.module.system.service.ISysRoleService;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工业级强类型零泄露权限过滤器 (Phase 17 升级)
 * <p>
 * 核心特性：
 * 1. 实施 Fail-Closed 闭环交集原则，绝不向未授权租户返回 null 或放行
 * 2. 区分全局未指定租户模式（兼容既有单测）与强类型租户工作空间模式 (workspaceId)
 * 3. 强制三元交集过滤：requested ∩ workspace ∩ accessible，杜绝跨租户探针探测
 * </p>
 */
@Slf4j
@Component
public class PermissionFilter {

    private static final Set<String> ADMIN_ROLE_KEYS = Set.of("admin", "system", "sales");
    private static final Set<String> SYSTEM_SUPER_ADMIN_KEYS = Set.of("admin", "system");

    @Resource
    private IKmcKnowledgeRoleService kmcKnowledgeRoleService;

    @Resource
    private ISysRoleService sysRoleService;

    @Resource
    private IKmcKnowledgeBaseService kmcKnowledgeBaseService;

    /**
     * 兼容既有未指定工作区的老方法
     */
    public Filter.Expression buildPermissionFilter(Long userId) {
        List<SysRole> roles = sysRoleService.selectRoleByUserId(userId);

        boolean isAdmin = roles.stream()
                .anyMatch(r -> r.getRoleKey() != null && ADMIN_ROLE_KEYS.contains(r.getRoleKey()));
        if (isAdmin) {
            return null;
        }

        List<Long> roleIds = roles.stream()
                .map(SysRole::getRoleId)
                .collect(Collectors.toList());

        if (roleIds.isEmpty()) {
            return buildAlwaysFalseFilter();
        }

        List<Long> knowledgeIds = kmcKnowledgeRoleService.list(
                        new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                                .in(KmcKnowledgeRoleDO::getRoleId, roleIds)
                                .eq(KmcKnowledgeRoleDO::getValidFlag, true))
                .stream()
                .map(KmcKnowledgeRoleDO::getKnowledgeId)
                .distinct()
                .collect(Collectors.toList());

        if (knowledgeIds.isEmpty()) {
            return buildAlwaysFalseFilter();
        }

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.in(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, knowledgeIds.toArray()).build();
    }

    /**
     * 兼容既有未指定工作区的老方法（管理员返回 null，无权限返回空列表）
     */
    public List<Long> getAccessibleKnowledgeBaseIds(Long userId) {
        List<SysRole> roles = sysRoleService.selectRoleByUserId(userId);

        boolean isAdmin = roles.stream()
                .anyMatch(r -> r.getRoleKey() != null && ADMIN_ROLE_KEYS.contains(r.getRoleKey()));
        if (isAdmin) {
            return null;
        }

        List<Long> roleIds = roles.stream()
                .map(SysRole::getRoleId)
                .collect(Collectors.toList());

        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        return kmcKnowledgeRoleService.list(
                        new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                                .in(KmcKnowledgeRoleDO::getRoleId, roleIds)
                                .eq(KmcKnowledgeRoleDO::getValidFlag, true))
                .stream()
                .map(KmcKnowledgeRoleDO::getKnowledgeId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 强类型租户安全过滤表达式构建（Fail-Closed 保证，绝不返回 null）
     */
    public Filter.Expression buildPermissionFilter(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return buildAlwaysFalseFilter();
        }

        List<Long> accessibleKbIds = getAccessibleKnowledgeBaseIds(workspaceId, userId);
        if (accessibleKbIds.isEmpty()) {
            return buildAlwaysFalseFilter();
        }

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.in(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, accessibleKbIds.toArray()).build();
    }

    /**
     * 获取用户在指定 Workspace 内合法受限的知识库 ID 列表（Fail-Closed 保证，绝不返回 null）
     */
    public List<Long> getAccessibleKnowledgeBaseIds(Long workspaceId, Long userId) {
        if (workspaceId == null || userId == null) {
            return Collections.emptyList();
        }

        // 1. 查询该 Workspace 下存在的全量知识库
        List<Long> workspaceKbIds = Collections.emptyList();
        if (kmcKnowledgeBaseService != null) {
            var kbList = kmcKnowledgeBaseService.list(
                    new LambdaQueryWrapperX<KmcKnowledgeBaseDO>()
                            .eq(KmcKnowledgeBaseDO::getWorkspaceId, workspaceId));
            if (kbList != null) {
                workspaceKbIds = kbList.stream()
                        .map(KmcKnowledgeBaseDO::getId)
                        .toList();
            }
        }

        if (workspaceKbIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 判定用户角色
        List<SysRole> roles = sysRoleService.selectRoleByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        boolean isSuperAdmin = roles.stream()
                .anyMatch(r -> r.getRoleKey() != null && SYSTEM_SUPER_ADMIN_KEYS.contains(r.getRoleKey()));
        if (isSuperAdmin) {
            // 超管在当前租户下可访问当前工作区的全部知识库，绝不跨租户越权
            return workspaceKbIds;
        }

        // 3. 普通角色交集过滤
        List<Long> roleIds = roles.stream()
                .map(SysRole::getRoleId)
                .collect(Collectors.toList());

        List<Long> authorizedKbIds = kmcKnowledgeRoleService.list(
                        new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                                .in(KmcKnowledgeRoleDO::getRoleId, roleIds)
                                .eq(KmcKnowledgeRoleDO::getValidFlag, true))
                .stream()
                .map(KmcKnowledgeRoleDO::getKnowledgeId)
                .distinct()
                .toList();

        Set<Long> workspaceKbSet = new HashSet<>(workspaceKbIds);
        return authorizedKbIds.stream()
                .filter(workspaceKbSet::contains)
                .collect(Collectors.toList());
    }

    /**
     * 强类型三元交集过滤：requested ∩ workspace ∩ authorized（Fail-Closed 保证，绝不返回 null）
     */
    public List<Long> filterAccessibleKbIds(Long workspaceId, Long userId, List<Long> requestedKbIds) {
        if (requestedKbIds == null || requestedKbIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> allowedInWorkspace = getAccessibleKnowledgeBaseIds(workspaceId, userId);
        if (allowedInWorkspace.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> allowedSet = new HashSet<>(allowedInWorkspace);
        return requestedKbIds.stream()
                .filter(allowedSet::contains)
                .collect(Collectors.toList());
    }

    private Filter.Expression buildAlwaysFalseFilter() {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.in(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, (List<Object>) Collections.emptyList()).build();
    }
}
