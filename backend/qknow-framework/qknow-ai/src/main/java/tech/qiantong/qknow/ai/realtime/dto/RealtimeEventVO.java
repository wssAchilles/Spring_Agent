package tech.qiantong.qknow.ai.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 全双工 WebSocket 控制与数据交互事件视图对象
 *
 * @author qknow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeEventVO {
    private String eventId;
    private EventType eventType;
    private String sessionId;
    private long epoch;
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    public enum EventType {
        SESSION_CREATED,       // 会话建立成功
        SPEECH_STARTED,        // VAD 判定说话开始
        SPEECH_STOPPED,        // VAD 判定说话结束
        RESPONSE_INTERRUPTED,  // 触发打断通知 (Barge-in)
        TRANSCRIPT_DELTA,      // ASR 实时转录文本增量
        AUDIO_DELTA,           // TTS 音频分块增量推送
        VISION_FRAME,          // 视频帧上报
        ERROR                  // 异常错误
    }
}
