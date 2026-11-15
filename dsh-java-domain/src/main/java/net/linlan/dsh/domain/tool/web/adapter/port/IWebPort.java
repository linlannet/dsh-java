package net.linlan.dsh.domain.tool.web.adapter.port;

import java.util.List;

/**
 * IWebPort 定义IWeb的领域端口，由基础设施层适配。
 */
public interface IWebPort {

    SearchResult search(String query, int maxResults);

    FetchResult fetch(String url, int maxChars);

    /**
     * 使用显式凭据执行搜索，默认忽略凭据。
     *
     * @param query      搜索词
     * @param maxResults 最大结果数
     * @param credential 凭据值
     * @return 搜索结果
     */
    default SearchResult search(String query, int maxResults, String credential) {
        return search(query, maxResults);
    }

    /**
     * 使用显式凭据执行抓取，默认忽略凭据。
     *
     * @param url      目标 URL
     * @param maxChars 最大字符数
     * @param credential 凭据值
     * @return 抓取结果
     */
    default FetchResult fetch(String url, int maxChars, String credential) {
        return fetch(url, maxChars);
    }

    /**
     * SearchResult 定义当前模块中的record契约与职责。
     */
    record SearchResult(
            String content,
            List<WebSource> sources,
            boolean truncated
    ) {}

    /**
     * FetchResult 定义当前模块中的record契约与职责。
     */
    record FetchResult(
            String url,
            String content,
            String title,
            String mimeType
    ) {}

    /**
     * WebSource 定义当前模块中的record契约与职责。
     */
    record WebSource(
            String url,
            String title,
            String snippet,
            String publishedAt
    ) {}
}
