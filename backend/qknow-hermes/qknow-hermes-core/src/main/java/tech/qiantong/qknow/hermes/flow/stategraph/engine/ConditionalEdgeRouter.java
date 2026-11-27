package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import tech.qiantong.qknow.hermes.flow.stategraph.reducer.DynamicSendPacket;
import tech.qiantong.qknow.hermes.flow.stategraph.reducer.NodeExecutionEnvelope;

import java.util.List;
import java.util.Map;

/**
 * 类型化状态图条件边路由器（Phase 9.2 PA-TSG）。
 * <p>
 * 替代原有的“无条件广播所有出边”设计，仅在当前归约状态与节点执行包满足条件时返回需要激活的下跳 {@link DynamicSendPacket}。
 * </p>
 */
@FunctionalInterface
public interface ConditionalEdgeRouter {

    List<DynamicSendPacket> route(Map<String, Object> reducedState, NodeExecutionEnvelope lastEnvelope);
}
