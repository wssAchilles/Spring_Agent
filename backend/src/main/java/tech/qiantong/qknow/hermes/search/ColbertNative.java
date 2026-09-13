package tech.qiantong.qknow.hermes.search;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ColbertNative {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle MAXSIM_BATCH;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        String libExt = osName.contains("mac") ? "dylib" : osName.contains("win") ? "dll" : "so";
        String prefix = osName.contains("win") ? "" : "lib";
        String libName = prefix + "colbert_native." + libExt;

        Path libPath = Paths.get(System.getProperty("user.dir"), "rust", "target", "release", libName);
        if (!libPath.toFile().exists()) {
            libPath = Paths.get(System.getProperty("user.dir"), "..", "backend", "rust", "target", "release", libName);
        }
        if (!libPath.toFile().exists()) {
            libPath = Paths.get(System.getProperty("user.dir"), "backend", "rust", "target", "release", libName);
        }

        System.load(libPath.toAbsolutePath().normalize().toString());
        SymbolLookup lookup = SymbolLookup.loaderLookup();

        MemorySegment func = lookup.find("maxsim_batch").orElseThrow(() -> new RuntimeException("Cannot find maxsim_batch"));
        MAXSIM_BATCH = LINKER.downcallHandle(func, FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, // query_ptr, query_len
                ValueLayout.ADDRESS, ValueLayout.ADDRESS,   // doc_ptr, doc_lens_ptr
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, // num_docs, dim
                ValueLayout.ADDRESS                         // scores_out
        ));
    }

    /**
     * Compute MaxSim for a batch of documents against a query.
     *
     * @param query    Flattened query vector
     * @param queryLen Number of tokens in the query
     * @param docs     Flattened documents vectors for all docs
     * @param docLens  Number of tokens in each document
     * @param numDocs  Total number of documents
     * @param dim      Vector dimension
     * @return Array of scores
     */
    public static float[] computeMaxSim(float[] query, int queryLen, float[] docs, int[] docLens, int numDocs, int dim) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment querySegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, query.length);
            MemorySegment.copy(query, 0, querySegment, ValueLayout.JAVA_FLOAT, 0, query.length);

            MemorySegment docSegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, docs.length);
            MemorySegment.copy(docs, 0, docSegment, ValueLayout.JAVA_FLOAT, 0, docs.length);

            MemorySegment docLensSegment = arena.allocateArray(ValueLayout.JAVA_INT, docLens.length);
            MemorySegment.copy(docLens, 0, docLensSegment, ValueLayout.JAVA_INT, 0, docLens.length);

            MemorySegment scoresOutSegment = arena.allocateArray(ValueLayout.JAVA_FLOAT, numDocs);

            MAXSIM_BATCH.invokeExact(
                    querySegment, (long) queryLen,
                    docSegment, docLensSegment,
                    (long) numDocs, (long) dim,
                    scoresOutSegment
            );

            float[] scores = new float[numDocs];
            MemorySegment.copy(scoresOutSegment, ValueLayout.JAVA_FLOAT, 0, scores, 0, numDocs);

            return scores;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to execute native maxsim_batch", t);
        }
    }
}
