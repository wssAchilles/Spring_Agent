package tech.qiantong.qknow.module.app.convert.kac;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplyPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplyRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacSolutionApplySaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacSolutionApplyDO;

/**
 * 解决方案应用关联 Convert
 *
 * @author qknow
 * @date 2026-06-22
 */
@Mapper
public interface KacSolutionApplyConvert {
    KacSolutionApplyConvert INSTANCE = Mappers.getMapper(KacSolutionApplyConvert.class);

    KacSolutionApplyDO convertToDO(KacSolutionApplyPageReqVO kacSolutionApplyPageReqVO);

    KacSolutionApplyDO convertToDO(KacSolutionApplySaveReqVO kacSolutionApplySaveReqVO);

    KacSolutionApplyRespVO convertToRespVO(KacSolutionApplyDO kacSolutionApplyDO);

    List<KacSolutionApplyRespVO> convertToRespVOList(List<KacSolutionApplyDO> kacSolutionApplyDOList);
}
