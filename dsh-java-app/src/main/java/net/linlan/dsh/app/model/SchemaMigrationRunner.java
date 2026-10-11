package net.linlan.dsh.app.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等执行开发期 schema 补齐。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private final DataSource dataSource;

    public SchemaMigrationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            addColumnIfMissing(
                    "harness_model_setting", "protocol",
                    "ALTER TABLE harness_model_setting ADD COLUMN protocol VARCHAR(32) NOT NULL DEFAULT 'openai'");
            addColumnIfMissing(
                    "harness_model_setting", "active",
                    "ALTER TABLE harness_model_setting ADD COLUMN active BOOLEAN NOT NULL DEFAULT FALSE");
            addColumnIfMissing(
                    "harness_model_setting", "channel_code",
                    "ALTER TABLE harness_model_setting ADD COLUMN channel_code VARCHAR(64) NOT NULL DEFAULT 'default'");
            addColumnIfMissing(
                    "harness_model_setting", "display_name",
                    "ALTER TABLE harness_model_setting ADD COLUMN display_name VARCHAR(128) NOT NULL DEFAULT ''");
            addColumnIfMissing(
                    "harness_session", "channel_code",
                    "ALTER TABLE harness_session ADD COLUMN channel_code VARCHAR(64) NULL");
            ensurePayloadJsonCapacity();
            migrateLegacyProfileColumn();
            backfillActiveModelChannel();
            ensureTokenSnapshotTable();
            addColumnIfMissing(
                    "harness_plugin_installation", "module_id",
                    "ALTER TABLE harness_plugin_installation ADD COLUMN module_id VARCHAR(128) NULL");
        } catch (Exception e) {
            log.error("Schema migration failed: {}", e.getMessage(), e);
        }
    }

    private void backfillActiveModelChannel() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            int activeCount = countChannels(conn, "active = TRUE");
            String activeChannel = findFirstChannel(conn, "active = TRUE AND enabled = TRUE");
            if (activeCount == 1 && activeChannel != null) {
                backfillDisplayName(conn);
                return;
            }
            if (activeChannel == null) {
                activeChannel = findFirstChannel(conn, "enabled = TRUE");
            }
            try (var st = conn.createStatement()) {
                st.executeUpdate("UPDATE harness_model_setting SET active = FALSE");
            }
            int updated = 0;
            if (activeChannel != null) {
                try (PreparedStatement statement = conn.prepareStatement(
                        "UPDATE harness_model_setting SET active = TRUE WHERE channel_code = ?")) {
                    statement.setString(1, activeChannel);
                    updated = statement.executeUpdate();
                }
            }
            if (updated > 0) {
                log.info("Schema migration: activated {} model channel(s)", updated);
            }
            backfillDisplayName(conn);
        }
    }

    private void ensureTokenSnapshotTable() throws Exception {
        try (Connection conn = dataSource.getConnection();
             var st = conn.createStatement()) {
            st.execute("""
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
                    """);
        }
    }

    private void backfillDisplayName(Connection conn) throws Exception {
        try (var st = conn.createStatement()) {
            int updated = st.executeUpdate(
                    "UPDATE harness_model_setting SET display_name = CONCAT('#', channel_code) WHERE display_name IS NULL OR display_name = ''");
            if (updated > 0) {
                log.info("Schema migration: filled display_name for {} model channel(s)", updated);
            }
        }
    }

    private void ensurePayloadJsonCapacity() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String typeName = findColumnType(conn, "harness_session_event_log", "payload_json");
            if (typeName == null || isLargeTextType(typeName)) {
                return;
            }
            String databaseProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
            try (var statement = conn.createStatement()) {
                if (databaseProduct.contains("mysql") || databaseProduct.contains("mariadb")) {
                    statement.execute("ALTER TABLE harness_session_event_log "
                            + "MODIFY COLUMN payload_json LONGTEXT NULL");
                } else {
                    statement.execute("ALTER TABLE harness_session_event_log "
                            + "ALTER COLUMN payload_json CLOB");
                }
            }
            log.info("Schema migration: expanded harness_session_event_log.payload_json to large text");
        }
    }

    private String findColumnType(Connection conn, String table, String column) throws Exception {
        String[][] candidates = {
                {table, column},
                {table.toUpperCase(), column.toUpperCase()},
                {table.toLowerCase(), column.toLowerCase()}
        };
        for (String[] pair : candidates) {
            try (ResultSet columns = conn.getMetaData().getColumns(
                    conn.getCatalog(), null, pair[0], pair[1])) {
                if (columns.next()) {
                    return columns.getString("TYPE_NAME");
                }
            }
        }
        return null;
    }

    private boolean isLargeTextType(String typeName) {
        String normalized = typeName.toUpperCase();
        return normalized.contains("LONGTEXT") || normalized.contains("CLOB");
    }

    /**
     * 将旧版按 profile_code 保存的模型配置迁移为独立渠道。
     * <p>开发阶段不再保留 profile_code；旧记录各自生成内部渠道编码，名称沿用原 profile_code。
     */
    private void migrateLegacyProfileColumn() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            if (!columnExists(conn, "harness_model_setting", "profile_code")) {
                return;
            }

            boolean hasPrimaryKey = primaryKeyColumnCount(conn) > 0;
            if (hasPrimaryKey) {
                try (var statement = conn.createStatement()) {
                    statement.execute("ALTER TABLE harness_model_setting DROP PRIMARY KEY");
                }
            }

            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT profile_code, model_code FROM harness_model_setting");
                 ResultSet rows = select.executeQuery();
                 PreparedStatement update = conn.prepareStatement(
                         "UPDATE harness_model_setting SET channel_code = ?, display_name = ? "
                                 + "WHERE profile_code = ? AND model_code = ?")) {
                while (rows.next()) {
                    String profileCode = rows.getString("profile_code");
                    String modelCode = rows.getString("model_code");
                    update.setString(1, "channel-" + UUID.randomUUID());
                    update.setString(2, profileCode == null || profileCode.isBlank() ? "模型渠道" : profileCode);
                    update.setString(3, profileCode);
                    update.setString(4, modelCode);
                    update.addBatch();
                }
                update.executeBatch();
            }

            try (var statement = conn.createStatement()) {
                statement.execute("ALTER TABLE harness_model_setting DROP COLUMN profile_code");
                statement.execute("ALTER TABLE harness_model_setting ADD PRIMARY KEY (channel_code)");
            }
            log.info("Schema migration: harness_model_setting profile_code -> channel_code");
        }
    }

    private int primaryKeyColumnCount(Connection conn) throws Exception {
        int count = 0;
        try (ResultSet keys = conn.getMetaData().getPrimaryKeys(
                conn.getCatalog(), null, "harness_model_setting")) {
            while (keys.next()) {
                count++;
            }
        }
        if (count == 0) {
            try (ResultSet keys = conn.getMetaData().getPrimaryKeys(
                    conn.getCatalog(), null, "HARNESS_MODEL_SETTING")) {
                while (keys.next()) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countChannels(Connection conn, String condition) throws Exception {
        String sql = "SELECT COUNT(*) FROM harness_model_setting WHERE " + condition;
        try (PreparedStatement statement = conn.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String findFirstChannel(Connection conn, String condition) throws Exception {
        String sql = "SELECT channel_code FROM harness_model_setting WHERE " + condition
                + " ORDER BY updated_at ASC LIMIT 1";
        try (PreparedStatement statement = conn.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getString("channel_code") : null;
        }
    }

    private void addColumnIfMissing(String table, String column, String ddl) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            if (columnExists(conn, table, column)) {
                return;
            }
            try (var st = conn.createStatement()) {
                st.execute(ddl);
                log.info("Schema migration: added column {}.{}", table, column);
            }
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        String[][] candidates = {
                {table, column},
                {table.toUpperCase(), column.toUpperCase()},
                {table.toLowerCase(), column.toLowerCase()}
        };
        for (String[] pair : candidates) {
            try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, pair[0], pair[1])) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
