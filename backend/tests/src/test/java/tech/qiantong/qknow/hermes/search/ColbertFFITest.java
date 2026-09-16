package tech.qiantong.qknow.hermes.search;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ColbertFFITest {

    @Test
    public void testComputeMaxSim() {
        Assumptions.assumeTrue(ColbertNative.isNativeLoaded(), "Colbert native library not loaded, skipping native test");

        int dim = 4;
        float[] query = new float[]{
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f
        };
        int queryLen = 2;

        float[] docs = new float[]{
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,

                0.5f, 0.5f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        };
        int[] docLens = new int[]{3, 2};
        int numDocs = 2;

        float[] scores = ColbertNative.computeMaxSim(query, queryLen, docs, docLens, numDocs, dim);

        assertEquals(numDocs, scores.length);
        assertEquals(2.0f, scores[0], 0.001f);
        assertEquals(1.0f, scores[1], 0.001f);
    }
}
