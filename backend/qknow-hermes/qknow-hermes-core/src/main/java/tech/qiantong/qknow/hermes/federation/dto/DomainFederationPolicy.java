package tech.qiantong.qknow.hermes.federation.dto;

import java.util.List;

/**
 * 跨域联邦数据主权策略配置 Record
 */
public record DomainFederationPolicy(
        String domainId,
        String domainName,
        double privacyEpsilon,
        double privacyDelta,
        boolean allowCrossDomainAggregation,
        List<String> restrictedSensitiveKeywords
) {}
