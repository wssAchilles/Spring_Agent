package tech.qiantong.qknow.hermes;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hermes 认知内核微服务启动类
 * 独立于控制面（Spring Boot 单体），通过 gRPC 提供 Agent 推理能力
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"tech.qiantong.qknow.hermes", "tech.qiantong.qknow.redis"})
public class HermesApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesApplication.class, args);
        log.info("========== Hermes 认知内核启动成功 ==========");
    }

    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public io.micrometer.core.instrument.MeterRegistry meterRegistry() {
        return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    }

    @org.springframework.context.annotation.Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public org.springframework.ai.chat.model.ChatModel defaultChatModel(
            tech.qiantong.qknow.hermes.config.ChatModelFactory chatModelFactory,
            org.springframework.core.env.Environment env) {
        String apiKey = env.getProperty("HERMES_OPENAI_API_KEY", env.getProperty("DEEPSEEK_API_KEY", "sk-placeholder"));
        String baseUrl = env.getProperty("HERMES_OPENAI_BASE_URL", "https://api.deepseek.com");
        return chatModelFactory.getChatModel("deepseek", baseUrl, apiKey, "deepseek-chat");
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.CommandLineRunner runMemoryAgent(tech.qiantong.qknow.hermes.memory.SleepTimeMemoryAgent agent) {
        return args -> {
            log.info("========== 强制执行 SleepTimeMemoryAgent.consolidateIdleConversations() ==========");
            agent.consolidateIdleConversations();
            log.info("========== 强制执行结束 ==========");
        };
    }
}
