package tech.qiantong.qknow.mcp.core.orchestration.dto;

/**
 * 工具链有向数据依赖关系 Record (Java 21 原生 Record)
 * 描述工具节点间的数据流通管道、耦合度权重以及最小破环自愈解耦标记
 */
public record ToolchainDependencyEdge(
        String fromTool,
        String toTool,
        String outputField,
        String inputField,
        double couplingWeight,
        boolean decoupled,
        String stubValue
) {
    /**
     * 创建切断解耦并注入影子桩的新边
     */
    public ToolchainDependencyEdge withDecoupledStub(String stubValue) {
        return new ToolchainDependencyEdge(
                fromTool, toTool, outputField, inputField, couplingWeight, true, stubValue
        );
    }
}
