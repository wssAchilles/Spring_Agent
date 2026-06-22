package tech.qiantong.qknow.module.app.dal.mapper.kac;

import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionApplyDO;
import java.util.Arrays;
import tech.qiantong.qknow.common.core.page.PageResult;
import java.util.HashSet;
import java.util.Set;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplyPageReqVO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 解决方案应用关联Mapper接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface KacSolutionApplyMapper extends BaseMapperX<KacSolutionApplyDO> {

    default PageResult<KacSolutionApplyDO> selectPage(KacSolutionApplyPageReqVO reqVO) {
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        return selectPage(reqVO, new LambdaQueryWrapperX<KacSolutionApplyDO>()
                .eqIfPresent(KacSolutionApplyDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KacSolutionApplyDO::getSolutionId, reqVO.getSolutionId())
                .eqIfPresent(KacSolutionApplyDO::getApplyId, reqVO.getApplyId())
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
