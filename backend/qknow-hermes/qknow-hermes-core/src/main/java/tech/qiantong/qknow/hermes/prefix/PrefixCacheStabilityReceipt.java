package tech.qiantong.qknow.hermes.prefix;

import java.util.Arrays;
import java.util.Objects;

/**
 * DeepSeek 1M Context Caching 64-Token 块前缀不变性密码学审计凭单（纯 Java 21 不可变 Record）。
 * <p>
 * 对应博士论文第 2 章定理 2.1：当系统提示词与顶层静态元工具字节序列在多轮推理/反思中保持恒等时，
 * 输入前缀缓存命中率下界为 {@code 64*m_0 / (64*m_0 + L_tail)}。
 * </p>
 */
public record PrefixCacheStabilityReceipt(
        String requestId,
        int roundIndex,
        byte[] systemAndToolsSha256,
        int aligned64TokenBlocks,
        boolean prefixPreserved,
        byte[] hmacSignature
) {
    public PrefixCacheStabilityReceipt {
        Objects.requireNonNull(requestId, "requestId 不能为空");
        Objects.requireNonNull(systemAndToolsSha256, "systemAndToolsSha256 不能为空");
        Objects.requireNonNull(hmacSignature, "hmacSignature 不能为空");
        systemAndToolsSha256 = Arrays.copyOf(systemAndToolsSha256, systemAndToolsSha256.length);
        hmacSignature = Arrays.copyOf(hmacSignature, hmacSignature.length);
    }

    @Override
    public byte[] systemAndToolsSha256() {
        return Arrays.copyOf(systemAndToolsSha256, systemAndToolsSha256.length);
    }

    @Override
    public byte[] hmacSignature() {
        return Arrays.copyOf(hmacSignature, hmacSignature.length);
    }
}
