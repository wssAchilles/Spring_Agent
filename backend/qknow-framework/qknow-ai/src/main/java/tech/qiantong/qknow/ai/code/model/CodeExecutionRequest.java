package tech.qiantong.qknow.ai.code.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 代码执行请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionRequest {

    /**
     * 语言类型 (python, sql, javascript, bash)
     */
    private String language;

    /**
     * 待执行代码源码
     */
    private String code;

    /**
     * 硬超时限制 (毫秒, 默认 5000ms)
     */
    @Builder.Default
    private long timeoutMs = 5000L;

    /**
     * 最大自愈重试轮次 (默认 3 轮, 定理 2.2 黄金分割点)
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * 多租户隔离租户 ID
     */
    private String tenantId;

    /**
     * 业务上下文参数或输入数据
     */
    private Map<String, Object> context;
}
