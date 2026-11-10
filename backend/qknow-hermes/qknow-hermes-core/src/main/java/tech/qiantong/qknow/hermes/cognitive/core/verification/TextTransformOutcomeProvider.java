package tech.qiantong.qknow.hermes.cognitive.core.verification;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Deterministically verifies the structured result of the local textTransform tool. */
@Component
public final class TextTransformOutcomeProvider implements CognitiveToolOutcomeProvider {
    @Override
    public String toolName() {
        return "textTransform";
    }

    @Override
    public CognitiveToolOutcomeEvidence evaluate(String argumentsJson, String toolResult) {
        try {
            JSONObject arguments = JSON.parseObject(argumentsJson);
            JSONObject response = JSON.parseObject(toolResult);
            if (arguments == null || response == null) {
                return CognitiveToolOutcomeEvidence.unknown("TEXT_TRANSFORM_RESULT_MALFORMED");
            }
            String error = response.getString("error");
            if (error != null && !error.isBlank()) {
                return CognitiveToolOutcomeEvidence.fromCanonicalState(
                        CognitiveToolOutcomeEvidence.Status.REJECTED,
                        "TEXT_TRANSFORM_REJECTED", canonical(arguments, "error"));
            }
            if (!response.containsKey("result")) {
                return CognitiveToolOutcomeEvidence.unknown("TEXT_TRANSFORM_RESULT_MISSING");
            }
            String expected = transform(arguments);
            String observed = response.getString("result");
            if (expected == null || observed == null) {
                return CognitiveToolOutcomeEvidence.unknown("TEXT_TRANSFORM_RESULT_UNVERIFIABLE");
            }
            return CognitiveToolOutcomeEvidence.fromCanonicalState(
                    expected.equals(observed)
                            ? CognitiveToolOutcomeEvidence.Status.APPLIED
                            : CognitiveToolOutcomeEvidence.Status.REJECTED,
                    expected.equals(observed) ? "TEXT_TRANSFORM_APPLIED" : "TEXT_TRANSFORM_RESULT_MISMATCH",
                    canonical(arguments, observed));
        } catch (RuntimeException ignored) {
            return CognitiveToolOutcomeEvidence.unknown("TEXT_TRANSFORM_RESULT_UNPARSEABLE");
        }
    }

    private String transform(JSONObject arguments) {
        String text = arguments.getString("text");
        String operation = arguments.getString("operation");
        if (text == null || operation == null) {
            return null;
        }
        return switch (operation) {
            case "uppercase" -> text.toUpperCase(Locale.getDefault());
            case "lowercase" -> text.toLowerCase(Locale.getDefault());
            case "trim" -> text.trim();
            case "substring" -> {
                int length = arguments.getIntValue("param", 100);
                if (length < 0) {
                    yield null;
                }
                yield text.substring(0, Math.min(text.length(), length));
            }
            case "length" -> String.valueOf(text.length());
            default -> null;
        };
    }

    private String canonical(JSONObject arguments, String observed) {
        return "textTransform|" + arguments.toJSONString() + "|" + observed;
    }
}
