package tech.qiantong.qknow.hermes.flow.hitl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 密码学工作流时空分叉执行不可变存证凭单
 * 遵循 Java 21 Record 规范与 SHA-256 自签名防篡改验证
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowForkReceipt(
        String forkReceiptId,
        String parentReceiptId,
        String workflowId,
        int forkStepIndex,
        String forkBatchId,
        String mutatedVariablesHash,
        String sha256Signature,
        long timestamp
) {
    /**
     * 工厂方法：生成并自签署分叉凭单
     *
     * @param parentReceiptId 溯源父凭单 ID
     * @param workflowId 工作流 ID
     * @param forkStepIndex 分叉截断历史步数
     * @param forkBatchId 分叉派生批次 ID
     * @param mutatedVariablesHash 局部变量/Mock 修改哈希
     * @return 签署完成的不可变凭单
     */
    public static WorkflowForkReceipt create(
            String parentReceiptId,
            String workflowId,
            int forkStepIndex,
            String forkBatchId,
            String mutatedVariablesHash
    ) {
        String forkReceiptId = "FORK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();
        String signature = computeSignature(parentReceiptId, workflowId, forkStepIndex, forkBatchId, mutatedVariablesHash, now);
        return new WorkflowForkReceipt(
                forkReceiptId,
                parentReceiptId,
                workflowId,
                forkStepIndex,
                forkBatchId,
                mutatedVariablesHash,
                signature,
                now
        );
    }

    /**
     * 校验凭单的自签名完整性
     */
    public boolean verifySignature() {
        String expected = computeSignature(parentReceiptId, workflowId, forkStepIndex, forkBatchId, mutatedVariablesHash, timestamp);
        return expected.equals(sha256Signature);
    }

    private static String computeSignature(
            String parentReceiptId, String workflowId, int forkStepIndex,
            String forkBatchId, String mutatedVariablesHash, long timestamp
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = (parentReceiptId == null ? "" : parentReceiptId) + ":" +
                    (workflowId == null ? "" : workflowId) + ":" +
                    forkStepIndex + ":" +
                    (forkBatchId == null ? "" : forkBatchId) + ":" +
                    (mutatedVariablesHash == null ? "" : mutatedVariablesHash) + ":" +
                    timestamp;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 算法", e);
        }
    }
}
