package tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase;

import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRolePageReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRoleDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 知识库角色关联Mapper接口
 *
 * @author qknow
 * @date 2025-07-24
 */
public interface KmcKnowledgeRoleMapper extends BaseMapperX<KmcKnowledgeRoleDO> {

    default PageResult<KmcKnowledgeRoleDO> selectPage(KmcKnowledgeRolePageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KmcKnowledgeRoleDO>()
                .eqIfPresent(KmcKnowledgeRoleDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KmcKnowledgeRoleDO::getKnowledgeId, reqVO.getKnowledgeId())
                .eqIfPresent(KmcKnowledgeRoleDO::getRoleId, reqVO.getRoleId())
                .eqIfPresent(KmcKnowledgeRoleDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KmcKnowledgeRoleDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
