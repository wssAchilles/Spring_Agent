package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public record ExperienceDomain(long botId, long knowledgeBaseId, String taskCategory) {
    private static final String UNSCOPED_CATEGORY = "__unscoped_replay__";

    public ExperienceDomain {
        if (taskCategory == null || taskCategory.isBlank()) {
            throw new IllegalArgumentException("taskCategory is required");
        }
        boolean unscoped = botId == 0 && knowledgeBaseId == 0
                && UNSCOPED_CATEGORY.equals(taskCategory);
        boolean cognitiveNoKnowledgeBase = knowledgeBaseId == 0
                && taskCategory.startsWith("cognitive_");
        if (!unscoped && (botId <= 0 || (knowledgeBaseId <= 0 && !cognitiveNoKnowledgeBase)
                || UNSCOPED_CATEGORY.equals(taskCategory))) {
            throw new IllegalArgumentException("business domain identifiers must be positive");
        }
        if (!unscoped && taskCategory.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "").isEmpty()) {
            throw new IllegalArgumentException("taskCategory must contain a letter or digit");
        }
    }

    public static ExperienceDomain unscopedReplay() {
        return new ExperienceDomain(0, 0, UNSCOPED_CATEGORY);
    }

    public static ExperienceDomain cognitiveScope(long botId, List<String> knowledgeIds) {
        if (botId <= 0) {
            throw new IllegalArgumentException("botId must be positive");
        }
        List<String> ids = knowledgeIds == null ? List.of() : knowledgeIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim).distinct().sorted().toList();
        if (ids.size() == 1) {
            try {
                long id = Long.parseLong(ids.getFirst());
                if (id > 0) {
                    return new ExperienceDomain(botId, id, "cognitive_task");
                }
            } catch (NumberFormatException ignored) {
                // Non-numeric knowledge identifiers use the hashed scope below.
            }
        }
        String canonical = ids.isEmpty() ? "no_knowledge_base"
                : ids.stream().map(id -> id.length() + ":" + id).collect(Collectors.joining());
        return new ExperienceDomain(botId, 0, "cognitive_" + sha256(canonical).substring(0, 24));
    }

    public boolean isUnscopedReplay() {
        return botId == 0 && knowledgeBaseId == 0 && UNSCOPED_CATEGORY.equals(taskCategory);
    }

    public String toSlug() {
        if (isUnscopedReplay()) {
            return "unscoped_replay";
        }
        String category = taskCategory.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_");
        String knowledgeBase = knowledgeBaseId == 0 ? "none" : Long.toString(knowledgeBaseId);
        return "bot_" + botId + "__kb_" + knowledgeBase + "__cat_" + category;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
