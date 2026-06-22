package tech.qiantong.qknow.module.app.convert.kac;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionDO;

/**
 * 解决方案管理 Convert
 *
 * @author qknow
 * @date 2026-06-22
 */
@Mapper
public interface KacSolutionConvert {
    KacSolutionConvert INSTANCE = Mappers.getMapper(KacSolutionConvert.class);

    KacSolutionDO convertToDO(KacSolutionPageReqVO kacSolutionPageReqVO);

    KacSolutionDO convertToDO(KacSolutionSaveReqVO kacSolutionSaveReqVO);

    KacSolutionRespVO convertToRespVO(KacSolutionDO kacSolutionDO);

    List<KacSolutionRespVO> convertToRespVOList(List<KacSolutionDO> kacSolutionDOList);
}
