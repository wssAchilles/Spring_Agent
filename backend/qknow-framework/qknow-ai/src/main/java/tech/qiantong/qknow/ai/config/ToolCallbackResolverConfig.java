package tech.qiantong.qknow.ai.config;

import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;

/**
 * @author wang
 */
@Configuration
public class ToolCallbackResolverConfig {

    @Resource
    private GenericApplicationContext applicationContext;

    @Bean
    public ToolCallbackResolver toolCallbackResolver() {
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
