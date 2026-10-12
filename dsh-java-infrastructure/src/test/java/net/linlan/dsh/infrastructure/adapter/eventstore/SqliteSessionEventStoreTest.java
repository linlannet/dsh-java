package net.linlan.dsh.infrastructure.adapter.eventstore;

import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.valobj.SessionEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSessionEventStoreTest {

    private Path dbFile;
    private SqliteSessionEventStore store;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("sqlite-session-test-", ".db");
        store = new SqliteSessionEventStore(dbFile.toString());
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
        try {
            Files.deleteIfExists(dbFile);
        } catch (Exception ignored) { }
    }

    @Test
    void shouldSaveAndLoadHeader() {
        SessionHeader header = SessionHeader.create("sess-1", Instant.now(), "/tmp", "preset");
        store.saveHeader(header);

        var loaded = store.getHeader("sess-1");
        assertTrue(loaded.isPresent());
        assertEquals("sess-1", loaded.get().sessionId());
        assertEquals("/tmp", loaded.get().cwd());
        assertEquals("preset", loaded.get().agentPreset());
    }

    @Test
    void shouldReturnEmptyForMissingHeader() {
        assertTrue(store.getHeader("missing").isEmpty());
    }

    @Test
    void shouldAppendAndReadAll() {
        store.saveHeader(SessionHeader.create("s1", Instant.now(), null, null));
        store.append("s1", genericEvent(0));
        store.append("s1", genericEvent(1));

        List<SessionEvent> events = store.readAll("s1");
        assertEquals(2, events.size());
        assertEquals(0L, events.get(0).seq());
        assertEquals(1L, events.get(1).seq());
    }

    @Test
    void shouldAppendBatch() {
        store.saveHeader(SessionHeader.create("s2", Instant.now(), null, null));
        store.appendBatch("s2", List.of(genericEvent(0), genericEvent(1), genericEvent(2)));

        assertEquals(3, store.readAll("s2").size());
    }

    @Test
    void shouldReadAfter() {
        store.saveHeader(SessionHeader.create("s3", Instant.now(), null, null));
        store.append("s3", genericEvent(0));
        store.append("s3", genericEvent(1));
        store.append("s3", genericEvent(2));

        List<SessionEvent> after = store.readAfter("s3", 0);
        assertEquals(2, after.size());
    }

    @Test
    void shouldReadRange() {
        store.saveHeader(SessionHeader.create("s4", Instant.now(), null, null));
        for (long i = 0; i < 5; i++) {
            store.append("s4", genericEvent(i));
        }

        List<SessionEvent> range = store.readRange("s4", 1, 3);
        assertEquals(3, range.size());
    }

    @Test
    void shouldGetLastSeq() {
        store.saveHeader(SessionHeader.create("s5", Instant.now(), null, null));
        store.append("s5", genericEvent(0));
        store.append("s5", genericEvent(7));

        assertEquals(7L, store.getLastSeq("s5"));
    }

    @Test
    void shouldReturnNegativeOneForMissingSessionLastSeq() {
        assertEquals(-1L, store.getLastSeq("no-such"));
    }

    @Test
    void shouldCount() {
        store.saveHeader(SessionHeader.create("s6", Instant.now(), null, null));
        store.append("s6", genericEvent(0));
        store.append("s6", genericEvent(1));

        assertEquals(2L, store.count("s6"));
    }

    @Test
    void shouldListHeaders() {
        store.saveHeader(SessionHeader.create("h1", Instant.now(), null, null));
        store.saveHeader(SessionHeader.create("h2", Instant.now(), null, null));

        List<SessionHeader> headers = store.listHeaders(10);
        assertTrue(headers.size() >= 2);
    }

    @Test
    void shouldBeIdempotentOnHeaderSave() {
        SessionHeader header = SessionHeader.create("idem", Instant.now(), null, null);
        store.saveHeader(header);
        store.saveHeader(header);

        assertTrue(store.getHeader("idem").isPresent());
    }

    @Test
    void shouldImplementSessionPersistenceSeam() {
        assertTrue(store instanceof SessionPersistenceSeam);
    }

    @Test
    void shouldReadAllEmptyForMissingSession() {
        assertTrue(store.readAll("no-such").isEmpty());
    }

    private SessionEvent genericEvent(long seq) {
        return new SessionEvent.Generic(seq, Instant.now(), SessionEventType.AGENT_EVENT, "{}");
    }
}
