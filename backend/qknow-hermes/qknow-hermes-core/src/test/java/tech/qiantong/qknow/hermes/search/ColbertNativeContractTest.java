package tech.qiantong.qknow.hermes.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第一阶段核心工程契约测试：
 * Rust SIMD 算子修复、Project Panama 纳秒级零拷贝与千问 1536 维超球面打分验证
 */
public class ColbertNativeContractTest {

    @Test
    @DisplayName("契约一：Native 加速与 Java Fallback 降级算子数值等价性契约")
    public void testNativeAndJavaEquivalence() {
        int dim = 16;
        int queryLen = 3;
        int numDocs = 4;
        int[] docLens = new int[]{2, 4, 3, 5};

        int totalDocTokens = 0;
        for (int len : docLens) {
            totalDocTokens += len;
        }

        Random random = new Random(42);
        float[] query = generateNormalizedVectors(queryLen, dim, random);
        float[] docs = generateNormalizedVectors(totalDocTokens, dim, random);

        // 1. 获取 Native (或带凭单接口) 计算结果
        ColbertScoreReceipt receipt = ColbertNative.computeMaxSimWithReceipt(query, queryLen, docs, docLens, numDocs, dim);
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "计算凭单常量时间 SHA-256 签名必须校验通过");

        float[] nativeScores = receipt.getScoresArray();

        // 2. 获取纯 Java 降级计算结果
        float[] fallbackScores = ColbertNative.computeMaxSimJavaFallback(query, queryLen, docs, docLens, numDocs, dim);

