package tech.qiantong.qknow.hermes.tool.mcp.sagas.dto;

import java.io.Serializable;

/**
 * 分布式 Sagas 事务租约元组 (纯 Java 21 Record 格式)
 * 对齐 ESWA Lemma 3.1 活性自愈条件与单调递增世代 Fencing Token 铁律
 *
 * @param transactionId   全局事务唯一标识
 * @param leaseOwnerId    当前租约持有节点唯一标识
 * @param fencingToken    64 位单调严格递增世代偏序令牌 (初始为 1)
 * @param expireAtMillis  租约截止物理时间戳 (毫秒)
 * @param status          租约状态 (ACTIVE, SUSPENDED, EXPIRED, RELEASED)
 */
public record McpSagaLeaseRecord(
        String transactionId,
        String leaseOwnerId,
        long fencingToken,
        long expireAtMillis,
        String status
) implements Serializable {

    public McpSagaLeaseRecord {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId 不能为空");
        }
        if (leaseOwnerId == null || leaseOwnerId.isBlank()) {
            throw new IllegalArgumentException("leaseOwnerId 不能为空");
        }
        if (fencingToken < 1) {
            throw new IllegalArgumentException("fencingToken 必须单调递增且大于等于 1");
        }
    }

    /**
     * 判断当前租约在物理时间戳 nowMillis 是否已超期
     */
    public boolean isExpired(long nowMillis) {
        return nowMillis > expireAtMillis;
    }
}
