package tech.qiantong.qknow.hermes.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 中央模型逻辑别名与历史兼容映射器
 * 负责全系统模型请求的统一拦截、归一化规整与平滑演化
 *
 * @author Achilles
 * @since Phase 153
 */
@Slf4j
@Component
public class ModelNameResolver {

    private final AiModelAliasProperties properties;

    @Autowired
    public ModelNameResolver(@Autowired(required = false) AiModelAliasProperties properties) {
        this.properties = properties != null ? properties : new AiModelAliasProperties();
    }

    public ModelNameResolver() {
        this(new AiModelAliasProperties());
    }

    /**
     * 极速解析方法（纯字符内存映射，无锁快速返回）
     *
     * @param platform 平台名称
     * @param modelName 原始请求模型名称或别名
     * @return 规整后的官方合法活跃模型 ID
     */
    public String resolve(String platform, String modelName) {
        return resolveWithReceipt(platform, modelName).resolvedModel();
    }

    /**
     * 决策完备解析方法（附带不可变密码学自验真凭单）
     *
     * @param platform 平台名称
     * @param modelName 原始请求模型名称或别名
     * @return 携带不可变存证与自验真签名的凭单 Record
     */
    public ModelResolutionReceipt resolveWithReceipt(String platform, String modelName) {
        String cleanPlatform = (platform != null && !platform.isBlank())
                ? platform.trim() : properties.getDefaultPlatform();

        // 1. 处理入参为空或空白情况
        if (modelName == null || modelName.isBlank()) {
            String defaultTarget = properties.getAliases().getOrDefault("default", properties.getPrimaryModel());
            log.debug("模型入参为空，应用默认回退: {} -> {}", modelName, defaultTarget);
            return ModelResolutionReceipt.of(cleanPlatform, modelName, defaultTarget,
                    ModelResolutionReceipt.ResolutionType.DEFAULT_FALLBACK);
        }

        String normalizedName = modelName.trim().toLowerCase();

        // 2. 检查是否匹配逻辑别名 (primary, fast, default, reasoning, chat 等)
        if (properties.getAliases() != null && properties.getAliases().containsKey(normalizedName)) {
            String targetModel = properties.getAliases().get(normalizedName);
            log.debug("命中模型逻辑能力别名: {} -> {}", modelName, targetModel);
            return ModelResolutionReceipt.of(cleanPlatform, modelName, targetModel,
                    ModelResolutionReceipt.ResolutionType.LOGICAL_ALIAS);
        }

        // 3. 检查是否属于历史退役废弃模型（如 deepseek-chat, deepseek-reasoner, deepseek-v3, deepseek-r1 等）
        if (properties.getLegacyFallbacks() != null && properties.getLegacyFallbacks().containsKey(normalizedName)) {
            String upgradedModel = properties.getLegacyFallbacks().get(normalizedName);
            log.warn("检测到已退役模型调用 [{}], 自动透明平滑升级至官方活跃模型 [{}]", modelName, upgradedModel);
            return ModelResolutionReceipt.of(cleanPlatform, modelName, upgradedModel,
                    ModelResolutionReceipt.ResolutionType.LEGACY_UPGRADE);
        }

        // 4. 精确匹配直通（支持用户自定义模型或未来新模型显式传参）
        return ModelResolutionReceipt.of(cleanPlatform, modelName, modelName.trim(),
                ModelResolutionReceipt.ResolutionType.EXACT_MATCH);
    }
}
