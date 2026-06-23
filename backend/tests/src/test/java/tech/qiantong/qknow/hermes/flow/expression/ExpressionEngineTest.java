package tech.qiantong.qknow.hermes.flow.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionEngineTest {

    private SpelExpressionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SpelExpressionEngine();
    }

    @Nested
    @DisplayName("基本比较操作")
    class BasicComparisons {

        @Test
        @DisplayName("简单相等: {{ status }} == 'active'")
        void simpleEquality() {
            Map<String, Object> context = Map.of("status", "active");
            assertTrue(engine.evaluate("{{ status }} == 'active'", context));
        }

        @Test
        @DisplayName("简单不等: {{ status }} != 'inactive'")
        void simpleInequality() {
            Map<String, Object> context = Map.of("status", "active");
            assertTrue(engine.evaluate("{{ status }} != 'inactive'", context));
        }

        @Test
        @DisplayName("数值比较: {{ score }} >= 0.8")
        void numericGreaterThanOrEqual() {
            Map<String, Object> context = Map.of("score", 0.9);
            assertTrue(engine.evaluate("{{ score }} >= 0.8", context));
        }

        @Test
        @DisplayName("数值比较: {{ score }} < 0.5")
        void numericLessThan() {
            Map<String, Object> context = Map.of("score", 0.3);
            assertTrue(engine.evaluate("{{ score }} < 0.5", context));
        }

        @Test
        @DisplayName("数值大于: {{ a }} > 10")
        void numericGreaterThan() {
            Map<String, Object> context = Map.of("a", 15);
            assertTrue(engine.evaluate("{{ a }} > 10", context));
        }

        @Test
        @DisplayName("数值小于等于: {{ a }} <= 10")
        void numericLessThanOrEqual() {
            Map<String, Object> context = Map.of("a", 10);
            assertTrue(engine.evaluate("{{ a }} <= 10", context));
        }

        @Test
        @DisplayName("相等比较返回 false")
        void equalityReturnsFalse() {
            Map<String, Object> context = Map.of("status", "inactive");
            assertFalse(engine.evaluate("{{ status }} == 'active'", context));
        }
    }

    @Nested
    @DisplayName("逻辑操作")
    class LogicalOperations {

        @Test
        @DisplayName("逻辑 AND: {{ a }} > 0 && {{ b }} < 10")
        void logicalAnd() {
            Map<String, Object> context = Map.of("a", 5, "b", 3);
            assertTrue(engine.evaluate("{{ a }} > 0 && {{ b }} < 10", context));
        }

        @Test
        @DisplayName("逻辑 AND 返回 false")
        void logicalAndFalse() {
            Map<String, Object> context = Map.of("a", -1, "b", 3);
            assertFalse(engine.evaluate("{{ a }} > 0 && {{ b }} < 10", context));
        }

        @Test
        @DisplayName("逻辑 OR: {{ a }} > 100 || {{ b }} < 10")
        void logicalOr() {
            Map<String, Object> context = Map.of("a", 5, "b", 3);
            assertTrue(engine.evaluate("{{ a }} > 100 || {{ b }} < 10", context));
        }

        @Test
        @DisplayName("逻辑 OR 两端都 false")
        void logicalOrBothFalse() {
            Map<String, Object> context = Map.of("a", 5, "b", 30);
            assertFalse(engine.evaluate("{{ a }} > 100 || {{ b }} > 100", context));
        }

        @Test
        @DisplayName("逻辑 NOT: !{{ flag }}")
        void logicalNot() {
            Map<String, Object> context = Map.of("flag", false);
            assertTrue(engine.evaluate("!{{ flag }}", context));
        }
    }

    @Nested
    @DisplayName("字符串操作")
    class StringOperations {

        @Test
        @DisplayName("contains: {{ name }} contains 'test'")
        void stringContains() {
            Map<String, Object> context = Map.of("name", "test-user");
            assertTrue(engine.evaluate("{{ name }} contains 'test'", context));
        }

        @Test
        @DisplayName("contains 返回 false")
        void stringContainsFalse() {
            Map<String, Object> context = Map.of("name", "production");
            assertFalse(engine.evaluate("{{ name }} contains 'test'", context));
        }

        @Test
        @DisplayName("startsWith: {{ name }} startsWith 'test'")
        void stringStartsWith() {
            Map<String, Object> context = Map.of("name", "test-user");
            assertTrue(engine.evaluate("{{ name }} startsWith 'test'", context));
        }

        @Test
        @DisplayName("matches: {{ email }} matches '.*@.*'")
        void stringMatches() {
            Map<String, Object> context = Map.of("email", "user@example.com");
            assertTrue(engine.evaluate("{{ email }} matches '.*@.*'", context));
        }
    }

    @Nested
    @DisplayName("变量处理")
    class VariableHandling {

        @Test
        @DisplayName("缺失变量返回 false，不抛异常")
        void missingVariableReturnsFalse() {
            Map<String, Object> context = Map.of("other", "value");
            assertDoesNotThrow(() -> {
                boolean result = engine.evaluate("{{ missing }} == 'x'", context);
                assertFalse(result);
            });
        }

        @Test
        @DisplayName("null 上文返回 false")
        void nullContextReturnsFalse() {
            assertFalse(engine.evaluate("{{ x }} == 'y'", null));
        }

        @Test
        @DisplayName("空表达式返回 false")
        void emptyExpressionReturnsFalse() {
            assertFalse(engine.evaluate("", Map.of()));
            assertFalse(engine.evaluate(null, Map.of()));
        }

        @Test
        @DisplayName("多变量表达式")
        void multipleVariables() {
            Map<String, Object> context = new HashMap<>();
            context.put("type", "image");
            context.put("size", 1024);
            assertTrue(engine.evaluate("{{ type }} == 'image' && {{ size }} > 512", context));
        }
    }

    @Nested
    @DisplayName("安全限制")
    class SecurityTests {

        @Test
        @DisplayName("阻止 Runtime.exec 攻击")
        void blockRuntimeExec() {
            Map<String, Object> context = Map.of();
            assertFalse(engine.evaluate(
                    "T(Runtime).getRuntime().exec('rm -rf /')", context));
        }

        @Test
        @DisplayName("阻止 ProcessBuilder 攻击")
        void blockProcessBuilder() {
            Map<String, Object> context = Map.of();
            assertFalse(engine.evaluate(
                    "T(ProcessBuilder).start()", context));
        }

        @Test
        @DisplayName("阻止 System.exit 攻击")
        void blockSystemExit() {
            Map<String, Object> context = Map.of();
            assertFalse(engine.evaluate(
                    "T(System).exit(0)", context));
        }

        @Test
        @DisplayName("阻止 Class.forName 攻击")
        void blockClassForName() {
            Map<String, Object> context = Map.of();
            assertFalse(engine.evaluate(
                    "T(Class).forName('java.lang.Runtime')", context));
        }

        @Test
        @DisplayName("阻止完全限定名 Runtime 引用")
        void blockFullyQualifiedRuntime() {
            Map<String, Object> context = Map.of();
            assertFalse(engine.evaluate(
                    "T(java.lang.Runtime).getRuntime().exec('ls')", context));
        }
    }
}
