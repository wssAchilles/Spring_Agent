package tech.qiantong.qknow.hermes.cognitive.core.engine;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.experience.StrategyTaskType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CognitiveTaskClassifierTest {
    @Test
    void classifiesOnlyExplicitRequestsWithMatchingMutationTools() {
        assertEquals(StrategyTaskType.CANCELLATION,
                CognitiveTaskClassifier.classify("Please cancel my order", List.of("cancel_pending_order")));
        assertEquals(StrategyTaskType.UPDATE,
                CognitiveTaskClassifier.classify("请帮我修改收货地址", List.of("modify_pending_order_address")));
        assertEquals(StrategyTaskType.CANCELLATION,
                CognitiveTaskClassifier.classify("The order arrived damaged. Please cancel it.",
                        List.of("cancel_pending_order")));
        assertEquals(StrategyTaskType.CANCELLATION,
                CognitiveTaskClassifier.classify("订单已经发出。请帮我取消订单。", List.of("cancel_pending_order")));
        assertEquals(StrategyTaskType.EXCHANGE, CognitiveTaskClassifier.classify(
                "Please exchange this item", List.of("exchange_delivered_order_items")));
        assertEquals(StrategyTaskType.RETURN_REFUND, CognitiveTaskClassifier.classify(
                "Refund this order", List.of("refund_order")));
        assertEquals(StrategyTaskType.GENERAL,
                CognitiveTaskClassifier.classify("How can I cancel my order?", List.of("cancel_pending_order")));
        assertEquals(StrategyTaskType.GENERAL,
                CognitiveTaskClassifier.classify("Tell me how to cancel my order", List.of("cancel_pending_order")));
        assertEquals(StrategyTaskType.GENERAL,
                CognitiveTaskClassifier.classify("Please cancel my order", List.of("get_order_details")));
        assertEquals(StrategyTaskType.GENERAL,
                CognitiveTaskClassifier.classify("Please return this item", List.of("get_return_policy")));
        assertEquals(StrategyTaskType.STATE_MUTATION, CognitiveTaskClassifier.classify(
                "Please cancel and return these items",
                List.of("cancel_pending_order", "return_delivered_order_items")));
    }

    @Test
    void classifiesObservedMutationToolsWithoutQuestionText() {
        assertEquals(StrategyTaskType.CANCELLATION,
                CognitiveTaskClassifier.classifyObservedTools(List.of("get_order_details", "cancel_pending_order")));
        assertEquals(StrategyTaskType.STATE_MUTATION,
                CognitiveTaskClassifier.classifyObservedTools(List.of(
                        "cancel_pending_order", "modify_pending_order_address")));
        assertEquals(StrategyTaskType.GENERAL,
                CognitiveTaskClassifier.classifyObservedTools(List.of("get_order_details")));
    }
}
