package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.runtime.meter.adapter.repository.ITokenSnapshotRepository;
import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/**
 * JdbcTokenSnapshotRepository 基于 JDBC 实现 Token 快照仓储。
 */
@Repository
public class JdbcTokenSnapshotRepository implements ITokenSnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTokenSnapshotRepository.class);

    private static final String SELECT_SQL = """
            SELECT last_event_step_index, input_tokens, output_tokens,
                   cache_read_tokens, cache_write_tokens, reasoning_tokens, model_calls
            FROM token_snapshot WHERE session_id = ?
            """;

    private static final String UPSERT_SQL = """
            INSERT INTO token_snapshot
                (session_id, last_event_step_index, input_tokens, output_tokens,
                 cache_read_tokens, cache_write_tokens, reasoning_tokens, model_calls)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                last_event_step_index = VALUES(last_event_step_index),
                input_tokens = VALUES(input_tokens),
                output_tokens = VALUES(output_tokens),
                cache_read_tokens = VALUES(cache_read_tokens),
                cache_write_tokens = VALUES(cache_write_tokens),
                reasoning_tokens = VALUES(reasoning_tokens),
                model_calls = VALUES(model_calls)
            """;

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS token_snapshot (
                session_id VARCHAR(64) PRIMARY KEY,
                last_event_step_index BIGINT NOT NULL,
                input_tokens INT NOT NULL,
                output_tokens INT NOT NULL,
                cache_read_tokens INT NULL,
                cache_write_tokens INT NULL,
                reasoning_tokens INT NULL,
                model_calls BIGINT NOT NULL
            )
            """;

    private final DataSource dataSource;

    public JdbcTokenSnapshotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureTable();
    }

    private void ensureTable() {
        try (Connection conn = dataSource.getConnection();
             var st = conn.createStatement()) {
            st.execute(CREATE_TABLE_SQL);
        } catch (Exception e) {
            log.error("Failed to ensure token_snapshot table: {}", e.getMessage(), e);
        }
    }

    @Override
    public Optional<TokenSnapshot> load(String sessionId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new TokenSnapshot(
                            rs.getLong("last_event_step_index"),
                            new TokenUsage(
                                    rs.getInt("input_tokens"),
                                    rs.getInt("output_tokens"),
                                    (Integer) rs.getObject("cache_read_tokens"),
                                    (Integer) rs.getObject("cache_write_tokens"),
                                    (Integer) rs.getObject("reasoning_tokens")
                            ),
                            rs.getLong("model_calls")
                    ));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to load token snapshot for session {}: {}", sessionId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void save(String sessionId, TokenSnapshot snapshot) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, sessionId);
            ps.setLong(2, snapshot.lastEventStepIndex());
            ps.setInt(3, snapshot.totalTokens().inputTokens());
            ps.setInt(4, snapshot.totalTokens().outputTokens());
            ps.setObject(5, snapshot.totalTokens().cacheReadTokens());
            ps.setObject(6, snapshot.totalTokens().cacheWriteTokens());
            ps.setObject(7, snapshot.totalTokens().reasoningTokens());
            ps.setLong(8, snapshot.modelCalls());
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to save token snapshot for session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}
