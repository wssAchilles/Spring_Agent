package tech.qiantong.qknow.module.app.convert.kac;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgePageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgeRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyKnowledgeSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyKnowledgeDO;

/**
 * 应用知识库关联 Convert
 *
 * @author qknow
 * @date 2026-06-22
 */
@Mapper
public interface KacApplyKnowledgeConvert {
    KacApplyKnowledgeConvert INSTANCE = Mappers.getMapper(KacApplyKnowledgeConvert.class);

    KacApplyKnowledgeDO convertToDO(KacApplyKnowledgePageReqVO kacApplyKnowledgePageReqVO);

    KacApplyKnowledgeDO convertToDO(KacApplyKnowledgeSaveReqVO kacApplyKnowledgeSaveReqVO);

    KacApplyKnowledgeRespVO convertToRespVO(KacApplyKnowledgeDO kacApplyKnowledgeDO);

    List<KacApplyKnowledgeRespVO> convertToRespVOList(List<KacApplyKnowledgeDO> kacApplyKnowledgeDOList);
}
