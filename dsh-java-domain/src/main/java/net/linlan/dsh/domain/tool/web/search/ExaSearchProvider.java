package net.linlan.dsh.domain.tool.web.search;

import net.linlan.dsh.domain.tool.web.search.adapter.port.IWebSearchPort;
import net.linlan.dsh.domain.tool.web.search.model.valobj.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * ExaSearchProvider 表示通过 Exa API 进行Web搜索的领域提供方。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class ExaSearchProvider implements IWebSearchPort {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final Function<String, String> httpPost;

    /**
     * 处理Exa Search Provider。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param apiKey api Key参数。
     * @param httpPost http Post参数。
     * @return 方法执行结果。
     */
    public ExaSearchProvider(String apiKey, Function<String, String> httpPost) {
        this.apiKey = apiKey;
        this.httpPost = httpPost;
    }

    @Override
    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("query", query);
            body.put("numResults", Math.max(1, limit));
            body.put("contents", mapper.createObjectNode().put("text", true));
            String requestJson = mapper.writeValueAsString(body);
            String responseJson = httpPost.apply(requestJson);
            return parseResponse(responseJson);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 返回api Key。
     * @return 方法执行结果。
     */
    public String getApiKey() { return apiKey; }

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
