package net.linlan.dsh.infrastructure.adapter.eventstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlSessionEventStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldMigrateLegacyHeaderToCurrentFormat() throws Exception {
        Path sessionDir = Files.createDirectories(tempDir.resolve("legacy-session"));
        Files.writeString(sessionDir.resolve("header.json"), """
                {"version":0,"sessionId":"legacy-session","createdAt":"2026-01-01T00:00:00Z"}
                """);
        JsonlSessionEventStore store = new JsonlSessionEventStore(tempDir.toString());

        var header = store.getHeader("legacy-session");

        assertEquals(3, header.orElseThrow().version());
        assertEquals("3", Files.readAllLines(sessionDir.resolve("header.json")).stream()
                .filter(line -> line.contains("\"version\""))
                .findFirst()
                .orElseThrow()
                .replaceAll(".*\"version\"\\s*:\\s*\"?(\\d+)\"?.*", "$1"));
    }

    @Test
    void shouldRejectNewerSessionFormat() throws Exception {
        Path sessionDir = Files.createDirectories(tempDir.resolve("future-session"));
        Files.writeString(sessionDir.resolve("header.json"), """
                {"version":4,"sessionId":"future-session","createdAt":"2026-01-01T00:00:00Z"}
                """);
        JsonlSessionEventStore store = new JsonlSessionEventStore(tempDir.toString());

        assertThrows(IllegalStateException.class, () -> store.getHeader("future-session"));
    }

    @Test
    void shouldSaveLegacyHeaderUsingCurrentFormat() throws Exception {
        JsonlSessionEventStore store = new JsonlSessionEventStore(tempDir.toString());
        SessionHeader legacy = new SessionHeader(
                0, "legacy-save", Instant.EPOCH, null, null, 0, null, 0, null
        );

        store.saveHeader(legacy);

        assertEquals(3, store.getHeader("legacy-save").orElseThrow().version());
    }

    @Test
    void shouldRoundTripEventsAndUsage() {
        JsonlSessionEventStore store = new JsonlSessionEventStore(tempDir.toString());
        store.saveHeader(SessionHeader.create("round-trip", Instant.EPOCH, null, null));
        store.append("round-trip", SessionEventFactory.assistantMessage(
                1,
                1,
                Message.createAssistant(
                        java.util.List.of(new TextBlock("done")),
                        new MessageSource.ModelMessageSource("provider", "model")
                ),
                new TokenUsage(10, 4, 2, 1, 3)
        ));

        var events = store.readAll("round-trip");

        assertEquals(1, events.size());
        var message = (net.linlan.dsh.domain.session.event.model.entity.SessionEvent.AssistantMessage) events.get(0);
        assertEquals(10, message.data().usage().inputTokens());
        assertEquals(4, message.data().usage().outputTokens());
        assertEquals(2, message.data().usage().cacheReadTokens());
        assertEquals(1, message.data().usage().cacheWriteTokens());
        assertEquals(3, message.data().usage().reasoningTokens());
    }
}
