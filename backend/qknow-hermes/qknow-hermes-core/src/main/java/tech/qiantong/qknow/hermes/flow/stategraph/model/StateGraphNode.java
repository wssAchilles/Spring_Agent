package tech.qiantong.qknow.hermes.flow.stategraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.stategraph.dto.StateGraphContext;

import java.util.function.BiFunction;

/**
 * 状态图节点模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateGraphNode {

    /**
     * 节点 UUID
     */
    private String nodeUuid;

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 节点类型
     */
    private String nodeType;

    /**
     * 旁路 Fallback 节点 UUID（当节点自愈重试耗尽时流转）
     */
    private String fallbackNodeUuid;

    /**
     * 单节点局部最大重试次数（默认 3 次）
     */
    @Builder.Default
    private int maxLocalRetries = 3;

    /**
     * 是否为批处理迭代子图节点
     */
    @Builder.Default
    private boolean isIterationSubgraph = false;

    /**
     * 批处理迭代子图实例
     */
    private StateGraph subGraph;

    /**
     * 批处理项变量抽取 Key 或 SpEL 表达式
     */
    private String batchItemsKey;

    /**
     * 节点业务执行器函数（入参：当前节点与执行上下文，返回：执行结果）
     */
    private BiFunction<StateGraphNode, StateGraphContext, NodeRunResultBO> executionHandler;
}
