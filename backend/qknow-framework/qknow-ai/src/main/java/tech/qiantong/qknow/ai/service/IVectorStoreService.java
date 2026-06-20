package tech.qiantong.qknow.ai.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * vectorStore 是向量数据库的连接对象
 *
 * @author fabian
 */
public interface IVectorStoreService {
    /**
     * 获取 VectorStore 数据库连接
     *
     * @param embeddingModel 文本向量模型
     * @return 数据库连接
     */
    VectorStore getVectorStore(EmbeddingModel embeddingModel);
}
