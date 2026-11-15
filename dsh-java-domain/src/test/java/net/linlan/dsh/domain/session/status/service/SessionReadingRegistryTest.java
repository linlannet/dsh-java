package net.linlan.dsh.domain.session.status.service;

import net.linlan.dsh.domain.session.status.model.valobj.SessionReadState;
import net.linlan.dsh.domain.session.status.model.valobj.SessionStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionReadingRegistryTest {

    @Test
    void markBeforeBaselineIsRejected() {
        SessionReadingRegistry registry = new SessionReadingRegistry();

        assertThrows(IllegalStateException.class, () -> registry.mark("s1", true));
    }

    @Test
    void markUnreadThenReadClearsReminder() {
        SessionReadingRegistry registry = new SessionReadingRegistry();
        registry.reportBaseline(Map.of());

        registry.mark("s1", true);
        assertTrue(registry.status("s1", false).completionUnread());

        registry.mark("s1", false);
        assertFalse(registry.status("s1", false).completionUnread());
    }

    @Test
    void failedCompletionIsUnreadOnlyUntilAcknowledged() {
        SessionReadingRegistry registry = new SessionReadingRegistry();
        registry.reportBaseline(Map.of());

        registry.recordTurnEnd("s1", true);
        SessionStatus unread = registry.status("s1", false);
        assertTrue(unread.completionUnread());
        assertTrue(unread.completionFailed());

        registry.acknowledge("s1", 1);
        SessionStatus read = registry.status("s1", false);
        assertFalse(read.completionUnread());
        assertFalse(read.completionFailed());
    }

    @Test
    void acknowledgeKeepsManualUnreadAndClampsPosition() {
        SessionReadingRegistry registry = new SessionReadingRegistry();
        registry.reportBaseline(Map.of());
        registry.recordTurnEnd("s1", false);
        registry.mark("s1", true);

        registry.acknowledge("s1", 99);

        SessionReadState state = registry.state("s1").orElseThrow();
        assertTrue(state.manualUnread());
        assertEquals(1, state.readTurn());
        assertEquals(1, state.completedTurn());
        assertTrue(registry.status("s1", false).completionUnread());
    }

    @Test
    void explicitReadAdvancesThroughLatestCompletion() {
        SessionReadingRegistry registry = new SessionReadingRegistry();
        registry.reportBaseline(Map.of());
        registry.recordTurnEnd("s1", false);
        registry.recordTurnEnd("s1", false);
        registry.mark("s1", true);

        registry.read("s1", 10);

        SessionReadState state = registry.state("s1").orElseThrow();
        assertEquals(2, state.readTurn());
        assertFalse(state.manualUnread());
        assertFalse(registry.status("s1", false).completionUnread());
    }

    @Test
    void ensureBaselineIsIdempotentAndEnablesMark() {
        SessionReadingRegistry registry = new SessionReadingRegistry();
        registry.recordTurnEnd("s1", false);

        registry.ensureBaseline(java.util.List.of("s1", "s2"));
        registry.ensureBaseline(java.util.List.of("s3"));

        assertTrue(registry.readingReady());
        assertFalse(registry.status("s2", false).completionUnread());
        assertTrue(registry.status("s1", false).completionUnread());
        registry.mark("s2", true);
        assertTrue(registry.status("s2", false).completionUnread());
    }

    @Test
    void unknownSessionIsReadAndSnapshotTracksRunning() {
        SessionReadingRegistry registry = new SessionReadingRegistry();
        registry.reportBaseline(Map.of());

        SessionStatus idle = registry.status("unknown", false);
        assertFalse(idle.completionUnread());
        assertFalse(idle.completionFailed());

        Map<String, SessionStatus> snapshot = registry.snapshot(
                java.util.List.of("unknown", "running"), java.util.List.of("running"));
        assertFalse(snapshot.get("unknown").running());
        assertTrue(snapshot.get("running").running());
    }
}
