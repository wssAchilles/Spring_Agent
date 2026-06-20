package tech.qiantong.qknow.module.kb.dal.mapper.runtime;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimePageReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeDO;

import java.util.Arrays;

import tech.qiantong.qknow.common.core.page.PageResult;

import java.util.HashSet;
import java.util.Set;

import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * bot运行Mapper接口
 *
 * @author qknow
 * @date 2026-03-18
 */
public interface KbRuntimeMapper extends BaseMapperX<KbRuntimeDO> {

    default PageResult<KbRuntimeDO> selectPage(KbRuntimePageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KbRuntimeDO>()
                .eqIfPresent(KbRuntimeDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KbRuntimeDO::getBotId, reqVO.getBotId())
                .eqIfPresent(KbRuntimeDO::getInput, reqVO.getInput())
                .eqIfPresent(KbRuntimeDO::getOutput, reqVO.getOutput())
                .eqIfPresent(KbRuntimeDO::getStatus, reqVO.getStatus())
                .eqIfPresent(KbRuntimeDO::getRuntime, reqVO.getRuntime())
                .eqIfPresent(KbRuntimeDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KbRuntimeDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }

    @Update("update kb_runtime set status = #{item.status},runtime=#{item.runtime}, `output`=#{item.output}, runtime = #{item.runtime} where id = #{item.id}")
    void saveRunSuccess(@Param("item") KbRuntimeDO runtimeDO);
}
