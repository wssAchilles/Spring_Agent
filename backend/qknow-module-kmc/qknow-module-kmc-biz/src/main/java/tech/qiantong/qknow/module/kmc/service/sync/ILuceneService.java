package tech.qiantong.qknow.module.kmc.service.sync;

import tech.qiantong.qknow.module.kmc.dal.dataobject.document.KmcDocumentDO;
import tech.qiantong.qknow.module.kmc.dal.dataobject.knowledgeSegment.KmcDocumentSegmentDO;

import java.util.List;
import java.util.Map;

/**
 * 全文检索服务
 *
 * @author fabian
 */
public interface ILuceneService {

    /**
     * 保存 文档片段
     *
     * @param documentDO 文档对象
     * @param segmentDO  分段对象
     */
    void saveSegment(KmcDocumentSegmentDO segmentDO, KmcDocumentDO documentDO);

    /**
     * 批量报错文档片段
     *
     * @param documentDO  文档对象
     * @param segmentList 分段列表
     */
    void saveAiDocumentBatch(Map<String, Long> map, KmcDocumentDO documentDO, List<org.springframework.ai.document.Document> segmentList);

    /**
     * 更新文档片段
     *
     * @param documentDO 文档对象
     * @param segmentDO  分段对象
     */
    void updateSegment(KmcDocumentSegmentDO segmentDO, KmcDocumentDO documentDO);

    /**
     * 批量删除文档片段
     *
     * @param segmentDOList 文档片段列表
     */
    void deleteSegmentList(List<KmcDocumentSegmentDO> segmentDOList);

    /**
     * 根据文档id 删除索引
     *
     * @param documentId 文档id
     */
    void deleteByDocumentId(String documentId);
}
