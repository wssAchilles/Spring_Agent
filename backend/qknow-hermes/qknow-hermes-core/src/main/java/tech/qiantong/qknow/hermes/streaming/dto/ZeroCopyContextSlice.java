package tech.qiantong.qknow.hermes.streaming.dto;

import java.util.Arrays;

/**
 * 零拷贝上下文切片 Java 21 Record
 * 封装底层只读文本引用、切片分段偏移量与阿里千问 1536 维超球面单位向量
 */
public record ZeroCopyContextSlice(
        String sliceId,
        String sessionId,
        String rawContentRef,
        int offset,
        int length,
        float[] sphericalEmbedding,
        String topicLabel,
        long createdTimestamp
) {
    public static final int EXPECTED_DIMENSION = 1536;
    public static final double NORM_TOLERANCE = 1e-4;

    /**
     * 严格强校验阿里千问 1536 维超球面单位向量模长范数 (||v||_2 = 1.0 +- 1e-4)
     */
    public boolean isValidEmbedding() {
        if (sphericalEmbedding == null || sphericalEmbedding.length != EXPECTED_DIMENSION) {
            return false;
        }
        double sumSq = 0.0;
        for (float v : sphericalEmbedding) {
            sumSq += (double) v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= NORM_TOLERANCE;
    }

    /**
     * 零拷贝视图提取切片文本内容，底层共享原始内存引用
     */
    public String getSliceContent() {
        if (rawContentRef == null || offset < 0 || length <= 0) {
            return "";
        }
        int end = Math.min(rawContentRef.length(), offset + length);
        if (offset >= end) {
            return "";
        }
        return rawContentRef.substring(offset, end);
    }
}
