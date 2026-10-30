package tech.qiantong.qknow.hermes.agent.harness.manifold;

import java.util.List;
import java.util.Objects;

/** Geometry of normalized 1024-dimensional Qwen embedding vectors. */
public final class HypersphericalManifoldProjector {
    public static final int QWEN_EMBEDDING_DIM = 1024;

    public record Centroid(double[] vector, double chordalLoss) {
        public Centroid {
            vector = vector.clone();
        }

        @Override
        public double[] vector() {
            return vector.clone();
        }
    }

    public double[] normalize(double[] vector) {
        Objects.requireNonNull(vector, "vector");
        if (vector.length != QWEN_EMBEDDING_DIM) {
            throw new IllegalArgumentException("Embedding dimension must be 1024");
        }
        double norm = 0.0;
        for (double coordinate : vector) {
            if (!Double.isFinite(coordinate)) {
                throw new IllegalArgumentException("Embedding coordinates must be finite");
            }
            norm = Math.hypot(norm, coordinate);
        }
        if (norm == 0.0) {
            throw new IllegalArgumentException("Zero vector has no spherical projection");
        }
        double[] unit = vector.clone();
        for (int i = 0; i < unit.length; i++) {
            unit[i] /= norm;
        }
        return unit;
    }

    public double cosine(double[] left, double[] right) {
        double[] u = normalize(left);
        double[] v = normalize(right);
        double dot = 0.0;
        for (int i = 0; i < QWEN_EMBEDDING_DIM; i++) {
            dot += u[i] * v[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

    public double chordSquared(double[] left, double[] right) {
        return 2.0 * (1.0 - cosine(left, right));
    }

    public double geodesic(double[] left, double[] right) {
        return Math.acos(cosine(left, right));
    }

    /** The normalized extrinsic mean minimizes average squared chordal distance. */
    public Centroid frechetMean(List<double[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("At least one vector is required");
        }
        double[] mean = new double[QWEN_EMBEDDING_DIM];
        for (double[] vector : vectors) {
            double[] unit = normalize(vector);
            for (int i = 0; i < mean.length; i++) {
                mean[i] += unit[i] / vectors.size();
            }
        }
        double norm = 0.0;
        for (double coordinate : mean) {
            norm = Math.hypot(norm, coordinate);
        }
        if (norm < 1.0e-12) {
            throw new IllegalArgumentException("Antipodal centroid is undefined");
        }
        return new Centroid(normalize(mean), 2.0 * (1.0 - Math.min(1.0, norm)));
    }
}
