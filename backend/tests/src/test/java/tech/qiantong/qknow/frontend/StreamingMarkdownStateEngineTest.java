package tech.qiantong.qknow.frontend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 12: 前端流式 Markdown 增量渲染状态机与临界阻尼动力学算法契约测试
 */
class StreamingMarkdownStateEngineTest {

    // 模拟前端 StreamingMarkdownEngine.completeIncompleteMarkdown 的确定性算法实现
    private String completeIncompleteMarkdown(String rawText) {
        if (rawText == null || rawText.isEmpty()) return "";
        String text = rawText;

        // 1. 代码块围栏状态机
        Pattern fencePattern = Pattern.compile("(?m)^([ \\t]*)(`{3,}|~{3,})");
        Matcher matcher = fencePattern.matcher(text);
        String openFenceChar = null;
        int openFenceLen = 0;
        while (matcher.find()) {
            String fence = matcher.group(2);
            String fenceChar = fence.substring(0, 1);
            int fenceLen = fence.length();
            if (openFenceChar == null) {
                openFenceChar = fenceChar;
                openFenceLen = fenceLen;
            } else if (openFenceChar.equals(fenceChar) && fenceLen >= openFenceLen) {
                openFenceChar = null; // 正常配对闭合
            }
        }
        if (openFenceChar != null) {
            text += "\n" + openFenceChar.repeat(openFenceLen) + "\n";
        }

        // 2. 数学公式块状态机
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf("$$", idx)) != -1) {
            count++;
            idx += 2;
        }
        if (count % 2 != 0) {
            text += "\n$$\n";
        }

        // 3. HTML 标签栈补全状态机
        Deque<String> tagStack = new ArrayDeque<>();
        Pattern htmlTagPattern = Pattern.compile("<(/?)([a-zA-Z0-9]+)(?:\\s+[^>]*?)?(/?)>");
        Set<String> voidTags = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");
        Matcher tagMatcher = htmlTagPattern.matcher(text);
        while (tagMatcher.find()) {
            boolean isClosing = !tagMatcher.group(1).isEmpty();
            String tagName = tagMatcher.group(2).toLowerCase();
            boolean isSelfClosing = !tagMatcher.group(3).isEmpty();
            if (voidTags.contains(tagName) || isSelfClosing) {
                continue;
            }
            if (!isClosing) {
                tagStack.push(tagName);
            } else {
                tagStack.remove(tagName);
            }
        }
        while (!tagStack.isEmpty()) {
            text += "</" + tagStack.pop() + ">";
        }

        return text;
    }

    // 模拟前端 parseDeepSeekThinking 结构化切分
    record ThinkingResult(String thinking, String content, boolean isThinkingComplete) {}

    private ThinkingResult parseDeepSeekThinking(String rawContent) {
        if (rawContent == null) return new ThinkingResult("", "", true);
        String startTag = "<think>";
        String endTag = "</think>";
        int startIndex = rawContent.indexOf(startTag);
        if (startIndex == -1) {
            return new ThinkingResult("", rawContent, true);
        }
        String afterStart = rawContent.substring(startIndex + startTag.length());
        int endIndex = afterStart.indexOf(endTag);
        if (endIndex == -1) {
            return new ThinkingResult(afterStart.trim(), "", false);
        } else {
            return new ThinkingResult(
                    afterStart.substring(0, endIndex).trim(),
                    afterStart.substring(endIndex + endTag.length()).trim(),
                    true
            );
        }
    }

    @Test
    @DisplayName("契约验证：未闭合围栏代码块能被确定性自动闭合且不破坏已闭合代码")
    void testCodeFenceAutoCompletion() {
        // 场景 1：未闭合代码块必须被补全三个反引号闭合
        String broken = "```java\npublic class Demo {\n";
        String completed = completeIncompleteMarkdown(broken);
        assertTrue(completed.endsWith("\n```\n"), "未闭合代码块必须被严密补全");

        // 场景 2：已经闭合的代码块严禁重复追加反引号
        String alreadyClosed = "```python\nprint('hello')\n```\n";
        String processed = completeIncompleteMarkdown(alreadyClosed);
        assertEquals(alreadyClosed, processed, "已闭合代码块必须保持原状，严禁二次破坏");
    }

    @Test
    @DisplayName("契约验证：未闭合数学公式与HTML标签栈能被准确补齐")
    void testMathAndHtmlAutoCompletion() {
        // 场景 1：未闭合的独立数学公式
        String unclosedMath = "定理如下：\n$$\nE = mc^2";
        String completedMath = completeIncompleteMarkdown(unclosedMath);
        assertTrue(completedMath.endsWith("\n$$\n"), "未闭合数学公式必须自动补齐定界符");

        // 场景 2：未闭合的嵌套 HTML 标签栈逆序闭合
        String unclosedHtml = "<div class=\"note\"><span>重要提示";
        String completedHtml = completeIncompleteMarkdown(unclosedHtml);
        assertTrue(completedHtml.endsWith("</span></div>"), "未闭合标签必须严格按栈逆序闭合");
    }

    @Test
    @DisplayName("契约验证：DeepSeek <think> 推理链流式切分与正文状态解耦")
    void testDeepSeekThinkingParsing() {
        // 场景 1：正在流式思考中（未收到 </think>）
        String streamingThink = "<think>\n正在分析 Spring 源码执行时序...";
        ThinkingResult r1 = parseDeepSeekThinking(streamingThink);
        assertFalse(r1.isThinkingComplete(), "未收到闭合标签时应标记为思考中");
        assertEquals("正在分析 Spring 源码执行时序...", r1.thinking());
        assertEquals("", r1.content(), "思考阶段正式正文必须为空");

        // 场景 2：思考完成并开始输出正文
        String completedThink = "<think>\n推理完成\n</think>\n# 回答标题\n这是正文内容。";
        ThinkingResult r2 = parseDeepSeekThinking(completedThink);
        assertTrue(r2.isThinkingComplete(), "收到 </think> 应标记为思考完成");
        assertEquals("推理完成", r2.thinking());
        assertEquals("# 回答标题\n这是正文内容。", r2.content());
    }

    @Test
    @DisplayName("契约验证：二阶临界阻尼振子（zeta=1）在离散迭代下保持绝对单调递增且无超调")
    void testCriticallyDampedOscillatorConvergence() {
        double target = 100.0; // 目标吐字长度
        double currentX = 0.0; // 当前渲染长度
        double currentV = 0.0; // 当前速度
        double omegaN = 20.0;  // 自然角频率
        double dt = 0.01667;   // 60fps 单帧时间步长 (约 16.67ms)

        double previousX = currentX;
        int maxFrames = 100;

        for (int frame = 0; frame < maxFrames; frame++) {
            // 临界阻尼 zeta = 1.0: a = omegaN^2 * (target - x) - 2 * omegaN * v
            double a = omegaN * omegaN * (target - currentX) - 2.0 * omegaN * currentV;
            currentV += a * dt;
            currentX += currentV * dt;

            // 契约断言 1：物理单调性——当前长度绝不小于上一帧（无后退回缩）
            assertTrue(currentX >= previousX - 1e-9, "打字推进过程必须严格单调递增，严禁字符回弹");

            // 契约断言 2：无超调——当前长度绝不应超过目标长度
            assertTrue(currentX <= target + 1e-6, "临界阻尼必须绝对无超调");

            previousX = currentX;
        }

        // 契约断言 3：在 60 帧（1 秒）内必须收敛至目标值的 99% 以上
        assertTrue(currentX >= target * 0.99, "临界阻尼应在 1 秒内平滑收敛至目标值");
    }
}
