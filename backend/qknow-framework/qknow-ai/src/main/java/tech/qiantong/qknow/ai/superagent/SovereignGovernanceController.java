package tech.qiantong.qknow.ai.superagent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 60: 全局主权自治控制器
 * <p>
 * 基于定理 1.3，掌控节点生命周期、黑名单入狱隔离与紧急物理硬断路器。
 */
public class SovereignGovernanceController {

    public enum AgentState {
        ACTIVE,
        JAIL_ISOLATED,
        TERMINATED
    }

    public static final int MAX_VIOLATIONS_BEFORE_ISOLATION = 3;

    private final Map<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final Map<String, Integer> violationCounters = new ConcurrentHashMap<>();
    private final AtomicBoolean emergencyKillSwitch = new AtomicBoolean(false);

    /**
     * 注册智能体入网
     */
    public void registerAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("智能体 ID 不能为空");
        }
        agentStates.put(agentId, AgentState.ACTIVE);
    }

    /**
     * 报告智能体违规行为
     */
    public void reportViolation(String agentId, String reason) {
        if (agentId == null) {
            return;
        }
        int count = violationCounters.merge(agentId, 1, Integer::sum);
        if (count >= MAX_VIOLATIONS_BEFORE_ISOLATION) {
            isolateAgent(agentId, "违规次数达到阈值(" + count + "): " + reason);
        }
    }

    /**
     * 主动将智能体置入入狱隔离区
     */
    public void isolateAgent(String agentId, String reason) {
        if (agentId != null) {
            agentStates.put(agentId, AgentState.JAIL_ISOLATED);
        }
    }

    /**
     * 解冻恢复智能体
     */
    public void releaseAgent(String agentId) {
        if (agentId != null) {
            agentStates.put(agentId, AgentState.ACTIVE);
            violationCounters.remove(agentId);
        }
    }

    /**
     * 终止智能体生命周期
     */
    public void terminateAgent(String agentId) {
        if (agentId != null) {
            agentStates.put(agentId, AgentState.TERMINATED);
        }
    }

    /**
     * 检查智能体是否被允许通行/发布指令
     */
    public boolean isAgentAllowed(String agentId) {
        if (emergencyKillSwitch.get()) {
            return false;
        }
        if (agentId == null) {
            return false;
        }
        AgentState state = agentStates.get(agentId);
        return state == AgentState.ACTIVE;
    }

    /**
     * 触发紧急物理硬断路器 (Emergency Kill-Switch)
     * <p>
     * 保证在 <= 50ms 内完成全局切断
     */
    public boolean triggerEmergencyKillSwitch(String reason) {
        return emergencyKillSwitch.compareAndSet(false, true);
    }

    /**
     * 复位紧急断路器
     */
    public void resetEmergencyKillSwitch() {
        emergencyKillSwitch.set(false);
    }

    public boolean isEmergencyHalted() {
        return emergencyKillSwitch.get();
    }

    public AgentState getAgentState(String agentId) {
        return agentStates.getOrDefault(agentId, AgentState.TERMINATED);
    }

    public int getActiveAgentCount() {
        return (int) agentStates.values().stream().filter(s -> s == AgentState.ACTIVE).count();
    }

    public int getIsolatedAgentCount() {
        return (int) agentStates.values().stream().filter(s -> s == AgentState.JAIL_ISOLATED).count();
    }

    public void reset() {
        agentStates.clear();
        violationCounters.clear();
        emergencyKillSwitch.set(false);
    }
}
