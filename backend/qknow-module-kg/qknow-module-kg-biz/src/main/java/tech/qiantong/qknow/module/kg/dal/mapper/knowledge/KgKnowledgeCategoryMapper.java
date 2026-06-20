package tech.qiantong.qknow.module.kg.dal.mapper.knowledge;

import org.apache.ibatis.annotations.Param;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo.KgKnowledgeCategoryPageReqVO;
import tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo.KgKnowledgeCategoryReqVO;
import tech.qiantong.qknow.module.kg.dal.dataobject.knowledge.KgKnowledgeCategoryDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识分类Mapper接口
 *
 * @author qknow
 * @date 2025-10-20
 */
public interface KgKnowledgeCategoryMapper extends BaseMapperX<KgKnowledgeCategoryDO> {

    default PageResult<KgKnowledgeCategoryDO> selectPage(KgKnowledgeCategoryPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KgKnowledgeCategoryDO>()
                .eqIfPresent(KgKnowledgeCategoryDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KgKnowledgeCategoryDO::getParentId, reqVO.getParentId())
                .likeIfPresent(KgKnowledgeCategoryDO::getName, reqVO.getName())
                .eqIfPresent(KgKnowledgeCategoryDO::getOrderNum, reqVO.getOrderNum())
                .eqIfPresent(KgKnowledgeCategoryDO::getAncestors, reqVO.getAncestors())
                .eqIfPresent(KgKnowledgeCategoryDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KgKnowledgeCategoryDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }

    default List<KgKnowledgeCategoryDO> getKgKnowledgeCategoryList(KgKnowledgeCategoryReqVO reqVO) {
        // 构造动态查询条件
        return selectList(new LambdaQueryWrapperX<KgKnowledgeCategoryDO>()
                .eqIfPresent(KgKnowledgeCategoryDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KgKnowledgeCategoryDO::getParentId, reqVO.getParentId())
                .likeIfPresent(KgKnowledgeCategoryDO::getName, reqVO.getName())
        );
    }

    KgKnowledgeCategoryDO selectKgCategoryById(Long id);

    /**
     * 根据父分类ID查询所有子分类
     * @param parentId 父分类ID
     * @return 子分类列表
     */
    List<KgKnowledgeCategoryDO> selectChildrenCategoryById(@Param("parentId") Long parentId);
}
