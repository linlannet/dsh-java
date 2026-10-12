package net.linlan.dsh.domain.session.search.service;

import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.session.search.adapter.port.ISessionSearchIndexPort;
import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionFullTextIndex 全文索引领域服务")
class SessionFullTextIndexTest {

    @Test
    @DisplayName("UserMessage 事件文本被索引")
    void userMessageIndexed() {
        var stub = new StubPort();
        var index = new SessionFullTextIndex(stub);
        var event = SessionEventFactory.userMessage("hello world search test");
        index.index("sess-1", event);
        assertEquals(1, stub.indexed.size());
        assertEquals("sess-1", stub.indexed.get(0).sessionId);
        assertTrue(stub.indexed.get(0).content.contains("hello"));
    }

    @Test
    @DisplayName("ToolCall 事件名称和参数被索引")
    void toolCallIndexed() {
        var stub = new StubPort();
        var index = new SessionFullTextIndex(stub);
        var event = SessionEventFactory.toolCall(1, 1, "call-1", "Read", "{\"path\":\"/tmp/foo\"}");
        index.index("sess-1", event);
        assertEquals(1, stub.indexed.size());
        assertTrue(stub.indexed.get(0).content.contains("Read"));
        assertTrue(stub.indexed.get(0).content.contains("path"));
    }

    @Test
    @DisplayName("空内容事件不被索引")
    void blankContentNotIndexed() {
        var stub = new StubPort();
        var index = new SessionFullTextIndex(stub);
        var event = SessionEventFactory.turnStart();
        index.index("sess-1", event);
        assertEquals(0, stub.indexed.size());
    }

    @Test
    @DisplayName("port 为 null 抛异常")
    void nullPortRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SessionFullTextIndex(null));
    }

    static final class StubPort implements ISessionSearchIndexPort {
        final List<IndexedEntry> indexed = new ArrayList<>();

        record IndexedEntry(String sessionId, long seq, String content) {}

        @Override
        public void index(String sessionId, long seq, String content) {
            indexed.add(new IndexedEntry(sessionId, seq, content));
        }

        @Override
        public List<SearchHit> search(String query, int limit) {
            return List.of();
        }

        @Override
        public void clear() {}
    }
}
