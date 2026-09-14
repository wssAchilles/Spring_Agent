package tech.qiantong.qknow.ai.compressor.paging;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 虚拟认知页面 Record (Java 21 不可变实体)
 * 封装 512~1024 Token 块、千问 1536 维超球面嵌入与因果指针
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CognitivePage(
        String pageId,
        Long tenantId,
        String content,
        int tokenCount,
        float[] qwen1536Embedding,
        List<String> causalPointers, // 外部因果依赖切片或关联页面 ID
        long accessTimestamp,
        long creationTimestamp
) {
    public CognitivePage {
        if (tenantId == null) {
            tenantId = 1L;
        }
        if (causalPointers == null) {
            causalPointers = List.of();
        }
        if (creationTimestamp <= 0) {
            creationTimestamp = System.currentTimeMillis();
        }
        if (accessTimestamp <= 0) {
            accessTimestamp = creationTimestamp;
        }
    }

    public CognitivePage withUpdatedAccessTime(long now) {
        return new CognitivePage(
                pageId, tenantId, content, tokenCount, qwen1536Embedding, causalPointers, now, creationTimestamp
        );
    }
}
