package net.linlan.dsh.domain.session.search.adapter.port;

import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ISessionSearchIndexPort 全文索引出站端口契约")
class ISessionSearchIndexPortTest {

    @Test
    @DisplayName("端口声明 index/search/clear/remove/removeOlderThan/removeOldest/documentCount/close 八个方法")
    void portDeclaresExpectedMethods() throws NoSuchMethodException {
        var index = ISessionSearchIndexPort.class.getMethod(
                "index", String.class, long.class, String.class);
        var search = ISessionSearchIndexPort.class.getMethod(
                "search", String.class, int.class);
        var clear = ISessionSearchIndexPort.class.getMethod("clear");
        var remove = ISessionSearchIndexPort.class.getMethod("remove", String.class);
        var removeOlderThan = ISessionSearchIndexPort.class.getMethod(
                "removeOlderThan", Instant.class);
        var removeOldest = ISessionSearchIndexPort.class.getMethod("removeOldest", int.class);
        var documentCount = ISessionSearchIndexPort.class.getMethod("documentCount");
        var close = ISessionSearchIndexPort.class.getMethod("close");

        assertEquals(void.class, index.getReturnType());
        assertEquals(List.class, search.getReturnType());
        assertEquals(void.class, clear.getReturnType());
        assertEquals(void.class, remove.getReturnType());
        assertEquals(void.class, removeOlderThan.getReturnType());
        assertEquals(void.class, removeOldest.getReturnType());
        assertEquals(int.class, documentCount.getReturnType());
        assertEquals(void.class, close.getReturnType());
    }

    @Test
    @DisplayName("端口继承 AutoCloseable")
    void extendsAutoCloseable() {
        assertTrue(java.lang.AutoCloseable.class.isAssignableFrom(ISessionSearchIndexPort.class),
                "ISessionSearchIndexPort 应继承 AutoCloseable");
    }

    @Test
    @DisplayName("端口自有方法数量恰为 8")
    void ownMethodCountIsEight() {
        var own = java.util.Arrays.stream(ISessionSearchIndexPort.class.getMethods())
                .filter(m -> m.getDeclaringClass() == ISessionSearchIndexPort.class)
                .toArray(Method[]::new);
        assertEquals(8, own.length,
                "ISessionSearchIndexPort 自身应声明 8 个方法");
    }

    @Test
    @DisplayName("匿名实现：index 后 search 可命中，clear 后清空")
    void anonymousImplIndexSearchClear() {
        var store = new ArrayList<SearchHit>();
        ISessionSearchIndexPort port = new ISessionSearchIndexPort() {
            @Override
            public void index(String sessionId, long seq, String content) {
                store.add(SearchHit.of(sessionId, seq, content, 1f));
            }
            @Override
            public List<SearchHit> search(String query, int limit) {
                return store.stream()
                        .filter(h -> h.snippet().contains(query))
                        .limit(limit)
                        .toList();
            }
            @Override
            public void clear() { store.clear(); }
            @Override
            public void remove(String sessionId) {
                store.removeIf(h -> h.sessionId().equals(sessionId));
            }
            @Override
            public void removeOlderThan(Instant threshold) {}
            @Override
            public void removeOldest(int count) {
                for (int i = 0; i < count && !store.isEmpty(); i++) store.remove(0);
            }
            @Override
            public int documentCount() { return store.size(); }
        };

        port.index("s1", 1, "hello world");
        port.index("s1", 2, "hello dsh");
        var hits = port.search("hello", 10);
        assertEquals(2, hits.size());

        var one = port.search("world", 10);
        assertEquals(1, one.size());
        assertEquals("s1", one.get(0).sessionId());
        assertEquals(1L, one.get(0).seq());

        port.clear();
        assertTrue(port.search("hello", 10).isEmpty());
    }

    @Test
    @DisplayName("匿名实现：close 默认空实现不抛异常")
    void anonymousImplDefaultCloseNoOp() {
        ISessionSearchIndexPort port = noopPort();
        assertDoesNotThrow(() -> port.close());
    }

    @Test
    @DisplayName("search limit=0 返回空列表")
    void searchWithZeroLimitReturnsEmpty() {
        ISessionSearchIndexPort port = noopPort();
        assertTrue(port.search("anything", 0).isEmpty());
    }

    @Test
    @DisplayName("remove 按 sessionId 删除文档")
    void removeBySessionId() {
        var store = new ArrayList<SearchHit>();
        ISessionSearchIndexPort port = new ISessionSearchIndexPort() {
            @Override
            public void index(String sessionId, long seq, String content) {
                store.add(SearchHit.of(sessionId, seq, content, 1f));
            }
            @Override
            public List<SearchHit> search(String query, int limit) { return List.of(); }
            @Override
            public void clear() { store.clear(); }
            @Override
            public void remove(String sessionId) {
                store.removeIf(h -> h.sessionId().equals(sessionId));
            }
            @Override
            public void removeOlderThan(Instant threshold) {}
            @Override
            public void removeOldest(int count) {}
            @Override
            public int documentCount() { return store.size(); }
        };

        port.index("s1", 1, "a");
        port.index("s2", 2, "b");
        assertEquals(2, port.documentCount());
        port.remove("s1");
        assertEquals(1, port.documentCount());
    }

    private static ISessionSearchIndexPort noopPort() {
        return new ISessionSearchIndexPort() {
            @Override
            public void index(String sessionId, long seq, String content) {}
            @Override
            public List<SearchHit> search(String query, int limit) { return List.of(); }
            @Override
            public void clear() {}
            @Override
            public void remove(String sessionId) {}
            @Override
            public void removeOlderThan(Instant threshold) {}
            @Override
            public void removeOldest(int count) {}
            @Override
            public int documentCount() { return 0; }
        };
    }
}
