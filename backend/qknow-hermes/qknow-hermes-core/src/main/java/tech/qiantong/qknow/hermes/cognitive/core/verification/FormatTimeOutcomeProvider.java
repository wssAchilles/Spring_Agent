package tech.qiantong.qknow.hermes.cognitive.core.verification;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Verifies format_time only when its requested output format is explicit and reproducible. */
@Component
public final class FormatTimeOutcomeProvider implements CognitiveToolOutcomeProvider {
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String toolName() {
        return "format_time";
    }

    @Override
    public CognitiveToolOutcomeEvidence evaluate(String argumentsJson, String toolResult) {
        try {
            JSONObject arguments = JSON.parseObject(argumentsJson);
            if (arguments == null) {
                return CognitiveToolOutcomeEvidence.unknown("FORMAT_TIME_ARGUMENTS_MALFORMED");
            }
            String formatPattern = arguments.getString("formatPattern");
            String timeString = arguments.getString("timeStr");
            if (formatPattern == null || formatPattern.isBlank() || timeString == null) {
                return CognitiveToolOutcomeEvidence.unknown("FORMAT_TIME_NOT_REPRODUCIBLE");
            }
            String observed = observedResult(toolResult);
            if (observed == null) {
                return CognitiveToolOutcomeEvidence.unknown("FORMAT_TIME_RESULT_MALFORMED");
            }
            String error = errorResult(toolResult);
            if (error != null && !error.isBlank()) {
                return CognitiveToolOutcomeEvidence.fromCanonicalState(
                        CognitiveToolOutcomeEvidence.Status.REJECTED,
                        "FORMAT_TIME_REJECTED", canonical(arguments, error));
            }
            String expected = LocalDateTime.parse(timeString, INPUT_FORMAT)
                    .format(DateTimeFormatter.ofPattern(formatPattern));
            boolean matches = expected.equals(observed);
            return CognitiveToolOutcomeEvidence.fromCanonicalState(
                    matches ? CognitiveToolOutcomeEvidence.Status.APPLIED
                            : CognitiveToolOutcomeEvidence.Status.REJECTED,
                    matches ? "FORMAT_TIME_APPLIED" : "FORMAT_TIME_RESULT_MISMATCH",
                    canonical(arguments, observed));
        } catch (RuntimeException ignored) {
            return CognitiveToolOutcomeEvidence.unknown("FORMAT_TIME_RESULT_UNVERIFIABLE");
        }
    }

    private String observedResult(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return null;
        }
        try {
            Object parsed = JSON.parse(toolResult);
            if (parsed instanceof JSONObject object) {
                return object.getString("result");
            }
            return parsed instanceof String ? (String) parsed : toolResult.trim();
        } catch (RuntimeException ignored) {
            return toolResult.trim();
        }
    }

    private String errorResult(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return null;
        }
        try {
            Object parsed = JSON.parse(toolResult);
            return parsed instanceof JSONObject object ? object.getString("error") : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String canonical(JSONObject arguments, String observed) {
        return "format_time|" + arguments.toJSONString() + "|" + observed;
    }
}
