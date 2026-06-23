package tech.qiantong.qknow.hermes.flow.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 知识检索服务
 * 负责从知识库中检索相关文档片段
 * 实际实现会调用 KMC 模块的向量检索
 */
@Slf4j
@Component
public class RagRetrievalService {

    /**
     * 从指定知识库中检索与查询相关的文档
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           查询文本
     * @return 检索到的文档片段列表
     */
    public List<String> retrieve(String knowledgeBaseId, String query) {
        // TODO: 实际实现会调用 KMC 模块的向量检索
        log.info("RAG 检索 - knowledgeBaseId: {}, query: {}", knowledgeBaseId, query);
        return List.of();
    }
}
