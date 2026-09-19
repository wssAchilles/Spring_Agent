package tech.qiantong.qknow.ai.mor.cache;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 结构化决策树认知脚手架实体 (Java 21 Record)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CognitiveScaffold(
        String clusterId,            // 千问 1536 维超球面聚类簇 ID
        String knowledgeSlicesSha256,// 知识切片签名哈希
        String scaffoldContent,      // 200~400 字结构化决策树脚手架
        long createdAtTimestamp,     // 生成时间
        long ttlSeconds,             // 有效时长
        String tenantId,             // 租户隔离标识
        String securityScopeHash     // 安全权限范围哈希
) {
    public CognitiveScaffold(
            String clusterId,
            String knowledgeSlicesSha256,
            String scaffoldContent,
            long createdAtTimestamp,
            long ttlSeconds
    ) {
        this(clusterId, knowledgeSlicesSha256, scaffoldContent, createdAtTimestamp, ttlSeconds, "default", "public");
    }

    public CognitiveScaffold {
        if (scaffoldContent == null) {
            scaffoldContent = "";
        }
        if (ttlSeconds <= 0) {
            ttlSeconds = 86400L; // 默认 24 小时
        }
        if (tenantId == null) {
            tenantId = "default";
        }
        if (securityScopeHash == null) {
            securityScopeHash = "public";
        }
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > createdAtTimestamp + (ttlSeconds * 1000L);
    }
}
