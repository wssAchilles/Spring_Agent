package tech.qiantong.qknow.ai.mor.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 双轨流式事件分发信封 (不可变 Java 21 Record)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DualTrackStreamEnvelope(
        String traceId,                    // 全链路分布式追踪 ID
        StreamTrack track,                 // 数据轨别: THINKING / CONTENT
        String textDelta,                  // 增量文本片段
        long sequenceNumber,               // 帧序列号
        boolean isFinished,                // 是否已结束
        String finishReason                // 结束原因 (stop / length / error / null)
) {
    public enum StreamTrack {
        THINKING,  // 推理思考流 (推向前端折叠抽屉)
        CONTENT    // 业务正文流 (推向前端正式打字机)
    }
}
