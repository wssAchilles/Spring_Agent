package tech.qiantong.qknow.module.kb.service.flow.bo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 极简统一 Agent 状态流数据模型 (纯 Java 21 Record 不可变对象)
 * 对标世界顶级 Agent 系统架构规范 (Anthropic Clean Agent / LangGraph State)
 * 具备：不可变更新、历史轨迹自留痕、线程安全与零锁高吞吐流转。
 */
public record AgentState(
        String conversationId,
        String currentStepNode,
        Map<String, Object> variables,
        List<Map<String, Object>> messageHistory,
        List<String> executionPath,
        boolean isTerminated,
        String terminationReason
) {
    public static AgentState initial(String conversationId, Map<String, Object> initialVariables) {
        return new AgentState(
                conversationId,
                "start",
                initialVariables != null ? Map.copyOf(initialVariables) : Collections.emptyMap(),
                Collections.emptyList(),
                Collections.singletonList("start"),
                false,
                null
        );
    }

    public AgentState step(String nextNode, Map<String, Object> variableDelta) {
        Map<String, Object> nextVars = new java.util.HashMap<>(this.variables);
        if (variableDelta != null) {
            nextVars.putAll(variableDelta);
        }
        List<String> nextPath = new java.util.ArrayList<>(this.executionPath);
        nextPath.add(nextNode);
        return new AgentState(
                this.conversationId,
                nextNode,
                Collections.unmodifiableMap(nextVars),
                this.messageHistory,
                Collections.unmodifiableList(nextPath),
                this.isTerminated,
                this.terminationReason
        );
    }

    public AgentState terminate(String reason) {
        return new AgentState(
                this.conversationId,
                this.currentStepNode,
                this.variables,
                this.messageHistory,
                this.executionPath,
                true,
                reason
        );
    }
}
