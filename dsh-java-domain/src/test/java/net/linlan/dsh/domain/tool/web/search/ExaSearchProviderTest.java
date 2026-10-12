package net.linlan.dsh.domain.tool.web.search;

import net.linlan.dsh.domain.tool.web.search.model.valobj.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T2.032 ExaSearchProvider")
class ExaSearchProviderTest {

    @Test
    @DisplayName("search 返回解析后的结果列表")
    void searchReturnsResults() {
        String responseJson = "{\"results\":[" +
                "{\"title\":\"Java Docs\",\"url\":\"https://docs.oracle.com/java\",\"text\":\"Java is a programming language\"}," +
                "{\"title\":\"Spring\",\"url\":\"https://spring.io\",\"text\":\"Spring framework\"}" +
                "]}";
        Function<String, String> httpPost = body -> responseJson;
        ExaSearchProvider provider = new ExaSearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("java tutorial", 5);

        assertEquals(2, results.size());
        assertEquals("Java Docs", results.get(0).title());
        assertEquals("https://docs.oracle.com/java", results.get(0).url());
        assertEquals("Java is a programming language", results.get(0).snippet());
    }

    @Test
    @DisplayName("search 空查询返回空列表")
    void searchBlankQueryReturnsEmpty() {
        Function<String, String> httpPost = body -> "{\"results\":[]}";
        ExaSearchProvider provider = new ExaSearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("search null 查询返回空列表")
    void searchNullQueryReturnsEmpty() {
        Function<String, String> httpPost = body -> "{\"results\":[]}";
        ExaSearchProvider provider = new ExaSearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search(null, 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("search 空响应返回空列表")
    void searchEmptyResponseReturnsEmpty() {
        Function<String, String> httpPost = body -> "{\"results\":[]}";
        ExaSearchProvider provider = new ExaSearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("java", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("search HTTP 异常返回空列表")
    void searchHttpExceptionReturnsEmpty() {
        Function<String, String> httpPost = body -> {
            throw new RuntimeException("connection failed");
        };
        ExaSearchProvider provider = new ExaSearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("java", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("snippet 超过 200 字符时截断")
    void snippetTruncatedTo200Chars() {
        String longText = "a".repeat(300);
        String responseJson = "{\"results\":[{\"title\":\"T\",\"url\":\"https://x\",\"text\":\"" + longText + "\"}]}";
        Function<String, String> httpPost = body -> responseJson;
        ExaSearchProvider provider = new ExaSearchProvider("test-key", httpPost);

        List<SearchResult> results = provider.search("test", 1);

        assertEquals(1, results.size());
        assertEquals(200, results.get(0).snippet().length());
    }

    @Test
    @DisplayName("getApiKey 返回构造时传入的 key")
    void getApiKeyReturnsKey() {
        ExaSearchProvider provider = new ExaSearchProvider("my-key", body -> "{}");
        assertEquals("my-key", provider.getApiKey());
    }
}
