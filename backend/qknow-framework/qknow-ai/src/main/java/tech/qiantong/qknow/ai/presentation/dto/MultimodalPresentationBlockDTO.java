package tech.qiantong.qknow.ai.presentation.dto;

import tech.qiantong.qknow.ai.presentation.enums.PresentationBlockType;
import java.io.Serializable;

/**
 * 结构化多模态展示块 DTO (卡片定义)
 */
public class MultimodalPresentationBlockDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 展示块 ID */
    private String blockId;
    /** 展示块类型 */
    private PresentationBlockType blockType;
    /** 标题 */
    private String title;
    /** 内容或结构化 JSON 字符串 */
    private String payload;
    /** 是否默认折叠 (基于认知负荷阻尼裁决) */
    private boolean collapsed;
    /** 视觉呈现优先级 (数值越小优先级越高，如 1 为首屏最高优先) */
    private int displayOrder;
    /** 认知负荷权重 (消耗的组块数，通常为 1) */
    private int cognitiveChunkWeight;

    public MultimodalPresentationBlockDTO() {
        this.cognitiveChunkWeight = 1;
        this.collapsed = false;
    }

    public MultimodalPresentationBlockDTO(String blockId, PresentationBlockType blockType, String title, String payload, int displayOrder) {
        this.blockId = blockId;
        this.blockType = blockType;
        this.title = title;
        this.payload = payload;
        this.displayOrder = displayOrder;
        this.collapsed = false;
        this.cognitiveChunkWeight = 1;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public PresentationBlockType getBlockType() {
        return blockType;
    }

    public void setBlockType(PresentationBlockType blockType) {
        this.blockType = blockType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public int getCognitiveChunkWeight() {
        return cognitiveChunkWeight;
    }

    public void setCognitiveChunkWeight(int cognitiveChunkWeight) {
        this.cognitiveChunkWeight = cognitiveChunkWeight;
    }
}
