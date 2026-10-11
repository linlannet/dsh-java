package net.linlan.dsh.infrastructure.adapter.web;

import net.linlan.dsh.domain.tool.web.adapter.port.IWebPort;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地网页服务实现。
 * <p>
 * 执行流程：
 * 1. 使用 JDK 自带网络客户端发起网页请求；
 * 2. 抓取页面后提取可读文本；
 * 3. 搜索优先走配置源，未配置时回落到公开搜索页面解析。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class LocalWebService implements IWebPort {

    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int REQUEST_TIMEOUT_SEC = 30;

    private final HttpClient client;
    private final String searchApiBase;
    private final String searchApiKey;

    /**
     * 使用默认配置创建。
     * <p>
     * 执行流程：
     * 1. 不配置搜索 API；
     * 2. 仅保留抓取能力；
     * 3. 返回本地网页服务实例。
     */
    public LocalWebService() {
        this(null, null);
    }

    /**
     * 使用搜索服务配置创建。
     * <p>
     * 执行流程：
     * 1. 读取搜索服务地址和密钥；
     * 2. 初始化外部搜索能力；
     * 3. 返回本地网页服务实例。
     *
     * @param searchApiBase 搜索服务基础地址
     * @param searchApiKey 搜索服务密钥
     */
    public LocalWebService(String searchApiBase, String searchApiKey) {
        this.searchApiBase = searchApiBase;
        this.searchApiKey = searchApiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SEC))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public SearchResult search(String query, int maxResults) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return new SearchResult("Query must not be blank.", List.of(), false);
        }

        if (searchApiBase != null && !searchApiBase.isBlank()) {
            SearchResult configured = searchWithConfiguredEndpoint(normalizedQuery, maxResults);
            if (configured != null && (!configured.sources().isEmpty() || (configured.content() != null && !configured.content().isBlank()))) {
                return configured;
            }
        }

        SearchResult duckDuckGo = searchWithDuckDuckGo(normalizedQuery, maxResults);
        if (!duckDuckGo.sources().isEmpty() || (duckDuckGo.content() != null && !duckDuckGo.content().isBlank())) {
            return duckDuckGo;
        }

        return new SearchResult(
                "Web search returned no readable results for query: " + normalizedQuery,
                List.of(),
                false
        );
    }
    /**
     * fetch。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param url url参数。
     * @param maxChars max Chars参数。
     * @return 方法执行结果。
     */


    @Override
    public FetchResult fetch(String url, int maxChars) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("User-Agent", "dsh-java/1.0")
                    .header("Accept", "text/html, text/plain, application/json, */*")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String contentType = response.headers()
                    .firstValue("content-type")
                    .orElse("text/plain");

            String body = response.body();
            if (body == null) body = "";

            // 从 HTML 中提取标题
            String title = extractTitle(body);

            // 如果是 HTML，则去除标签以便阅读
            String readable;
            if (contentType.contains("text/html")) {
                readable = stripHtml(body);
            } else {
                readable = body;
            }

            if (readable.length() > maxChars) {
                readable = readable.substring(0, maxChars);
            }

            return new FetchResult(url, readable, title, contentType);
        } catch (Exception e) {
            return new FetchResult(url, "Fetch error: " + e.getMessage(), null, "text/plain");
        }
    }

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern STYLE_PATTERN = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern SEARCH_TITLE_PATTERN = Pattern.compile(
            "(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");

    private static final Pattern SEARCH_SNIPPET_PATTERN = Pattern.compile(
            "(?is)class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</");

    /**
     * 提取 Title。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param html html参数。
     * @return 方法执行结果。
     */
    private String extractTitle(String html) {
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * 处理strip Html。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param html html参数。
     * @return 方法执行结果。
     */
    private String stripHtml(String html) {
        // 移除脚本和样式块
        String s = SCRIPT_PATTERN.matcher(html).replaceAll("");
        s = STYLE_PATTERN.matcher(s).replaceAll("");
        // 移除所有标签
        s = TAG_PATTERN.matcher(s).replaceAll("");
        // 合并多余空白
        s = WHITESPACE_PATTERN.matcher(s).replaceAll(" ");
        return s.trim();
    }

    /**
     * 搜索 With Configured Endpoint。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param query query参数。
     * @param maxResults max Results参数。
     * @return 方法执行结果。
     */
    private SearchResult searchWithConfiguredEndpoint(String query, int maxResults) {
        try {
            URI uri = buildSearchUri(searchApiBase, query, maxResults);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("User-Agent", "dsh-java/1.0")
                    .header("Accept", "text/html, text/plain, application/json, */*");
            if (searchApiKey != null && !searchApiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + searchApiKey.trim());
            }

            HttpResponse<String> response = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
            String body = Optional.ofNullable(response.body()).orElse("");
            String contentType = response.headers().firstValue("content-type").orElse("text/plain");
            if (contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                return parseSearchPage(body, maxResults);
            }
            return new SearchResult(body, List.of(), false);
        } catch (Exception e) {
            return new SearchResult("Configured search failed: " + e.getMessage(), List.of(), false);
        }
    }

    /**
     * 搜索 With Duck Duck Go。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param query query参数。
     * @param maxResults max Results参数。
     * @return 方法执行结果。
     */
    private SearchResult searchWithDuckDuckGo(String query, int maxResults) {
        try {
            URI uri = URI.create("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SEC))
                    .header("User-Agent", "dsh-java/1.0")
                    .header("Accept", "text/html, text/plain, application/json, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = Optional.ofNullable(response.body()).orElse("");
            return parseSearchPage(body, maxResults);
        } catch (Exception e) {
            return new SearchResult("DuckDuckGo search failed: " + e.getMessage(), List.of(), false);
        }
    }

    /**
     * 解析 搜索 Page。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param html html参数。
     * @param maxResults max Results参数。
     * @return 方法执行结果。
     */
    private SearchResult parseSearchPage(String html, int maxResults) {
        List<IWebPort.WebSource> sources = new ArrayList<>();
        Matcher matcher = SEARCH_TITLE_PATTERN.matcher(html);
        while (matcher.find() && sources.size() < maxResults) {
            String url = decodeSearchUrl(matcher.group(1));
            String title = stripHtml(matcher.group(2));
            String snippet = extractSnippet(html, matcher.end());
            sources.add(new IWebPort.WebSource(url, title, snippet, null));
        }

        if (sources.isEmpty()) {
            String content = stripHtml(html);
            if (content.length() > 8000) {
                content = content.substring(0, 8000);
            }
            return new SearchResult(content, List.of(), false);
        }

        StringBuilder summary = new StringBuilder();
        for (IWebPort.WebSource source : sources) {
            summary.append("- [")
                    .append(source.title() != null ? source.title() : source.url())
                    .append("](")
                    .append(source.url())
                    .append(")");
            if (source.snippet() != null && !source.snippet().isBlank()) {
                summary.append(" — ").append(source.snippet());
            }
            summary.append('\n');
        }
        return new SearchResult(summary.toString().trim(), sources, false);
    }

    /**
     * 提取 Snippet。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param html html参数。
     * @param fromIndex from Index参数。
     * @return 方法执行结果。
     */
    private String extractSnippet(String html, int fromIndex) {
        int endIndex = Math.min(html.length(), fromIndex + 1200);
        String window = html.substring(fromIndex, endIndex);
        Matcher matcher = SEARCH_SNIPPET_PATTERN.matcher(window);
        if (matcher.find()) {
            return stripHtml(matcher.group(1));
        }
        return null;
    }

    /**
     * 构建Search Uri。
     */
    private URI buildSearchUri(String base, String query, int maxResults) {
        String normalizedBase = base.trim();
        if (normalizedBase.contains("{query}")) {
            return URI.create(normalizedBase.replace("{query}", URLEncoder.encode(query, StandardCharsets.UTF_8)));
        }
        String separator = normalizedBase.contains("?") ? "&" : "?";
        return URI.create(normalizedBase + separator + "q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&max_results=" + Math.max(1, maxResults));
    }

    /**
     * 处理decode 搜索 URL。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param url URL参数。
     * @return 方法执行结果。
     */
    private String decodeSearchUrl(String url) {
        try {
            if (url.startsWith("//")) {
                url = "https:" + url;
            }
            URI uri = URI.create(url);
            String query = uri.getQuery();
            if (query != null && query.contains("uddg=")) {
                for (String pair : query.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length == 2 && "uddg".equals(parts[0])) {
                        return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    }
                }
            }
            return url;
        } catch (Exception e) {
            return url;
        }
    }
}
