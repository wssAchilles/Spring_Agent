package tech.qiantong.qknow.module.kmc.service.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CragRetrievalEvaluation {

    public enum Label {
        CORRECT,
        INCORRECT,
        AMBIGUOUS
    }

    private Label label;

    private double confidence;

    private String reason;

    private String rewrittenQuery;

    public boolean isIncorrect() {
        return label == Label.INCORRECT;
    }
}
