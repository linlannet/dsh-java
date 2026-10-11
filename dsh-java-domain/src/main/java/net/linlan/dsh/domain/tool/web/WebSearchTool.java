package net.linlan.dsh.domain.tool.web;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.web.adapter.port.IWebPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 网页搜索工具。
 * <p>
 * 执行流程：
 * 1. 接收搜索词；
 * 2. 调用网页搜索服务获取结果；
 * 3. 将结果整理后返回给模型。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class WebSearchTool implements ToolDefinition {

    private static final String NAME = "web_search";
    private static final String DESCRIPTION =
            "Search the web for current information. Returns a list of sources " +
            "with titles, URLs, and snippets. Use this for finding up-to-date " +
            "information, documentation, or answers to factual questions.";
    private static final int DEFAULT_MAX_RESULTS = 8;
    private static final long TIMEOUT_MS = 30_000;

    private final IWebPort webService;

    /**
     * 处理Web 搜索 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param webService Web Service参数。
     * @return 方法执行结果。
     */
    public WebSearchTool(IWebPort webService) {
        this.webService = webService;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "The search query."
                        ),
                        "max_results", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results. Default 8."
                        )
                ),
                "required", List.of("query")
        );
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", true,
                "properties", Map.of(
                        "content", Map.of("type", "string"),
                        "sources", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "url", Map.of("type", "string"),
                                                "title", Map.of("type", "string"),
                                                "snippet", Map.of("type", "string")
                                        )
                                )
                        )
                )
        );
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        try {
            Map<String, Object> argMap = (Map<String, Object>) args;
            String query = String.valueOf(argMap.get("query"));
            if (query == null || query.isBlank()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("query parameter is required", "MISSING_PARAM"));
            }

            int maxResults = argMap.get("max_results") != null
                    ? ((Number) argMap.get("max_results")).intValue()
                    : DEFAULT_MAX_RESULTS;

            IWebPort.SearchResult result = webService.search(query, maxResults);

            StringBuilder sb = new StringBuilder();
            if (result.content() != null && !result.content().isEmpty()) {
                sb.append(result.content()).append("\n\n");
            }

            if (result.sources() != null && !result.sources().isEmpty()) {
                sb.append("Sources:\n");
                for (IWebPort.WebSource src : result.sources()) {
                    String label = (src.title() != null && !src.title().isEmpty())
                            ? src.title()
                            : hostname(src.url());
                    List<String> meta = new java.util.ArrayList<>();
                    if (src.snippet() != null && !src.snippet().isEmpty()) meta.add(src.snippet());
                    if (src.publishedAt() != null && !src.publishedAt().isEmpty()) meta.add("(" + src.publishedAt() + ")");
                    String suffix = meta.isEmpty() ? "" : " — " + String.join(" ", meta);
                    sb.append("- [").append(label).append("](").append(src.url()).append(")").append(suffix).append("\n");
                }
            } else if (result.content() == null || result.content().isEmpty()) {
                sb.append("No results found.");
            }

            if (result.truncated()) {
                sb.append("\n(Showing the first ").append(result.sources().size()).append(" sources. Refine the query for more.)");
            }
            sb.append("\n\nCite the relevant URLs above as markdown links in your answer.");

            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("web_search failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }
    /**
     * 返回 timeout Ms。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public long timeoutMs() { return TIMEOUT_MS; }

    /**
     * 处理hostname。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param url URL参数。
     * @return 方法执行结果。
     */
    private static String hostname(String url) {
        try {
            return new java.net.URI(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }
}
