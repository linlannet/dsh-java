package net.linlan.dsh.infrastructure.adapter.storage;

import net.linlan.dsh.domain.sdk.spill.adapter.port.ISpillStorePort;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Repository
public class JdbcSpillStore implements ISpillStorePort {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS spill_ref (
                ref_id VARCHAR(64) PRIMARY KEY,
                content CLOB,
                created_at TIMESTAMP NOT NULL
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO spill_ref (ref_id, content, created_at) VALUES (?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT content FROM spill_ref WHERE ref_id = ?
            """;

    private static final String EXISTS_SQL = """
            SELECT 1 FROM spill_ref WHERE ref_id = ?
            """;

    private final DataSource dataSource;

    public JdbcSpillStore(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureTable();
    }

    private void ensureTable() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(CREATE_TABLE_SQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure spill_ref table", e);
        }
    }

    @Override
    public void save(String refId, String content) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setString(1, refId);
            ps.setString(2, content);
            ps.setTimestamp(3, java.sql.Timestamp.from(java.time.Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save spill content", e);
        }
    }

    @Override
    public String load(String refId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_SQL)) {
            ps.setString(1, refId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("content");
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load spill content", e);
        }
    }

    @Override
    public boolean exists(String refId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(EXISTS_SQL)) {
            ps.setString(1, refId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check spill existence", e);
        }
    }
}
