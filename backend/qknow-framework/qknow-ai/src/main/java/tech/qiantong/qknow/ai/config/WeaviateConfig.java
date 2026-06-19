package tech.qiantong.qknow.ai.config;

import org.springframework.context.annotation.Configuration;

/**
 * 向量数据库配置 (已从 Weaviate 迁移到 PgVector)
 * PgVector 使用主数据源，无需额外配置
 *
 * @author fabian
 */
@Configuration
public class VectorStoreConfig {
}
