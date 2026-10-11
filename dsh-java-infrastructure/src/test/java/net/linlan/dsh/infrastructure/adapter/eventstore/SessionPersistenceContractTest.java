package net.linlan.dsh.infrastructure.adapter.eventstore;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.valobj.AssistantMessagePayload;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceIntent;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceOp;
import net.linlan.dsh.domain.session.event.model.valobj.UserMessagePayload;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SessionPersistenceContractTest 是会话持久化契约测试抽象基类。
 * <p>所有 {@link SessionPersistenceSeam} 实现都应继承此类，跑同一套契约用例。
 */
public abstract class SessionPersistenceContractTest {

    protected abstract SessionPersistenceSeam createStore();

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("saveHeader + getHeader 往返一致")
    void headerRoundTrip() {
        var store = createStore();
        var header = SessionHeader.create("contract-rt", Instant.EPOCH, null, null);

        store.saveHeader(header);
        var loaded = store.getHeader("contract-rt");

        assertTrue(loaded.isPresent());
        assertEquals("contract-rt", loaded.get().sessionId());
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("getHeader 不存在的 session 返回 empty")
    void getHeaderNonExistentReturnsEmpty() {
        var store = createStore();
        assertTrue(store.getHeader("non-existent").isEmpty());
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("append + readAll 追加事件可读")
    void appendAndReadAll() {
        var store = createStore();
        store.saveHeader(SessionHeader.create("append-test", Instant.EPOCH, null, null));

        var e0 = userMessage(0, "hello");
        var e1 = assistantMessage(1, "hi");
        store.append("append-test", e0);
        store.append("append-test", e1);

        var events = store.readAll("append-test");
        assertEquals(2, events.size());
        assertEquals(0L, events.get(0).seq());
        assertEquals(1L, events.get(1).seq());
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("appendBatch + readAll 批量追加可读")
    void appendBatchAndReadAll() {
        var store = createStore();
        store.saveHeader(SessionHeader.create("batch-test", Instant.EPOCH, null, null));

        List<SessionEvent> events = java.util.Arrays.asList(
                (SessionEvent) userMessage(0, "a"),
                (SessionEvent) assistantMessage(1, "b"),
                (SessionEvent) userMessage(2, "c")
        );
        store.appendBatch("batch-test", events);

        var loaded = store.readAll("batch-test");
        assertEquals(3, loaded.size());
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("readAll 不存在的 session 返回空列表")
    void readAllNonExistentReturnsEmpty() {
        var store = createStore();
        assertTrue(store.readAll("no-such-session").isEmpty());
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("readAfter 返回指定 seq 之后的事件")
    void readAfter() {
        var store = createStore();
        store.saveHeader(SessionHeader.create("after-test", Instant.EPOCH, null, null));

        store.append("after-test", userMessage(0, "a"));
        store.append("after-test", assistantMessage(1, "b"));
        store.append("after-test", userMessage(2, "c"));

        var after = store.readAfter("after-test", 0);
        assertEquals(2, after.size());
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("getLastSeq 返回最后事件序号")
    void getLastSeq() {
        var store = createStore();
        store.saveHeader(SessionHeader.create("seq-test", Instant.EPOCH, null, null));

        store.append("seq-test", userMessage(0, "a"));
        store.append("seq-test", assistantMessage(5, "b"));

        assertEquals(5L, store.getLastSeq("seq-test"));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("getLastSeq 不存在的 session 返回 -1")
    void getLastSeqNonExistent() {
        var store = createStore();
        assertEquals(-1L, store.getLastSeq("no-such"));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("count 返回事件数")
    void count() {
        var store = createStore();
        store.saveHeader(SessionHeader.create("count-test", Instant.EPOCH, null, null));

        store.append("count-test", userMessage(0, "a"));
        store.append("count-test", assistantMessage(1, "b"));

        assertEquals(2L, store.count("count-test"));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("listHeaders 返回已保存的 header")
    void listHeaders() {
        var store = createStore();
        store.saveHeader(SessionHeader.create("list-1", Instant.EPOCH, null, null));
        store.saveHeader(SessionHeader.create("list-2", Instant.EPOCH, null, null));

        var headers = store.listHeaders(10);
        assertTrue(headers.size() >= 2);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("saveHeader 幂等：重复保存不报错")
    void saveHeaderIdempotent() {
        var store = createStore();
        var header = SessionHeader.create("idempotent", Instant.EPOCH, null, null);

        store.saveHeader(header);
        store.saveHeader(header);

        assertTrue(store.getHeader("idempotent").isPresent());
    }

    // ── Helpers ───────────────────────────────────────────────

    static SessionEvent.UserMessage userMessage(long seq, String content) {
        var msg = Message.createUser(
                List.of(new TextBlock(content)),
                new MessageSource.UserSource()
        );
        return new SessionEvent.UserMessage(
                seq, Instant.now(),
                new UserMessagePayload(0, 0, msg),
                new SurfaceIntent(SurfaceOp.append(), null)
        );
    }

    static SessionEvent.AssistantMessage assistantMessage(long seq, String content) {
        var msg = Message.createAssistant(
                List.of(new TextBlock(content)),
                new MessageSource.ModelMessageSource("test", "test")
        );
        return new SessionEvent.AssistantMessage(
                seq, Instant.now(),
                new AssistantMessagePayload(0, 0, msg, new TokenUsage(0, 0)),
                new SurfaceIntent(SurfaceOp.append(), null)
        );
    }
}
