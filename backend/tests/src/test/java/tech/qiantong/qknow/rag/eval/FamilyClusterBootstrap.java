package tech.qiantong.qknow.rag.eval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class FamilyClusterBootstrap {

    public static final long DEFAULT_SEED = 20260714L;
    public static final int RESAMPLES = 10_000;

    public FamilyClusterBootstrap() {
    }

    public ConfidenceInterval mean(Map<String, List<Double>> valuesByFamily) {
        return bootstrap(validate(valuesByFamily)).interval();
    }

    public ConfidenceInterval pairedDelta(Map<String, List<Double>> baseline,
                                          Map<String, List<Double>> candidate) {
        return pairedInference(baseline, candidate).interval();
    }

    public PairedInference pairedInference(Map<String, List<Double>> baseline,
                                           Map<String, List<Double>> candidate) {
        Map<String, List<Double>> validBaseline = validate(baseline);
        Map<String, List<Double>> validCandidate = validate(candidate);
        if (!validBaseline.keySet().equals(validCandidate.keySet())) {
            throw new IllegalArgumentException("Paired bootstrap requires identical families");
        }
        Map<String, List<Double>> deltas = new LinkedHashMap<>();
        validBaseline.forEach((family, baselineValues) -> {
            List<Double> candidateValues = validCandidate.get(family);
            if (baselineValues.size() != candidateValues.size()) {
                throw new IllegalArgumentException("Paired bootstrap requires matching cases for " + family);
            }
            List<Double> familyDeltas = new ArrayList<>(baselineValues.size());
            for (int i = 0; i < baselineValues.size(); i++) {
                familyDeltas.add(candidateValues.get(i) - baselineValues.get(i));
            }
            deltas.put(family, familyDeltas);
        });
        BootstrapResult result = bootstrap(deltas);
        long nonPositive = Arrays.stream(result.samples()).filter(sample -> sample <= 0.0D).count();
        double pValue = (nonPositive + 1.0D) / (RESAMPLES + 1.0D);
        return new PairedInference(result.interval(), pValue);
    }

    private BootstrapResult bootstrap(Map<String, List<Double>> valuesByFamily) {
        List<String> families = valuesByFamily.keySet().stream().sorted().toList();
        double[] samples = new double[RESAMPLES];
        Random random = new Random(DEFAULT_SEED);
        for (int sample = 0; sample < RESAMPLES; sample++) {
            double sum = 0.0;
            int count = 0;
            for (int i = 0; i < families.size(); i++) {
                List<Double> values = valuesByFamily.get(families.get(random.nextInt(families.size())));
                for (double value : values) {
                    sum += value;
                    count++;
                }
            }
            samples[sample] = sum / count;
        }
        Arrays.sort(samples);
        ConfidenceInterval interval = new ConfidenceInterval(
                flatMean(valuesByFamily),
                samples[(int) Math.floor(0.025 * RESAMPLES)],
                samples[(int) Math.ceil(0.975 * RESAMPLES) - 1],
                families.size(),
                RESAMPLES);
        return new BootstrapResult(interval, samples);
    }

    private static Map<String, List<Double>> validate(Map<String, List<Double>> valuesByFamily) {
        if (valuesByFamily == null || valuesByFamily.isEmpty()) {
            throw new IllegalArgumentException("valuesByFamily must not be empty");
        }
        Map<String, List<Double>> result = new LinkedHashMap<>();
        valuesByFamily.forEach((family, values) -> {
            if (family == null || family.isBlank() || values == null || values.isEmpty()) {
                throw new IllegalArgumentException("Each family must contain values");
            }
            for (Double value : values) {
                if (value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("Metric values must be finite");
                }
            }
            result.put(family, List.copyOf(values));
        });
        return result;
    }

    private static double flatMean(Map<String, List<Double>> valuesByFamily) {
        double sum = 0.0;
        int count = 0;
        for (List<Double> values : valuesByFamily.values()) {
            for (double value : values) {
                sum += value;
                count++;
            }
        }
        return sum / count;
    }

    public record ConfidenceInterval(
            double estimate,
            double ciLow,
            double ciHigh,
            int clusters,
            int resamples
    ) {
    }

    public record PairedInference(ConfidenceInterval interval, double pValue) {
    }

    private record BootstrapResult(ConfidenceInterval interval, double[] samples) {
    }
}
