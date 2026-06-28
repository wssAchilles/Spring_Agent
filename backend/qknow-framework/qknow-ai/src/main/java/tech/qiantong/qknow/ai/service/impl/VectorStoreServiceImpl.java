package tech.qiantong.qknow.ai.service.impl;

import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.service.IVectorStoreService;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PgVector 向量存储实现
 *
 * @author fabian
 */
@Service
public class VectorStoreServiceImpl implements IVectorStoreService {

    @Resource
    private DataSource dataSource;

    private final ConcurrentHashMap<String, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    /**
     * 获取 VectorStore 数据库连接（基于 PostgreSQL + PgVector）
     *
     * @param embeddingModel 文本向量模型
     * @return 数据库连接
     */
    @Override
    public VectorStore getVectorStore(EmbeddingModel embeddingModel) {
        String key = embeddingModel.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(embeddingModel));
        return vectorStoreCache.computeIfAbsent(key, k -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .build();
        });
    }
}
