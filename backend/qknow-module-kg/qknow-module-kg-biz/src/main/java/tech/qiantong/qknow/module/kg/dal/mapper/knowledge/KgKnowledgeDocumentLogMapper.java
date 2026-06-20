package tech.qiantong.qknow.module.kg.dal.mapper.knowledge;

import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo.KgKnowledgeDocumentLogPageReqVO;
import tech.qiantong.qknow.module.kg.dal.dataobject.knowledge.KgKnowledgeDocumentLogDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件操作日志Mapper接口
 *
 * @author qknow
 * @date 2025-10-22
 */
public interface KgKnowledgeDocumentLogMapper extends BaseMapperX<KgKnowledgeDocumentLogDO> {

    default PageResult<KgKnowledgeDocumentLogDO> selectPage(KgKnowledgeDocumentLogPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KgKnowledgeDocumentLogDO>()
                .eqIfPresent(KgKnowledgeDocumentLogDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KgKnowledgeDocumentLogDO::getUserId, reqVO.getUserId())
                .likeIfPresent(KgKnowledgeDocumentLogDO::getUserName, reqVO.getUserName())
                .eqIfPresent(KgKnowledgeDocumentLogDO::getDocumentId, reqVO.getDocumentId())
                .likeIfPresent(KgKnowledgeDocumentLogDO::getDocumentName, reqVO.getDocumentName())
                .eqIfPresent(KgKnowledgeDocumentLogDO::getType, reqVO.getType())
                .eqIfPresent(KgKnowledgeDocumentLogDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KgKnowledgeDocumentLogDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
