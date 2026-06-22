package tech.qiantong.qknow.module.app.dal.mapper.kac;

import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacPluginDO;
import java.util.Arrays;
import tech.qiantong.qknow.common.core.page.PageResult;
import java.util.HashSet;
import java.util.Set;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginPageReqVO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 插件管理Mapper接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface KacPluginMapper extends BaseMapperX<KacPluginDO> {

    default PageResult<KacPluginDO> selectPage(KacPluginPageReqVO reqVO) {
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        return selectPage(reqVO, new LambdaQueryWrapperX<KacPluginDO>()
                .eqIfPresent(KacPluginDO::getWorkspaceId, reqVO.getWorkspaceId())
                .likeIfPresent(KacPluginDO::getName, reqVO.getName())
                .eqIfPresent(KacPluginDO::getDescription, reqVO.getDescription())
                .eqIfPresent(KacPluginDO::getType, reqVO.getType())
                .eqIfPresent(KacPluginDO::getStatus, reqVO.getStatus())
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
