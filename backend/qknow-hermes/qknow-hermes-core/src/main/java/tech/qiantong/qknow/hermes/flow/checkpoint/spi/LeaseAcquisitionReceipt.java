package tech.qiantong.qknow.hermes.flow.checkpoint.spi;

import java.io.Serializable;

/**
 * 租约抢占与续约结果凭单 (不可变 Record)
 */
public record LeaseAcquisitionReceipt(
        boolean acquired,
        String runtimeId,
        String currentOwnerId,
        long leaseExpireAtMs,
        long fencingToken,
        String message
) implements Serializable {}
