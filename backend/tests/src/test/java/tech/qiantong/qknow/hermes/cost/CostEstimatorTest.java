package tech.qiantong.qknow.hermes.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CostEstimatorTest {

    private CostEstimator costEstimator;
    private TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        costEstimator = new CostEstimator();
        tokenCounter = new TokenCounter();
    }

    @Test
    @DisplayName("DeepSeek 模型费用计算")
    void deepSeekCostCalculation() {
        double cost = costEstimator.estimate("deepseek-chat", 1_000_000, 1_000_000);
        assertEquals(3.0, cost, 0.001);
    }

    @Test
    @DisplayName("GPT-4o 模型费用计算")
    void gpt4oCostCalculation() {
        double cost = costEstimator.estimate("gpt-4o", 1_000_000, 1_000_000);
        assertEquals(72.0, cost, 0.001);
    }

    @Test
    @DisplayName("未知模型使用默认价格")
    void unknownModelUsesDefaultPrice() {
        double cost = costEstimator.estimate("unknown-model", 1_000_000, 1_000_000);
        assertEquals(3.0, cost, 0.001);
    }

    @Test
    @DisplayName("零 token 费用为零")
    void zeroTokensReturnsZeroCost() {
        double cost = costEstimator.estimate("deepseek-chat", 0, 0);
        assertEquals(0.0, cost, 0.0001);
    }

    @Test
    @DisplayName("Token 聚合统计与费用估算")
    void tokenAggregationAndCostEstimation() {
        ChatResponseMetadata metadata1 = mock(ChatResponseMetadata.class);
        Usage usage1 = mock(Usage.class);
        doReturn(usage1).when(metadata1).getUsage();
        doReturn(500).when(usage1).getPromptTokens();
        doReturn(200).when(usage1).getCompletionTokens();
        ChatResponse response1 = mock(ChatResponse.class);
        when(response1.getMetadata()).thenReturn(metadata1);

        ChatResponseMetadata metadata2 = mock(ChatResponseMetadata.class);
        Usage usage2 = mock(Usage.class);
        doReturn(usage2).when(metadata2).getUsage();
        doReturn(1000).when(usage2).getPromptTokens();
        doReturn(500).when(usage2).getCompletionTokens();
        ChatResponse response2 = mock(ChatResponse.class);
        when(response2.getMetadata()).thenReturn(metadata2);

        tokenCounter.recordUsage("deepseek-chat", response1);
        tokenCounter.recordUsage("deepseek-chat", response2);

        TokenUsage total = tokenCounter.getTotal();
        assertEquals(1500, total.getPromptTokens());
        assertEquals(700, total.getCompletionTokens());
        assertEquals(2200, total.getTotalTokens());

        TokenUsage perModel = tokenCounter.getPerModel("deepseek-chat");
        assertEquals(1500, perModel.getPromptTokens());
        assertEquals(700, perModel.getCompletionTokens());

        double cost = costEstimator.estimate("deepseek-chat", perModel.getPromptTokens(), perModel.getCompletionTokens());
        assertEquals(0.0029, cost, 0.0001);
    }
}
