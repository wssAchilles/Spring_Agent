package tech.qiantong.qknow.hermes.memory.persona;

import java.util.List;
import java.util.Map;

/**
 * 睡眠期画像提炼结果对象（纯 Java 21 Record）。
 * 封装用户偏好、领域实体与反思纠偏三维结构化认知数据。
 */
public record ProfileExtractionResult(
        String sessionId,
        String userId,
        Map<String, Object> userPreferences,       // 维度一：用户偏好（输出详略度、代码规范、语言偏好等）
        List<Map<String, Object>> domainEntities,   // 维度二：领域实体（专有名词、业务实体、术语等）
        List<String> reflectionsAndCorrections,    // 维度三：反思与纠偏（显式纠偏规则、负向约束等）
        double confidenceScore,                    // 综合置信度得分 (0.0 ~ 1.0)
        long extractedAt                           // 提炼时间戳 (毫秒)
) {
    public ProfileExtractionResult {
        userPreferences = userPreferences != null ? Map.copyOf(userPreferences) : Map.of();
        domainEntities = domainEntities != null ? List.copyOf(domainEntities) : List.of();
        reflectionsAndCorrections = reflectionsAndCorrections != null ? List.copyOf(reflectionsAndCorrections) : List.of();
    }
}
