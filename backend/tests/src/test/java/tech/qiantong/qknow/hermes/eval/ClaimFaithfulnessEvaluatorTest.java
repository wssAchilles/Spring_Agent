package tech.qiantong.qknow.hermes.eval;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

public class ClaimFaithfulnessEvaluatorTest {

    @Test
    public void testCompletelyFaithfulText() {
        ClaimFaithfulnessEvaluator evaluator = new ClaimFaithfulnessEvaluator() {
            @Override
            protected List<String> decomposeIntoClaims(String text) {
                return Arrays.asList("天空是蓝色的", "草地是绿色的");
            }

            @Override
            protected boolean verifyClaimFaithfulness(String claim, String context) {
                return true;
            }
        };

        String generatedText = "天空是蓝色的，草地是绿色的。";
        String context = "今天天气很好，天空是蓝色的，草地是绿色的。";

        ClaimFaithfulnessEvaluator.EvaluationResult result = evaluator.evaluate(generatedText, context);

        assertEquals(2, result.getTotalClaims(), "拆解出的 Claim 数量应为 2");
        assertEquals(2, result.getFaithfulClaims(), "忠实的 Claim 数量应为 2");
        assertEquals(1.0, result.getFaithfulnessScore(), 0.001, "完全忠实文本的得分应为 1.0");
    }

    @Test
    public void testHallucinatedText() {
        ClaimFaithfulnessEvaluator evaluator = new ClaimFaithfulnessEvaluator() {
            @Override
            protected List<String> decomposeIntoClaims(String text) {
                return Arrays.asList("天空是蓝色的", "草地是紫色的", "太阳是绿色的");
            }

            @Override
            protected boolean verifyClaimFaithfulness(String claim, String context) {
                if ("天空是蓝色的".equals(claim)) {
                    return true;
                }
                return false;
            }
        };

        String generatedText = "天空是蓝色的，草地是紫色的，并且太阳是绿色的。";
        String context = "天空是蓝色的，草地是绿色的，太阳是黄色的。";

        ClaimFaithfulnessEvaluator.EvaluationResult result = evaluator.evaluate(generatedText, context);

        assertEquals(3, result.getTotalClaims(), "拆解出的 Claim 数量应为 3");
        assertEquals(1, result.getFaithfulClaims(), "忠实的 Claim 数量应为 1");
        assertEquals(1.0 / 3.0, result.getFaithfulnessScore(), 0.001, "含幻觉文本的得分应为 1/3");
    }
}
