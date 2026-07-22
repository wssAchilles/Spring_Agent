package tech.qiantong.qknow.hermes.eval;

public enum EvaluationError {
    TIMEOUT("EVAL_TIMEOUT", "Metric evaluation timed out"),
    INTERRUPTED("EVALUATION_INTERRUPTED", "Evaluation interrupted"),
    MODEL_CALL_FAILED("MODEL_CALL_FAILED", "Metric model call failed"),
    GENERATION_FAILED("GENERATION_FAILED", "Answer generation failed"),
    PARSE_FAILED("PARSE_FAILED", "Metric response could not be parsed"),
    ITEM_EVALUATION_FAILED("ITEM_EVALUATION_FAILED", "Evaluation item failed"),
    CLAIM_EXTRACTION_MODEL_FAILED("CLAIM_EXTRACTION_MODEL_FAILED", "Claim extraction model call failed"),
    CLAIM_EXTRACTION_PARSE_FAILED("CLAIM_EXTRACTION_PARSE_FAILED", "Claim extraction response could not be parsed"),
    NO_CLAIMS_EXTRACTED("NO_CLAIMS_EXTRACTED", "Claim extraction returned no claims"),
    ENTAILMENT_MODEL_FAILED("ENTAILMENT_MODEL_FAILED", "Entailment model call failed"),
    ENTAILMENT_PARSE_FAILED("ENTAILMENT_PARSE_FAILED", "Entailment response could not be parsed");

    private final String code;
    private final String reason;

    EvaluationError(String code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    public String getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }
}
