package tech.qiantong.qknow.hermes.agent.harness.experience;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record StrategyTemplate(
        String templateId,
        int version,
        String parentTemplateId,
        List<String> preconditions,
        List<String> planSkeleton,
        List<String> toolPolicy,
        List<String> reflectionRules,
        List<String> successCriteria,
        StrategyTaskType taskType,
        String fingerprint
) {
    public record Content(List<String> preconditions, List<String> planSkeleton,
                          List<String> toolPolicy, List<String> reflectionRules,
                          List<String> successCriteria) {
        public Content {
            preconditions = immutableNonBlank(preconditions, "preconditions");
            planSkeleton = immutableNonBlank(planSkeleton, "planSkeleton");
            toolPolicy = immutableNonBlank(toolPolicy, "toolPolicy");
            reflectionRules = immutableNonBlank(reflectionRules, "reflectionRules");
            successCriteria = immutableNonBlank(successCriteria, "successCriteria");
        }

        public static Content fromLesson(String lesson) {
            if (lesson == null || lesson.isBlank()) {
                throw new IllegalArgumentException("lesson is required to seed a strategy template");
            }
            return new Content(List.of("仅在当前请求与同域证据范围内应用"),
                    List.of("按已验证计划顺序执行并保留步骤结果"),
                    List.of("仅调用当前任务已提供的工具"),
                    List.of(lesson), List.of("独立验证通过，或达到可记录的执行反馈"));
        }
    }

    public StrategyTemplate {
        if (templateId == null || !templateId.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || version < 1) {
            throw new IllegalArgumentException("safe templateId and positive version are required");
        }
        if (parentTemplateId != null && parentTemplateId.isBlank()) {
            throw new IllegalArgumentException("parentTemplateId must be absent or nonblank");
        }
        preconditions = immutableNonBlank(preconditions, "preconditions");
        planSkeleton = immutableNonBlank(planSkeleton, "planSkeleton");
        toolPolicy = immutableNonBlank(toolPolicy, "toolPolicy");
        reflectionRules = immutableNonBlank(reflectionRules, "reflectionRules");
        successCriteria = immutableNonBlank(successCriteria, "successCriteria");
        taskType = taskType == null ? StrategyTaskType.UNCLASSIFIED : taskType;
        String expected = digest(version, parentTemplateId, preconditions, planSkeleton,
                toolPolicy, reflectionRules, successCriteria, taskType);
        String legacy = taskType == StrategyTaskType.UNCLASSIFIED
                ? digestLegacy(version, parentTemplateId, preconditions, planSkeleton,
                toolPolicy, reflectionRules, successCriteria) : null;
        if (fingerprint == null || !matches(fingerprint, expected)
                && (legacy == null || !matches(fingerprint, legacy))) {
            throw new IllegalArgumentException("template fingerprint does not match its content");
        }
    }

    public StrategyTemplate(String templateId, int version, String parentTemplateId,
                            List<String> preconditions, List<String> planSkeleton,
                            List<String> toolPolicy, List<String> reflectionRules,
                            List<String> successCriteria, String fingerprint) {
        this(templateId, version, parentTemplateId, preconditions, planSkeleton,
                toolPolicy, reflectionRules, successCriteria, StrategyTaskType.UNCLASSIFIED, fingerprint);
    }

    public static StrategyTemplate create(String templateId, int version, String parentTemplateId,
                                          List<String> preconditions, List<String> planSkeleton,
                                          List<String> toolPolicy, List<String> reflectionRules,
                                          List<String> successCriteria) {
        return create(templateId, version, parentTemplateId, preconditions, planSkeleton, toolPolicy,
                reflectionRules, successCriteria, StrategyTaskType.UNCLASSIFIED);
    }

    public static StrategyTemplate create(String templateId, int version, String parentTemplateId,
                                          List<String> preconditions, List<String> planSkeleton,
                                          List<String> toolPolicy, List<String> reflectionRules,
                                          List<String> successCriteria, StrategyTaskType taskType) {
        return new StrategyTemplate(templateId, version, parentTemplateId, preconditions, planSkeleton,
                toolPolicy, reflectionRules, successCriteria, taskType, digest(version, parentTemplateId,
                preconditions, planSkeleton, toolPolicy, reflectionRules, successCriteria, taskType));
    }

    public String renderGuidance() {
        return "策略模板 v" + version + " (" + templateId + ")\n"
                + "适用前提：" + String.join("；", preconditions) + "\n"
                + "计划骨架：" + String.join(" → ", planSkeleton) + "\n"
                + "工具策略：" + String.join("；", toolPolicy) + "\n"
                + "反思规则：" + String.join("；", reflectionRules) + "\n"
                + "成功标准：" + String.join("；", successCriteria);
    }

    public Content content() {
        return new Content(preconditions, planSkeleton, toolPolicy, reflectionRules, successCriteria);
    }

    private static List<String> immutableNonBlank(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> copy = List.copyOf(values);
        if (copy.isEmpty() || copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " must contain nonblank values");
        }
        return copy;
    }

    private static String digest(int version, String parentTemplateId, List<String> preconditions,
                                 List<String> planSkeleton, List<String> toolPolicy,
                                 List<String> reflectionRules, List<String> successCriteria,
                                 StrategyTaskType taskType) {
        String canonical = version + "|" + Objects.toString(parentTemplateId, "") + "|"
                + canonicalList(preconditions) + "|" + canonicalList(planSkeleton) + "|"
                + canonicalList(toolPolicy) + "|" + canonicalList(reflectionRules) + "|"
                + canonicalList(successCriteria) + "|" + taskType.name();
        return hash(canonical);
    }

    private static String digestLegacy(int version, String parentTemplateId, List<String> preconditions,
                                       List<String> planSkeleton, List<String> toolPolicy,
                                       List<String> reflectionRules, List<String> successCriteria) {
        String canonical = version + "|" + Objects.toString(parentTemplateId, "") + "|"
                + canonicalList(preconditions) + "|" + canonicalList(planSkeleton) + "|"
                + canonicalList(toolPolicy) + "|" + canonicalList(reflectionRules) + "|"
                + canonicalList(successCriteria);
        return hash(canonical);
    }

    private static boolean matches(String fingerprint, String expected) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                fingerprint.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String canonicalList(List<String> values) {
        return values.stream().map(value -> value.length() + ":" + value)
                .collect(java.util.stream.Collectors.joining());
    }
}
