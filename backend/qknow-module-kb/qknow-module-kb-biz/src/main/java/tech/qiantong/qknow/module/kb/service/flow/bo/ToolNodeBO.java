package tech.qiantong.qknow.module.kb.service.flow.bo;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.agent.guard.ReActCycleGuard;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.RuntimeStatusEnums;
import cn.hutool.core.util.StrUtil;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产级工具执行节点 (ToolNodeBO)
 * 具备：
 * 1. 外部 MCP 工具与内置函数无缝物理调度 (对接 hermes-core 权威 McpToolAdapter)；
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
        KbFlowNodeDO nodeDef = getNodeDefinition();
        String nodeName = nodeDef != null ? nodeDef.getName() : "tool";

        // 从节点配置中优先提取 toolCode / toolName
        String targetTool = nodeName;
        if (nodeDef != null && StrUtil.isNotBlank(nodeDef.getConfig())) {
            try {
                JSONObject configJson = JSONObject.parseObject(nodeDef.getConfig());
                String cfgToolCode = configJson.getString("toolCode");
                if (StrUtil.isBlank(cfgToolCode)) {
                    cfgToolCode = configJson.getString("toolName");
                }
                if (StrUtil.isNotBlank(cfgToolCode)) {
                    targetTool = cfgToolCode;
                }
            } catch (Exception e) {
                log.debug("[ToolNode] 解析节点配置 JSON 失败，回退使用节点名称: {}", nodeName);
            }
        }

        String toolInputStr = inputData != null ? JSONObject.toJSONString(inputData) : "{}";

        // 1. 循环卫士前置检测：防止 Agent 陷入死循环或无限重复调用
        ReActCycleGuard.CycleCheckResult checkResult = cycleGuard.inspectToolCall(targetTool, toolInputStr);
        if (checkResult != null && checkResult.tripped()) {
            log.warn("[ToolNode] 触发死循环熔断保护: tool={}, message={}", targetTool, checkResult.injectionMessage());
            NodeRunResultBO breakerResult = new NodeRunResultBO();
            breakerResult.setNodeUuid(nodeDef != null ? nodeDef.getUuid() : "unknown");
            breakerResult.setNodeName(nodeName);
            breakerResult.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
            Map<String, Object> output = new HashMap<>();
            output.put("result", checkResult.injectionMessage());
            output.put("status", "CIRCUIT_TRIPPED");
            output.put("tool", targetTool);
            breakerResult.setOutput(output);
            return breakerResult;
        }

        // 1.5 敏感高危工具人机协同审批 (HITL) 门禁
        boolean requireApproval = false;
        String approvalReason = "调用高危工具需要人工审批";
        if (nodeDef != null && StrUtil.isNotBlank(nodeDef.getConfig())) {
            try {
                JSONObject configJson = JSONObject.parseObject(nodeDef.getConfig());
                if (configJson.getBooleanValue("requireApproval", false)) {
                    requireApproval = true;
                    if (configJson.containsKey("reason")) {
                        approvalReason = configJson.getString("reason");
                    }
                }
            } catch (Exception ignored) {}
        }
        String lowerTool = targetTool.toLowerCase();
        if (lowerTool.contains("delete") || lowerTool.contains("drop") || lowerTool.contains("rm_rf")
                || lowerTool.contains("shutdown") || lowerTool.contains("transfer") || lowerTool.contains("execute_command")) {
            requireApproval = true;
            approvalReason = "检测到高危破坏性操作 [" + targetTool + "]，触发系统强制风控审批";
        }

        boolean alreadyApproved = false;
        if (context != null && context.getVariables() != null) {
            String appResult = context.getVariables().getString("approvalResult");
            if ("APPROVED".equalsIgnoreCase(appResult) || context.getVariables().getBooleanValue("approvalApproved", false)) {
                alreadyApproved = true;
            }
        }

        if (requireApproval && !alreadyApproved) {
            log.info("[ToolNode] 命中高危工具审批门禁: tool={}, 挂起工作流等待人工确认", targetTool);
            Map<String, Object> suspendPayload = new HashMap<>();
            suspendPayload.put("status", "SUSPENDED");
            suspendPayload.put("tool", targetTool);
            suspendPayload.put("reason", approvalReason);
            suspendPayload.put("input", inputData);
            suspendPayload.put("suspendedAt", System.currentTimeMillis());
            return NodeRunResultBO.suspended(nodeDef != null ? nodeDef.getUuid() : "unknown", nodeName, suspendPayload);
        }

        // 2. 真实物理调用 MCP 工具或回退
        Map<String, Object> output = new HashMap<>();
        try {
            Map<String, Object> toolArguments = inputData != null ? inputData : new HashMap<>();
            String callResult = null;
            if (mcpToolAdapter != null) {
                callResult = mcpToolAdapter.executeTool(targetTool, toolArguments);
            }

            if (callResult == null) {
                callResult = "{\"status\":\"NO_ADAPTER\",\"message\":\"MCP 工具适配器未初始化\"}";
            }

            // 解析结果
            Object parsedResult;
            try {
                parsedResult = JSONObject.parse(callResult);
            } catch (Exception ignored) {
                parsedResult = callResult;
            }

            output.put("result", parsedResult);
            output.put("rawResult", callResult);
            output.put("tool", targetTool);
            output.put("input", toolArguments);
            output.put("status", "SUCCESS");

            NodeRunResultBO successResult = new NodeRunResultBO();
            successResult.setNodeUuid(nodeDef != null ? nodeDef.getUuid() : "unknown");
            successResult.setNodeName(nodeName);
            successResult.setStatus(RuntimeStatusEnums.SUCCESS.getCode());
            successResult.setOutput(output);
            return successResult;
        } catch (Exception e) {
            log.error("[ToolNode] 工具调用执行异常: tool=" + targetTool, e);
            return NodeRunResultBO.failure(nodeDef != null ? nodeDef.getUuid() : "unknown", nodeName, "工具执行失败: " + e.getMessage());
        }
    }
}
