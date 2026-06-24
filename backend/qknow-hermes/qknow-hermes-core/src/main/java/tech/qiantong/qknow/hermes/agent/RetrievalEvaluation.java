package tech.qiantong.qknow.hermes.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalEvaluation {

    public enum Label {
        CORRECT,
        INCORRECT,
        AMBIGUOUS
    }

    private Label label;

    private double confidence;

    private String reason;

    private String rewrittenQuery;

    public static RetrievalEvaluation correct(String reason) {
        return RetrievalEvaluation.builder()
                .label(Label.CORRECT)
                .confidence(1.0D)
                .reason(reason)
                .build();
    }

    public boolean isIncorrect() {
        return Label.INCORRECT.equals(label);
    }

    public boolean isAmbiguous() {
        return Label.AMBIGUOUS.equals(label);
    }
}
