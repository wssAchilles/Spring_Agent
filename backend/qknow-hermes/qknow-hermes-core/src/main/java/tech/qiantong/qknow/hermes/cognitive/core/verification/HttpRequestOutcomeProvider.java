package tech.qiantong.qknow.hermes.cognitive.core.verification;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

/** Records explicit HTTP transport failures while preserving UNKNOWN for business-level success. */
@Component
public final class HttpRequestOutcomeProvider implements CognitiveToolOutcomeProvider {
    @Override
    public String toolName() {
        return "httpRequest";
    }

    @Override
    public CognitiveToolOutcomeEvidence evaluate(String argumentsJson, String toolResult) {
        try {
            JSONObject arguments = JSON.parseObject(argumentsJson);
            JSONObject response = JSON.parseObject(toolResult);
            if (arguments == null || response == null) {
                return CognitiveToolOutcomeEvidence.unknown("HTTP_RESULT_MALFORMED");
            }
            String error = response.getString("error");
            if (error != null && !error.isBlank()) {
                return rejected(arguments, "HTTP_REQUEST_ERROR", error);
            }
            Integer statusCode = response.getInteger("statusCode");
            if (statusCode == null) {
                return CognitiveToolOutcomeEvidence.unknown("HTTP_STATUS_UNAVAILABLE");
            }
            return statusCode >= 400
                    ? rejected(arguments, "HTTP_STATUS_REJECTED", String.valueOf(statusCode))
                    : CognitiveToolOutcomeEvidence.unknown("HTTP_BUSINESS_OUTCOME_UNVERIFIED");
        } catch (RuntimeException ignored) {
            return CognitiveToolOutcomeEvidence.unknown("HTTP_RESULT_UNPARSEABLE");
        }
    }

    private CognitiveToolOutcomeEvidence rejected(JSONObject arguments, String reasonCode, String detail) {
        return CognitiveToolOutcomeEvidence.fromCanonicalState(
                CognitiveToolOutcomeEvidence.Status.REJECTED, reasonCode,
                "httpRequest|" + arguments.toJSONString() + "|" + detail);
    }
}
