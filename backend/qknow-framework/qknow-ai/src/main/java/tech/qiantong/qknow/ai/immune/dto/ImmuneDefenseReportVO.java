package tech.qiantong.qknow.ai.immune.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 主动免疫演练与防御态势综合视图
 *
 * @author Achilles
 * @since Phase 45
 */
public class ImmuneDefenseReportVO {

    /**
     * 演练中生成的红队探针总数
     */
    private int totalProbesGenerated;

    /**
     * 蓝队免疫成功拦截阻断数
     */
    private int totalBlocked;

    /**
     * 记忆库活跃成熟抗体数
     */
    private int totalAntibodiesActive;

    /**
     * 防御成功率 [0.0, 1.0]
     */
    private double defenseSuccessRate;

    /**
     * 平均单次免疫决策耗时 (ms)
     */
    private double averageLatencyMs;

    /**
     * 本轮演化产生的新抗体编号列表
     */
    private List<String> evolvedAntibodyIds = new ArrayList<>();

    /**
     * 是否达到纳什均衡收敛态
     */
    private boolean nashEquilibriumReached;

    public ImmuneDefenseReportVO() {
    }

    public int getTotalProbesGenerated() {
        return totalProbesGenerated;
    }

    public void setTotalProbesGenerated(int totalProbesGenerated) {
        this.totalProbesGenerated = totalProbesGenerated;
    }

    public int getTotalBlocked() {
        return totalBlocked;
    }

    public void setTotalBlocked(int totalBlocked) {
        this.totalBlocked = totalBlocked;
    }

    public int getTotalAntibodiesActive() {
        return totalAntibodiesActive;
    }

    public void setTotalAntibodiesActive(int totalAntibodiesActive) {
        this.totalAntibodiesActive = totalAntibodiesActive;
    }

    public double getDefenseSuccessRate() {
        return defenseSuccessRate;
    }

    public void setDefenseSuccessRate(double defenseSuccessRate) {
        this.defenseSuccessRate = defenseSuccessRate;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(double averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }

    public List<String> getEvolvedAntibodyIds() {
        return evolvedAntibodyIds;
    }

    public void setEvolvedAntibodyIds(List<String> evolvedAntibodyIds) {
        this.evolvedAntibodyIds = evolvedAntibodyIds;
    }

    public boolean isNashEquilibriumReached() {
        return nashEquilibriumReached;
    }

    public void setNashEquilibriumReached(boolean nashEquilibriumReached) {
        this.nashEquilibriumReached = nashEquilibriumReached;
    }
}
