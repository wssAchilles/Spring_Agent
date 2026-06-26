package tech.qiantong.qknow.hermes.flow;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;
import tech.qiantong.qknow.hermes.flow.dag.DagExecutor;
import tech.qiantong.qknow.hermes.flow.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.hermes.proto.FlowEdge;
import tech.qiantong.qknow.hermes.proto.FlowNode;
import tech.qiantong.qknow.hermes.proto.FlowRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * DAG 工作流执行器
 * <p>
 * 负责将 gRPC FlowRequest 转换为内部数据结构，并委托 DagExecutor 执行
 */
@Slf4j
@Component
public class FlowExecutor {

    private final DagExecutor dagExecutor;

    public FlowExecutor(DagExecutor dagExecutor) {
        this.dagExecutor = dagExecutor;
    }

    /**
     * 执行工作流
     *
     * @param request gRPC FlowRequest
     * @return 所有节点的执行结果
     */
    public List<NodeRunResultBO> execute(FlowRequest request) {
        log.info("开始执行工作流, requestId: {}, flowId: {}",
                request.getRequestId(), request.getFlowId());

        // 1. 将 proto FlowNode 转换为 KbFlowNodeDO
        List<KbFlowNodeDO> nodes = convertNodes(request.getNodesList());

        // 2. 将 proto FlowEdge 转换为 KbFlowEdgeDO
        List<KbFlowEdgeDO> edges = convertEdges(request.getEdgesList());

        // 3. 解析运行时上下文
        RuntimeContextBO context = parseRuntimeContext(request.getRuntimeContextJson());

        // 4. 构建节点映射并设置到上下文
        java.util.Map<String, KbFlowNodeDO> nodeMap = new java.util.HashMap<>(nodes.size());
        for (KbFlowNodeDO node : nodes) {
            nodeMap.put(node.getUuid(), node);
        }
        context.setNodeMap(nodeMap);

        // 5. 委托 DagExecutor 执行
        List<NodeRunResultBO> results = dagExecutor.execute(nodes, edges, context);

        log.info("工作流执行完成, requestId: {}, 共执行 {} 个节点",
                request.getRequestId(), results.size());
        return results;
    }

    /**
     * 将 proto FlowNode 列表转换为 KbFlowNodeDO 列表
     */
    private List<KbFlowNodeDO> convertNodes(List<FlowNode> protoNodes) {
        List<KbFlowNodeDO> nodes = new ArrayList<>(protoNodes.size());
        for (FlowNode protoNode : protoNodes) {
            FlowNodeTypeEnums typeEnum = FlowNodeTypeEnums.getByName(protoNode.getType());
            Integer typeCode = typeEnum != null ? typeEnum.getCode() : null;

            KbFlowNodeDO node = new KbFlowNodeDO();
            node.setUuid(protoNode.getUuid());
            node.setName(protoNode.getName());
            node.setType(typeCode);
            node.setConfig(protoNode.getConfig());
            node.setInput(protoNode.getInput());
            nodes.add(node);
        }
        return nodes;
    }

    /**
     * 将 proto FlowEdge 列表转换为 KbFlowEdgeDO 列表
     */
    private List<KbFlowEdgeDO> convertEdges(List<FlowEdge> protoEdges) {
        List<KbFlowEdgeDO> edges = new ArrayList<>(protoEdges.size());
        for (FlowEdge protoEdge : protoEdges) {
            KbFlowEdgeDO edge = new KbFlowEdgeDO();
            edge.setSourceNodeUuid(protoEdge.getSourceNodeUuid());
            edge.setTargetNodeUuid(protoEdge.getTargetNodeUuid());
            edges.add(edge);
        }
        return edges;
    }

    /**
     * 解析运行时上下文 JSON
     */
    private RuntimeContextBO parseRuntimeContext(String runtimeContextJson) {
        JSONObject variables;
        if (runtimeContextJson != null && !runtimeContextJson.isBlank()) {
            variables = JSONObject.parseObject(runtimeContextJson);
        } else {
            variables = new JSONObject();
        }
        return new RuntimeContextBO(null, variables);
    }
}
