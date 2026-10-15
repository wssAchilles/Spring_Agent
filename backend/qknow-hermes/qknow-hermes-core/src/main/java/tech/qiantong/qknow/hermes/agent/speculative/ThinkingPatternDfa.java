package tech.qiantong.qknow.hermes.agent.speculative;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek 思考链模式识别确定性有限状态机 (Thinking Pattern DFA)
 * 实时从流式接收到的 reasoning_content 片段中提取潜在前向决策意图，异步触发预取
 */
public class ThinkingPatternDfa {

    public record PatternRule(String name, Pattern regexPattern, Consumer<String> onMatch) {}

    private final List<PatternRule> rules = new CopyOnWriteArrayList<>();
    private final StringBuilder slidingBuffer = new StringBuilder();
    private final Set<String> triggeredMatches = Collections.synchronizedSet(new HashSet<>());
    private static final int MAX_BUFFER_WINDOW = 4096;

    public ThinkingPatternDfa registerRule(String ruleName, String regexPattern, Consumer<String> onMatch) {
        Objects.requireNonNull(ruleName, "ruleName cannot be null");
        Objects.requireNonNull(regexPattern, "regexPattern cannot be null");
        Objects.requireNonNull(onMatch, "onMatch cannot be null");
        rules.add(new PatternRule(ruleName, Pattern.compile(regexPattern), onMatch));
        return this;
    }

    /**
     * 流式增量注入思考链字符块并即时执行状态转移与模式匹配
     */
    public synchronized int feedChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return 0;
        }

        slidingBuffer.append(chunk);
        if (slidingBuffer.length() > MAX_BUFFER_WINDOW) {
            slidingBuffer.delete(0, slidingBuffer.length() - MAX_BUFFER_WINDOW);
        }

        String currentText = slidingBuffer.toString();
        int matchedCount = 0;

        for (PatternRule rule : rules) {
            Matcher matcher = rule.regexPattern().matcher(currentText);
            while (matcher.find()) {
                String matchContent = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
                String matchKey = rule.name() + ":" + matchContent.trim().toLowerCase();
                if (triggeredMatches.add(matchKey)) {
                    matchedCount++;
                    try {
                        rule.onMatch().accept(matchContent.trim());
                    } catch (Exception ignored) {}
                }
            }
        }
        return matchedCount;
    }

    public synchronized void reset() {
        slidingBuffer.setLength(0);
        triggeredMatches.clear();
    }

    public Set<String> getTriggeredMatches() {
        return Collections.unmodifiableSet(new HashSet<>(triggeredMatches));
    }
}
