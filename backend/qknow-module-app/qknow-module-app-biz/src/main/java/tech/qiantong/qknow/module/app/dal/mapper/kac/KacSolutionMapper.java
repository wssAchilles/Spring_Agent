package tech.qiantong.qknow.module.app.dal.mapper.kac;

import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionDO;
import java.util.Arrays;
import tech.qiantong.qknow.common.core.page.PageResult;
import java.util.HashSet;
import java.util.Set;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionPageReqVO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 解决方案管理Mapper接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface KacSolutionMapper extends BaseMapperX<KacSolutionDO> {

    default PageResult<KacSolutionDO> selectPage(KacSolutionPageReqVO reqVO) {
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        return selectPage(reqVO, new LambdaQueryWrapperX<KacSolutionDO>()
                .eqIfPresent(KacSolutionDO::getWorkspaceId, reqVO.getWorkspaceId())
                .likeIfPresent(KacSolutionDO::getName, reqVO.getName())
                .eqIfPresent(KacSolutionDO::getDescription, reqVO.getDescription())
                .eqIfPresent(KacSolutionDO::getType, reqVO.getType())
                .eqIfPresent(KacSolutionDO::getStatus, reqVO.getStatus())
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
