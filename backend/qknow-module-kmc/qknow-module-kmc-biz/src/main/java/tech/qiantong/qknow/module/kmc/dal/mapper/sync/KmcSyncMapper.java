package tech.qiantong.qknow.module.kmc.dal.mapper.sync;

import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kmc.controller.admin.sync.vo.KmcSyncPageReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.sync.KmcSyncDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件同步Mapper接口
 *
 * @author qknow
 * @date 2025-03-18
 */
public interface KmcSyncMapper extends BaseMapperX<KmcSyncDO> {

    default PageResult<KmcSyncDO> selectPage(KmcSyncPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));
        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KmcSyncDO>()
                .eqIfPresent(KmcSyncDO::getDocumentId, reqVO.getDocumentId())
                .eqIfPresent(KmcSyncDO::getQmDatasetId, reqVO.getQmDatasetId())
                .eqIfPresent(KmcSyncDO::getQmDocumentId, reqVO.getQmDocumentId())
                .eqIfPresent(KmcSyncDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KmcSyncDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
