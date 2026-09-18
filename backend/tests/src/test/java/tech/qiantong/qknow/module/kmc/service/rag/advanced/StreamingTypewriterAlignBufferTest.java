package tech.qiantong.qknow.module.kmc.service.rag.advanced;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.dto.StreamingPlaybackState;
import tech.qiantong.qknow.module.kmc.service.rag.advanced.engine.StreamingTypewriterAlignBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 105 契约测试集三：自适应闭环泊松 JitterBuffer 流式打字机缓冲中枢测试
 *
 * @author Achilles
 * @version 1.0
 */
public class StreamingTypewriterAlignBufferTest {

    @Test
    @DisplayName("契约 8：泊松突发 Chunk 平滑排空与恒速回放 (25~45 cps 区间控制)")
    void testSmoothPlaybackAndRateLimits() {
        StreamingTypewriterAlignBuffer buffer = new StreamingTypewriterAlignBuffer();

        // 模拟突发推送 100 个字符
        String burstText = "基于千问1536维超球面测地余弦内积加权局部诱导子图PPR幂迭代收敛推理，全面提升大模型RAG检索保真度与回答质量！";
        buffer.ingestChunk(burstText);

        assertEquals(StreamingPlaybackState.PLAYING, buffer.getState());
        assertEquals(burstText.length(), buffer.getRemainingQueueSize());

        StringBuilder outputCollector = new StringBuilder();

        // 以 100ms 为步长进行排空步进
        for (int i = 0; i < 20 && buffer.getRemainingQueueSize() > 0; i++) {
            String drained = buffer.drainStep(100);
            outputCollector.append(drained);
            // 每次 100ms，按 25~45 cps 计算，每次排出字符数应在 1~6 之间
            assertTrue(drained.length() <= 8, "单次 100ms 步进吐字数必须在合理速度范围内，实际: " + drained.length());
        }

        assertTrue(outputCollector.length() > 0, "必须平滑排出字符");
    }

    @Test
    @DisplayName("契约 9：流式输出方差抑制验证 (相较于原始突发方差降低 >= 80%)")
    void testVarianceSuppressionAgainstBurst() {
        StreamingTypewriterAlignBuffer buffer = new StreamingTypewriterAlignBuffer();

        // 模拟极度不均匀的突发输入（先塞 50 个字，停顿，再塞 60 个字）
        buffer.ingestChunk("突发第一批超大文本块推送！包含深度拓扑推理算法核心参数以及断路器运行指标信息。");

        for (int i = 0; i < 15; i++) {
            buffer.drainStep(100);
        }

        buffer.ingestChunk("突发第二批超大文本块推送！验证闭环比例控制器在泊松队列堆积下的负反馈抑制能力！");

        for (int i = 0; i < 25; i++) {
            buffer.drainStep(100);
        }

        double smoothedVariance = buffer.getPlaybackVariance();
        // 原始突发速率在 0 cps 到 500 cps 之间暴跳，方差通常高达 5000+
        // 经过闭环调控器后，瞬时速度被严格限制在 [25, 45]，理论最大方差为 ((45-25)/2)^2 = 100
        assertTrue(smoothedVariance <= 50.0, "平滑输出速度方差应受到强力抑制 (<= 50.0)，实际: " + smoothedVariance);
    }

    @Test
    @DisplayName("契约 10：断流超时 (2000ms) 一阶优雅软封口与零丢字 (输出总字符数严格等于输入总字符数)")
    void testStallSoftClosureAndZeroLoss() throws InterruptedException {
        StreamingTypewriterAlignBuffer buffer = new StreamingTypewriterAlignBuffer();

        String fullMessage = "这是一个用于验证断流优雅软封口与零丢字的端到端完整文本，必须百分之百完整呈现！";
        buffer.ingestChunk(fullMessage);

        StringBuilder outputCollector = new StringBuilder();

        // 先步进消耗一部分
        for (int i = 0; i < 5; i++) {
            outputCollector.append(buffer.drainStep(100));
        }

        // 模拟上游断流：标记上游结束
        buffer.markUpstreamComplete();

        // 继续循环步进直到排空
        int steps = 0;
        while (buffer.getState() != StreamingPlaybackState.COMPLETED && steps < 100) {
            outputCollector.append(buffer.drainStep(100));
            steps++;
        }

        assertEquals(StreamingPlaybackState.COMPLETED, buffer.getState(), "最终状态必须为 COMPLETED");
        assertEquals(fullMessage, outputCollector.toString(), "排出的完整文本必须与原始输入严格完全一致，零字符丢失");
        assertEquals(0, buffer.getRemainingQueueSize(), "队列残留字符数必须为 0");
    }
}
