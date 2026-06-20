package tech.qiantong.qknow.module.kg.convert.knowledge;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo.KgKnowledgeDocumentLogPageReqVO;
import tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo.KgKnowledgeDocumentLogRespVO;
import tech.qiantong.qknow.module.kg.controller.admin.knowledge.vo.KgKnowledgeDocumentLogSaveReqVO;
import tech.qiantong.qknow.module.kg.dal.dataobject.knowledge.KgKnowledgeDocumentLogDO;

/**
 * 文件操作日志 Convert
 *
 * @author qknow
 * @date 2025-10-22
 */
@Mapper
public interface KgKnowledgeDocumentLogConvert {
    KgKnowledgeDocumentLogConvert INSTANCE = Mappers.getMapper(KgKnowledgeDocumentLogConvert.class);

    /**
     * PageReqVO 转换为 DO
     * @param kgKnowledgeDocumentLogPageReqVO 请求参数
     * @return KgKnowledgeDocumentLogDO
     */
     KgKnowledgeDocumentLogDO convertToDO(KgKnowledgeDocumentLogPageReqVO kgKnowledgeDocumentLogPageReqVO);

    /**
     * SaveReqVO 转换为 DO
     * @param kgKnowledgeDocumentLogSaveReqVO 保存请求参数
     * @return KgKnowledgeDocumentLogDO
     */
     KgKnowledgeDocumentLogDO convertToDO(KgKnowledgeDocumentLogSaveReqVO kgKnowledgeDocumentLogSaveReqVO);

    /**
     * DO 转换为 RespVO
     * @param kgKnowledgeDocumentLogDO 实体对象
     * @return KgKnowledgeDocumentLogRespVO
     */
     KgKnowledgeDocumentLogRespVO convertToRespVO(KgKnowledgeDocumentLogDO kgKnowledgeDocumentLogDO);

    /**
     * DOList 转换为 RespVOList
     * @param kgKnowledgeDocumentLogDOList 实体对象列表
     * @return List<KgKnowledgeDocumentLogRespVO>
     */
     List<KgKnowledgeDocumentLogRespVO> convertToRespVOList(List<KgKnowledgeDocumentLogDO> kgKnowledgeDocumentLogDOList);
}
