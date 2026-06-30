package tech.qiantong.qknow.hermes.agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmartRoutingTest {

    private boolean invokeNeedsToolCalling(String question, List<String> toolNames) throws Exception {
        // Use the compiled class directly instead of reflection
        AgentOrchestrator orchestrator = new AgentOrchestrator(null, null, null);
        Method method = AgentOrchestrator.class.getDeclaredMethod("needsToolCalling", String.class, List.class);
        method.setAccessible(true);
        return (boolean) method.invoke(orchestrator, question, toolNames);
    }

    @Test
    void weatherQueryTriggersRouting() throws Exception {
        assertTrue(invokeNeedsToolCalling("北京今天天气怎么样", Arrays.asList("weather_query")));
        assertTrue(invokeNeedsToolCalling("上海气温多少度", Arrays.asList("weather_query")));
        assertTrue(invokeNeedsToolCalling("明天会下雨吗", Arrays.asList("weather_query")));
        assertTrue(invokeNeedsToolCalling("What's the weather in Tokyo", Arrays.asList("weather_query")));
    }

    @Test
    void searchQueryTriggersRouting() throws Exception {
        assertTrue(invokeNeedsToolCalling("搜索一下2026年AI趋势", Arrays.asList("web_search")));
        assertTrue(invokeNeedsToolCalling("搜一下最新的新闻", Arrays.asList("web_search")));
        assertTrue(invokeNeedsToolCalling("查一下苹果公司信息", Arrays.asList("web_search")));
        assertTrue(invokeNeedsToolCalling("百度一下Python教程", Arrays.asList("web_search")));
    }

    @Test
    void httpQueryTriggersRouting() throws Exception {
        assertTrue(invokeNeedsToolCalling("访问 https://example.com 获取内容", Arrays.asList("http_request")));
        assertTrue(invokeNeedsToolCalling("调用api获取数据", Arrays.asList("http_request")));
        assertTrue(invokeNeedsToolCalling("请求这个url", Arrays.asList("http_request")));
    }

    @Test
    void textTransformTriggersRouting() throws Exception {
        assertTrue(invokeNeedsToolCalling("把hello转成大写", Arrays.asList("text_transform")));
        assertTrue(invokeNeedsToolCalling("uppercase this text", Arrays.asList("text_transform")));
        assertTrue(invokeNeedsToolCalling("截取前10个字符", Arrays.asList("text_transform")));
        assertTrue(invokeNeedsToolCalling("获取这段文本的长度", Arrays.asList("text_transform")));
    }

    @Test
    void knowledgeBaseQueryDoesNotTriggerRouting() throws Exception {
        assertFalse(invokeNeedsToolCalling("第一天我干了什么", Arrays.asList("weather_query", "web_search")));
        assertFalse(invokeNeedsToolCalling("项目架构是什么", Arrays.asList("weather_query", "web_search")));
        assertFalse(invokeNeedsToolCalling("Bug修复流程是什么", Arrays.asList("weather_query", "web_search")));
    }

    @Test
    void emptyQuestionDoesNotTrigger() throws Exception {
        assertFalse(invokeNeedsToolCalling("", Arrays.asList("weather_query")));
    }

    @Test
    void emptyToolListDoesNotTrigger() throws Exception {
        assertFalse(invokeNeedsToolCalling("北京天气怎么样", new ArrayList<>()));
    }

    @Test
    void missingToolDoesNotTrigger() throws Exception {
        assertFalse(invokeNeedsToolCalling("北京天气怎么样", Arrays.asList("web_search")));
        assertFalse(invokeNeedsToolCalling("搜索AI趋势", Arrays.asList("weather_query")));
    }
}
