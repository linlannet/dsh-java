package net.linlan.dsh.domain.session.search.service;

import net.linlan.dsh.domain.session.search.adapter.port.ISessionSearchIndexPort;
import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionSearchProvider 搜索领域服务")
class SessionSearchProviderTest {

    @Test
    @DisplayName("空查询返回空列表")
    void blankQueryReturnsEmpty() {
        var provider = new SessionSearchProvider(new NoopPort());
        assertTrue(provider.search("", 10).isEmpty());
        assertTrue(provider.search("  ", 10).isEmpty());
        assertTrue(provider.search(null, 10).isEmpty());
    }

    @Test
    @DisplayName("limit 非正返回空列表")
    void nonPositiveLimitReturnsEmpty() {
        var provider = new SessionSearchProvider(new NoopPort());
        assertTrue(provider.search("test", 0).isEmpty());
        assertTrue(provider.search("test", -1).isEmpty());
    }

    @Test
    @DisplayName("正常查询委托给端口")
    void normalQueryDelegatesToPort() {
        var port = new StubPort();
        var provider = new SessionSearchProvider(port);
        var hits = provider.search("hello", 5);
        assertEquals(1, hits.size());
        assertEquals("hello", port.lastQuery);
        assertEquals(5, port.lastLimit);
    }

    @Test
    @DisplayName("port 为 null 抛异常")
    void nullPortRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SessionSearchProvider(null));
    }

    static final class NoopPort implements ISessionSearchIndexPort {
        @Override public void index(String sessionId, long seq, String content) {}
        @Override public List<SearchHit> search(String query, int limit) { return List.of(); }
        @Override public void clear() {}
        @Override public void remove(String sessionId) {}
        @Override public void removeOlderThan(java.time.Instant threshold) {}
        @Override public void removeOldest(int count) {}
        @Override public int documentCount() { return 0; }
    }

    static final class StubPort implements ISessionSearchIndexPort {
        String lastQuery;
        int lastLimit;

        @Override public void index(String sessionId, long seq, String content) {}

        @Override
        public List<SearchHit> search(String query, int limit) {
            lastQuery = query;
            lastLimit = limit;
            return List.of(SearchHit.of("sess-1", 1, "snippet", 1.0f));
        }

        @Override public void clear() {}
        @Override public void remove(String sessionId) {}
        @Override public void removeOlderThan(java.time.Instant threshold) {}
        @Override public void removeOldest(int count) {}
        @Override public int documentCount() { return 0; }
    }
}
