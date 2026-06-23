package tech.qiantong.qknow.module.kmc.service.rag;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.constant.WeaviateConstant;
import tech.qiantong.qknow.common.core.domain.entity.SysRole;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;
import tech.qiantong.qknow.module.kmc.service.knowledgeBase.IKmcKnowledgeRoleService;
import tech.qiantong.qknow.module.system.service.ISysRoleService;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PermissionFilter {

    private static final Set<String> ADMIN_ROLE_KEYS = Set.of("admin", "system", "sales");

    @Resource
    private IKmcKnowledgeRoleService kmcKnowledgeRoleService;

    @Resource
    private ISysRoleService sysRoleService;

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

    private Filter.Expression buildAlwaysFalseFilter() {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.in(WeaviateConstant.METADATA_FIELD_KNOWLEDGE_BASE_ID, (List<Object>) Collections.emptyList()).build();
    }
}
