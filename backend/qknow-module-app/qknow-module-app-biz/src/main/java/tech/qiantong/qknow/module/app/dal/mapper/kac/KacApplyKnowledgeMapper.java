package tech.qiantong.qknow.module.app.dal.mapper.kac;

import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyKnowledgeDO;
import java.util.Arrays;
import tech.qiantong.qknow.common.core.page.PageResult;
import java.util.HashSet;
import java.util.Set;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgePageReqVO;
import tech.qiantong.qknow.mybatis.core.mapper.BaseMapperX;
import tech.qiantong.qknow.mybatis.core.query.LambdaQueryWrapperX;

/**
 * 应用知识库关联Mapper接口
 *
 * @author qknow
 * @date 2026-06-22
 */
public interface KacApplyKnowledgeMapper extends BaseMapperX<KacApplyKnowledgeDO> {

    default PageResult<KacApplyKnowledgeDO> selectPage(KacApplyKnowledgePageReqVO reqVO) {
        Set<String> allowedColumns = new HashSet<>(Arrays.asList("id", "create_time", "update_time"));

        return selectPage(reqVO, new LambdaQueryWrapperX<KacApplyKnowledgeDO>()
                .eqIfPresent(KacApplyKnowledgeDO::getWorkspaceId, reqVO.getWorkspaceId())
                .eqIfPresent(KacApplyKnowledgeDO::getApplyId, reqVO.getApplyId())
                .eqIfPresent(KacApplyKnowledgeDO::getKnowledgeId, reqVO.getKnowledgeId())
                .orderBy(reqVO.getOrderByColumn(), reqVO.getIsAsc(), allowedColumns));
    }
}
