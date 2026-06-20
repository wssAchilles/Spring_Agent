package tech.qiantong.qknow.module.kmc.dal.mapper.knowledgeBase;

import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo.KmcKnowledgeRecallLogPageReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeBase.KmcKnowledgeRecallLogDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 召回记录Mapper接口
 *
 * @author qknow
 * @date 2025-07-24
 */
public interface KmcKnowledgeRecallLogMapper extends BaseMapperX<KmcKnowledgeRecallLogDO> {

    default PageResult<KmcKnowledgeRecallLogDO> selectPage(KmcKnowledgeRecallLogPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KmcKnowledgeRecallLogDO>()
                .eqIfPresent(KmcKnowledgeRecallLogDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KmcKnowledgeRecallLogDO::getKnowledgeId, reqVO.getKnowledgeId())
                .likeIfPresent(KmcKnowledgeRecallLogDO::getQuery, reqVO.getQuery())
                .eqIfPresent(KmcKnowledgeRecallLogDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(KmcKnowledgeRecallLogDO::getCreatorId, reqVO.getCreatorId())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KmcKnowledgeRecallLogDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
