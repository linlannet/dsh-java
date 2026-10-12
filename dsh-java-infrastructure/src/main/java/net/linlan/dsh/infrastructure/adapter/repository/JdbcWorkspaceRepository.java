package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.session.workspace.adapter.repository.IWorkspaceRepository;
import net.linlan.dsh.domain.session.workspace.model.entity.WorkspaceEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcWorkspaceRepository implements IWorkspaceRepository {

    private static final String CREATE_WORKSPACE_SQL = """
            CREATE TABLE IF NOT EXISTS workspace (
                workspace_id VARCHAR(64) PRIMARY KEY,
                root_path VARCHAR(1024) NOT NULL,
                created_at TIMESTAMP NOT NULL
            )
            """;

    private static final String CREATE_SESSION_WORKSPACE_SQL = """
            CREATE TABLE IF NOT EXISTS session_workspace (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                workspace_id VARCHAR(64) NOT NULL,
                session_id VARCHAR(64) NOT NULL,
                INDEX idx_session_workspace (workspace_id, session_id)
            )
            """;

    private static final String UPSERT_WORKSPACE_SQL = """
            INSERT INTO workspace (workspace_id, root_path, created_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE root_path = VALUES(root_path)
            """;

    private static final String DELETE_SESSION_WORKSPACE_SQL = """
            DELETE FROM session_workspace WHERE workspace_id = ?
            """;

    private static final String INSERT_SESSION_WORKSPACE_SQL = """
            INSERT INTO session_workspace (workspace_id, session_id) VALUES (?, ?)
            """;

    private static final String SELECT_WORKSPACE_SQL = """
            SELECT workspace_id, root_path FROM workspace WHERE workspace_id = ?
            """;

    private static final String SELECT_ATTACHED_SESSIONS_SQL = """
            SELECT session_id FROM session_workspace WHERE workspace_id = ?
            """;

    private final DataSource dataSource;

    public JdbcWorkspaceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureTable();
    }

    private void ensureTable() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(CREATE_WORKSPACE_SQL);
            st.execute(CREATE_SESSION_WORKSPACE_SQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure workspace tables", e);
        }
    }

    @Override
    @Transactional
    public void save(WorkspaceEntity workspace) {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(UPSERT_WORKSPACE_SQL)) {
                ps.setString(1, workspace.workspaceId());
                ps.setString(2, workspace.rootPath());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(DELETE_SESSION_WORKSPACE_SQL)) {
                ps.setString(1, workspace.workspaceId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(INSERT_SESSION_WORKSPACE_SQL)) {
                for (String sessionId : workspace.attachedSessionIds()) {
                    ps.setString(1, workspace.workspaceId());
                    ps.setString(2, sessionId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save workspace", e);
        }
    }

    @Override
    public Optional<WorkspaceEntity> findById(String workspaceId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_WORKSPACE_SQL)) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String rootPath = rs.getString("root_path");
                    List<String> sessions = listAttachedSessions(workspaceId);
                    return Optional.of(new WorkspaceEntity(workspaceId, rootPath, sessions));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find workspace", e);
        }
    }

    @Override
    public List<String> listAttachedSessions(String workspaceId) {
        List<String> sessions = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_ATTACHED_SESSIONS_SQL)) {
            ps.setString(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(rs.getString("session_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list attached sessions", e);
        }
        return sessions;
    }
}
