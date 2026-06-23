package tech.qiantong.qknow.hermes.tool.function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSearchToolTest {

    @Mock
    private SearchProvider searchProvider;

    private WebSearchToolFunction toolFunction;

    @BeforeEach
    void setUp() {
        toolFunction = new WebSearchToolFunction(searchProvider);
    }

    @Test
    void searchReturnsResults() {
        WebSearchToolFunction.Response.SearchResult result = new WebSearchToolFunction.Response.SearchResult();
        result.setTitle("Test Title");
        result.setSnippet("Test Snippet");
        result.setUrl("https://example.com");

        when(searchProvider.search("test query", 5)).thenReturn(List.of(result));
        when(searchProvider.getName()).thenReturn("duckduckgo");

        WebSearchToolFunction.Request request = new WebSearchToolFunction.Request();
        request.setQuery("test query");

        WebSearchToolFunction.Response response = toolFunction.apply(request);

        assertNull(response.getError());
        assertEquals("test query", response.getQuery());
        assertEquals(1, response.getResults().size());
        assertEquals("Test Title", response.getResults().get(0).getTitle());
    }

    @Test
    void providerTimeoutReturnsEmptyResults() {
        when(searchProvider.search(eq("timeout query"), anyInt()))
                .thenThrow(new RuntimeException("Connection timed out"));

        WebSearchToolFunction.Request request = new WebSearchToolFunction.Request();
        request.setQuery("timeout query");

        WebSearchToolFunction.Response response = toolFunction.apply(request);

        assertNotNull(response.getError());
        assertTrue(response.getError().contains("搜索失败"));
        verify(searchProvider).search("timeout query", 5);
    }

    @Test
    void providerSwitchViaConfig() {
        SearchProvider serperProvider = mock(SearchProvider.class);
        when(serperProvider.getName()).thenReturn("serper");
        when(serperProvider.search("q", 5)).thenReturn(Collections.emptyList());

        WebSearchToolFunction serperTool = new WebSearchToolFunction(serperProvider);

        WebSearchToolFunction.Request request = new WebSearchToolFunction.Request();
        request.setQuery("q");

        WebSearchToolFunction.Response response = serperTool.apply(request);

        assertNull(response.getError());
        assertEquals("q", response.getQuery());
        assertTrue(response.getResults().isEmpty());
        verify(serperProvider).search("q", 5);
    }

    @Test
    void emptyQueryStillCallsProvider() {
        when(searchProvider.search("", 5)).thenReturn(Collections.emptyList());

        WebSearchToolFunction.Request request = new WebSearchToolFunction.Request();
        request.setQuery("");

        WebSearchToolFunction.Response response = toolFunction.apply(request);

        assertNull(response.getError());
        assertTrue(response.getResults().isEmpty());
        verify(searchProvider).search("", 5);
    }
}
