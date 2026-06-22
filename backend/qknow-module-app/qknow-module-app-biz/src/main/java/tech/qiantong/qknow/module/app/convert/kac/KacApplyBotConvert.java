package tech.qiantong.qknow.module.app.convert.kac;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacApplyBotSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacApplyBotDO;

/**
 * 应用机器人关联 Convert
 *
 * @author qknow
 * @date 2026-06-22
 */
@Mapper
public interface KacApplyBotConvert {
    KacApplyBotConvert INSTANCE = Mappers.getMapper(KacApplyBotConvert.class);

    KacApplyBotDO convertToDO(KacApplyBotPageReqVO kacApplyBotPageReqVO);

    KacApplyBotDO convertToDO(KacApplyBotSaveReqVO kacApplyBotSaveReqVO);

    KacApplyBotRespVO convertToRespVO(KacApplyBotDO kacApplyBotDO);

    List<KacApplyBotRespVO> convertToRespVOList(List<KacApplyBotDO> kacApplyBotDOList);
}
