package tech.qiantong.qknow.module.ext.dal.mapper.extTaskLog;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.ext.controller.admin.extTaskLog.vo.ExtTaskLogDetailPageReqVO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extTaskLog.ExtTaskLogDetailDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 抽取任务执行日志详情Mapper接口
 *
 * @author qknow
 * @date 2025-12-03
 */
public interface ExtTaskLogDetailMapper extends BaseMapperX<ExtTaskLogDetailDO> {

    default PageResult<ExtTaskLogDetailDO> selectPage(ExtTaskLogDetailPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<ExtTaskLogDetailDO>()
                .eqIfPresent(ExtTaskLogDetailDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(ExtTaskLogDetailDO::getLogId, reqVO.getLogId())
                .eqIfPresent(ExtTaskLogDetailDO::getStep, reqVO.getStep())
                .eqIfPresent(ExtTaskLogDetailDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(ExtTaskLogDetailDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
