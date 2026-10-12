package net.linlan.dsh.infrastructure.adapter.web;

import net.linlan.dsh.domain.tool.web.search.adapter.port.IWebSearchPort;
import net.linlan.dsh.domain.tool.web.search.model.valobj.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ExaHttpSearch 表示用 HttpClient 调用 Exa API 的基础设施适配器。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class ExaHttpSearch implements IWebSearchPort {

    private static final String DEFAULT_ENDPOINT = "https://api.exa.ai/search";
    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int REQUEST_TIMEOUT_SEC = 30;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private final String endpoint;
    private final String apiKey;

    /**
     * 处理Exa Http Search。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param apiKey api Key参数。
     * @return 方法执行结果。
     */
    public ExaHttpSearch(String apiKey) {
        this(DEFAULT_ENDPOINT, apiKey);
    }

    /**
     * 处理Exa Http Search。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param endpoint endpoint参数。
     * @param apiKey api Key参数。
     * @return 方法执行结果。
     */
    public ExaHttpSearch(String endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
                .build();
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            String body = buildRequestBody(query, limit);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey != null ? apiKey : "")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response.body());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildRequestBody(String query, int limit) throws Exception {
        var root = mapper.createObjectNode();
        root.put("query", query);
        root.put("numResults", Math.max(1, limit));
        root.set("contents", mapper.createObjectNode().put("text", true));
        return mapper.writeValueAsString(root);
    }

    private List<SearchResult> parseResponse(String responseJson) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        if (responseJson == null || responseJson.isBlank()) {
            return results;
        }
        JsonNode root = mapper.readTree(responseJson);
        JsonNode data = root.path("results");
        if (data.isArray()) {
            for (JsonNode node : data) {
                String title = node.path("title").asText("");
                String url = node.path("url").asText("");
                String snippet = node.path("text").asText("");
                if (snippet.length() > 200) {
                    snippet = snippet.substring(0, 200);
                }
                results.add(new SearchResult(title, url, snippet));
            }
        }
        return results;
    }
}
