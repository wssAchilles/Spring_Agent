package tech.qiantong.qknow.module.kmc.service.rag.advanced.dto;

/**
 * 流式打字机回放状态枚举类 (Streaming Playback State)
 * <p>
 * 定义流式打字机基于 JitterBuffer 队列的回放控制状态。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public enum StreamingPlaybackState {

    /**
     * 正常恒速回放中 (Playing)
     */
    PLAYING("恒速回放中", true),

    /**
     * 水位偏低，正在缓冲预热 (Buffering)
     */
    BUFFERING("低水位缓冲预热", true),

    /**
     * 上游断流，正在一阶减速优雅排空软封口 (Draining)
     */
    DRAINING("优雅减速软封口", true),

    /**
     * 全量内容回放完成 (Completed)
     */
    COMPLETED("播放已完成", false);

    private final String description;
    private final boolean active;

    StreamingPlaybackState(String description, boolean active) {
        this.description = description;
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
