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
 * 网页抓取工具。
 * <p>
 * 执行流程：
 * 1. 接收 URL；
 * 2. 抓取网页并提取可读内容；
 * 3. 返回给调用方继续分析。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class WebFetchTool implements ToolDefinition {

    private static final String NAME = "web_fetch";
    private static final String DESCRIPTION =
            "Fetch a URL and extract readable content as text or markdown. " +
            "Use this for reading web pages, API documentation, or downloading " +
            "text-based resources. Output is truncated to 200,000 characters.";
    private static final int DEFAULT_MAX_CHARS = 200_000;
    private static final long TIMEOUT_MS = 30_000;

    private final IWebPort webService;

    /**
     * 处理Web Fetch 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param webService Web Service参数。
     * @return 方法执行结果。
     */
    public WebFetchTool(IWebPort webService) {
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
                        "url", Map.of(
                                "type", "string",
                                "description", "The URL to fetch."
                        ),
                        "max_chars", Map.of(
                                "type", "integer",
                                "description", "Maximum characters to return. Default 200000."
                        )
                ),
                "required", List.of("url")
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
                        "url", Map.of("type", "string"),
                        "content", Map.of("type", "string"),
                        "title", Map.of("type", "string"),
                        "mime_type", Map.of("type", "string")
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
            String url = String.valueOf(argMap.get("url"));
            if (url == null || url.isBlank()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("url parameter is required", "MISSING_PARAM"));
            }

            int maxChars = argMap.get("max_chars") != null
                    ? ((Number) argMap.get("max_chars")).intValue()
                    : DEFAULT_MAX_CHARS;

            IWebPort.FetchResult result = webService.fetch(url, maxChars);

            StringBuilder sb = new StringBuilder();
            if (result.title() != null && !result.title().isEmpty()) {
                sb.append("# ").append(result.title()).append("\n\n");
            }
            sb.append("URL: ").append(result.url()).append("\n");
            if (result.mimeType() != null) {
                sb.append("Content-Type: ").append(result.mimeType()).append("\n");
            }
            sb.append("\n---\n\n");

            String content = result.content();
            if (content != null && content.length() > maxChars) {
                content = content.substring(0, maxChars)
                        + "\n... [truncated, " + content.length() + " chars total]";
            }
            sb.append(content != null ? content : "[empty response]");

            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("web_fetch failed: " + e.getMessage(), "EXECUTION_ERROR"));
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
}
