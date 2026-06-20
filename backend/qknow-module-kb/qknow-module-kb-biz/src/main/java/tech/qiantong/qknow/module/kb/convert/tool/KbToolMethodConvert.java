package tech.qiantong.qknow.module.kb.convert.tool;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolMethodPageReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolMethodRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.tool.vo.KbToolMethodSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.tool.KbToolMethodDO;

/**
 * 工具方法 Convert
 *
 * @author qknow
 * @date 2026-03-19
 */
@Mapper
public interface KbToolMethodConvert {
    KbToolMethodConvert INSTANCE = Mappers.getMapper(KbToolMethodConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kbToolMethodPageReqVO 请求参数
     * @return KbToolMethodDO
     */
     KbToolMethodDO convertToDO(KbToolMethodPageReqVO kbToolMethodPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kbToolMethodSaveReqVO 保存请求参数
     * @return KbToolMethodDO
     */
     KbToolMethodDO convertToDO(KbToolMethodSaveReqVO kbToolMethodSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kbToolMethodDO 实体对象
     * @return KbToolMethodRespVO
     */
     KbToolMethodRespVO convertToRespVO(KbToolMethodDO kbToolMethodDO);

    /**
     * DOList 转换为 RespVOList
     * @param kbToolMethodDOList 实体对象列表
     * @return List<KbToolMethodRespVO>
     */
     List<KbToolMethodRespVO> convertToRespVOList(List<KbToolMethodDO> kbToolMethodDOList);
}
