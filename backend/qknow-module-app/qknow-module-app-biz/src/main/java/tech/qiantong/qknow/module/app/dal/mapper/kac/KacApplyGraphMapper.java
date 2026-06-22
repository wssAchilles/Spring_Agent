package tech.qiantong.qknow.module.app.dal.mapper.kac;

import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyGraphDO;
import java.util.Arrays;
import tech.qiantong.qknow.common.core.page.PageResult;
import java.util.HashSet;
import java.util.Set;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphPageReqVO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 应用图谱关联Mapper接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface KacApplyGraphMapper extends BaseMapperX<KacApplyGraphDO> {

    default PageResult<KacApplyGraphDO> selectPage(KacApplyGraphPageReqVO reqVO) {
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        return selectPage(reqVO, new LambdaQueryWrapperX<KacApplyGraphDO>()
                .eqIfPresent(KacApplyGraphDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KacApplyGraphDO::getApplyId, reqVO.getApplyId())
                .eqIfPresent(KacApplyGraphDO::getGraphId, reqVO.getGraphId())
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
