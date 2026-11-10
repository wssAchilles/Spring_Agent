package tech.qiantong.qknow.hermes.cognitive.core.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestOutcomeProviderTest {
    private final HttpRequestOutcomeProvider provider = new HttpRequestOutcomeProvider();

    @Test
    void recordsExplicitTransportFailuresWithoutClaimingBusinessSuccess() {
        String arguments = "{\"url\":\"https://example.test/api\",\"method\":\"POST\"}";

        assertEquals(CognitiveToolOutcomeEvidence.Status.UNKNOWN,
                provider.evaluate(arguments, "{\"statusCode\":200,\"body\":\"ok\"}").status());
        assertEquals(CognitiveToolOutcomeEvidence.Status.REJECTED,
                provider.evaluate(arguments, "{\"statusCode\":503,\"body\":\"down\"}").status());
        assertEquals(CognitiveToolOutcomeEvidence.Status.REJECTED,
                provider.evaluate(arguments, "{\"error\":\"timeout\"}").status());
    }

    @Test
    void malformedResponseRemainsUnknown() {
        assertEquals(CognitiveToolOutcomeEvidence.Status.UNKNOWN,
                provider.evaluate("not-json", "not-json").status());
    }
}
