package tech.qiantong.qknow.ai.gateway.registry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.gateway.circuitbreaker.AdaptiveCircuitBreaker;
import tech.qiantong.qknow.ai.gateway.model.ApiKeyEntry;
import tech.qiantong.qknow.ai.gateway.model.ChannelStatus;
import tech.qiantong.qknow.ai.gateway.model.ProviderChannel;
import tech.qiantong.qknow.ai.gateway.model.ProviderType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 28: 大模型提供商多通道动态注册表与平滑加权 Key 调度器
 */
@Slf4j
@Component
public class ProviderChannelRegistry {

    private final Map<String, ProviderChannel> channelMap = new ConcurrentHashMap<>();
    private final Map<String, AdaptiveCircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();

    public ProviderChannelRegistry() {
        // 预注册默认的主通道与备用通道拓扑
        registerDefaultChannels();
    }

    private void registerDefaultChannels() {
        // 1. 主通道: 官方 DeepSeek (Priority 0)
        ProviderChannel official = ProviderChannel.builder()
                .channelId("ch_deepseek_official")
                .channelName("DeepSeek 官方直连")
                .providerType(ProviderType.OFFICIAL_DEEPSEEK)
                .baseUrl("https://api.deepseek.com")
                .priority(0)
                .weight(100)
                .costPerMillionTokens(12.0)
                .keyPool(new ArrayList<>(List.of(
                        ApiKeyEntry.builder().keyId("k_official_01").secretKey("sk-official-primary-key").weight(10).build(),
                        ApiKeyEntry.builder().keyId("k_official_02").secretKey("sk-official-secondary-key").weight(10).build()
                )))
                .modelMapping(Map.of())
                .build();

        // 2. 备用通道 1: 火山引擎 Ark (Priority 1)
        ProviderChannel volc = ProviderChannel.builder()
                .channelId("ch_volcengine_ark")
                .channelName("火山引擎 Ark DeepSeek 托管")
                .providerType(ProviderType.VOLC_ENGINE)
                .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                .priority(1)
                .weight(80)
                .costPerMillionTokens(14.0)
                .keyPool(new ArrayList<>(List.of(
                        ApiKeyEntry.builder().keyId("k_volc_01").secretKey("sk-volc-endpoint-key").weight(10).build()
                )))
                .modelMapping(Map.of(
                        "deepseek-chat", "ep-20250210-chat-v3",
                        "deepseek-reasoner", "ep-20250210-reasoner-r1"
                ))
                .build();

        // 3. 备用通道 2: 硅基流动 (Priority 2)
        ProviderChannel silicon = ProviderChannel.builder()
                .channelId("ch_silicon_flow")
                .channelName("硅基流动 DeepSeek 托管")
                .providerType(ProviderType.SILICON_FLOW)
                .baseUrl("https://api.siliconflow.cn/v1")
                .priority(2)
                .weight(60)
                .costPerMillionTokens(12.0)
                .keyPool(new ArrayList<>(List.of(
                        ApiKeyEntry.builder().keyId("k_silicon_01").secretKey("sk-silicon-standard-key").weight(10).build()
                )))
                .modelMapping(Map.of(
                        "deepseek-chat", "deepseek-ai/DeepSeek-V3",
                        "deepseek-reasoner", "deepseek-ai/DeepSeek-R1"
                ))
                .build();

        // 4. 备用通道 3: 阿里云百炼 (Priority 3)
        ProviderChannel ali = ProviderChannel.builder()
                .channelId("ch_ali_dashscope")
                .channelName("阿里云百炼 DeepSeek 托管")
                .providerType(ProviderType.ALI_DASHSCOPE)
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .priority(3)
                .weight(50)
                .costPerMillionTokens(13.5)
                .keyPool(new ArrayList<>(List.of(
                        ApiKeyEntry.builder().keyId("k_ali_01").secretKey("sk-ali-dashscope-key").weight(10).build()
                )))
                .modelMapping(Map.of(
                        "deepseek-chat", "deepseek-v3",
                        "deepseek-reasoner", "deepseek-r1"
                ))
                .build();

        registerChannel(official);
        registerChannel(volc);
        registerChannel(silicon);
        registerChannel(ali);
    }

    public void registerChannel(ProviderChannel channel) {
        channelMap.put(channel.getChannelId(), channel);
        circuitBreakerMap.computeIfAbsent(channel.getChannelId(), id ->
                new AdaptiveCircuitBreaker(id, 10, 0.50, 15000L, 5, 5)
        );
        log.info("已注册模型代理通道: [{}], 优先级: {}, 权重: {}",
                channel.getChannelName(), channel.getPriority(), channel.getWeight());
    }

    public ProviderChannel getChannel(String channelId) {
        return channelMap.get(channelId);
    }

    public List<ProviderChannel> getAllChannels() {
        return new ArrayList<>(channelMap.values());
    }

    public AdaptiveCircuitBreaker getCircuitBreaker(String channelId) {
        return circuitBreakerMap.get(channelId);
    }

    /**
     * 平滑加权轮询获取通道内可用的有效 API Key (Smooth Weighted Round-Robin)
     */
    public Optional<ApiKeyEntry> selectAvailableApiKey(ProviderChannel channel) {
        List<ApiKeyEntry> pool = channel.getKeyPool();
        if (pool == null || pool.isEmpty()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        List<ApiKeyEntry> availableKeys = pool.stream()
                .filter(k -> k.isAvailable(now))
                .toList();

        if (availableKeys.isEmpty()) {
            return Optional.empty();
        }
        if (availableKeys.size() == 1) {
            return Optional.of(availableKeys.getFirst());
        }

        // Nginx 平滑加权轮询算法
        int totalWeight = 0;
        ApiKeyEntry bestKey = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        for (ApiKeyEntry key : availableKeys) {
            totalWeight += key.getEffectiveWeight().get();
            int current = key.getCurrentWeight().addAndGet(key.getEffectiveWeight().get());
            if (current > maxCurrentWeight) {
                maxCurrentWeight = current;
                bestKey = key;
            }
        }

        if (bestKey != null) {
            bestKey.getCurrentWeight().addAndGet(-totalWeight);
            return Optional.of(bestKey);
        }

        return Optional.of(availableKeys.getFirst());
    }
}
