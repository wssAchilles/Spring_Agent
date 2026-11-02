package tech.qiantong.qknow.hermes.agent.harness.ppc.controlled;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** One frozen controlled task with retrieval, tool and history provenance kept separate from its rubric. */
public record ControlledFixture(String id, Split split, Category category, String question,
                                String retrievalQuery, RetrievalHit retrieval, ToolFixture tool,
                                List<HistoryTurn> history, Rubric rubric) {
    public enum Split { BOOTSTRAP, HOLDOUT }
    public enum Category { PROMPT_ONLY, RAG, TOOL, HISTORY, COMPOSITE }

    public ControlledFixture {
        if (id == null || !id.matches("[BH]0[1-5]")) {
            throw new IllegalArgumentException("controlled task ID is required");
        }
        Objects.requireNonNull(split, "split");
        Objects.requireNonNull(category, "category");
        if (!id.startsWith(split == Split.BOOTSTRAP ? "B" : "H") || question == null || question.isBlank()) {
            throw new IllegalArgumentException("task split or question is invalid");
        }
        history = List.copyOf(history);
        Objects.requireNonNull(rubric, "rubric");
        boolean requiresRag = category == Category.RAG || category == Category.COMPOSITE;
        boolean requiresTool = category == Category.TOOL || category == Category.COMPOSITE;
        if (requiresRag != (retrieval != null) || requiresTool != (tool != null)
                || requiresRag != (retrievalQuery != null && !retrievalQuery.isBlank())
                || (category == Category.HISTORY ? history.size() != 4 : !history.isEmpty())) {
            throw new IllegalArgumentException("controlled task capability provenance is incomplete");
        }
    }

    public record RetrievalHit(String documentId, String segmentId, String body, double rank) {
        public RetrievalHit {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(segmentId, "segmentId");
            Objects.requireNonNull(body, "body");
            if (body.isBlank() || !Double.isFinite(rank) || rank <= 0) {
                throw new IllegalArgumentException("invalid actual retrieval hit");
            }
        }
        public String bodySha256() { return sha256(body); }
    }

    public record ToolFixture(String resultId, String name, String description,
                              String inputSchema, String expectedArgs, String outputValue) {
        public ToolFixture {
            Objects.requireNonNull(resultId, "resultId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(inputSchema, "inputSchema");
            Objects.requireNonNull(expectedArgs, "expectedArgs");
            Objects.requireNonNull(outputValue, "outputValue");
        }
    }

    public record HistoryTurn(String sourceId, String role, String content) {
        public HistoryTurn {
            Objects.requireNonNull(sourceId, "sourceId");
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw new IllegalArgumentException("invalid history role");
            }
            Objects.requireNonNull(content, "content");
        }
    }

    public record Rubric(List<String> answerAllOf, List<String> forbid, String goldSegmentId,
                         String requiredToolResultId, List<String> requiredHistorySources) {
        public Rubric {
            answerAllOf = List.copyOf(answerAllOf);
            forbid = List.copyOf(forbid);
            requiredHistorySources = List.copyOf(requiredHistorySources);
            if (answerAllOf.isEmpty()) {
                throw new IllegalArgumentException("nonempty answer rubric is required");
            }
        }
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
