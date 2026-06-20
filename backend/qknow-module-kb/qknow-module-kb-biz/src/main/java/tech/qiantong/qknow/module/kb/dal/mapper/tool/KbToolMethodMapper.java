package tech.qiantong.qknow.module.kb.dal.mapper.tool;

import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolMethodPageReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.tool.KbToolMethodDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 工具方法Mapper接口
 *
 * @author qknow
 * @date 2026-03-19
 */
public interface KbToolMethodMapper extends BaseMapperX<KbToolMethodDO> {

    default PageResult<KbToolMethodDO> selectPage(KbToolMethodPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KbToolMethodDO>()
                .eqIfPresent(KbToolMethodDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KbToolMethodDO::getToolId, reqVO.getToolId())
                .eqIfPresent(KbToolMethodDO::getCode, reqVO.getCode())
                .likeIfPresent(KbToolMethodDO::getName, reqVO.getName())
                .eqIfPresent(KbToolMethodDO::getDescription, reqVO.getDescription())
                .eqIfPresent(KbToolMethodDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KbToolMethodDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
