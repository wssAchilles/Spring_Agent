package tech.qiantong.qknow.module.kg.api.knowledge;

import tech.qiantong.qknow.module.kg.api.knowledge.dto.KgKnowledgeDocumentRespDTO;

import java.util.List;

public interface IKgKnowledgeApiService {

    /**
     * 获得知识文件列表
     * @author jinwang
     * @date 2025/06/10 17:14
     * @param ids
     */
    public List<KgKnowledgeDocumentRespDTO> getKgDocumentListByIds(List<Long> ids);

    /**
     * 获得知识文件列表
     * @author jinwang
     * @date 2025/06/10 17:14
     * @param id
     */
    public KgKnowledgeDocumentRespDTO getKgDocumentById(Long id);
}
