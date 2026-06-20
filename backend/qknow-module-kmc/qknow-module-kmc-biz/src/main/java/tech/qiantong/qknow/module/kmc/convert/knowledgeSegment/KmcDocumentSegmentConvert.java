package tech.qiantong.qknow.module.kmc.convert.knowledgeSegment;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo.KmcDocumentSegmentPageReqVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo.KmcDocumentSegmentRespVO;
import tech.qiantong.qknow.module.kmc.controller.admin.knowledgeSegment.vo.KmcDocumentSegmentSaveReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeSegment.KmcDocumentSegmentDO;

import java.util.List;

/**
 * 文件分段 Convert
 *
 * @author qknow
 * @date 2025-08-28
 */
@Mapper
public interface KmcDocumentSegmentConvert {
    KmcDocumentSegmentConvert INSTANCE = Mappers.getMapper(KmcDocumentSegmentConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kmcDocumentSegmentPageReqVO 请求参数
     * @return KmcDocumentSegmentDO
     */
     KmcDocumentSegmentDO convertToDO(KmcDocumentSegmentPageReqVO kmcDocumentSegmentPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kmcDocumentSegmentSaveReqVO 保存请求参数
     * @return KmcDocumentSegmentDO
     */
     KmcDocumentSegmentDO convertToDO(KmcDocumentSegmentSaveReqVO kmcDocumentSegmentSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kmcDocumentSegmentDO 实体对象
     * @return KmcDocumentSegmentRespVO
     */
     KmcDocumentSegmentRespVO convertToRespVO(KmcDocumentSegmentDO kmcDocumentSegmentDO);

    /**
     * DOList 转换为 RespVOList
     * @param kmcDocumentSegmentDOList 实体对象列表
     * @return List<KmcDocumentSegmentRespVO>
     */
     List<KmcDocumentSegmentRespVO> convertToRespVOList(List<KmcDocumentSegmentDO> kmcDocumentSegmentDOList);
}
