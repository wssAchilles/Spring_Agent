package tech.qiantong.qknow.hermes.config;

import org.springframework.ai.tool.resolution.SpringBeanToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

@Configuration
public class ToolCallbackResolverConfig {

    @Bean
    public ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext) {
        return SpringBeanToolCallbackResolver.builder()
                .applicationContext(applicationContext)
                .build();
    }
}
