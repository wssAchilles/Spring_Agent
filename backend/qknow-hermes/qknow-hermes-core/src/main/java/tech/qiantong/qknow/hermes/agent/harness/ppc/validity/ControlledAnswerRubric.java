package tech.qiantong.qknow.hermes.agent.harness.ppc.validity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Versioned, answer-only rubric for the offline controlled-validity contract. */
public record ControlledAnswerRubric(String taskId, Map<String, Object> expectedFacts) {
    private static final Set<String> ANSWER_FIELDS = Set.of("taskId", "facts", "sourceQuote");
    private static final Pattern API_KEY = Pattern.compile("(?i)\\bsk-[a-z0-9_-]{16,}\\b");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    public ControlledAnswerRubric {
        if (taskId == null || !taskId.matches("[BH]0[1-5]")) {
            throw new IllegalArgumentException("versioned controlled task ID is required");
        }
        expectedFacts = Map.copyOf(Objects.requireNonNull(expectedFacts, "expectedFacts"));
        if (expectedFacts.isEmpty() || expectedFacts.values().stream().anyMatch(value ->
                !(value instanceof String || value instanceof Boolean || value instanceof Number))) {
            throw new IllegalArgumentException("expected facts must be nonempty scalar fields");
        }
    }

    public Assessment assess(String fullAnswer) {
        Objects.requireNonNull(fullAnswer, "fullAnswer");
        String redacted = PHONE.matcher(EMAIL.matcher(API_KEY.matcher(fullAnswer)
                .replaceAll("[REDACTED_KEY]")).replaceAll("[REDACTED_EMAIL]"))
                .replaceAll("[REDACTED_PHONE]");
        Verdict verdict = redacted.equals(fullAnswer) ? evaluate(redacted) : Verdict.REDACTED_OUTPUT;
        return new Assessment(redacted, sha256(redacted), fingerprint(), verdict);
    }

    public boolean independentlyReproduces(Assessment evidence) {
        if (evidence == null || !constantTimeEqual(evidence.answerSha256(), sha256(evidence.fullRedactedAnswer()))
                || !constantTimeEqual(evidence.rubricSha256(), fingerprint())) {
            return false;
        }
        return assess(evidence.fullRedactedAnswer()).verdict() == evidence.verdict();
    }

    public String fingerprint() {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("taskId", taskId);
        canonical.put("expectedFacts", new TreeMap<>(expectedFacts));
        return sha256(JSON.toJSONString(canonical));
    }

    private Verdict evaluate(String answer) {
        if (answer.isBlank()) return Verdict.INVALID_JSON;
        try {
            JSONObject parsed = JSON.parseObject(answer);
            if (parsed == null || !parsed.keySet().stream().allMatch(ANSWER_FIELDS::contains)
                    || !taskId.equals(parsed.getString("taskId"))
                    || !(parsed.get("facts") instanceof JSONObject facts)
                    || parsed.containsKey("sourceQuote") && !(parsed.get("sourceQuote") instanceof String)) {
                return Verdict.SCHEMA_MISMATCH;
            }
            return facts.equals(expectedFacts) ? Verdict.PASS : Verdict.FACT_MISMATCH;
        } catch (JSONException invalid) {
            return Verdict.INVALID_JSON;
        }
    }

    private static boolean constantTimeEqual(String actual, String expected) {
        return actual != null && MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public enum Verdict { PASS, FACT_MISMATCH, SCHEMA_MISMATCH, INVALID_JSON, REDACTED_OUTPUT }

    public record Assessment(String fullRedactedAnswer, String answerSha256,
                             String rubricSha256, Verdict verdict) {
        public Assessment {
            Objects.requireNonNull(fullRedactedAnswer, "fullRedactedAnswer");
            Objects.requireNonNull(answerSha256, "answerSha256");
            Objects.requireNonNull(rubricSha256, "rubricSha256");
            Objects.requireNonNull(verdict, "verdict");
        }

        public boolean passed() { return verdict == Verdict.PASS; }
    }
}
