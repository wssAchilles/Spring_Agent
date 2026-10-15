package tech.qiantong.qknow.hermes.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.infra.offheap.ArenaOffHeapSlabPool;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.IntStream;

/**
 * ColBERT MaxSim 晚期交互原生加速引擎
 * 采用 Java 21 Project Panama (Foreign Function & Memory API) 标准规范实现
 * 支持 ARM64 NEON 与 x86_64 AVX2 硬件级 SIMD 并行加速，并具备完整的 Fail-open 纯 Java 降级保障
 */
public class ColbertNative {
    private static final Logger log = LoggerFactory.getLogger(ColbertNative.class);
    private static final Linker LINKER = Linker.nativeLinker();
    private static MethodHandle MAXSIM_BATCH = null;
    private static MethodHandle GET_SIMD_ARCH = null;
    private static boolean nativeLoaded = false;
    private static int nativeArchCode = 0;

    static {
        loadNativeLibrary();
    }

    private static synchronized void loadNativeLibrary() {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            String libExt = osName.contains("mac") ? "dylib" : osName.contains("win") ? "dll" : "so";
            String prefix = osName.contains("win") ? "" : "lib";
            String libName = prefix + "colbert_native." + libExt;

            Path[] candidatePaths = new Path[]{
                    Paths.get(System.getProperty("user.dir"), "rust", "target", "release", libName),
                    Paths.get(System.getProperty("user.dir"), "..", "rust", "target", "release", libName),
                    Paths.get(System.getProperty("user.dir"), "backend", "rust", "target", "release", libName),
                    Paths.get(System.getProperty("user.dir"), "..", "backend", "rust", "target", "release", libName),
                    Paths.get("/Users/achilles/Documents/许子祺/Agent/backend/rust/target/release", libName)
            };

            Path selectedPath = null;
            for (Path path : candidatePaths) {
                if (path.toFile().exists()) {
                    selectedPath = path;
                    break;
                }
            }

            if (selectedPath != null) {
                System.load(selectedPath.toAbsolutePath().normalize().toString());
                SymbolLookup lookup = SymbolLookup.loaderLookup();

                MemorySegment maxsimFunc = lookup.find("maxsim_batch").orElse(null);
                if (maxsimFunc != null) {
                    MAXSIM_BATCH = LINKER.downcallHandle(maxsimFunc, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,         // 返回状态码 (0: 成功)
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, // query_ptr, query_len
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,   // doc_ptr, doc_lens_ptr
                            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, // num_docs, dim
                            ValueLayout.ADDRESS                         // scores_out
                    ));
                }

                MemorySegment archFunc = lookup.find("get_simd_architecture").orElse(null);
                if (archFunc != null) {
                    GET_SIMD_ARCH = LINKER.downcallHandle(archFunc, FunctionDescriptor.of(ValueLayout.JAVA_INT));
                    nativeArchCode = (int) GET_SIMD_ARCH.invokeExact();
                }

                if (MAXSIM_BATCH != null) {
                    nativeLoaded = true;
                    log.info("Colbert native 库加载成功: {}, 硬件架构代码: {} ({})",
                            selectedPath, nativeArchCode, getArchitectureName());
                }
            } else {
                log.warn("Colbert native 动态链接库未找到，将启用纯 Java 降级运行模式");
            }
        } catch (Throwable t) {
            log.warn("加载 Colbert native 库失败，已启用纯 Java 降级运行: {}", t.getMessage());
            nativeLoaded = false;
        }
    }

    public static boolean isNativeLoaded() {
        return nativeLoaded;
    }

    public static String getArchitectureName() {
        if (!nativeLoaded) {
            return "JAVA_FALLBACK";
        }
        return switch (nativeArchCode) {
            case 1 -> "NATIVE_SIMD_AVX2";
            case 2 -> "NATIVE_SIMD_NEON";
            default -> "NATIVE_SIMD_SCALAR";
        };
    }

    /**
     * 纳秒级真零拷贝接口：直接在调用方分配的堆外 MemorySegment 上执行打分
     * 无任何 JVM 堆到堆外数组的反复拷贝
     */
    public static int computeMaxSimZeroCopy(
            MemorySegment querySegment,
            long queryLen,
            MemorySegment docSegment,
            MemorySegment docLensSegment,
            long numDocs,
            long dim,
            MemorySegment scoresOutSegment
    ) {
        if (!nativeLoaded || MAXSIM_BATCH == null) {
            throw new UnsupportedOperationException("Colbert native library 未就绪，无法执行零拷贝操作");
        }
        try {
            return (int) MAXSIM_BATCH.invokeExact(
                    querySegment, queryLen,
                    docSegment, docLensSegment,
                    numDocs, dim,
                    scoresOutSegment
            );
        } catch (Throwable t) {
            throw new RuntimeException("执行零拷贝 native maxsim_batch 发生异常", t);
        }
    }

    /**
     * 基于堆外连续 Slab 内存池的纳秒级真零拷贝打分计算
     * 消除全部 query 和 doc 向量的堆内存分配与数据拷贝，达成极致 0-GC 延迟
     */
    public static ColbertScoreReceipt computeMaxSimWithSlab(
            ArenaOffHeapSlabPool.OffHeapSlice querySlice,
            int queryLen,
            ArenaOffHeapSlabPool.OffHeapSlice docsSlice,
            int[] docLens,
            int numDocs,
            int dim
    ) {
        long startNanos = System.nanoTime();
        if (nativeLoaded && MAXSIM_BATCH != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment docLensSegment = arena.allocateArray(ValueLayout.JAVA_INT, docLens.length);
                MemorySegment.copy(docLens, 0, docLensSegment, ValueLayout.JAVA_INT, 0, docLens.length);

                MemorySegment scoresOutSegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, numDocs);

                int statusCode = (int) MAXSIM_BATCH.invokeExact(
                        querySlice.sliceSegment(), (long) queryLen,
                        docsSlice.sliceSegment(), docLensSegment,
                        (long) numDocs, (long) dim,
                        scoresOutSegment
                );

                if (statusCode == 0) {
                    float[] scores = new float[numDocs];
                    MemorySegment.copy(scoresOutSegment, ValueLayout.JAVA_FLOAT, 0, scores, 0, numDocs);
                    long duration = System.nanoTime() - startNanos;
                    return ColbertScoreReceipt.create(
                            queryLen, numDocs, dim, getArchitectureName() + "_SLAB", duration, scores
                    );
                }
                log.warn("Native maxsim_batch 堆外 Slab 计算返回非零状态码: {}，降级运行", statusCode);
            } catch (Throwable t) {
                log.warn("执行 native maxsim_batch 堆外 Slab 计算发生异常，自动降级: {}", t.getMessage());
            }
        }

        // 纯 Java 降级实现
        float[] query = new float[querySlice.floatCount()];
        querySlice.readFloats(query, 0, query.length);
        float[] docs = new float[docsSlice.floatCount()];
        docsSlice.readFloats(docs, 0, docs.length);
        float[] fallbackScores = computeMaxSimJavaFallback(query, queryLen, docs, docLens, numDocs, dim);
        long duration = System.nanoTime() - startNanos;
        return ColbertScoreReceipt.create(
                queryLen, numDocs, dim, "JAVA_FALLBACK_SLAB", duration, fallbackScores
        );
    }

    /**
     * 传统数组接口，兼容既有调用并具备自动 Fail-open 纯 Java 降级能力
     */
    public static float[] computeMaxSim(
            float[] query,
            int queryLen,
            float[] docs,
            int[] docLens,
            int numDocs,
            int dim
    ) {
        ColbertScoreReceipt receipt = computeMaxSimWithReceipt(query, queryLen, docs, docLens, numDocs, dim);
        return receipt.getScoresArray();
    }

    /**
     * 执行打分并封装纯 Java 21 Record 不可变安全凭单
     */
    public static ColbertScoreReceipt computeMaxSimWithReceipt(
            float[] query,
            int queryLen,
            float[] docs,
            int[] docLens,
            int numDocs,
            int dim
    ) {
        long startNanos = System.nanoTime();
        if (nativeLoaded && MAXSIM_BATCH != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment querySegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, query.length);
                MemorySegment.copy(query, 0, querySegment, ValueLayout.JAVA_FLOAT, 0, query.length);

                MemorySegment docSegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, docs.length);
                MemorySegment.copy(docs, 0, docSegment, ValueLayout.JAVA_FLOAT, 0, docs.length);

                MemorySegment docLensSegment = arena.allocateArray(ValueLayout.JAVA_INT, docLens.length);
                MemorySegment.copy(docLens, 0, docLensSegment, ValueLayout.JAVA_INT, 0, docLens.length);

                MemorySegment scoresOutSegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, numDocs);

                int statusCode = (int) MAXSIM_BATCH.invokeExact(
                        querySegment, (long) queryLen,
                        docSegment, docLensSegment,
                        (long) numDocs, (long) dim,
                        scoresOutSegment
                );

                if (statusCode == 0) {
                    float[] scores = new float[numDocs];
                    MemorySegment.copy(scoresOutSegment, ValueLayout.JAVA_FLOAT, 0, scores, 0, numDocs);
                    long duration = System.nanoTime() - startNanos;
                    return ColbertScoreReceipt.create(
                            queryLen, numDocs, dim, getArchitectureName(), duration, scores
                    );
                }
                log.warn("Native maxsim_batch 返回非零状态码: {}，自动降级至纯 Java 计算", statusCode);
            } catch (Throwable t) {
                log.warn("执行 native maxsim_batch 发生异常，自动降级至纯 Java 计算: {}", t.getMessage());
            }
        }

        // 纯 Java 降级实现 (Fail-open)
        float[] fallbackScores = computeMaxSimJavaFallback(query, queryLen, docs, docLens, numDocs, dim);
        long duration = System.nanoTime() - startNanos;
        return ColbertScoreReceipt.create(
                queryLen, numDocs, dim, "JAVA_FALLBACK", duration, fallbackScores
        );
    }

    /**
     * 高性能纯 Java 降级兜底计算器
     * 利用 Java 21 并行流计算保证系统的高可用性
     */
    public static float[] computeMaxSimJavaFallback(
            float[] query,
            int queryLen,
            float[] docs,
            int[] docLens,
            int numDocs,
            int dim
    ) {
        int[] offsets = new int[numDocs];
        int currentOffset = 0;
        for (int d = 0; d < numDocs; d++) {
            offsets[d] = currentOffset;
            currentOffset += docLens[d] * dim;
        }

        float[] scores = new float[numDocs];
        IntStream.range(0, numDocs).parallel().forEach(d -> {
            int docStart = offsets[d];
            int docLen = docLens[d];
            float totalScore = 0.0f;

            for (int i = 0; i < queryLen; i++) {
                int qOffset = i * dim;
                float maxSim = Float.NEGATIVE_INFINITY;

                for (int j = 0; j < docLen; j++) {
                    int dOffset = docStart + j * dim;
                    float dotProduct = 0.0f;
                    for (int k = 0; k < dim; k++) {
                        dotProduct += query[qOffset + k] * docs[dOffset + k];
                    }
                    if (dotProduct > maxSim) {
                        maxSim = dotProduct;
                    }
                }
                totalScore += maxSim;
            }
            scores[d] = totalScore;
        });

        return scores;
    }
}
