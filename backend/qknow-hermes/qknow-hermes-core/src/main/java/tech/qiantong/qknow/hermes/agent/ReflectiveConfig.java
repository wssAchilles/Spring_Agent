package tech.qiantong.qknow.hermes.agent;

import lombok.Data;

/**
 * 反思智能体配置
 */
@Data
public class ReflectiveConfig {

    private boolean enabled;

    private int maxRetries;
}
