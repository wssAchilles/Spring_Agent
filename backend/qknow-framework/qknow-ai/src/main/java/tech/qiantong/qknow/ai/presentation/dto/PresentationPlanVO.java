package tech.qiantong.qknow.ai.presentation.dto;

import tech.qiantong.qknow.ai.presentation.enums.CognitiveLoadLevel;
import java.io.Serializable;
import java.util.List;

/**
 * 自适应呈现综合规划视图 VO
 */
public class PresentationPlanVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    /** 连续认知负荷得分 (0.0 ~ 1.0) */
    private double cognitiveScore;
    /** 认知负荷等级 */
    private CognitiveLoadLevel loadLevel;
    /** 活跃首屏非折叠展示块列表 (严格 <= loadLevel.getMaxActiveChunks()) */
    private List<MultimodalPresentationBlockDTO> activeBlocks;
    /** 自动折叠的次要展示块列表 */
    private List<MultimodalPresentationBlockDTO> collapsedBlocks;
    /** 规划耗时毫秒数 */
    private long planningDurationMs;

    public PresentationPlanVO() {}

    public PresentationPlanVO(String taskId, double cognitiveScore, CognitiveLoadLevel loadLevel,
                              List<MultimodalPresentationBlockDTO> activeBlocks,
                              List<MultimodalPresentationBlockDTO> collapsedBlocks,
                              long planningDurationMs) {
        this.taskId = taskId;
        this.cognitiveScore = cognitiveScore;
        this.loadLevel = loadLevel;
        this.activeBlocks = activeBlocks;
        this.collapsedBlocks = collapsedBlocks;
        this.planningDurationMs = planningDurationMs;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public double getCognitiveScore() {
        return cognitiveScore;
    }

    public void setCognitiveScore(double cognitiveScore) {
        this.cognitiveScore = cognitiveScore;
    }

    public CognitiveLoadLevel getLoadLevel() {
        return loadLevel;
    }

    public void setLoadLevel(CognitiveLoadLevel loadLevel) {
        this.loadLevel = loadLevel;
    }

    public List<MultimodalPresentationBlockDTO> getActiveBlocks() {
        return activeBlocks;
    }

    public void setActiveBlocks(List<MultimodalPresentationBlockDTO> activeBlocks) {
        this.activeBlocks = activeBlocks;
    }

    public List<MultimodalPresentationBlockDTO> getCollapsedBlocks() {
        return collapsedBlocks;
    }

    public void setCollapsedBlocks(List<MultimodalPresentationBlockDTO> collapsedBlocks) {
        this.collapsedBlocks = collapsedBlocks;
    }

    public long getPlanningDurationMs() {
        return planningDurationMs;
    }

    public void setPlanningDurationMs(long planningDurationMs) {
        this.planningDurationMs = planningDurationMs;
    }
}
