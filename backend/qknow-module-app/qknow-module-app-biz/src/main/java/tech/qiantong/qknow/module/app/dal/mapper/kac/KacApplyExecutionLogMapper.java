package tech.qiantong.qknow.module.app.dal.mapper.kac;

import tech.qiantong.qknow.common.core.page.PageParam;
import tech.qiantong.qknow.common.core.page.PageResult;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyExecutionLogDO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 应用执行日志与凭单审计 Mapper 接口
 *
 * @author qknow
 */
public interface KacApplyExecutionLogMapper extends BaseMapperX<KacApplyExecutionLogDO> {

    default PageResult<KacApplyExecutionLogDO> selectPageByApply(Long applyId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<KacApplyExecutionLogDO>()
                .eqIfPresent(KacApplyExecutionLogDO::getApplyId, applyId)
                .orderByDesc(KacApplyExecutionLogDO::getId));
    }
}
