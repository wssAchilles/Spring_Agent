package tech.qiantong.qknow.module.kmc.dal.mapper.kmcDocumentLog;

import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kmc.controller.admin.kmcDocumentLog.vo.KmcDocumentLogPageReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.kmcDocumentLog.KmcDocumentLogDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件操作日志Mapper接口
 *
 * @author qknow
 * @date 2025-03-24
 */
public interface KmcDocumentLogMapper extends BaseMapperX<KmcDocumentLogDO> {

    default PageResult<KmcDocumentLogDO> selectPage(KmcDocumentLogPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KmcDocumentLogDO>()
                .eqIfPresent(KmcDocumentLogDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KmcDocumentLogDO::getUserId, reqVO.getUserId())
                .likeIfPresent(KmcDocumentLogDO::getUserName, reqVO.getUserName())
                .eqIfPresent(KmcDocumentLogDO::getDocumentId, reqVO.getDocumentId())
                .likeIfPresent(KmcDocumentLogDO::getDocumentName, reqVO.getDocumentName())
                .eqIfPresent(KmcDocumentLogDO::getType, reqVO.getType())
                .eqIfPresent(KmcDocumentLogDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KmcDocumentLogDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }

    List<KmcDocumentLogDO> seletAllDocumentLogList(KmcDocumentLogDO kmcDocumentLog);
}
