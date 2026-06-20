package tech.qiantong.qknow.module.kb.convert.runtime;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimePageReqVO;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeDO;

import java.util.List;

/**
 * bot运行 Convert
 *
 * @author qknow
 * @date 2026-03-18
 */
@Mapper
public interface KbRuntimeConvert {
    KbRuntimeConvert INSTANCE = Mappers.getMapper(KbRuntimeConvert.class);

    /**
     * PageReqVO 转换为 DO
     *
     * @param kbRuntimePageReqVO 请求参数
     * @return KbRuntimeDO
     */
    KbRuntimeDO convertToDO(KbRuntimePageReqVO kbRuntimePageReqVO);

    /**
     * SaveReqVO 转换为 DO
     *
     * @param kbRuntimeSaveReqVO 保存请求参数
     * @return KbRuntimeDO
     */
    KbRuntimeDO convertToDO(KbRuntimeSaveReqVO kbRuntimeSaveReqVO);

    /**
     * DO 转换为 RespVO
     *
     * @param kbRuntimeDO 实体对象
     * @return KbRuntimeRespVO
     */
    KbRuntimeRespVO convertToRespVO(KbRuntimeDO kbRuntimeDO);

    /**
     * DOList 转换为 RespVOList
     *
     * @param kbRuntimeDOList 实体对象列表
     * @return List<KbRuntimeRespVO>
     */
    List<KbRuntimeRespVO> convertToRespVOList(List<KbRuntimeDO> kbRuntimeDOList);
}
