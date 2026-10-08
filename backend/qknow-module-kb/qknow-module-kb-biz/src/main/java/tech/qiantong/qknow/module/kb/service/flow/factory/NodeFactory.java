package tech.qiantong.qknow.module.kb.service.flow.factory;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.module.ai.api.modelMarket.IAiModelApiService;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowEdgeDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.flow.KbFlowNodeDO;
import tech.qiantong.qknow.module.kb.dal.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.module.kb.service.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.ConditionNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.KnowledgeNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.LLMNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.ReplyNodeBO;
import tech.qiantong.qknow.module.kb.service.flow.bo.StartNodeBO;
import tech.qiantong.qknow.module.kmc.api.service.IKmcApiService;

import org.springframework.beans.factory.annotation.Autowired;
import tech.qiantong.qknow.module.kb.service.flow.bo.ToolNodeBO;
import tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter;

import java.util.List;
import java.util.Objects;

/**
 * 节点创建工厂
 */
@Slf4j
@Component
public class NodeFactory {

    @Resource
    private IAiModelApiService aiModelService;

    @Resource
    private IKmcApiService kmcApiService;

    @Autowired(required = false)
    private McpToolAdapter mcpToolAdapter;

    /**
     * 根据节点定义创建节点实例
     *
     * @param nodeDefinition 节点定义
     * @param edgeList       所有边列表
     * @return 节点实例
     */
    public BaseNodeBO createNode(KbFlowNodeDO nodeDefinition, List<KbFlowEdgeDO> edgeList) {
        FlowNodeTypeEnums nodeType = FlowNodeTypeEnums.getByCode(nodeDefinition.getType());

        if (Objects.isNull(nodeType)) {
            throw new ServiceException("节点类型不能为空");
        }

        log.info("创建节点实例：{}", nodeDefinition.getName());

        return switch (nodeType) {
            case START -> new StartNodeBO(nodeDefinition, edgeList);
            case LLM -> new LLMNodeBO(nodeDefinition, edgeList, aiModelService);
            case REPLY -> new ReplyNodeBO(nodeDefinition, edgeList);
            case CONDITION -> new ConditionNodeBO(nodeDefinition, edgeList);
            case KNOWLEDGE -> new KnowledgeNodeBO(nodeDefinition, edgeList, kmcApiService);
            case TOOL -> new ToolNodeBO(nodeDefinition, edgeList, mcpToolAdapter);

            default -> throw new ServiceException("不支持的节点类型：" + nodeType);
        };
    }
}
