package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.util.Locale;

public record ExperienceDomain(long botId, long knowledgeBaseId, String taskCategory) {
    private static final String UNSCOPED_CATEGORY = "__unscoped_replay__";

    public ExperienceDomain {
        if (taskCategory == null || taskCategory.isBlank()) {
            throw new IllegalArgumentException("taskCategory is required");
        }
        boolean unscoped = botId == 0 && knowledgeBaseId == 0
                && UNSCOPED_CATEGORY.equals(taskCategory);
        if (!unscoped && (botId <= 0 || knowledgeBaseId <= 0
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

    public boolean isUnscopedReplay() {
        return botId == 0 && knowledgeBaseId == 0 && UNSCOPED_CATEGORY.equals(taskCategory);
    }

    public String toSlug() {
        if (isUnscopedReplay()) {
            return "unscoped_replay";
        }
        String category = taskCategory.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_");
        return "bot_" + botId + "__kb_" + knowledgeBaseId + "__cat_" + category;
    }
}
