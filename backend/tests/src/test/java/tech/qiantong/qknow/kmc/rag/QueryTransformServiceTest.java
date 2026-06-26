package tech.qiantong.qknow.kmc.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import tech.qiantong.qknow.module.kmc.service.rag.QueryTransformService;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryTransformServiceTest {

    @Mock
    private ChatClient chatClient;

    private Object chatClientService;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private QueryTransformService.QueryTransformConfig config;
    private QueryTransformService service;

    @BeforeEach
    void setUp() throws Exception {
        config = new QueryTransformService.QueryTransformConfig();
        config.setEnabled(true);
        config.setStrategy("rewrite");
        config.setPlatform("OpenAI");
        config.setBaseUrl("https://api.openai.com");
        config.setApiKey("test-key");
        config.setModelName("gpt-4o-mini");

        Class<?> chatClientServiceType = Class.forName("tech.qiantong.qknow.ai.service.IChatClientService");
        chatClientService = Proxy.newProxyInstance(
                chatClientServiceType.getClassLoader(),
                new Class<?>[]{chatClientServiceType},
                (proxy, method, args) -> "getChatClient".equals(method.getName()) ? chatClient : null);
        service = (QueryTransformService) QueryTransformService.class
                .getConstructor(chatClientServiceType, QueryTransformService.QueryTransformConfig.class)
                .newInstance(chatClientService, config);
    }

    @Test
    void rewriteQueryReturnsRewrittenText() {
        stubChatClient();
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("What is the capital city of Denmark?");

        String result = service.rewriteQuery("Denmark capital?");

        assertEquals("What is the capital city of Denmark?", result);
        verify(chatClient).prompt();
    }

    @Test
    void compressQueryWithHistoryReturnsStandalone() {
        stubChatClient();
        List<Message> history = new ArrayList<>();
        history.add(new UserMessage("What is Denmark's capital?"));
        history.add(new AssistantMessage("Copenhagen"));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("What is Denmark's second largest city?");

        String result = service.compressQuery("What is its second largest city?", history);

        assertEquals("What is Denmark's second largest city?", result);
        verify(chatClient).prompt();
    }

    @Test
    void disabledConfigReturnsOriginalQuery() {
        config.setEnabled(false);

        String result = service.rewriteQuery("original query");

        assertEquals("original query", result);
        verifyNoInteractions(chatClient);
    }

    @Test
    void emptyHistoryReturnsOriginalQuery() {
        String result = service.compressQuery("standalone query", new ArrayList<>());

        assertEquals("standalone query", result);
        verifyNoInteractions(chatClient);
    }

    @Test
    void noneStrategyReturnsOriginalQuery() {
        config.setStrategy("none");

        String result = service.rewriteQuery("original query");

        assertEquals("original query", result);
        verifyNoInteractions(chatClient);
    }

    @Test
    void hydeSkipsNumericQueries() {
        config.setStrategy("hyde");

        String result = service.rewriteQuery("Day12 的收入是多少？");

        assertEquals("Day12 的收入是多少？", result);
        verifyNoInteractions(chatClient);
    }

    @Test
    void hydeReturnsHypotheticalDocumentForTextQuery() {
        config.setStrategy("hyde");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Copenhagen is the capital and largest city of Denmark.");

        String result = service.rewriteQuery("Denmark capital?");

        assertEquals("Copenhagen is the capital and largest city of Denmark.", result);
    }

    @Test
    void expandQueriesIncludesOriginalAndVariants() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("[\"capital of Denmark\", \"Denmark main city\"]");

        List<String> result = service.expandQueries("Denmark capital?", 2);

        assertEquals(List.of("Denmark capital?", "capital of Denmark", "Denmark main city"), result);
    }

    private void stubChatClient() {
        // The proxy created in setUp returns chatClient for the service lookup.
    }
}
