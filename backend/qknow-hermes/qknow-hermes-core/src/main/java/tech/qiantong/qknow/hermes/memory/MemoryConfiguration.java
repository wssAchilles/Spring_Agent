package tech.qiantong.qknow.hermes.memory;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
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
    public EmbeddingModel embeddingModel(@org.springframework.beans.factory.annotation.Value("${spring.ai.tongyi.base-url:https://dashscope.aliyuncs.com/compatible-mode}") String baseUrl,
                                         @org.springframework.beans.factory.annotation.Value("${TONGYI_API_KEY}") String apiKey) {
        return new org.springframework.ai.openai.OpenAiEmbeddingModel(
                org.springframework.ai.openai.api.OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build(),
                org.springframework.ai.document.MetadataMode.EMBED,
                org.springframework.ai.openai.OpenAiEmbeddingOptions.builder().model("text-embedding-v2").build());
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public LongTermMemory longTermMemory(ObjectProvider<VectorStore> vectorStore,
                                         ObjectProvider<EmbeddingModel> embeddingModel,
                                         ObjectProvider<ChatModel> chatModel,
                                         ObjectProvider<UserMemoryGraphService> graphService) {
        return new LongTermMemory(vectorStore.getIfAvailable(), embeddingModel.getIfAvailable(),
                chatModel.getIfAvailable(), graphService.getIfAvailable());
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

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.memory.dst.DialogueStateTracker dialogueStateTracker() {
        return new tech.qiantong.qknow.hermes.memory.dst.DialogueStateTrackerImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringService memoryScoringService() {
        return new tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngine reflectionTreeEngine(ObjectProvider<ChatModel> chatModel) {
        return new tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngineImpl(chatModel.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.memory.reflection.AsyncReflectionWorker asyncReflectionWorker(tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngine reflectionTreeEngine) {
        return new tech.qiantong.qknow.hermes.memory.reflection.AsyncReflectionWorker(reflectionTreeEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.memory.graph.EpisodicGraphService episodicGraphService() {
        return new tech.qiantong.qknow.hermes.memory.graph.EpisodicGraphServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public tech.qiantong.qknow.hermes.memory.persona.UserPreferenceEvolutionGovernor userPreferenceEvolutionGovernor() {
        return new tech.qiantong.qknow.hermes.memory.persona.UserPreferenceEvolutionGovernorImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReflectiveMemoryCoordinator reflectiveMemoryCoordinator(
            WorkingMemory workingMemory,
            tech.qiantong.qknow.hermes.memory.dst.DialogueStateTracker dialogueStateTracker,
            tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringService memoryScoringService,
            tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngine reflectionTreeEngine,
            tech.qiantong.qknow.hermes.memory.graph.EpisodicGraphService episodicGraphService,
            tech.qiantong.qknow.hermes.memory.persona.UserPreferenceEvolutionGovernor preferenceGovernor) {
        return new ReflectiveMemoryCoordinator(
                workingMemory,
                dialogueStateTracker,
                memoryScoringService,
                reflectionTreeEngine,
                episodicGraphService,
                preferenceGovernor
        );
    }
}
