package tech.qiantong.qknow.hermes.cognitive.core.engine;

import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTaskType;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public final class CognitiveTaskClassifier {
    private static final Pattern EXPLICIT_MUTATION_REQUEST = Pattern.compile(
            "(?iu)^\\s*(?:[^.!?。！？\\n]*[.!?。！？]\\s*)?(?:(?:(?:please|could you|can you|would you|will you|"
                    + "please help me|help me|i want you to|i need you to|i want to|i need to|"
                    + "i would like to|i'd like to)\\s+)?(?:please\\s+)?"
                    + "(?:cancel|exchange|return|refund|modify|update|change|replace|delete|remove|edit)\\b"
                    + "|(?:请(?:帮我)?|麻烦(?:帮我)?|帮我|替我|我要|我想|我需要|给我|把.{1,20})"
                    + "(?:取消|换货|退货|退款|修改|更改|更新|替换|删除|移除|更换|编辑))");
    private static final List<MutationToolFamily> TOOL_FAMILIES = List.of(
            new MutationToolFamily(Pattern.compile("(?iu)^(?:cancel)(?:_|[A-Z]|$)"),
                    Pattern.compile("(?iu)\\b(?:cancel)\\b|取消")),
            new MutationToolFamily(Pattern.compile("(?iu)^(?:exchange|replace)(?:_|[A-Z]|$)"),
                    Pattern.compile("(?iu)\\b(?:exchange|replace)\\b|换货|替换|更换")),
            new MutationToolFamily(Pattern.compile("(?iu)^(?:return|refund)(?:_|[A-Z]|$)"),
                    Pattern.compile("(?iu)\\b(?:return|refund)\\b|退货|退款")),
            new MutationToolFamily(Pattern.compile("(?iu)^(?:modify|update|change|edit)(?:_|[A-Z]|$)"),
                    Pattern.compile("(?iu)\\b(?:modify|update|change|edit)\\b|修改|更改|更新|编辑")),
            new MutationToolFamily(Pattern.compile("(?iu)^(?:delete|remove)(?:_|[A-Z]|$)"),
                    Pattern.compile("(?iu)\\b(?:delete|remove)\\b|删除|移除")));

    private CognitiveTaskClassifier() { }

    public static StrategyTaskType classify(String question, Collection<String> toolNames) {
        if (question == null || question.isBlank() || toolNames == null
                || !EXPLICIT_MUTATION_REQUEST.matcher(question).find()) {
            return StrategyTaskType.GENERAL;
        }
        return TOOL_FAMILIES.stream().anyMatch(family ->
                family.intent().matcher(question).find()
                        && toolNames.stream().filter(name -> name != null)
                        .anyMatch(name -> family.tool().matcher(name).find()))
                ? StrategyTaskType.STATE_MUTATION : StrategyTaskType.GENERAL;
    }

    private record MutationToolFamily(Pattern tool, Pattern intent) { }
}
