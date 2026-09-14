package tech.qiantong.qknow.ai.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时多模态交互会话数据对象
 *
 * @author qknow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeSessionDO {
    private String sessionId;
    private String userId;
    @Builder.Default
    private AtomicLong currentEpoch = new AtomicLong(1L);
    @Builder.Default
    private SessionState sessionState = SessionState.IDLE;
    @Builder.Default
    private long createdTimestamp = System.currentTimeMillis();
    @Builder.Default
    private long lastActiveTimestamp = System.currentTimeMillis();
    @Builder.Default
    private long audioFramesReceived = 0L;
    @Builder.Default
    private long audioFramesSent = 0L;
    @Builder.Default
    private long visionFramesReceived = 0L;
    @Builder.Default
    private long visionFramesSent = 0L;
    @Builder.Default
    private int bargeInCount = 0;

    public enum SessionState {
        IDLE,            // 空闲等待
        USER_SPEAKING,   // 用户正在说话
        BOT_THINKING,    // 智能体思考中
        BOT_SPEAKING,    // 智能体语音播报中
        CLOSED           // 会话已关闭
    }

    public long getEpochValue() {
        return currentEpoch.get();
    }

    /**
     * 原子递增会话代际号并切换为用户说话状态 (打断发生)
     */
    public long incrementEpoch() {
        long newEpoch = currentEpoch.incrementAndGet();
        this.sessionState = SessionState.USER_SPEAKING;
        this.bargeInCount++;
        this.lastActiveTimestamp = System.currentTimeMillis();
        return newEpoch;
    }

    /**
     * 校验音频/控制帧代际号是否有效 (只有等于当前代际号的帧才被允许消费)
     */
    public boolean isEpochValid(long frameEpoch) {
        return frameEpoch == currentEpoch.get();
    }
}
