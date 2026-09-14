package tech.qiantong.qknow.ai.gateway.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 28: 大模型提供商通道实体元数据
 */
@Data
@Builder
public class ProviderChannel {
    private String channelId;
    private String channelName;
    private ProviderType providerType;
    private String baseUrl;
    private List<ApiKeyEntry> keyPool;

    /**
     * 优先级: 0 为最高主通道, 1, 2, 3 为递进瀑布备用通道
     */
    private int priority;

    /**
     * 同优先级下的加权权重
     */
    private int weight;

    /**
     * 单位百万 Token 的官方基准综合成本 (元/M Tokens)
     */
    private double costPerMillionTokens;

    /**
     * 模型别名映射: 例如将统一请求名 "deepseek-chat" 映射为火山引擎的 "ep-20250210-xxxx"
     */
    private Map<String, String> modelMapping;

    /**
     * 通道运行状态 (原子状态机)
     */
    @Builder.Default
    private AtomicReference<ChannelStatus> status = new AtomicReference<>(ChannelStatus.HEALTHY);

    /**
     * 获取映射后的上游实际模型名称
     */
    public String resolveUpstreamModel(String standardModelName) {
        if (modelMapping != null && modelMapping.containsKey(standardModelName)) {
            return modelMapping.get(standardModelName);
        }
        return standardModelName;
    }

    public ChannelStatus getStatus() {
        return status.get();
    }

    public void setStatus(ChannelStatus newStatus) {
        this.status.set(newStatus);
    }
}
