package net.linlan.dsh.infrastructure.adapter.credentials;

import net.linlan.dsh.domain.credentials.anonymous.adapter.port.IAnonymousIdStorePort;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class JdbcAnonymousIdStore implements IAnonymousIdStorePort {

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS anonymous_user_id (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                fingerprint VARCHAR(255) NOT NULL UNIQUE,
                anonymous_id VARCHAR(128) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_anonymous_fingerprint (fingerprint)
            )
            """;

    private static final String SELECT_SQL = """
            SELECT anonymous_id FROM anonymous_user_id WHERE fingerprint = ?
            """;

    private static final String INSERT_SQL = """
            INSERT INTO anonymous_user_id (fingerprint, anonymous_id) VALUES (?, ?)
            """;

    private final DataSource dataSource;

    public JdbcAnonymousIdStore(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureTable();
    }

    private void ensureTable() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(CREATE_TABLE_SQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure anonymous_user_id table", e);
        }
    }

    @Override
    public Optional<String> findById(String fingerprint) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_SQL)) {
            ps.setString(1, fingerprint);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("anonymous_id"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find anonymous id", e);
        }
    }

    @Override
    public void save(String fingerprint, String anonymousId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
            ps.setString(1, fingerprint);
            ps.setString(2, anonymousId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save anonymous id", e);
        }
    }
}
