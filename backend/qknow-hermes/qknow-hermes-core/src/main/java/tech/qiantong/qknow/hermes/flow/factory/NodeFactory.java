package tech.qiantong.qknow.hermes.flow.factory;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.common.exception.ServiceException;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;
import tech.qiantong.qknow.hermes.flow.bo.AggregatorNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.BaseNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.ConditionNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.HttpNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.KnowledgeNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.LLMNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.ReplyNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.StartNodeBO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowEdgeDO;
import tech.qiantong.qknow.hermes.flow.bo.KbFlowNodeDO;
import tech.qiantong.qknow.hermes.flow.enums.FlowNodeTypeEnums;
import tech.qiantong.qknow.hermes.flow.rag.RagRetrievalService;

import java.util.List;
import java.util.Objects;

/**
 * 节点创建工厂
 */
@Slf4j
@Component
public class NodeFactory {

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private RagRetrievalService ragRetrievalService;

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
            case LLM -> new LLMNodeBO(nodeDefinition, edgeList, chatModelFactory);
            case REPLY -> new ReplyNodeBO(nodeDefinition, edgeList);
            case CONDITION -> new ConditionNodeBO(nodeDefinition, edgeList);
            case HTTP -> new HttpNodeBO(nodeDefinition, edgeList);
            case KNOWLEDGE -> new KnowledgeNodeBO(nodeDefinition, edgeList, ragRetrievalService);
            case AGGREGATOR -> new AggregatorNodeBO(nodeDefinition, edgeList);

            default -> throw new ServiceException("不支持的节点类型：" + nodeType);
        };
    }
}
