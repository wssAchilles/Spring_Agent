package tech.qiantong.qknow.module.kb.convert.agent;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbAgentConfigPageReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbAgentConfigRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.agent.vo.KbAgentConfigSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.agent.KbAgentConfigDO;

import java.util.List;

/**
 * agent配置 Convert
 *
 * @author qknow
 * @date 2026-03-19
 */
@Mapper
public interface KbAgentConfigConvert {
    KbAgentConfigConvert INSTANCE = Mappers.getMapper(KbAgentConfigConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kbAgentConfigPageReqVO 请求参数
     * @return KbAgentConfigDO
     */
     KbAgentConfigDO convertToDO(KbAgentConfigPageReqVO kbAgentConfigPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kbAgentConfigSaveReqVO 保存请求参数
     * @return KbAgentConfigDO
     */
     KbAgentConfigDO convertToDO(KbAgentConfigSaveReqVO kbAgentConfigSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kbAgentConfigDO 实体对象
     * @return KbAgentConfigRespVO
     */
     KbAgentConfigRespVO convertToRespVO(KbAgentConfigDO kbAgentConfigDO);

    /**
     * DOList 转换为 RespVOList
     * @param kbAgentConfigDOList 实体对象列表
     * @return List<KbAgentConfigRespVO>
     */
     List<KbAgentConfigRespVO> convertToRespVOList(List<KbAgentConfigDO> kbAgentConfigDOList);
}
