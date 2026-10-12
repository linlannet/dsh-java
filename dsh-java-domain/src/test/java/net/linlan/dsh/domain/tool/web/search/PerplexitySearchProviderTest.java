package net.linlan.dsh.domain.tool.web.search;

import net.linlan.dsh.domain.tool.web.search.model.valobj.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T2.032 PerplexitySearchProvider")
class PerplexitySearchProviderTest {

    @Test
    @DisplayName("search 返回解析后的引用列表")
    void searchReturnsCitations() {
        String responseJson = "{\"citations\":[" +
                "{\"title\":\"Answer 1\",\"url\":\"https://example.com/1\",\"snippet\":\"snippet 1\"}," +
                "{\"title\":\"Answer 2\",\"url\":\"https://example.com/2\",\"snippet\":\"snippet 2\"}" +
                "]}";
        Function<String, String> httpPost = body -> responseJson;
        PerplexitySearchProvider provider = new PerplexitySearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("what is java", 5);

        assertEquals(2, results.size());
        assertEquals("Answer 1", results.get(0).title());
        assertEquals("https://example.com/1", results.get(0).url());
        assertEquals("snippet 1", results.get(0).snippet());
    }

    @Test
    @DisplayName("search 空查询返回空列表")
    void searchBlankQueryReturnsEmpty() {
        Function<String, String> httpPost = body -> "{}";
        PerplexitySearchProvider provider = new PerplexitySearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("search null 查询返回空列表")
    void searchNullQueryReturnsEmpty() {
        Function<String, String> httpPost = body -> "{}";
        PerplexitySearchProvider provider = new PerplexitySearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search(null, 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("search 无引用时回退到 choices 内容")
    void searchFallsBackToChoicesContent() {
        String responseJson = "{\"choices\":[{\"message\":{\"content\":\"Java is a language\"}}]}";
        Function<String, String> httpPost = body -> responseJson;
        PerplexitySearchProvider provider = new PerplexitySearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("java", 5);

        assertEquals(1, results.size());
        assertEquals("Perplexity Answer", results.get(0).title());
        assertEquals("Java is a language", results.get(0).snippet());
    }

    @Test
    @DisplayName("search HTTP 异常返回空列表")
    void searchHttpExceptionReturnsEmpty() {
        Function<String, String> httpPost = body -> {
            throw new RuntimeException("connection failed");
        };
        PerplexitySearchProvider provider = new PerplexitySearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("java", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("getApiKey 返回构造时传入的 key")
    void getApiKeyReturnsKey() {
        PerplexitySearchProvider provider = new PerplexitySearchProvider("my-key", body -> "{}");
        assertEquals("my-key", provider.getApiKey());
    }
}
