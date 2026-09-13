package tech.qiantong.qknow.hermes.search;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;

public class ColbertFFITest {

    // Pure Java baseline for MaxSim
    private float[] computeMaxSimJava(float[] query, int queryLen, float[] docs, int[] docLens, int numDocs, int dim) {
        float[] scores = new float[numDocs];
        int docStart = 0;
        for (int d = 0; d < numDocs; d++) {
            int docLen = docLens[d];
            float totalScore = 0.0f;
            for (int i = 0; i < queryLen; i++) {
                int qOffset = i * dim;
                float maxSim = Float.NEGATIVE_INFINITY;
                for (int j = 0; j < docLen; j++) {
                    int dOffset = docStart + j * dim;
                    float sim = 0.0f;
                    for (int k = 0; k < dim; k++) {
                        sim += query[qOffset + k] * docs[dOffset + k];
                    }
                    if (sim > maxSim) {
                        maxSim = sim;
                    }
                }
                totalScore += maxSim;
            }
            scores[d] = totalScore;
            docStart += docLen * dim;
        }
        return scores;
    }

    @Test
    public void testMaxSimAccuracyAndBenchmark() {
        int dim = 128;
        int queryLen = 32;
        int numDocs = 5000;
        int docLen = 128;

        float[] query = new float[queryLen * dim];
        float[] docs = new float[numDocs * docLen * dim];
        int[] docLens = new int[numDocs];

        Random rand = new Random(42);
        for (int i = 0; i < query.length; i++) {
            query[i] = rand.nextFloat() * 2 - 1;
        }
        for (int i = 0; i < docs.length; i++) {
            docs[i] = rand.nextFloat() * 2 - 1;
        }
        for (int i = 0; i < numDocs; i++) {
            docLens[i] = docLen;
        }

        // 1. Benchmark Java
        long startJava = System.nanoTime();
        float[] scoresJava = computeMaxSimJava(query, queryLen, docs, docLens, numDocs, dim);
        long endJava = System.nanoTime();
        double javaMs = (endJava - startJava) / 1e6;
        System.out.println("Pure Java execution time: " + javaMs + " ms");

        // 2. Benchmark FFM + Rust
        ColbertNative.computeMaxSim(query, queryLen, docs, docLens, numDocs, dim);
        
        long startFFM = System.nanoTime();
        float[] scoresFFM = ColbertNative.computeMaxSim(query, queryLen, docs, docLens, numDocs, dim);
        long endFFM = System.nanoTime();
        double ffmMs = (endFFM - startFFM) / 1e6;
        System.out.println("FFM + Rust execution time: " + ffmMs + " ms");
        System.out.println("Speedup: " + (javaMs / ffmMs) + "x");

        // 3. Accuracy Assertion
        Assertions.assertEquals(scoresJava.length, scoresFFM.length);
        for (int i = 0; i < scoresJava.length; i++) {
            Assertions.assertEquals(scoresJava[i], scoresFFM[i], 1e-4, 
                "Score mismatch at index " + i + ": Java=" + scoresJava[i] + ", FFM=" + scoresFFM[i]);
        }
    }
}
