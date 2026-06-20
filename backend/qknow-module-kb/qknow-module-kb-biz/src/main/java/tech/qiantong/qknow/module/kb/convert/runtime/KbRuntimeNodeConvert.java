package tech.qiantong.qknow.module.kb.convert.runtime;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeNodeRespVO;
import tech.qiantong.qknow.module.kb.controller.admin.runtime.vo.KbRuntimeNodeSaveReqVO;
import tech.qiantong.qknow.module.kb.dal.dataobject.runtime.KbRuntimeNodeDO;

import java.util.List;

/**
 * bot节点运行 Convert
 *
 * @author qknow
 * @date 2026-03-18
 */
@Mapper
public interface KbRuntimeNodeConvert {
    KbRuntimeNodeConvert INSTANCE = Mappers.getMapper(KbRuntimeNodeConvert.class);

    /**
     * SaveReqVO 转换为 DO
     *
     * @param kbRuntimeNodeSaveReqVO 保存请求参数
     * @return KbRuntimeNodeDO
     */
    KbRuntimeNodeDO convertToDO(KbRuntimeNodeSaveReqVO kbRuntimeNodeSaveReqVO);

    /**
     * DO 转换为 RespVO
     *
     * @param kbRuntimeNodeDO 实体对象
     * @return KbRuntimeNodeRespVO
     */
    KbRuntimeNodeRespVO convertToRespVO(KbRuntimeNodeDO kbRuntimeNodeDO);

    /**
     * DOList 转换为 RespVOList
     *
     * @param kbRuntimeNodeDOList 实体对象列表
     * @return List<KbRuntimeNodeRespVO>
     */
    List<KbRuntimeNodeRespVO> convertToRespVOList(List<KbRuntimeNodeDO> kbRuntimeNodeDOList);
}
