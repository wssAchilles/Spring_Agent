package tech.qiantong.qknow.module.kmc.api.service;


import tech.qiantong.qknow.module.kmc.api.kmcDocument.dto.KmcDocumentRespDTO;
import tech.qiantong.qknow.module.kmc.api.kmcDocument.dto.TreeSelectsDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.KmcKnowledgeBaseRespDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.SemanticCacheHitDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.SemanticCacheLookupReqDTO;
import tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto.SemanticCacheSaveReqDTO;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.util.List;
import java.util.Optional;

public interface IKmcApiService {
    /**
     * 获得全部知识文件列表
     * @author shaun
     * @date 2025/06/10 17:14
     * @return java.util.List<tech.qiantong.qknow.module.kmc.dal.dataobject.document.KmcDocumentDO>
     */
    public List<KmcDocumentRespDTO> getKmcDocumentList();

    public List<KmcKnowledgeBaseRespDTO> getKmcKnowledgeBaseList(Long userId, Boolean isValid);

    /**
     * 获得知识文件列表
     * @author shaun
     * @date 2025/06/10 17:14
     * @param ids
     * @return java.util.List<tech.qiantong.qknow.module.kmc.dal.dataobject.document.KmcDocumentDO>
     */
    public List<KmcDocumentRespDTO> getKmcDocumentListByIds(List<Long> ids);

    /**
     * 获取单个知识文件
     * @param id
     * @return
     */
    public KmcDocumentRespDTO getKmcDocumentById(Long id);

    /**
     * 获取文件分类树列表
     * @author shaun
     * @date 2025/06/10 17:14
     * @param knowledgeId
     * @return java.util.List<tech.qiantong.qknow.module.kmc.domain.TreeSelects>
     */
    List<TreeSelectsDTO> getCategoryTreeByKnowledgeList(Long knowledgeId);

    /**
     * 测试召回
     * @author jinwang
     * @date 2025/06/10 17:23
     * @param knowledgeId
     * @param query
     * @return java.util.List<tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult>
     */
    List<RetrieveResult> recallTest(Long knowledgeId, String query);

    Optional<SemanticCacheHitDTO> findSemanticAnswer(SemanticCacheLookupReqDTO req);

    void saveSemanticAnswer(SemanticCacheSaveReqDTO req);


    /**
     * 获取知识库列表
     * @author wang
     * @date 2025年8月22日
     */
    public List<KmcKnowledgeBaseRespDTO> getKnowledgeBaseList();

    public List<KmcKnowledgeBaseRespDTO> getKnowledgeBaseByIds(List<Long> ids);

}
