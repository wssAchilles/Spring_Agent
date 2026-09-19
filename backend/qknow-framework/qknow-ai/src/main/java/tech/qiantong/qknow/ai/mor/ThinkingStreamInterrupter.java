package tech.qiantong.qknow.ai.mor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * DeepSeek R1 思考流实时熵减与死循环中断哨兵 (定理 1.1)
 * 维护 W=64 环形滑动窗口，通过单步 O(1) 增量 Shannon 条件熵与 N-gram 重复率测度，毫秒级检测思考死循环并发出中断信号
 */
@Component
public class ThinkingStreamInterrupter {

    private static final Logger log = LoggerFactory.getLogger(ThinkingStreamInterrupter.class);

    public static final int WINDOW_SIZE = 64;
    public static final double ENTROPY_THRESHOLD = 1.80; // Shannon 熵死循环阈值 (bits)
    public static final double NGRAM_REPETITION_THRESHOLD = 0.60; // 4-gram 重复率阈值
    public static final int CONSECUTIVE_TRIGGER_LIMIT = 3; // 连续命中周期触发中断

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[\\s,;:.!?，。！？；：、“”\"'()\\[\\]{}]+");

    /**
     * 中断决策判定结果
     */
    public record InterruptionDecision(
            boolean shouldInterrupt,
            String reason,
            double entropy,
            double ngramRepetition,
            long tokensProcessed
    ) {
        public static InterruptionDecision continueNormal(double entropy, double ngramRepetition, long tokens) {
            return new InterruptionDecision(false, "NORMAL", entropy, ngramRepetition, tokens);
        }

        public static InterruptionDecision interrupt(String reason, double entropy, double ngramRepetition, long tokens) {
            return new InterruptionDecision(true, reason, entropy, ngramRepetition, tokens);
        }
    }

    /**
     * 单个会话或流的滑动分析上下文
     */
    public static class SessionStreamContext {
        private final String[] ringBuffer = new String[WINDOW_SIZE];
        private int head = 0;
        private int count = 0;
        private long totalTokens = 0;
        private int consecutiveTriggers = 0;
        private final Map<String, Integer> freqMap = new HashMap<>();

        public synchronized void addToken(String token) {
            if (token == null || token.isBlank()) {
                return;
            }
            String normToken = token.trim().toLowerCase();
            totalTokens++;

            if (count == WINDOW_SIZE) {
                // 移出旧词元
                String oldToken = ringBuffer[head];
                int oldFreq = freqMap.getOrDefault(oldToken, 1);
                if (oldFreq <= 1) {
                    freqMap.remove(oldToken);
                } else {
                    freqMap.put(oldToken, oldFreq - 1);
                }
            } else {
                count++;
            }

            // 放入新词元
            ringBuffer[head] = normToken;
            freqMap.put(normToken, freqMap.getOrDefault(normToken, 0) + 1);
            head = (head + 1) % WINDOW_SIZE;
        }

        public synchronized double calculateEntropy() {
            if (count == 0) {
                return 0.0;
            }
            double entropy = 0.0;
            double n = count;
            for (int freq : freqMap.values()) {
                double p = freq / n;
                if (p > 0.0) {
                    entropy -= p * (Math.log(p) / Math.log(2.0));
                }
            }
            return entropy;
        }

        public synchronized double calculate4GramRepetition() {
            if (count < 8) {
                return 0.0;
            }
            int n = count;
            int numGrams = n - 3;
            if (numGrams <= 0) {
                return 0.0;
            }

            // 线性展开当前环形缓冲中的词元
            String[] tokens = new String[n];
            int start = (count == WINDOW_SIZE) ? head : 0;
            for (int i = 0; i < n; i++) {
                tokens[i] = ringBuffer[(start + i) % WINDOW_SIZE];
            }

            Set<String> uniqueGrams = new HashSet<>();
            for (int i = 0; i <= n - 4; i++) {
                String gram = tokens[i] + " " + tokens[i + 1] + " " + tokens[i + 2] + " " + tokens[i + 3];
                uniqueGrams.add(gram);
            }

            return 1.0 - ((double) uniqueGrams.size() / (double) numGrams);
        }

        public synchronized int getCount() {
            return count;
        }

        public synchronized long getTotalTokens() {
            return totalTokens;
        }

        public synchronized int incrementTrigger() {
            return ++consecutiveTriggers;
        }

        public synchronized void resetTrigger() {
            consecutiveTriggers = 0;
        }

        public synchronized int getConsecutiveTriggers() {
            return consecutiveTriggers;
        }
    }

    /**
     * 创建新的流分析上下文
     */
    public SessionStreamContext createContext() {
        return new SessionStreamContext();
    }

    /**
     * 针对新输入的文本 Chunk 执行滑动分析与死循环判定
     *
     * @param context 会话流上下文
     * @param chunk   当前 SSE 吐出的 reasoning_content 片段
     * @return 中断决策
     */
    public InterruptionDecision feedAndEvaluate(SessionStreamContext context, String chunk) {
        if (context == null || chunk == null || chunk.isEmpty()) {
            return InterruptionDecision.continueNormal(0.0, 0.0, 0);
        }

        String[] tokens = TOKEN_SPLIT_PATTERN.split(chunk);
        for (String t : tokens) {
            if (!t.isBlank()) {
                context.addToken(t);
            }
        }

        // 词元不足窗口一半时不作截断判定
        if (context.getCount() < WINDOW_SIZE / 2) {
            return InterruptionDecision.continueNormal(0.0, 0.0, context.getTotalTokens());
        }

        double entropy = context.calculateEntropy();
        double ngramRepetition = context.calculate4GramRepetition();

        // 判定死循环条件：信息熵超低 (<= 1.8) 且 4-gram 重复率高 (>= 0.60)
        boolean isLoopCondition = (entropy <= ENTROPY_THRESHOLD && ngramRepetition >= NGRAM_REPETITION_THRESHOLD);

        // 强特征判定：极高重复率 (>= 0.80)
        boolean isSevereLoop = ngramRepetition >= 0.80;

        if (isLoopCondition || isSevereLoop) {
            int triggers = context.incrementTrigger();
            int requiredLimit = isSevereLoop ? 2 : CONSECUTIVE_TRIGGER_LIMIT;

            if (triggers >= requiredLimit) {
                String reason = String.format("思考死循环检测触发 (Shannon 熵: %.2f bits, 4-gram 重复率: %.2f%%, 连续命中: %d 次)",
                        entropy, ngramRepetition * 100, triggers);
                log.warn("[ThinkingInterrupter] {}", reason);
                return InterruptionDecision.interrupt(reason, entropy, ngramRepetition, context.getTotalTokens());
            }
        } else {
            context.resetTrigger();
        }

        return InterruptionDecision.continueNormal(entropy, ngramRepetition, context.getTotalTokens());
    }
}
