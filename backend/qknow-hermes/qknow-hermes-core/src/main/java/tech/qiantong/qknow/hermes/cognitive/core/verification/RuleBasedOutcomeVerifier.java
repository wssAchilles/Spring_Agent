package tech.qiantong.qknow.hermes.cognitive.core.verification;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Deterministic, offline-only verifier for the three v1 outcome families. */
public final class RuleBasedOutcomeVerifier implements OutcomeVerifier {
    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);
    private final OutcomeVerifierRegistry registry;

    public RuleBasedOutcomeVerifier() {
        this(new OutcomeVerifierRegistry());
    }

    public RuleBasedOutcomeVerifier(OutcomeVerifierRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public OutcomeSignal verify(OutcomeVerificationRequest request) {
        String taskId = request == null || blank(request.taskId()) ? "unknown-task" : request.taskId();
        String familyId = request == null || blank(request.familyId()) ? "unknown-family" : request.familyId();
        if (request == null) {
            return unknown(taskId, familyId, "REQUEST_MISSING", "", "", List.of(), "");
        }
        OutcomeTaskFamily family = registry.find(familyId).orElse(null);
        if (family == null) {
            return unknown(request, "UNREGISTERED_FAMILY");
        }
        if (!family.deterministic() || !family.offlineOnly()) {
            return unknown(request, "UNSUPPORTED_FAMILY_CAPABILITY");
        }
        if (request.evidenceRefs() == null || request.evidenceRefs().isEmpty()
                || request.evidenceRefs().stream().anyMatch(RuleBasedOutcomeVerifier::blank)) {
            return unknown(taskId, familyId, "REQUIRED_EVIDENCE_MISSING", digest(request.expectedOutcome()),
                    digest(request.finalAnswer()), request.evidenceRefs(), observedPayload(request));
        }
        return switch (familyId) {
            case OutcomeTaskFamily.STRUCTURED_OUTPUT_ASSERTION -> verifyStructured(request);
            case OutcomeTaskFamily.TOOL_STATE_ASSERTION -> verifyToolState(request);
            case OutcomeTaskFamily.DETERMINISTIC_LOGIC_ASSERTION -> verifyLogic(request);
            default -> unknown(request, "UNREGISTERED_FAMILY");
        };
    }

    private OutcomeSignal verifyStructured(OutcomeVerificationRequest request) {
        if (blank(request.expectedOutcome()) || blank(request.finalAnswer())) {
            return unknown(request, "STRUCTURED_OUTPUT_MISSING");
        }
        try {
            JSONObject expected = parseObject(request.expectedOutcome());
            JSONObject observed = parseObject(request.finalAnswer());
            JSONObject assertions = expected.getJSONObject("assertions");
            if (assertions == null || assertions.isEmpty()
                    || assertions.values().stream().anyMatch(value -> !isValidAssertion(value))) {
                return unknown(request, "STRUCTURED_CONTRACT_INVALID");
            }
            for (Map.Entry<String, Object> entry : assertions.entrySet()) {
                Object observedValue = observed.get(entry.getKey());
                if (!matchesAssertion((JSONObject) entry.getValue(), observedValue)) {
                    return signal(request, OutcomeSignal.Status.FAIL, "ASSERTION_MISMATCH",
                            canonicalJson(expected), canonicalJson(observed));
                }
            }
            return signal(request, OutcomeSignal.Status.PASS, "VERIFIED",
                    canonicalJson(expected), canonicalJson(observed));
        } catch (RuntimeException error) {
            return unknown(request, "STRUCTURED_OUTPUT_INVALID");
        }
    }

    private OutcomeSignal verifyToolState(OutcomeVerificationRequest request) {
        if (blank(request.expectedOutcome()) || request.toolOutputs().isEmpty()) {
            return unknown(request, "TOOL_EVIDENCE_MISSING");
        }
        try {
            JSONObject expected = parseObject(request.expectedOutcome());
            if (!expected.containsKey("status") || expected.keySet().stream()
                    .anyMatch(field -> !field.equals("status") && !field.equals("summaryDigest"))) {
                return unknown(request, "TOOL_CONTRACT_INVALID");
            }
            for (String field : List.of("status", "summaryDigest")) {
                if (expected.containsKey(field) && !request.toolOutputs().containsKey(field)) {
                    return unknown(request, "TOOL_EVIDENCE_MISSING");
                }
                if (expected.containsKey(field)
                        && !Objects.equals(String.valueOf(expected.get(field)), request.toolOutputs().get(field))) {
                    return signal(request, OutcomeSignal.Status.FAIL, "TOOL_OUTPUT_MISMATCH",
                            canonicalJson(expected), canonicalMap(request.toolOutputs()));
                }
            }
            return signal(request, OutcomeSignal.Status.PASS, "VERIFIED",
                    canonicalJson(expected), canonicalMap(request.toolOutputs()));
        } catch (RuntimeException error) {
            return unknown(request, "TOOL_OUTPUT_INVALID");
        }
    }

    private OutcomeSignal verifyLogic(OutcomeVerificationRequest request) {
        if (blank(request.expectedOutcome()) || blank(request.finalAnswer())) {
            return unknown(request, "LOGIC_EVIDENCE_MISSING");
        }
        try {
            BigDecimal expected = new ArithmeticParser(request.expectedOutcome()).parse();
            BigDecimal observed = new BigDecimal(request.finalAnswer().trim(), MATH_CONTEXT);
            String expectedText = normalizeNumber(expected);
            String observedText = normalizeNumber(observed);
            OutcomeSignal.Status status = expected.compareTo(observed) == 0
                    ? OutcomeSignal.Status.PASS : OutcomeSignal.Status.FAIL;
            return signal(request, status, status == OutcomeSignal.Status.PASS ? "VERIFIED" : "OUTCOME_MISMATCH",
                    expectedText, observedText);
        } catch (RuntimeException error) {
            return unknown(request, "LOGIC_EXPRESSION_INVALID");
        }
    }

    private boolean matchesAssertion(JSONObject assertion, Object value) {
        if (value == null) {
            return false;
        }
        String type = String.valueOf(assertion.get("type")).toUpperCase(Locale.ROOT);
        boolean typeMatches = switch (type) {
            case "STRING" -> value instanceof String;
            case "BOOLEAN" -> value instanceof Boolean;
            case "NUMBER" -> value instanceof Number;
            default -> false;
        };
        if (!typeMatches) {
            return false;
        }
        if (value instanceof Number number) {
            BigDecimal actual = new BigDecimal(number.toString());
            if (assertion.get("equals") instanceof Number expected
                    && actual.compareTo(new BigDecimal(expected.toString())) != 0) {
                return false;
            }
            if (assertion.containsKey("min")
                    && actual.compareTo(new BigDecimal(String.valueOf(assertion.get("min")))) < 0) {
                return false;
            }
            if (assertion.containsKey("max")
                    && actual.compareTo(new BigDecimal(String.valueOf(assertion.get("max")))) > 0) {
                return false;
            }
        } else if (assertion.containsKey("equals")
                && !Objects.equals(assertion.get("equals"), value)) {
            return false;
        }
        return true;
    }

    private static boolean isValidAssertion(Object value) {
        if (!(value instanceof JSONObject assertion)
                || assertion.keySet().stream().anyMatch(key -> !List.of("type", "equals", "min", "max").contains(key))) {
            return false;
        }
        String type = String.valueOf(assertion.get("type")).toUpperCase(Locale.ROOT);
        if (!List.of("STRING", "BOOLEAN", "NUMBER").contains(type)) {
            return false;
        }
        Object equals = assertion.get("equals");
        boolean validEquals = !assertion.containsKey("equals") || switch (type) {
            case "STRING" -> equals instanceof String;
            case "BOOLEAN" -> equals instanceof Boolean;
            case "NUMBER" -> equals instanceof Number;
            default -> false;
        };
        if (!validEquals || (assertion.containsKey("min") || assertion.containsKey("max"))
                && !type.equals("NUMBER")) {
            return false;
        }
        try {
            BigDecimal min = assertion.containsKey("min")
                    ? new BigDecimal(String.valueOf(assertion.get("min"))) : null;
            BigDecimal max = assertion.containsKey("max")
                    ? new BigDecimal(String.valueOf(assertion.get("max"))) : null;
            return (!assertion.containsKey("min") || assertion.get("min") instanceof Number)
                    && (!assertion.containsKey("max") || assertion.get("max") instanceof Number)
                    && (min == null || max == null || min.compareTo(max) <= 0);
        } catch (NumberFormatException invalidRange) {
            return false;
        }
    }

    private OutcomeSignal unknown(OutcomeVerificationRequest request, String reason) {
        String observed = observedPayload(request);
        String taskId = blank(request.taskId()) ? "unknown-task" : request.taskId();
        String familyId = blank(request.familyId()) ? "unknown-family" : request.familyId();
        return unknown(taskId, familyId, reason, expectedDigest(request.expectedOutcome()),
                digest(observed), request.evidenceRefs(), observed);
    }

    private OutcomeSignal unknown(String taskId, String familyId, String reason, String expected,
                                  String observed, List<String> evidence, String observedPayload) {
        return new OutcomeSignal(taskId, familyId, OutcomeSignal.Status.UNKNOWN, reason, expected, observed,
                evidenceDigest(evidence, observedPayload));
    }

    private OutcomeSignal signal(OutcomeVerificationRequest request, OutcomeSignal.Status status, String reason,
                                 String expected, String observed) {
        String evidence = evidenceDigest(request.evidenceRefs(), observed);
        return new OutcomeSignal(request.taskId(), request.familyId(), status, reason, digest(expected),
                digest(observed), evidence);
    }

    private static JSONObject parseObject(String value) {
        JSONObject object = JSON.parseObject(value);
        if (object == null) {
            throw new IllegalArgumentException("JSON object required");
        }
        return object;
    }

    private static String canonicalMap(Map<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> quote(entry.getKey()) + ":" + quote(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String canonicalJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JSONObject object) {
            return object.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> quote(entry.getKey()) + ":" + canonicalJson(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof JSONArray array) {
            return array.stream().map(RuleBasedOutcomeVerifier::canonicalJson)
                    .collect(Collectors.joining(",", "[", "]"));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + canonicalJson(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private static String quote(String value) {
        return JSON.toJSONString(value);
    }

    private static String expectedDigest(String value) {
        if (blank(value)) {
            return "";
        }
        try {
            return digest(canonicalJson(JSON.parse(value)));
        } catch (RuntimeException ignored) {
            return digest(value.trim());
        }
    }

    private static String observedPayload(OutcomeVerificationRequest request) {
        if (!request.toolOutputs().isEmpty()) {
            return canonicalMap(request.toolOutputs());
        }
        if (!blank(request.finalAnswer())) {
            String answer = request.finalAnswer().trim();
            try {
                return canonicalJson(JSON.parse(answer));
            } catch (RuntimeException ignored) {
                return answer;
            }
        }
        return request.observedTrace() == null ? "" : request.observedTrace().trim();
    }

    private static String digest(String value) {
        return blank(value) ? "" : sha256(value);
    }

    private static String evidenceDigest(List<String> refs, String observedPayload) {
        if (refs == null || refs.isEmpty() || refs.stream().anyMatch(RuleBasedOutcomeVerifier::blank)
                || blank(observedPayload)) {
            return "";
        }
        String material = refs.stream().sorted().collect(Collectors.joining("\n"))
                + "\n--observed--\n" + observedPayload;
        return sha256(material);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String normalizeNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class ArithmeticParser {
        private final String input;
        private int position;

        private ArithmeticParser(String input) {
            if (input == null || input.length() > 256) {
                throw new IllegalArgumentException("expression is missing or too long");
            }
            this.input = input;
        }

        private BigDecimal parse() {
            BigDecimal result = expression();
            skipWhitespace();
            if (position != input.length()) {
                throw new IllegalArgumentException("unexpected expression token");
            }
            return result;
        }

        private BigDecimal expression() {
            BigDecimal result = term();
            while (true) {
                skipWhitespace();
                if (take('+')) {
                    result = result.add(term(), MATH_CONTEXT);
                } else if (take('-')) {
                    result = result.subtract(term(), MATH_CONTEXT);
                } else {
                    return result;
                }
            }
        }

        private BigDecimal term() {
            BigDecimal result = factor();
            while (true) {
                skipWhitespace();
                if (take('*')) {
                    result = result.multiply(factor(), MATH_CONTEXT);
                } else if (take('/')) {
                    result = result.divide(factor(), MATH_CONTEXT);
                } else {
                    return result;
                }
            }
        }

        private BigDecimal factor() {
            skipWhitespace();
            if (take('(')) {
                BigDecimal result = expression();
                if (!take(')')) {
                    throw new IllegalArgumentException("missing closing parenthesis");
                }
                return result;
            }
            int start = position;
            if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                position++;
            }
            while (position < input.length()
                    && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("number required");
            }
            return new BigDecimal(input.substring(start, position), MATH_CONTEXT);
        }

        private boolean take(char token) {
            if (position < input.length() && input.charAt(position) == token) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }
    }
}
