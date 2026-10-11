package net.linlan.dsh.domain.tool.web.adapter.port;

import java.util.List;

/**
 * IWebPort 定义IWeb的领域端口，由基础设施层适配。
 */
public interface IWebPort {

    SearchResult search(String query, int maxResults);

    FetchResult fetch(String url, int maxChars);

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
