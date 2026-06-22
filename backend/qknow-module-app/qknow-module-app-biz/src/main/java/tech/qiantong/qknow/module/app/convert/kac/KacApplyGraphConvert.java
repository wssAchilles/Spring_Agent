package tech.qiantong.qknow.module.app.convert.kac;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyGraphSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyGraphDO;

/**
 * 应用图谱关联 Convert
 *
 * @author qknow
 * @date 2026-06-22
 */
@Mapper
public interface KacApplyGraphConvert {
    KacApplyGraphConvert INSTANCE = Mappers.getMapper(KacApplyGraphConvert.class);

    KacApplyGraphDO convertToDO(KacApplyGraphPageReqVO kacApplyGraphPageReqVO);

    KacApplyGraphDO convertToDO(KacApplyGraphSaveReqVO kacApplyGraphSaveReqVO);

    KacApplyGraphRespVO convertToRespVO(KacApplyGraphDO kacApplyGraphDO);

    List<KacApplyGraphRespVO> convertToRespVOList(List<KacApplyGraphDO> kacApplyGraphDOList);
}
