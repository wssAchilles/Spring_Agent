package tech.qiantong.qknow.module.kmc.dal.mapper.kmcCategory;

import org.apache.ibatis.annotations.Param;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kmc.controller.admin.kmcCategory.vo.KmcCategoryPageReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.kmcCategory.KmcCategoryDO;
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
 * @date 2025-02-13
 */
public interface KmcCategoryMapper extends BaseMapperX<KmcCategoryDO> {

    default PageResult<KmcCategoryDO> selectPage(KmcCategoryPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KmcCategoryDO>()
                .eqIfPresent(KmcCategoryDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KmcCategoryDO::getParentId, reqVO.getParentId())
                .likeIfPresent(KmcCategoryDO::getName, reqVO.getName())
                .eqIfPresent(KmcCategoryDO::getOrderNum, reqVO.getOrderNum())
                .eqIfPresent(KmcCategoryDO::getAncestors, reqVO.getAncestors())
                .eqIfPresent(KmcCategoryDO::getCreateTime, reqVO.getCreateTime())
                .eqIfPresent(KmcCategoryDO::getUpdaterId, reqVO.getUpdaterId())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(kmcCategoryDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }

    List<KmcCategoryDO> getKmcCategoryAllList(KmcCategoryDO kmcCategoryDO);

    KmcCategoryDO selectKmcCategoryById(Long id);

    /**
     * 根据父分类ID查询所有子分类
     * @param parentId 父分类ID
     * @return 子分类列表
     */
    List<KmcCategoryDO> selectChildrenCategoryById(@Param("parentId") Long parentId);

}
