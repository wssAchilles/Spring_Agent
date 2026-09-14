package tech.qiantong.qknow.hermes.flow.saga;

import tech.qiantong.qknow.hermes.flow.bo.NodeRunResultBO;
import tech.qiantong.qknow.hermes.flow.bo.RuntimeContextBO;

/**
 * SAGA 事务可补偿节点接口
 * 实现此接口的节点支持在工作流异常或人工驳回时执行逆向副作用对冲
 */
public interface CompensableNode {

    /**
     * 执行逆向补偿逻辑
     *
     * @param context 运行时上下文
     * @param originalResult 该节点执行成功时的原输出结果
     * @return 补偿是否成功完成
     */
    boolean compensate(RuntimeContextBO context, NodeRunResultBO originalResult);

    /**
     * 节点补偿标识或描述
     */
    default String getCompensationIdentifier() {
        return getClass().getSimpleName();
    }
}
