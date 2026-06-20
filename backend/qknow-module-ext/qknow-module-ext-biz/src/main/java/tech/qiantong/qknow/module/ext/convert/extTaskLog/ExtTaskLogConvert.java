package tech.qiantong.qknow.module.ext.convert.extTaskLog;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.ext.controller.admin.extTaskLog.vo.ExtTaskLogPageReqVO;
import tech.qiantong.qknow.module.ext.controller.admin.extTaskLog.vo.ExtTaskLogRespVO;
import tech.qiantong.qknow.module.ext.controller.admin.extTaskLog.vo.ExtTaskLogSaveReqVO;
import tech.qiantong.qknow.module.ext.dal.dataobject.extTaskLog.ExtTaskLogDO;

import java.util.List;

/**
 * 抽取任务执行日志 Convert
 *
 * @author qknow
 * @date 2025-12-03
 */
@Mapper
public interface ExtTaskLogConvert {
    ExtTaskLogConvert INSTANCE = Mappers.getMapper(ExtTaskLogConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param extTaskLogPageReqVO 请求参数
     * @return ExtTaskLogDO
     */
     ExtTaskLogDO convertToDO(ExtTaskLogPageReqVO extTaskLogPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param extTaskLogSaveReqVO 保存请求参数
     * @return ExtTaskLogDO
     */
     ExtTaskLogDO convertToDO(ExtTaskLogSaveReqVO extTaskLogSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param extTaskLogDO 实体对象
     * @return ExtTaskLogRespVO
     */
     ExtTaskLogRespVO convertToRespVO(ExtTaskLogDO extTaskLogDO);

    /**
     * DOList 转换为 RespVOList
     * @param extTaskLogDOList 实体对象列表
     * @return List<ExtTaskLogRespVO>
     */
     List<ExtTaskLogRespVO> convertToRespVOList(List<ExtTaskLogDO> extTaskLogDOList);
}
