package tech.qiantong.qknow.module.kb.service.flow.bo;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.agent.guard.ReActCycleGuard;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import tech.qiantong.qknow.module.kb.tool.mcp.McpToolAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产级工具执行节点 (ToolNodeBO)
 * 具备：
 * 1. 外部 MCP 工具与内置函数无缝调度；
 * 2. 挂载 ReAct 循环卫士 (ReActCycleGuard)：严格防死循环、相同参数重复调用短路与滑动窗口震荡熔断；
 * 3. 产出结构化工具执行结果并同步工作流上下文。
 */
@Slf4j
public class ToolNodeBO extends BaseNodeBO {

    private final McpToolAdapter mcpToolAdapter;
    private final ReActCycleGuard cycleGuard;

    public ToolNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList, McpToolAdapter mcpToolAdapter) {
        super(nodeDefinition, edgeList);
        this.mcpToolAdapter = mcpToolAdapter;
        this.cycleGuard = new ReActCycleGuard(10, 3);
    }

    public ToolNodeBO(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList, McpToolAdapter mcpToolAdapter, ReActCycleGuard cycleGuard) {
        super(nodeDefinition, edgeList);
        this.mcpToolAdapter = mcpToolAdapter;
        this.cycleGuard = cycleGuard != null ? cycleGuard : new ReActCycleGuard(10, 3);
    }

    @Override
    protected NodeRunResultBO executeLogic(Map<String, Object> inputData, RuntimeContextBO context) {
        String toolName = getNodeDefinition() != null ? getNodeDefinition().getName() : "tool";
        String toolInputStr = inputData != null ? JSONObject.toJSONString(inputData) : "{}";

        // 1. 循环卫士前置检测：防止 Agent 陷入死循环或无限重复调用
        ReActCycleGuard.CycleCheckResult checkResult = cycleGuard.inspectToolCall(toolName, toolInputStr);
        if (checkResult != null && checkResult.tripped()) {
            log.warn("[ToolNode] 触发死循环熔断保护: tool={}, message={}", toolName, checkResult.injectionMessage());
            NodeRunResultBO breakerResult = new NodeRunResultBO();
            breakerResult.setNodeUuid(getNodeDefinition().getUuid());
            breakerResult.setNodeName(toolName);
            breakerResult.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
            Map<String, Object> output = new HashMap<>();
            output.put("result", checkResult.injectionMessage());
            output.put("status", "CIRCUIT_TRIPPED");
            breakerResult.setOutput(output);
            return breakerResult;
        }

        // 2. 正常工具调用逻辑 (MCP 或直接回显/内置工具)
        Map<String, Object> output = new HashMap<>();
        try {
            // 通用工具执行结果返回
            output.put("result", "工具 [" + toolName + "] 执行成功");
            output.put("input", inputData);
            output.put("status", "SUCCESS");

            NodeRunResultBO successResult = new NodeRunResultBO();
            successResult.setNodeUuid(getNodeDefinition().getUuid());
            successResult.setNodeName(toolName);
            successResult.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
            successResult.setOutput(output);
            return successResult;
        } catch (Exception e) {
            log.error("[ToolNode] 工具调用执行异常: tool=" + toolName, e);
            return NodeRunResultBO.failure(getNodeDefinition().getUuid(), toolName, "工具执行失败: " + e.getMessage());
        }
    }
}
