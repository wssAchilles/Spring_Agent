package tech.qiantong.qknow.hermes.agent.harness.manifold;

import java.util.List;
import java.util.Objects;

/**
 * Geometry of normalized 1024-dimensional Qwen embeddings on S^1023.
 * The extrinsic/chordal centroid is the normalized Euclidean mean, not the intrinsic
 * geodesic Frechet mean. It exactly minimizes empirical squared chordal risk.
 */
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

    /**
     * Returns mu_ext = mean(v_i) / ||mean(v_i)||, minimizing average squared chordal
     * distance with loss 2(1 - ||mean(v_i)||). Within a local convex spherical cap,
     * this O(Nd) closed form is a second-order approximation to the geodesic mean.
     */
    public Centroid extrinsicCentroid(List<double[]> vectors) {
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

    /** Equivalent name for the extrinsic centroid's squared-chord objective. */
    public Centroid chordalCentroid(List<double[]> vectors) {
        return extrinsicCentroid(vectors);
    }

    /**
     * Compatibility delegate returning the extrinsic/chordal centroid, not the
     * intrinsic Frechet mean. Prefer {@link #extrinsicCentroid(List)}.
     */
    @Deprecated(since = "stage7", forRemoval = false)
    public Centroid frechetMean(List<double[]> vectors) {
        return extrinsicCentroid(vectors);
    }

    /**
     * Bounds mean squared geodesic distortion within a cap of radius theta:
     * d_geo^2 <= ((theta/2)/sin(theta/2))^2 d_chord^2 <= (pi^2/4) d_chord^2.
     */
    public double geodesicDistortionUpperBound(Centroid centroid, double maxGeodesicRadians) {
        Objects.requireNonNull(centroid, "centroid must not be null");
        if (Double.isNaN(maxGeodesicRadians) || maxGeodesicRadians < 0.0
                || maxGeodesicRadians > Math.PI) {
            throw new IllegalArgumentException("maxGeodesicRadians must be in [0, pi], got: "
                    + maxGeodesicRadians);
        }
        double halfTheta = maxGeodesicRadians * 0.5;
        double factor = halfTheta < 1.0e-9 ? 1.0 : Math.pow(halfTheta / Math.sin(halfTheta), 2);
        return factor * centroid.chordalLoss();
    }
}
