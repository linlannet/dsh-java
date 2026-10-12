package net.linlan.dsh.domain.session.search.service;

import net.linlan.dsh.domain.session.search.adapter.port.ISessionSearchIndexPort;
import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;

import java.util.List;

/**
 * SessionSearchProvider 是会话搜索领域服务。
 * <p>委托 {@link ISessionSearchIndexPort} 执行搜索，返回匹配片段及高亮定位。
 */
public final class SessionSearchProvider {

    private final ISessionSearchIndexPort port;

    public SessionSearchProvider(ISessionSearchIndexPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port must not be null");
        }
        this.port = port;
    }

    /**
     * 搜索匹配的会话片段。
     *
     * @param query 查询关键词
     * @param limit 最大返回条数
     * @return 匹配的搜索命中列表
     */
    public List<SearchHit> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (limit <= 0) {
            return List.of();
        }
        return port.search(query, limit);
    }
}