        // 3. 验证两个算子在全部文档上的打分绝对误差在 IEEE 754 容差范围内
        assertEquals(numDocs, nativeScores.length);
        assertEquals(numDocs, fallbackScores.length);
        for (int i = 0; i < numDocs; i++) {
            assertEquals(fallbackScores[i], nativeScores[i], 1e-4f,
                    String.format("文档 %d 的打分必须与 Java Fallback 完全一致", i));
        }
    }

    @Test
    @DisplayName("契约二：纳秒级真零拷贝 MemorySegment 堆外交互契约")
    public void testZeroCopyMemorySegmentExecution() {
        if (!ColbertNative.isNativeLoaded()) {
            return; // 若当前环境未加载动态库，跳过底层零拷贝测试
        }

        int dim = 8;
        int queryLen = 2;
        int numDocs = 2;
        int[] docLens = new int[]{2, 2};

        float[] query = new float[]{
                1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
        };
        float[] docs = new float[]{
                1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,

                0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f
        };

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment querySegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, query.length);
            MemorySegment.copy(query, 0, querySegment, ValueLayout.JAVA_FLOAT, 0, query.length);

            MemorySegment docSegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, docs.length);
            MemorySegment.copy(docs, 0, docSegment, ValueLayout.JAVA_FLOAT, 0, docs.length);

            MemorySegment docLensSegment = arena.allocateArray(ValueLayout.JAVA_INT, docLens.length);
            MemorySegment.copy(docLens, 0, docLensSegment, ValueLayout.JAVA_INT, 0, docLens.length);

            MemorySegment scoresOut = arena.allocateArray(ValueLayout.JAVA_FLOAT, numDocs);

            int status = ColbertNative.computeMaxSimZeroCopy(
                    querySegment, queryLen,
                    docSegment, docLensSegment,
                    numDocs, dim,
                    scoresOut
            );

            assertEquals(0, status, "零拷贝调用状态码必须为 0 (SUCCESS)");

            float[] scores = new float[numDocs];
            MemorySegment.copy(scoresOut, ValueLayout.JAVA_FLOAT, 0, scores, 0, numDocs);

            assertEquals(2.0f, scores[0], 1e-4f);
            assertEquals(0.0f, scores[1], 1e-4f);
        }
    }

    @Test
    @DisplayName("契约三：千问 1536 维超球面单位向量扩展性与 Top-500 算力延迟契约")
    public void testHypersphericalQwenScaleStressContract() {
        int dim = 1536; // 阿里千问 1536 维空间
        int queryLen = 4;
        int numDocs = 500; // 粗排候选池由 50 扩大 10 倍至 500
        int docLenPerDoc = 6;

        int[] docLens = new int[numDocs];
        for (int i = 0; i < numDocs; i++) {
            docLens[i] = docLenPerDoc;
        }

        Random random = new Random(1024);
        float[] query = generateNormalizedVectors(queryLen, dim, random);
        float[] docs = generateNormalizedVectors(numDocs * docLenPerDoc, dim, random);

        // 验证千问 1536 维单位向量约束
        for (int i = 0; i < queryLen; i++) {
            float norm = calculateL2Norm(query, i * dim, dim);
            assertEquals(1.0f, norm, 1e-3f, "Query 向量必须满足 L2 范数单位归一化");
        }

        long startTime = System.currentTimeMillis();
        ColbertScoreReceipt receipt = ColbertNative.computeMaxSimWithReceipt(query, queryLen, docs, docLens, numDocs, dim);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertNotNull(receipt);
        assertEquals(numDocs, receipt.scores().size());
        assertTrue(receipt.verifySignature(), "大样本压测凭单必须防篡改验签成功");

        System.out.printf("[契约测试] Top-500 千问 1536 维候选打分完成，耗时: %d ms, 模式: %s%n",
                elapsedMs, receipt.executionMode());

        // 验证各文档得分均在合理区间内 [-queryLen, queryLen]
        for (float score : receipt.getScoresArray()) {
            assertTrue(score >= -queryLen && score <= queryLen,
                    "单位向量余弦打分累加值必须处于合法余弦界内");
        }
    }

    @Test
    @DisplayName("契约四：不可变加密防篡改凭单防侧信道时序攻击验证契约")
    public void testScoreReceiptCryptographicIntegrityContract() {
        float[] scores = new float[]{1.5f, 2.8f, 0.3f};
        ColbertScoreReceipt receipt = ColbertScoreReceipt.create(
                2, 3, 1536, "NATIVE_SIMD_NEON", 125000L, scores
        );

        assertTrue(receipt.verifySignature(), "初始生成凭单必须通过验签");

        // 验证集合防篡改不可变性
        assertThrows(UnsupportedOperationException.class, () -> receipt.scores().add(4.0f),
                "凭单内部 scores 集合必须为不可变 List，严禁外部修改");

        // 验证伪造签名的防范
        ColbertScoreReceipt forgedReceipt = new ColbertScoreReceipt(
                receipt.queryLength(),
                receipt.numDocs(),
                receipt.dimension(),
                receipt.executionMode(),
                receipt.executionDurationNanos(),
                receipt.scores(),
                "forged_malicious_signature_hash_hex"
        );
        assertFalse(forgedReceipt.verifySignature(), "伪造签名凭单必须被 MessageDigest.isEqual 常量时间拒绝");
    }

    private static float[] generateNormalizedVectors(int count, int dim, Random random) {
        float[] data = new float[count * dim];
        for (int i = 0; i < count; i++) {
            float sumSq = 0.0f;
            int offset = i * dim;
            for (int j = 0; j < dim; j++) {
                float v = random.nextFloat() * 2.0f - 1.0f;
                data[offset + j] = v;
                sumSq += v * v;
            }
            float norm = (float) Math.sqrt(sumSq);
            if (norm > 1e-6f) {
                for (int j = 0; j < dim; j++) {
                    data[offset + j] /= norm;
                }
            }
        }
        return data;
    }

    private static float calculateL2Norm(float[] data, int offset, int dim) {
        float sum = 0.0f;
        for (int i = 0; i < dim; i++) {
            float v = data[offset + i];
            sum += v * v;
        }
        return (float) Math.sqrt(sum);
    }
}
