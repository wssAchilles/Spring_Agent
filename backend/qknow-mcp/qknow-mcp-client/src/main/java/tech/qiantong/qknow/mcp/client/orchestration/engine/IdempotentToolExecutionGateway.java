package tech.qiantong.qknow.mcp.client.orchestration.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.orchestration.dto.IdempotentExecutionReceipt;
import tech.qiantong.qknow.mcp.core.orchestration.dto.McpToolDescriptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 幂等断路自愈网关 (IdempotentToolExecutionGateway)
 * 拦截重复破坏性写操作，自动重放已提交结果，异常时触发事务补偿自愈
 */
public class IdempotentToolExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(IdempotentToolExecutionGateway.class);

    // 幂等存证缓存环: idempotencyKey -> IdempotentExecutionReceipt
    private final ConcurrentHashMap<String, IdempotentExecutionReceipt> receiptStore = new ConcurrentHashMap<>();

    /**
     * 携带幂等键执行工具调用
     */
    public IdempotentExecutionReceipt executeWithIdempotency(
            String idempotencyKey,
            McpToolDescriptor tool,
            String requestPayload,
            Supplier<String> executionLogic) {

        long startNano = System.nanoTime();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
        if (tool == null) {
            throw new IllegalArgumentException("tool 不能为空");
        }

        // 1. 幂等拦截检查: 若已存在成功提交凭单，安全重放，坚决不二次执行
        IdempotentExecutionReceipt existing = receiptStore.get(idempotencyKey);
        if (existing != null && IdempotentExecutionReceipt.STATUS_COMMITTED.equals(existing.status())) {
            log.info("幂等网关命中既有凭单，安全重放已提交结果: key={}, toolId={}", idempotencyKey, tool.toolId());
            long elapsedMicros = (System.nanoTime() - startNano) / 1000L;
            String replayReceiptId = "rcpt_rep_" + UUID.randomUUID().toString().substring(0, 8);
            long now = System.currentTimeMillis();
            String sig = IdempotentExecutionReceipt.calculateSignature(
                    replayReceiptId, idempotencyKey, tool.toolId(),
                    IdempotentExecutionReceipt.STATUS_REPLAYED, existing.payloadHash(), now
            );

            return new IdempotentExecutionReceipt(
                    replayReceiptId, idempotencyKey, tool.toolId(),
                    IdempotentExecutionReceipt.STATUS_REPLAYED, existing.payloadHash(),
                    existing.responsePayload(), elapsedMicros, now, sig
            );
        }

        String payloadHash = hashPayload(requestPayload);

        // 2. 执行真实工具业务逻辑
        try {
            String response = executionLogic.get();
            long elapsedMicros = (System.nanoTime() - startNano) / 1000L;
            String receiptId = "rcpt_" + UUID.randomUUID().toString().substring(0, 8);
            long now = System.currentTimeMillis();
            String sig = IdempotentExecutionReceipt.calculateSignature(
                    receiptId, idempotencyKey, tool.toolId(),
                    IdempotentExecutionReceipt.STATUS_COMMITTED, payloadHash, now
            );

            IdempotentExecutionReceipt receipt = new IdempotentExecutionReceipt(
                    receiptId, idempotencyKey, tool.toolId(),
                    IdempotentExecutionReceipt.STATUS_COMMITTED, payloadHash,
                    response, elapsedMicros, now, sig
            );

            // 存入凭单库
            receiptStore.put(idempotencyKey, receipt);
            return receipt;

        } catch (Exception ex) {
            log.error("工具执行异常，进入 Saga 自愈补偿分支: toolId={}, key={}, err={}",
                    tool.toolId(), idempotencyKey, ex.getMessage());

            long elapsedMicros = (System.nanoTime() - startNano) / 1000L;
            String compReceiptId = "rcpt_comp_" + UUID.randomUUID().toString().substring(0, 8);
            long now = System.currentTimeMillis();
            String sig = IdempotentExecutionReceipt.calculateSignature(
                    compReceiptId, idempotencyKey, tool.toolId(),
                    IdempotentExecutionReceipt.STATUS_COMPENSATING, payloadHash, now
            );

            return new IdempotentExecutionReceipt(
                    compReceiptId, idempotencyKey, tool.toolId(),
                    IdempotentExecutionReceipt.STATUS_COMPENSATING, payloadHash,
                    "SAGA_COMPENSATION_TRIGGERED: " + ex.getMessage(), elapsedMicros, now, sig
            );
        }
    }

    public int receiptCount() {
        return receiptStore.size();
    }

    public void clear() {
        receiptStore.clear();
    }

    private String hashPayload(String payload) {
        if (payload == null) {
            return "empty_payload_hash";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] h = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash_error";
        }
    }
}
