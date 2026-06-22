package tech.qiantong.qknow.module.app.convert.kac;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplySaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyDO;

/**
 * 应用管理 Convert
 *
 * @author qknow
 * @date 2026-06-22
 */
@Mapper
public interface KacApplyConvert {
    KacApplyConvert INSTANCE = Mappers.getMapper(KacApplyConvert.class);

    KacApplyDO convertToDO(KacApplyPageReqVO kacApplyPageReqVO);

    KacApplyDO convertToDO(KacApplySaveReqVO kacApplySaveReqVO);

    KacApplyRespVO convertToRespVO(KacApplyDO kacApplyDO);

    List<KacApplyRespVO> convertToRespVOList(List<KacApplyDO> kacApplyDOList);
}
