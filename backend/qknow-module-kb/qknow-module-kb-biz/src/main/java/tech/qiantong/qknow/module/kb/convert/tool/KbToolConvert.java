package tech.qiantong.qknow.module.kb.convert.tool;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolPageReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.tool.KbToolDO;

/**
 * 工具管理 Convert
 *
 * @author qknow
 * @date 2026-03-19
 */
@Mapper
public interface KbToolConvert {
    KbToolConvert INSTANCE = Mappers.getMapper(KbToolConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kbToolPageReqVO 请求参数
     * @return KbToolDO
     */
     KbToolDO convertToDO(KbToolPageReqVO kbToolPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kbToolSaveReqVO 保存请求参数
     * @return KbToolDO
     */
     KbToolDO convertToDO(KbToolSaveReqVO kbToolSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kbToolDO 实体对象
     * @return KbToolRespVO
     */
     KbToolRespVO convertToRespVO(KbToolDO kbToolDO);

    /**
     * DOList 转换为 RespVOList
     * @param kbToolDOList 实体对象列表
     * @return List<KbToolRespVO>
     */
     List<KbToolRespVO> convertToRespVOList(List<KbToolDO> kbToolDOList);
}
