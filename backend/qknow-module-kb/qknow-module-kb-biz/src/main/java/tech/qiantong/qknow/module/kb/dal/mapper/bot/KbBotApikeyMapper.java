package tech.qiantong.qknow.module.kb.dal.mapper.bot;

import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.kb.controller.admin.bot.vo.KbBotApikeyPageReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.bot.KbBotApikeyDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * bot访问密钥Mapper接口
 *
 * @author qknow
 * @date 2026-04-24
 */
public interface KbBotApikeyMapper extends BaseMapperX<KbBotApikeyDO> {

    default PageResult<KbBotApikeyDO> selectPage(KbBotApikeyPageReqVO reqVO) {
        // 定义排序的字段（防止 SQL 注入，与数据库字段名称一致）
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "create_by"));

        // 构造动态查询条件
        return selectPage(reqVO, new LambdaQueryWrapperX<KbBotApikeyDO>()
                .eqIfPresent(KbBotApikeyDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KbBotApikeyDO::getApiKey, reqVO.getApiKey())
                .eqIfPresent(KbBotApikeyDO::getBotId, reqVO.getBotId())
                .eqIfPresent(KbBotApikeyDO::getCreateTime, reqVO.getCreateTime())
                // 如果 reqVO.getName() 不为空，则添加 name 的精确匹配条件（name = '<name>'）
                // .likeIfPresent(KbBotApikeyDO::getName, reqVO.getName())
                // 按照 createTime 字段降序排序
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
