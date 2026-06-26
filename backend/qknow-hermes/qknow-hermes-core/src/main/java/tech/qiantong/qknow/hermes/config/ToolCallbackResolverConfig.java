package tech.qiantong.qknow.hermes.config;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

@Configuration
public class ToolCallbackResolverConfig {

    @Bean
    public ToolCallbackResolver toolCallbackResolver(GenericApplicationContext applicationContext) {
        return name -> {
            if (applicationContext.containsBean(name)) {
                Object bean = applicationContext.getBean(name);
                if (bean instanceof ToolCallback toolCallback) {
                    return toolCallback;
                }
            }
            return null;
        };
    }
}
