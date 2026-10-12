package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenSnapshot;

import java.util.Optional;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTokenSnapshotRepositoryTest {

    private JdbcDataSource newDataSource(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    void loadReturnsEmptyWhenSessionNotFound() {
        JdbcTokenSnapshotRepository repo = new JdbcTokenSnapshotRepository(newDataSource("t1"));

        Optional<TokenSnapshot> result = repo.load("missing-session");

        assertTrue(result.isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsSnapshot() {
        JdbcTokenSnapshotRepository repo = new JdbcTokenSnapshotRepository(newDataSource("t2"));
        TokenSnapshot snapshot = new TokenSnapshot(
                42L,
                new TokenUsage(100, 200, 10, 5, 3),
                7L
        );

        repo.save("session-1", snapshot);
        Optional<TokenSnapshot> loaded = repo.load("session-1");

        assertTrue(loaded.isPresent());
        TokenSnapshot actual = loaded.get();
        assertEquals(42L, actual.lastEventStepIndex());
        assertEquals(100, actual.totalTokens().inputTokens());
        assertEquals(200, actual.totalTokens().outputTokens());
        assertEquals(10, actual.totalTokens().cacheReadTokens());
        assertEquals(5, actual.totalTokens().cacheWriteTokens());
        assertEquals(3, actual.totalTokens().reasoningTokens());
        assertEquals(7L, actual.modelCalls());
    }

    @Test
    void saveUpsertsOverExistingSnapshotForSameSession() {
        JdbcTokenSnapshotRepository repo = new JdbcTokenSnapshotRepository(newDataSource("t3"));
        TokenSnapshot first = new TokenSnapshot(1L, new TokenUsage(10, 20), 1L);
        TokenSnapshot second = new TokenSnapshot(2L, new TokenUsage(30, 40, 2, 1, 0), 3L);

        repo.save("session-upsert", first);
        repo.save("session-upsert", second);
        Optional<TokenSnapshot> loaded = repo.load("session-upsert");

        assertTrue(loaded.isPresent());
        TokenSnapshot actual = loaded.get();
        assertEquals(2L, actual.lastEventStepIndex());
        assertEquals(30, actual.totalTokens().inputTokens());
        assertEquals(40, actual.totalTokens().outputTokens());
        assertEquals(3L, actual.modelCalls());
    }

    @Test
    void savePersistsNullableCacheTokensAsNull() {
        JdbcTokenSnapshotRepository repo = new JdbcTokenSnapshotRepository(newDataSource("t4"));
        TokenSnapshot snapshot = new TokenSnapshot(
                5L,
                new TokenUsage(50, 60, null, null, null),
                2L
        );

        repo.save("session-nullables", snapshot);
        Optional<TokenSnapshot> loaded = repo.load("session-nullables");

        assertTrue(loaded.isPresent());
        TokenSnapshot actual = loaded.get();
        assertEquals(5L, actual.lastEventStepIndex());
        assertEquals(50, actual.totalTokens().inputTokens());
        assertEquals(60, actual.totalTokens().outputTokens());
        assertTrue(actual.totalTokens().cacheReadTokens() == null);
        assertTrue(actual.totalTokens().cacheWriteTokens() == null);
        assertTrue(actual.totalTokens().reasoningTokens() == null);
        assertEquals(2L, actual.modelCalls());
    }

    @Test
    void ensureTableCreatesTableOnConstructionSoLoadDoesNotThrow() {
        JdbcDataSource ds = newDataSource("t5");
        JdbcTokenSnapshotRepository repo = new JdbcTokenSnapshotRepository(ds);

        Optional<TokenSnapshot> result = repo.load("any-session");

        assertTrue(result.isEmpty());
    }

    @Test
    void multipleSessionsAreStoredIndependently() {
        JdbcTokenSnapshotRepository repo = new JdbcTokenSnapshotRepository(newDataSource("t6"));
        TokenSnapshot a = new TokenSnapshot(1L, new TokenUsage(1, 2), 1L);
        TokenSnapshot b = new TokenSnapshot(2L, new TokenUsage(3, 4), 2L);

        repo.save("session-a", a);
        repo.save("session-b", b);

        Optional<TokenSnapshot> loadedA = repo.load("session-a");
        Optional<TokenSnapshot> loadedB = repo.load("session-b");

        assertTrue(loadedA.isPresent());
        assertTrue(loadedB.isPresent());
        assertEquals(1L, loadedA.get().lastEventStepIndex());
        assertEquals(2L, loadedB.get().lastEventStepIndex());
    }
}
