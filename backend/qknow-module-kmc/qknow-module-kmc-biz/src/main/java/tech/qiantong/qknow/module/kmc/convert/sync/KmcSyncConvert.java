package tech.qiantong.qknow.module.kmc.convert.sync;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kmc.controller.admin.sync.vo.KmcSyncPageReqVO;
import tech.qiantong.qknow.module.kmc.controller.admin.sync.vo.KmcSyncRespVO;
import tech.qiantong.qknow.module.kmc.controller.admin.sync.vo.KmcSyncSaveReqVO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.sync.KmcSyncDO;

import java.util.List;

/**
 * 文件同步 Convert
 *
 * @author qknow
 * @date 2025-03-18
 */
@Mapper
public interface KmcSyncConvert {
    KmcSyncConvert INSTANCE = Mappers.getMapper(KmcSyncConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kmcSyncPageReqVO 请求参数
     * @return KmcSyncDO
     */
     KmcSyncDO convertToDO(KmcSyncPageReqVO kmcSyncPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kmcSyncSaveReqVO 保存请求参数
     * @return KmcSyncDO
     */
     KmcSyncDO convertToDO(KmcSyncSaveReqVO kmcSyncSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kmcSyncDO 实体对象
     * @return KmcSyncRespVO
     */
     KmcSyncRespVO convertToRespVO(KmcSyncDO kmcSyncDO);

    /**
     * DOList 转换为 RespVOList
     * @param kmcSyncDOList 实体对象列表
     * @return List<KmcSyncRespVO>
     */
     List<KmcSyncRespVO> convertToRespVOList(List<KmcSyncDO> kmcSyncDOList);
}
