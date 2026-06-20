package tech.qiantong.qknow.module.kg.dal.mapper.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo.KgKnowledgeDocumentPageReqVO;
import tech.qiantong.qknow.module.kg.dal.dataobject.knowledge.KgKnowledgeDocumentDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.*;

/**
 * 知识文件Mapper接口
 *
 * @author qknow
 * @date 2025-10-20
 */
public interface KgKnowledgeDocumentMapper extends BaseMapperX<KgKnowledgeDocumentDO> {

    default PageResult<KgKnowledgeDocumentDO> selectPage(KgKnowledgeDocumentPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KgKnowledgeDocumentDO>()
                .eqIfPresent(KgKnowledgeDocumentDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KgKnowledgeDocumentDO::getCategoryId, reqVO.getCategoryId())
                .likeIfPresent(KgKnowledgeDocumentDO::getCategoryName, reqVO.getCategoryName())
                .likeIfPresent(KgKnowledgeDocumentDO::getName, reqVO.getName())
                .eqIfPresent(KgKnowledgeDocumentDO::getPath, reqVO.getPath())
                .eqIfPresent(KgKnowledgeDocumentDO::getDescription, reqVO.getDescription())
                .eqIfPresent(KgKnowledgeDocumentDO::getCreateTime, reqVO.getCreateTime())
                .inIfPresent(KgKnowledgeDocumentDO::getCategoryId, reqVO.getIds())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KgKnowledgeDocumentDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }

    default List<Map<String, Object>> getFileTypes() {
        return selectMaps(
                new QueryWrapper<KgKnowledgeDocumentDO>()
                        .select("category_id", "COUNT(category_id) AS count")
                        .eq("del_flag", 0)
                        .groupBy("category_id")
        );
    }
}
