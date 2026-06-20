package tech.qiantong.qknow.module.kb.dal.mapper.codeNative;

import tech.qiantong.qknow.module.kb.dal.dataobject.codeNative.KbCodeNativeDO;

import java.util.Arrays;

import com.github.yulichang.base.MPJBaseMapper;
import tech.qiantong.qknow.common.core.page.PageResult;

import java.util.HashSet;
import java.util.Set;

import tech.qiantong.qknow.module.kb.controller.admin.codeNative.vo.KbCodeNativePageReqVO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 白盒化开发Mapper接口
 *
 * @author qknow
 * @date 2026-04-09
 */
public interface KbCodeNativeMapper extends BaseMapperX<KbCodeNativeDO> {

    default PageResult<KbCodeNativeDO> selectPage(KbCodeNativePageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KbCodeNativeDO>()
                .eqIfPresent(KbCodeNativeDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KbCodeNativeDO::getBotId, reqVO.getBotId())
                .likeIfPresent(KbCodeNativeDO::getClassName, reqVO.getClassName())
                .eqIfPresent(KbCodeNativeDO::getCode, reqVO.getCode())
                .eqIfPresent(KbCodeNativeDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KbCodeNativeDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
