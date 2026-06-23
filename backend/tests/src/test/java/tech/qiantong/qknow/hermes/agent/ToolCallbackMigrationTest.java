package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.support.GenericApplicationContext;
import tech.qiantong.qknow.hermes.tool.function.SearchKnowledgeTool;
import tech.qiantong.qknow.hermes.tool.function.query.knowledgeQuery;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolCallbackMigrationTest {

    @Test
    void functionToolCallbackBuilderCreatesValidCallback() {
        BiFunction<knowledgeQuery, ToolContext, String> fn = (query, ctx) -> "result for " + query.getQuery();

        FunctionToolCallback<knowledgeQuery, String> callback = FunctionToolCallback
                .builder("testTool", fn)
                .inputType(knowledgeQuery.class)
                .description("A test tool")
                .build();

        assertNotNull(callback);
        assertNotNull(callback.getToolDefinition());
        assertEquals("testTool", callback.getToolDefinition().name());
        assertEquals("A test tool", callback.getToolDefinition().description());
    }

    @Test
    void functionToolCallbackCallInvokesFunction() {
        BiFunction<knowledgeQuery, ToolContext, String> fn = (query, ctx) -> "知识库检索结果: " + query.getQuery();

        FunctionToolCallback<knowledgeQuery, String> callback = FunctionToolCallback
                .builder("knowledgeBase1", fn)
                .inputType(knowledgeQuery.class)
                .description("检索知识库")
                .build();

        String result = callback.call("{\"query\": \"测试查询\"}");

        assertNotNull(result);
        assertTrue(result.contains("测试查询"));
    }

    @Test
    void functionToolCallbackWithToolContext() {
        BiFunction<knowledgeQuery, ToolContext, String> fn = (query, ctx) -> {
            if (ctx != null) {
                return "context-aware result for " + query.getQuery();
            }
            return "no context result for " + query.getQuery();
        };

        FunctionToolCallback<knowledgeQuery, String> callback = FunctionToolCallback
                .builder("ctxTool", fn)
                .inputType(knowledgeQuery.class)
                .description("Context-aware tool")
                .build();

        String resultWithCtx = callback.call("{\"query\": \"test\"}", new ToolContext(java.util.Collections.emptyMap()));
        assertTrue(resultWithCtx.contains("context-aware"));

        String resultWithoutCtx = callback.call("{\"query\": \"test\"}");
        assertTrue(resultWithoutCtx.contains("no context"));
    }

    @Test
    void searchKnowledgeToolWorksWithFunctionToolCallback() {
        SearchKnowledgeTool tool = new SearchKnowledgeTool("kb1", "测试知识库", "预检索的内容");

        FunctionToolCallback<knowledgeQuery, String> callback = FunctionToolCallback
                .builder("knowledgeBase_kb1", tool)
                .inputType(knowledgeQuery.class)
                .description("查询测试知识库")
                .build();

        String result = callback.call("{\"query\": \"什么是AI\"}");

        assertNotNull(result);
        assertEquals("\"预检索的内容\"", result);
    }

    @Test
    void searchKnowledgeToolReturnsFallbackWhenEmpty() {
        SearchKnowledgeTool tool = new SearchKnowledgeTool("kb2", "空知识库", "");

        FunctionToolCallback<knowledgeQuery, String> callback = FunctionToolCallback
                .builder("knowledgeBase_kb2", tool)
                .inputType(knowledgeQuery.class)
                .description("查询空知识库")
                .build();

        String result = callback.call("{\"query\": \"不存在的内容\"}");

        assertNotNull(result);
        assertTrue(result.contains("没有找到"));
    }

    @Test
    void lambdaResolverResolvesToolCallbackBeans() {
        ToolCallback mockCallback = mock(ToolCallback.class);

        GenericApplicationContext context = mock(GenericApplicationContext.class);
        when(context.containsBean("myTool")).thenReturn(true);
        when(context.getBean("myTool")).thenReturn(mockCallback);

        ToolCallbackResolver resolver = name -> {
            if (context.containsBean(name)) {
                Object bean = context.getBean(name);
                if (bean instanceof ToolCallback tc) {
                    return tc;
                }
            }
            return null;
        };

        ToolCallback resolved = resolver.resolve("myTool");
        assertNotNull(resolved);
        assertSame(mockCallback, resolved);
    }

    @Test
    void lambdaResolverReturnsNullForMissingBean() {
        GenericApplicationContext context = mock(GenericApplicationContext.class);
        when(context.containsBean("nonExistent")).thenReturn(false);

        ToolCallbackResolver resolver = name -> {
            if (context.containsBean(name)) {
                Object bean = context.getBean(name);
                if (bean instanceof ToolCallback tc) {
                    return tc;
                }
            }
            return null;
        };

        ToolCallback resolved = resolver.resolve("nonExistent");
        assertNull(resolved);
    }

    @Test
    void lambdaResolverReturnsNullForNonToolCallbackBean() {
        GenericApplicationContext context = mock(GenericApplicationContext.class);
        when(context.containsBean("notATool")).thenReturn(true);
        when(context.getBean("notATool")).thenReturn("just a string");

        ToolCallbackResolver resolver = name -> {
            if (context.containsBean(name)) {
                Object bean = context.getBean(name);
                if (bean instanceof ToolCallback tc) {
                    return tc;
                }
            }
            return null;
        };

        ToolCallback resolved = resolver.resolve("notATool");
        assertNull(resolved);
    }

    @Test
    void multipleToolCallbacksCreatedIndependently() {
        SearchKnowledgeTool tool1 = new SearchKnowledgeTool("kb1", "知识库A", "内容A");
        SearchKnowledgeTool tool2 = new SearchKnowledgeTool("kb2", "知识库B", "内容B");

        FunctionToolCallback<knowledgeQuery, String> cb1 = FunctionToolCallback
                .builder("knowledgeBase_kb1", tool1)
                .inputType(knowledgeQuery.class)
                .description("查询知识库A")
                .build();

        FunctionToolCallback<knowledgeQuery, String> cb2 = FunctionToolCallback
                .builder("knowledgeBase_kb2", tool2)
                .inputType(knowledgeQuery.class)
                .description("查询知识库B")
                .build();

        assertEquals("knowledgeBase_kb1", cb1.getToolDefinition().name());
        assertEquals("knowledgeBase_kb2", cb2.getToolDefinition().name());

        String result1 = cb1.call("{\"query\": \"q\"}");
        String result2 = cb2.call("{\"query\": \"q\"}");

        assertEquals("\"内容A\"", result1);
        assertEquals("\"内容B\"", result2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void toolCallbackIsInstanceOfToolCallbackInterface() {
        FunctionToolCallback<knowledgeQuery, String> callback = (FunctionToolCallback<knowledgeQuery, String>) (FunctionToolCallback<?, ?>) FunctionToolCallback
                .builder("test", (knowledgeQuery q, ToolContext ctx) -> "ok")
                .inputType(knowledgeQuery.class)
                .description("test")
                .build();

        assertInstanceOf(ToolCallback.class, callback);
    }
}
