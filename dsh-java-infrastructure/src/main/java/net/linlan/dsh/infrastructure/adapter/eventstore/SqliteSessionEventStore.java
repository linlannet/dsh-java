package net.linlan.dsh.infrastructure.adapter.eventstore;

import net.linlan.dsh.domain.session.event.adapter.repository.SessionPersistenceSeam;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class SqliteSessionEventStore implements SessionPersistenceSeam {

    private static final String HEADER_DDL = """
            CREATE TABLE IF NOT EXISTS session_header (
                session_id TEXT PRIMARY KEY,
                version INTEGER NOT NULL,
                created_at TEXT NOT NULL,
                cwd TEXT,
                parent_session TEXT,
                seed_length INTEGER NOT NULL,
                origin TEXT,
                delegation_depth INTEGER NOT NULL,
                agent_preset TEXT,
                format_version INTEGER NOT NULL
            )
            """;

    private static final String EVENT_DDL = """
            CREATE TABLE IF NOT EXISTS session_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                seq INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                event_time TEXT NOT NULL,
                payload_json TEXT,
                surface_op TEXT,
                source_event_seqs TEXT
            )
            """;

    private static final String EVENT_INDEX_DDL = """
            CREATE INDEX IF NOT EXISTS idx_session_event_session_seq
            ON session_event(session_id, seq)
            """;

    private final Connection connection;
    private final ObjectMapper objectMapper;

    public SqliteSessionEventStore(String dbPath) {
        try {
            Properties props = new Properties();
            props.setProperty("journal_mode", "WAL");
            props.setProperty("foreign_keys", "true");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath, props);
            this.objectMapper = new ObjectMapper();
            this.objectMapper.findAndRegisterModules();
            initSchema();
            enableWal();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open sqlite session store: " + dbPath, e);
        }
    }

    public SqliteSessionEventStore(Connection connection) {
        this.connection = connection;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        try {
            initSchema();
            enableWal();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to init sqlite schema", e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(HEADER_DDL);
            st.execute(EVENT_DDL);
            st.execute(EVENT_INDEX_DDL);
        }
    }

    private void enableWal() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
        }
    }

    @Override
    public void saveHeader(SessionHeader header) {
        String sql = """
                INSERT OR REPLACE INTO session_header
                (session_id, version, created_at, cwd, parent_session, seed_length,
                 origin, delegation_depth, agent_preset, format_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, header.sessionId());
            ps.setInt(2, header.version());
            ps.setString(3, header.createdAt().toString());
            ps.setString(4, header.cwd());
            ps.setString(5, header.parentSession());
            ps.setInt(6, header.seedLength());
            ps.setString(7, header.origin());
            ps.setInt(8, header.delegationDepth());
            ps.setString(9, header.agentPreset());
            ps.setInt(10, header.formatVersion());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save header", e);
        }
    }

    @Override
    public Optional<SessionHeader> getHeader(String sessionId) {
        String sql = """
                SELECT version, session_id, created_at, cwd, parent_session, seed_length,
                       origin, delegation_depth, agent_preset, format_version
                FROM session_header WHERE session_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new SessionHeader(
                            rs.getInt("version"),
                            rs.getString("session_id"),
                            java.time.Instant.parse(rs.getString("created_at")),
                            rs.getString("cwd"),
                            rs.getString("parent_session"),
                            rs.getInt("seed_length"),
                            rs.getString("origin"),
                            rs.getInt("delegation_depth"),
                            rs.getString("agent_preset"),
                            rs.getInt("format_version")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get header", e);
        }
    }

    @Override
    public void append(String sessionId, SessionEvent event) {
        String sql = """
                INSERT INTO session_event
                (session_id, seq, event_type, event_time, payload_json, surface_op, source_event_seqs)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindEvent(ps, sessionId, event);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append event", e);
        }
    }

    @Override
    public void appendBatch(String sessionId, List<SessionEvent> events) {
        String sql = """
                INSERT INTO session_event
                (session_id, seq, event_type, event_time, payload_json, surface_op, source_event_seqs)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (SessionEvent event : events) {
                bindEvent(ps, sessionId, event);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append batch", e);
        }
    }

    @Override
    public List<SessionEvent> readAll(String sessionId) {
        return readEvents(sessionId, "SELECT seq, event_type, event_time, payload_json FROM session_event WHERE session_id = ? ORDER BY seq ASC");
    }

    @Override
    public List<SessionEvent> readAfter(String sessionId, long afterSeq) {
        String sql = "SELECT seq, event_type, event_time, payload_json FROM session_event WHERE session_id = ? AND seq > ? ORDER BY seq ASC";
        List<SessionEvent> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setLong(2, afterSeq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapGenericEvent(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read after", e);
        }
        return result;
    }

    @Override
    public List<SessionEvent> readRange(String sessionId, long fromSeq, long toSeq) {
        String sql = "SELECT seq, event_type, event_time, payload_json FROM session_event WHERE session_id = ? AND seq >= ? AND seq <= ? ORDER BY seq ASC";
        List<SessionEvent> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setLong(2, fromSeq);
            ps.setLong(3, toSeq);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapGenericEvent(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read range", e);
        }
        return result;
    }

    @Override
    public long getLastSeq(String sessionId) {
        String sql = "SELECT MAX(seq) AS max_seq FROM session_event WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long val = rs.getLong("max_seq");
                    if (rs.wasNull()) {
                        return -1;
                    }
                    return val;
                }
                return -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get last seq", e);
        }
    }

    @Override
    public long count(String sessionId) {
        String sql = "SELECT COUNT(*) AS cnt FROM session_event WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("cnt");
                }
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count", e);
        }
    }

    @Override
    public List<SessionHeader> listHeaders(int limit) {
        String sql = """
                SELECT version, session_id, created_at, cwd, parent_session, seed_length,
                       origin, delegation_depth, agent_preset, format_version
                FROM session_header ORDER BY created_at DESC LIMIT ?
                """;
        List<SessionHeader> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SessionHeader(
                            rs.getInt("version"),
                            rs.getString("session_id"),
                            java.time.Instant.parse(rs.getString("created_at")),
                            rs.getString("cwd"),
                            rs.getString("parent_session"),
                            rs.getInt("seed_length"),
                            rs.getString("origin"),
                            rs.getInt("delegation_depth"),
                            rs.getString("agent_preset"),
                            rs.getInt("format_version")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list headers", e);
        }
        return result;
    }

    public void close() {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close connection", e);
        }
    }

    private List<SessionEvent> readEvents(String sessionId, String sql) {
        List<SessionEvent> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapGenericEvent(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read events", e);
        }
        return result;
    }

    private void bindEvent(PreparedStatement ps, String sessionId, SessionEvent event) throws SQLException {
        ps.setString(1, sessionId);
        ps.setLong(2, event.seq());
        ps.setString(3, event.type().wireName());
        ps.setString(4, event.time().toString());
        ps.setString(5, serializePayload(event));
        ps.setString(6, null);
        ps.setString(7, null);
    }

    private String serializePayload(SessionEvent event) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "type", event.type().wireName(),
                    "seq", event.seq(),
                    "time", event.time().toString()
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    private SessionEvent mapGenericEvent(ResultSet rs) throws SQLException {
        long seq = rs.getLong("seq");
        java.time.Instant time = java.time.Instant.parse(rs.getString("event_time"));
        String eventType = rs.getString("event_type");
        String payload = rs.getString("payload_json");
        return new SessionEvent.Generic(seq, time,
                net.linlan.dsh.domain.session.event.model.valobj.SessionEventType.fromWireName(eventType),
                payload == null ? "" : payload);
    }
}
