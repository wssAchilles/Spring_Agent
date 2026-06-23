package tech.qiantong.qknow.hermes.tool.function;

import java.util.List;

/**
 * Search provider interface for web search functionality.
 * Implementations wrap different search APIs (DuckDuckGo, Serper, etc.).
 */
public interface SearchProvider {

    /**
     * @return provider name (e.g. "duckduckgo", "serper")
     */
    String getName();

    /**
     * Execute a web search query.
     *
     * @param query      search keywords
     * @param maxResults maximum number of results to return
     * @return list of search results, never null
     */
    List<WebSearchToolFunction.Response.SearchResult> search(String query, int maxResults);
}
