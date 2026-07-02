package tech.qiantong.qknow.hermes.memory;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.qiantong.qknow.redis.service.IRedisService;

@Configuration
public class MemoryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ShortTermMemory shortTermMemory(ObjectProvider<ChatModel> chatModel,
                                           ObjectProvider<IRedisService> redisService) {
        return new ShortTermMemory(chatModel.getIfAvailable(), redisService.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public LongTermMemory longTermMemory(ObjectProvider<VectorStore> vectorStore,
                                         ObjectProvider<EmbeddingModel> embeddingModel) {
        return new LongTermMemory(vectorStore.getIfAvailable(), embeddingModel.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public WorkingMemory workingMemory(ObjectProvider<IRedisService> redisService) {
        return new WorkingMemory(redisService.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryManager memoryManager(ShortTermMemory shortTermMemory,
                                       LongTermMemory longTermMemory,
                                       WorkingMemory workingMemory) {
        return new MemoryManager(shortTermMemory, longTermMemory, workingMemory);
    }
}
