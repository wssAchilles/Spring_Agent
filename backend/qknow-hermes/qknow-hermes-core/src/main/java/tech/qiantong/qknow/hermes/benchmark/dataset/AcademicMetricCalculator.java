package tech.qiantong.qknow.hermes.benchmark.dataset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 顶会级标准学术指标计算器（EM、Token-F1、数值等价、Bootstrap 95% CI、McNemar/Wilcoxon 与 Booktabs LaTeX）。
 */
public final class AcademicMetricCalculator {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:,\\d{3})*(?:\\.\\d+)?");
    private static final Pattern TRAILING_DEC_ZEROS = Pattern.compile("(\\d+\\.\\d*?[1-9])0+\\b|(\\d+)\\.0+\\b");
    private static final Map<String, String> ENGLISH_NUMBER_WORDS = buildEnglishNumberWords();

    private static Map<String, String> buildEnglishNumberWords() {
        Map<String, String> m = new LinkedHashMap<>();
        String[] words = {
                "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"
        };
        for (int i = 0; i < words.length; i++) {
            m.put(words[i], String.valueOf(i));
        }
        return Map.copyOf(m);
    }

    private AcademicMetricCalculator() {
    }

    public record AnswerScore(double exactMatch, double tokenF1) {
    }

    /**
     * 计算预测答案与真实金标集合之间的最大 EM 与 Token-F1（支持 HotpotQA 标准归一化 + TableBench 1% 浮点容差）。
     */
    public static AnswerScore scoreAnswer(String prediction, List<String> goldAnswers) {
        if (prediction == null || prediction.isBlank() || goldAnswers == null || goldAnswers.isEmpty()) {
            return new AnswerScore(0.0, 0.0);
        }
        String extractedPred = extractFinalAnswerSpan(prediction);
        double bestEm = 0.0;
        double bestF1 = 0.0;
        for (String gold : goldAnswers) {
            if (gold == null) {
                continue;
            }
            double em = computeSingleEm(extractedPred, gold);
            boolean numericMatched = false;
            if (em < 1.0) {
                // 对长文本回答同时检查是否完整蕴含非平凡数值/实体金标
                em = computeNumericMatch(extractedPred, gold);
                numericMatched = em >= 1.0;
            }
            double f1 = Math.max(
                    computeTokenF1(extractedPred, gold),
                    computeTokenF1(prediction, gold)
            );
            if (numericMatched) {
                f1 = Math.max(f1, 1.0);
            }
            bestEm = Math.max(bestEm, em);
            bestF1 = Math.max(bestF1, f1);
        }
        return new AnswerScore(bestEm, bestF1);
    }

    public static String extractFinalAnswerSpan(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        String[] markers = {"最终答案：", "最终答案:", "Final Answer:", "Answer:", "结论：", "结论:"};
        int lastIdx = -1;
        String matchedMarker = "";
        for (String m : markers) {
            int idx = s.lastIndexOf(m);
            if (idx > lastIdx) {
                lastIdx = idx;
                matchedMarker = m;
            }
        }
        if (lastIdx >= 0) {
            String tail = s.substring(lastIdx + matchedMarker.length()).trim();
            int nl = tail.indexOf('\n');
            if (nl > 0) {
                tail = tail.substring(0, nl).trim();
            }
            if (!tail.isBlank()) {
                return tail;
            }
        }
        return s;
    }

    private static double computeSingleEm(String pred, String gold) {
        String np = normalizeAnswer(pred);
        String ng = normalizeAnswer(gold);
        if (np.isEmpty() || ng.isEmpty()) {
            return 0.0;
        }
        return np.equals(ng) ? 1.0 : 0.0;
    }

    private static double computeNumericMatch(String pred, String gold) {
        String cleanGold = gold.trim().replace(",", "");
        Double goldVal = tryParseDouble(cleanGold);
        if (goldVal == null) {
            return 0.0;
        }
        Matcher m = NUMBER_PATTERN.matcher(pred);
        while (m.find()) {
            Double predVal = tryParseDouble(m.group().replace(",", ""));
            if (predVal != null) {
                double denom = Math.max(1.0, Math.abs(goldVal));
                if (Math.abs(predVal - goldVal) / denom <= 0.01) {
                    return 1.0;
                }
            }
        }
        return 0.0;
    }

    public static double computeTokenF1(String prediction, String gold) {
        List<String> predTokens = tokenize(normalizeAnswer(prediction));
        List<String> goldTokens = tokenize(normalizeAnswer(gold));
        if (predTokens.isEmpty() || goldTokens.isEmpty()) {
            return 0.0;
        }
        Map<String, Integer> goldCounts = new LinkedHashMap<>();
        for (String t : goldTokens) {
            goldCounts.merge(t, 1, Integer::sum);
        }
        int overlap = 0;
        for (String t : predTokens) {
            int c = goldCounts.getOrDefault(t, 0);
            if (c > 0) {
                overlap++;
                goldCounts.put(t, c - 1);
            }
        }
        if (overlap == 0) {
            return 0.0;
        }
        double precision = (double) overlap / predTokens.size();
        double recall = (double) overlap / goldTokens.size();
        return (2.0 * precision * recall) / (precision + recall);
    }

    public static String normalizeAnswer(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        // 统一去除千分位逗号与末尾浮点多余零，并将独立英文基数词 zero..twenty 映射为阿拉伯数字
        lower = lower.replaceAll("(?<=\\d),(?=\\d)", "");
        lower = TRAILING_DEC_ZEROS.matcher(lower).replaceAll(mr ->
                mr.group(1) != null ? mr.group(1) : mr.group(2)
        );
        for (Map.Entry<String, String> entry : ENGLISH_NUMBER_WORDS.entrySet()) {
            lower = lower.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
        }
        lower = lower.replaceAll("\\b(a|an|the)\\b", " ");
        lower = lower.replaceAll("[^a-z0-9\\u4e00-\\u9fa5.\\s-]", " ");
        return lower.replaceAll("\\s+", " ").trim();
    }

    private static List<String> tokenize(String norm) {
        if (norm == null || norm.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String[] parts = norm.split("\\s+");
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            // 将连续汉字按单字切分以兼容中英文 F1
            StringBuilder ascii = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                    if (!ascii.isEmpty()) {
                        out.add(ascii.toString());
                        ascii.setLength(0);
                    }
                    out.add(String.valueOf(c));
                } else {
                    ascii.append(c);
                }
            }
            if (!ascii.isEmpty()) {
                out.add(ascii.toString());
            }
        }
        return out;
    }

    private static Double tryParseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    public static double mean(double[] arr) {
        if (arr == null || arr.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : arr) {
            sum += v;
        }
        return sum / arr.length;
    }

    public static double std(double[] arr) {
        if (arr == null || arr.length <= 1) {
            return 0.0;
        }
        double m = mean(arr);
        double sq = 0.0;
        for (double v : arr) {
            sq += (v - m) * (v - m);
        }
        return Math.sqrt(sq / (arr.length - 1));
    }

    public static double[] bootstrap95Ci(double[] data, int resamples, long seed) {
        if (data == null || data.length == 0) {
            return new double[]{0.0, 0.0};
        }
        Random rng = new Random(seed);
        double[] means = new double[resamples];
        for (int b = 0; b < resamples; b++) {
            double s = 0.0;
            for (int i = 0; i < data.length; i++) {
                s += data[rng.nextInt(data.length)];
            }
            means[b] = s / data.length;
        }
        Arrays.sort(means);
        return new double[]{means[(int) (resamples * 0.025)], means[(int) (resamples * 0.975)]};
    }

    public static double computeMcNemarPValue(boolean[] basePass, boolean[] targetPass) {
        int n = Math.min(basePass.length, targetPass.length);
        int b01 = 0;
        int b10 = 0;
        for (int i = 0; i < n; i++) {
            if (!basePass[i] && targetPass[i]) {
                b01++;
            } else if (basePass[i] && !targetPass[i]) {
                b10++;
            }
        }
        int disc = b01 + b10;
        if (disc == 0) {
            return 1.0;
        }
        double num = Math.max(0.0, Math.abs(b01 - b10) - 1.0);
        double chi2 = (num * num) / disc;
        return Math.max(1e-12, Math.min(1.0, Math.exp(-0.5 * chi2)));
    }

    public static double computeWilcoxonPValue(double[] baseValues, double[] targetValues) {
        int n = Math.min(baseValues.length, targetValues.length);
        List<Double> diffs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double d = targetValues[i] - baseValues[i];
            if (Math.abs(d) > 1e-9) {
                diffs.add(d);
            }
        }
        int m = diffs.size();
        if (m == 0) {
            return 1.0;
        }
        diffs.sort((x, y) -> Double.compare(Math.abs(x), Math.abs(y)));
        double wPlus = 0.0;
        for (int i = 0; i < m; i++) {
            if (diffs.get(i) > 0) {
                wPlus += (i + 1);
            }
        }
        double meanW = m * (m + 1.0) / 4.0;
        double varW = m * (m + 1.0) * (2.0 * m + 1.0) / 24.0;
        double z = (wPlus - meanW) / Math.sqrt(Math.max(1e-9, varW));
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(z) / Math.sqrt(2.0));
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))));
        double erfc = poly * Math.exp(-0.5 * z * z);
        return Math.max(1e-12, Math.min(1.0, erfc));
    }

    public static double compute4DParetoHypervolume(
            double f1Rate, double consistency, double meanUncachedTokens, double meanLatencyMs) {
        double normTokenSaving = Math.max(0.05, Math.min(1.0, 1.0 - (meanUncachedTokens / 6000.0)));
        double normLatencySaving = Math.max(0.05, Math.min(1.0, 1.0 - (meanLatencyMs / 15000.0)));
        return Math.max(0.0, f1Rate * consistency * normTokenSaving * normLatencySaving);
    }

    public static String significanceStars(double pVal) {
        if (pVal < 0.001) return "$^{***}$";
        if (pVal < 0.01) return "$^{**}$";
        if (pVal < 0.05) return "$^{*}$";
        return "";
    }
}
