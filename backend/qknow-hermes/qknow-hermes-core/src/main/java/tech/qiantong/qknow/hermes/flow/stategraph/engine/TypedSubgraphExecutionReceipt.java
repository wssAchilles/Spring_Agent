package tech.qiantong.qknow.hermes.flow.stategraph.engine;

import java.io.Serializable;
import java.util.Objects;

/**
 * 类型化并行子图端到端执行不可变凭单（Phase 9.2 PA-TSG，映射论文定理 1 压缩比与加速验证指标）。
 */
public record TypedSubgraphExecutionReceipt(
        String executionId,
        String branchId,
        boolean completed,
        int superstepsExecuted,
        int totalNodeInvocations,
        int maxParallelWidth,
        int bridgeGateRejections,
        int speculativeForksTriggered,
        boolean zone0AnchorPreserved,
        long totalIsolatedInputChars,
        long estimatedSerialInputChars,
        double contextCompressionRatio,
        String finalStateDigest,
        String errorCode
) implements Serializable {

    public TypedSubgraphExecutionReceipt {
        Objects.requireNonNull(executionId, "executionId 不能为空");
        branchId = (branchId == null || branchId.isBlank()) ? "main" : branchId;
        finalStateDigest = finalStateDigest != null ? finalStateDigest : "";
        errorCode = errorCode != null ? errorCode : "";
    }
}
