package tech.qiantong.qknow.module.kb.convert.bot;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kb.controller.admin.bot.vo.KbBotPageReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.bot.vo.KbBotRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.bot.vo.KbBotSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.bot.KbBotDO;

/**
 * bot 管理 Convert
 *
 * @author qknow
 * @date 2026-03-18
 */
@Mapper
public interface KbBotConvert {
    KbBotConvert INSTANCE = Mappers.getMapper(KbBotConvert.class);

    /**
     * PageReqVO 转换为 DO
     *
     * @param kbBotPageReqVO 请求参数
     * @return KbBotDO
     */
    KbBotDO convertToDO(KbBotPageReqVO kbBotPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     *
     * @param kbBotSaveReqVO 保存请求参数
     * @return KbBotDO
     */
    KbBotDO convertToDO(KbBotSaveReqVO kbBotSaveReqVO);

    /**
     * DO 转换为 RespVO
     *
     * @param kbBotDO 实体对象
     * @return KbBotRespVO
     */
    KbBotRespVO convertToRespVO(KbBotDO kbBotDO);

    /**
     * DOList 转换为 RespVOList
     *
     * @param kbBotDOList 实体对象列表
     * @return List<KbBotRespVO>
     */
    List<KbBotRespVO> convertToRespVOList(List<KbBotDO> kbBotDOList);
}
