package tech.qiantong.qknow.ai.presentation.dto;

import java.io.Serializable;

/**
 * 流式传输自适应增量切片 DTO
 */
public class AdaptiveStreamChunkDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String chunkId;
    private String blockId;
    private String deltaContent;
    private boolean isBlockComplete;
    private long timestamp;

    public AdaptiveStreamChunkDTO() {
        this.timestamp = System.currentTimeMillis();
    }

    public AdaptiveStreamChunkDTO(String chunkId, String blockId, String deltaContent, boolean isBlockComplete) {
        this.chunkId = chunkId;
        this.blockId = blockId;
        this.deltaContent = deltaContent;
        this.isBlockComplete = isBlockComplete;
        this.timestamp = System.currentTimeMillis();
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public String getDeltaContent() {
        return deltaContent;
    }

    public void setDeltaContent(String deltaContent) {
        this.deltaContent = deltaContent;
    }

    public boolean isBlockComplete() {
        return isBlockComplete;
    }

    public void setBlockComplete(boolean blockComplete) {
        isBlockComplete = blockComplete;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
