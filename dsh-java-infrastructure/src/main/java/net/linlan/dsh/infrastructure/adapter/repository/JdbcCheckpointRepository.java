package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.session.checkpoint.adapter.repository.ICheckpointRepository;
import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCheckpointRepository implements ICheckpointRepository {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS checkpoint (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                session_id VARCHAR(64) NOT NULL,
                step_index BIGINT NOT NULL,
                reason VARCHAR(64) NOT NULL,
                context_snapshot_json TEXT,
                created_at TIMESTAMP NOT NULL,
                INDEX idx_checkpoint_session (session_id, step_index)
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO checkpoint (session_id, step_index, reason, context_snapshot_json, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String SELECT_LATEST_SQL = """
            SELECT session_id, step_index, reason, context_snapshot_json, created_at
            FROM checkpoint WHERE session_id = ?
            ORDER BY id DESC LIMIT 1
            """;

    private static final String SELECT_LATEST_BY_REASON_SQL = """
            SELECT session_id, step_index, reason, context_snapshot_json, created_at
            FROM checkpoint WHERE session_id = ? AND reason = ?
            ORDER BY id DESC LIMIT 1
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT session_id, step_index, reason, context_snapshot_json, created_at
            FROM checkpoint WHERE session_id = ?
            ORDER BY id ASC
            """;

    private static final String SELECT_AT_STEP_SQL = """
            SELECT session_id, step_index, reason, context_snapshot_json, created_at
            FROM checkpoint WHERE session_id = ? AND step_index = ?
            ORDER BY id DESC LIMIT 1
            """;

    private final DataSource dataSource;

    public JdbcCheckpointRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureTable();
    }

    private void ensureTable() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(CREATE_TABLE_SQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure checkpoint table", e);
        }
    }

    @Override
    @Transactional
    public void save(CheckpointSnapshot snapshot) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setString(1, snapshot.sessionId());
            ps.setLong(2, snapshot.stepIndex());
            ps.setString(3, snapshot.reason());
            ps.setString(4, snapshot.contextSnapshotJson());
            ps.setTimestamp(5, Timestamp.from(snapshot.createdAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save checkpoint", e);
        }
    }

    @Override
    public Optional<CheckpointSnapshot> loadLatest(String sessionId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_LATEST_SQL)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load latest checkpoint", e);
        }
    }

    @Override
    public Optional<CheckpointSnapshot> loadLatestByReason(String sessionId, String reason) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_LATEST_BY_REASON_SQL)) {
            ps.setString(1, sessionId);
            ps.setString(2, reason);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load latest checkpoint by reason", e);
        }
    }

    @Override
    public Optional<CheckpointSnapshot> loadAtStep(String sessionId, long stepIndex) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_AT_STEP_SQL)) {
            ps.setString(1, sessionId);
            ps.setLong(2, stepIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load checkpoint at step", e);
        }
    }

    @Override
    public List<CheckpointSnapshot> loadAll(String sessionId) {
        List<CheckpointSnapshot> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ALL_SQL)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load checkpoints", e);
        }
        return result;
    }

    private CheckpointSnapshot mapRow(ResultSet rs) throws SQLException {
        return new CheckpointSnapshot(
                rs.getString("session_id"),
                rs.getLong("step_index"),
                rs.getString("reason"),
                rs.getString("context_snapshot_json"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
