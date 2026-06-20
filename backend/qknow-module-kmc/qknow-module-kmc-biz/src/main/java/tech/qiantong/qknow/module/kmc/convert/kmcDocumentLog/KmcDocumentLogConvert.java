package tech.qiantong.qknow.module.kmc.convert.kmcDocumentLog;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kmc.controller.admin.kmcDocumentLog.vo.KmcDocumentLogPageReqVO;
import tech.qiantong.qknow.module.kmc.controller.admin.kmcDocumentLog.vo.KmcDocumentLogRespVO;
import tech.qiantong.qknow.module.kmc.controller.admin.kmcDocumentLog.vo.KmcDocumentLogSaveReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.kmcDocumentLog.KmcDocumentLogDO;

import java.util.List;

/**
 * 文件操作日志 Convert
 *
 * @author qknow
 * @date 2025-03-24
 */
@Mapper
public interface KmcDocumentLogConvert {
    KmcDocumentLogConvert INSTANCE = Mappers.getMapper(KmcDocumentLogConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kmcDocumentLogPageReqVO 请求参数
     * @return KmcDocumentLogDO
     */
    KmcDocumentLogDO convertToDO(KmcDocumentLogPageReqVO kmcDocumentLogPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kmcDocumentLogSaveReqVO 保存请求参数
     * @return KmcDocumentLogDO
     */
    KmcDocumentLogDO convertToDO(KmcDocumentLogSaveReqVO kmcDocumentLogSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kmcDocumentLogDO 实体对象
     * @return KmcDocumentLogRespVO
     */
    KmcDocumentLogRespVO convertToRespVO(KmcDocumentLogDO kmcDocumentLogDO);

    /**
     * DOList 转换为 RespVOList
     * @param kmcDocumentLogDOList 实体对象列表
     * @return List<KmcDocumentLogRespVO>
     */
    List<KmcDocumentLogRespVO> convertToRespVOList(List<KmcDocumentLogDO> kmcDocumentLogDOList);
}
