package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcCheckpointRepository JDBC 检查点仓储")
class JdbcCheckpointRepositoryTest {

    private Connection holdConnection;
    private javax.sql.DataSource dataSource;
    private JdbcCheckpointRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        // H2 内存库，MySQL 模式以兼容 AUTO_INCREMENT / INDEX 语法
        String url = "jdbc:h2:mem:checkpoint-test;MODE=MySQL;DB_CLOSE_DELAY=-1";
        holdConnection = DriverManager.getConnection(url, "sa", "");
        dataSource = new javax.sql.DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url, "sa", "");
            }
            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return DriverManager.getConnection(url, username, password);
            }
            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("not a wrapper"); }
            @Override
            public boolean isWrapperFor(Class<?> iface) { return false; }
            @Override
            public java.io.PrintWriter getLogWriter() { return null; }
            @Override
            public void setLogWriter(java.io.PrintWriter out) {}
            @Override
            public void setLoginTimeout(int seconds) {}
            @Override
            public int getLoginTimeout() { return 0; }
            @Override
            public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getGlobal();
            }
        };
        repository = new JdbcCheckpointRepository(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement st = holdConnection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS checkpoint");
        }
        holdConnection.close();
    }

    @Test
    @DisplayName("save 后 loadLatest 返回最近写入的快照")
    void saveThenLoadLatest() {
        var snap = CheckpointSnapshot.of("sess-a", 3, "before-model", "{\"k\":1}");
        repository.save(snap);

        Optional<CheckpointSnapshot> loaded = repository.loadLatest("sess-a");
        assertTrue(loaded.isPresent(), "刚保存的快照应可加载");
        assertEquals("sess-a", loaded.get().sessionId());
        assertEquals(3L, loaded.get().stepIndex());
        assertEquals("before-model", loaded.get().reason());
        assertEquals("{\"k\":1}", loaded.get().contextSnapshotJson());
    }

    @Test
    @DisplayName("多次 save 同 session，loadLatest 返回最新一条")
    void loadLatestReturnsNewest() {
        repository.save(CheckpointSnapshot.of("sess-b", 1, "before-model", "{}"));
        repository.save(CheckpointSnapshot.of("sess-b", 2, "before-tool", "{}"));
        repository.save(CheckpointSnapshot.of("sess-b", 3, "before-model", "{}"));

        var loaded = repository.loadLatest("sess-b");
        assertTrue(loaded.isPresent());
        assertEquals(3L, loaded.get().stepIndex());
    }

    @Test
    @DisplayName("loadLatest 不存在的 session 返回 empty")
    void loadLatestMissingSessionReturnsEmpty() {
        assertTrue(repository.loadLatest("no-such-session").isEmpty());
    }

    @Test
    @DisplayName("loadAll 按 id 升序返回全部快照")
    void loadAllReturnsAllInOrder() {
        repository.save(CheckpointSnapshot.of("sess-c", 1, "before-model", "{}"));
        repository.save(CheckpointSnapshot.of("sess-c", 2, "before-tool", "{}"));
        repository.save(CheckpointSnapshot.of("sess-c", 3, "before-model", "{}"));

        List<CheckpointSnapshot> all = repository.loadAll("sess-c");
        assertEquals(3, all.size());
        assertEquals(1L, all.get(0).stepIndex());
        assertEquals(2L, all.get(1).stepIndex());
        assertEquals(3L, all.get(2).stepIndex());
    }

    @Test
    @DisplayName("loadAll 不存在的 session 返回空列表")
    void loadAllMissingSessionReturnsEmpty() {
        assertTrue(repository.loadAll("no-such-session").isEmpty());
    }

    @Test
    @DisplayName("不同 session 的快照互不干扰")
    void differentSessionsIsolated() {
        repository.save(CheckpointSnapshot.of("sess-d1", 1, "r", "{}"));
        repository.save(CheckpointSnapshot.of("sess-d2", 1, "r", "{}"));

        assertEquals(1, repository.loadAll("sess-d1").size());
        assertEquals(1, repository.loadAll("sess-d2").size());
        assertTrue(repository.loadLatest("sess-d1").isPresent());
        assertEquals("sess-d1", repository.loadLatest("sess-d1").get().sessionId());
    }

    @Test
    @DisplayName("contextSnapshotJson 为 null 时可保存与读回")
    void nullContextJsonRoundTrip() {
        var snap = new CheckpointSnapshot("sess-e", 5, "before-tool", null, Instant.now());
        repository.save(snap);
        var loaded = repository.loadLatest("sess-e");
        assertTrue(loaded.isPresent());
        assertNull(loaded.get().contextSnapshotJson());
    }

    @Test
    @DisplayName("createdAt 精度往返保持一致")
    void createdAtRoundTrip() {
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var snap = new CheckpointSnapshot("sess-f", 1, "r", "{}", now);
        repository.save(snap);
        var loaded = repository.loadLatest("sess-f");
        assertTrue(loaded.isPresent());
        assertEquals(now, loaded.get().createdAt());
    }

    @Test
    @DisplayName("构造时自动建表，重复构造不报错")
    void ensureTableIdempotent() {
        assertDoesNotThrow(() -> new JdbcCheckpointRepository(dataSource));
    }

    @Test
    @DisplayName("loadLatestByReason 返回指定 reason 的最新快照")
    void loadLatestByReasonReturnsNewest() {
        repository.save(CheckpointSnapshot.of("sess-g", 1, "before-model", "{}"));
        repository.save(CheckpointSnapshot.of("sess-g", 2, "before-tool", "{}"));
        repository.save(CheckpointSnapshot.of("sess-g", 3, "before-model", "{}"));

        var loaded = repository.loadLatestByReason("sess-g", "before-model");
        assertTrue(loaded.isPresent());
        assertEquals(3L, loaded.get().stepIndex());
        assertEquals("before-model", loaded.get().reason());
    }

    @Test
    @DisplayName("loadLatestByReason 不存在时返回 empty")
    void loadLatestByReasonMissingReturnsEmpty() {
        repository.save(CheckpointSnapshot.of("sess-h", 1, "before-model", "{}"));

        assertTrue(repository.loadLatestByReason("sess-h", "nonexistent-reason").isEmpty());
    }

    @Test
    @DisplayName("loadAtStep 返回指定 stepIndex 的快照")
    void loadAtStepReturnsSnapshot() {
        repository.save(CheckpointSnapshot.of("sess-i", 1, "before-model", "{}"));
        repository.save(CheckpointSnapshot.of("sess-i", 5, "before-tool", "{}"));
        repository.save(CheckpointSnapshot.of("sess-i", 10, "before-model", "{}"));

        var loaded = repository.loadAtStep("sess-i", 5L);
        assertTrue(loaded.isPresent());
        assertEquals(5L, loaded.get().stepIndex());
        assertEquals("before-tool", loaded.get().reason());
    }

    @Test
    @DisplayName("loadAtStep 不存在时返回 empty")
    void loadAtStepMissingReturnsEmpty() {
        assertTrue(repository.loadAtStep("sess-j", 999L).isEmpty());
    }
}
