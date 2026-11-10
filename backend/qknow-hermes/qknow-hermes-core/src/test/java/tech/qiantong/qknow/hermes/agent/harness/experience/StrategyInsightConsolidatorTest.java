package tech.qiantong.qknow.hermes.agent.harness.experience;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyInsightConsolidatorTest {
    @Test
    void appliesBoundedAddEditAndRemoveOperationsDeterministically() {
        StrategyTemplate.Content base = StrategyTemplate.Content.fromLesson("先核对前置条件");
        StrategyTemplate.Content consolidated = StrategyInsightConsolidator.apply(base, List.of(
                StrategyInsightEdit.add("先核对前置条件"),
                StrategyInsightEdit.edit("先核对前置条件", "先核对前置条件并记录结果"),
                StrategyInsightEdit.add("执行后复核结果"),
                StrategyInsightEdit.remove("执行后复核结果")));

        assertEquals(1, consolidated.reflectionRules().stream()
                .filter(rule -> rule.equals("先核对前置条件并记录结果")).count());
        assertTrue(consolidated.reflectionRules().stream()
                .noneMatch(rule -> rule.equals("执行后复核结果")));
    }

    @Test
    void agreesWithoutDuplicatingAndCapsRules() {
        StrategyTemplate.Content base = StrategyTemplate.Content.fromLesson("基础规则");
        List<StrategyInsightEdit> edits = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> StrategyInsightEdit.add("规则-" + index)).toList();
        StrategyTemplate.Content consolidated = StrategyInsightConsolidator.apply(base,
                java.util.stream.Stream.concat(edits.stream(),
                        java.util.stream.Stream.of(StrategyInsightEdit.agree("规则-1"))).toList());

        assertEquals(12, consolidated.reflectionRules().size());
        assertEquals(1, consolidated.reflectionRules().stream()
                .filter(rule -> rule.equals("规则-1")).count());
    }
}
