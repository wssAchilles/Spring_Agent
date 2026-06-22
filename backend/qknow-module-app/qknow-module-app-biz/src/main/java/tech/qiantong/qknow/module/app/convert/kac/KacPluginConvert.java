package tech.qiantong.qknow.module.app.convert.kac;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginPageReqVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginRespVO;
import tech.qiantong.qknow.module.app.controller.admin.kac.vo.KacPluginSaveReqVO;
import tech.qiantong.qknow.module.app.dal.dataobject.kac.KacPluginDO;

/**
 * 插件管理 Convert
 *
 * @author qknow
 * @date 2026-06-22
 */
@Mapper
public interface KacPluginConvert {
    KacPluginConvert INSTANCE = Mappers.getMapper(KacPluginConvert.class);

    KacPluginDO convertToDO(KacPluginPageReqVO kacPluginPageReqVO);

    KacPluginDO convertToDO(KacPluginSaveReqVO kacPluginSaveReqVO);

    KacPluginRespVO convertToRespVO(KacPluginDO kacPluginDO);

    List<KacPluginRespVO> convertToRespVOList(List<KacPluginDO> kacPluginDOList);
}
