package tech.qiantong.qknow.ai.gateway.model;

import lombok.Getter;

/**
 * Phase 28: 大模型兼容提供商类型
 */
@Getter
public enum ProviderType {
    OFFICIAL_DEEPSEEK("DeepSeek 官方", "https://api.deepseek.com"),
    VOLC_ENGINE("火山引擎 Ark (DeepSeek 托管)", "https://ark.cn-beijing.volces.com/api/v3"),
    SILICON_FLOW("硅基流动 (DeepSeek 托管)", "https://api.siliconflow.cn/v1"),
    ALI_DASHSCOPE("阿里云百炼 (DeepSeek 托管)", "https://dashscope.aliyuncs.com/compatible-mode/v1");

    private final String displayName;
    private final String defaultBaseUrl;

    ProviderType(String displayName, String defaultBaseUrl) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
    }
}
