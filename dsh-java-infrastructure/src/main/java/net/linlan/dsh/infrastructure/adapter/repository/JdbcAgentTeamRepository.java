package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.agent.team.adapter.port.IAgentTeamRepository;
import net.linlan.dsh.domain.agent.team.model.entity.SharedTask;
import net.linlan.dsh.domain.agent.team.model.entity.TeamMessage;
import net.linlan.dsh.domain.agent.team.model.valobj.AgentTeamRoster;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JdbcAgentTeamRepository 多代理团队 JDBC 仓储。
 *
 * <p>操作 agent_team / team_task / team_message 三张表，实验性扩展，默认关闭。</p>
 *
 */
@Repository
public class JdbcAgentTeamRepository implements IAgentTeamRepository {

    private static final String CREATE_TEAM_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS agent_team (
                team_id VARCHAR(64) PRIMARY KEY,
                lead VARCHAR(128) NOT NULL,
                teammates_json TEXT,
                message_strategy VARCHAR(32) NOT NULL,
                created_at TIMESTAMP NOT NULL
            )
            """;

    private static final String CREATE_TASK_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS team_task (
                task_id VARCHAR(64) PRIMARY KEY,
                team_id VARCHAR(64) NOT NULL,
                title VARCHAR(255) NOT NULL,
                status VARCHAR(32) NOT NULL,
                assignee VARCHAR(128),
                created_at TIMESTAMP NOT NULL,
                INDEX idx_team_task_team (team_id)
            )
            """;

    private static final String CREATE_MESSAGE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS team_message (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                team_id VARCHAR(64) NOT NULL,
                from_agent VARCHAR(128) NOT NULL,
                to_agent VARCHAR(128) NOT NULL,
                content TEXT,
                created_at TIMESTAMP NOT NULL,
                INDEX idx_team_message_team (team_id)
            )
            """;

    private static final String INSERT_TEAM_SQL = """
            INSERT INTO agent_team (team_id, lead, teammates_json, message_strategy, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_TEAM_SQL = """
            UPDATE agent_team SET lead = ?, teammates_json = ?, message_strategy = ?
            WHERE team_id = ?
            """;

    private static final String SELECT_TEAM_SQL = """
            SELECT team_id, lead, teammates_json, message_strategy
            FROM agent_team WHERE team_id = ?
            """;

    private static final String INSERT_TASK_SQL = """
            INSERT INTO team_task (task_id, team_id, title, status, assignee, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_TASK_SQL = """
            UPDATE team_task SET title = ?, status = ?, assignee = ?
            WHERE task_id = ?
            """;

    private static final String SELECT_TASKS_SQL = """
            SELECT task_id, team_id, title, status, assignee, created_at
            FROM team_task WHERE team_id = ?
            ORDER BY created_at ASC
            """;

    private static final String INSERT_MESSAGE_SQL = """
            INSERT INTO team_message (team_id, from_agent, to_agent, content, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcAgentTeamRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureTable();
    }

    private void ensureTable() {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(CREATE_TEAM_TABLE_SQL);
            st.execute(CREATE_TASK_TABLE_SQL);
            st.execute(CREATE_MESSAGE_TABLE_SQL);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure agent_team tables", e);
        }
    }

    @Override
    public void save(AgentTeamRoster roster) {
        String teammatesJson = serializeTeammates(roster.teammates());
        try (Connection c = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(UPDATE_TEAM_SQL)) {
                ps.setString(1, roster.lead());
                ps.setString(2, teammatesJson);
                ps.setString(3, roster.messageStrategy());
                ps.setString(4, roster.teamId());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = c.prepareStatement(INSERT_TEAM_SQL)) {
                    ps.setString(1, roster.teamId());
                    ps.setString(2, roster.lead());
                    ps.setString(3, teammatesJson);
                    ps.setString(4, roster.messageStrategy());
                    ps.setTimestamp(5, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save agent team", e);
        }
    }

    @Override
    public Optional<AgentTeamRoster> findById(String teamId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_TEAM_SQL)) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new AgentTeamRoster(
                            rs.getString("team_id"),
                            rs.getString("lead"),
                            deserializeTeammates(rs.getString("teammates_json")),
                            rs.getString("message_strategy")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load agent team", e);
        }
    }

    @Override
    public void saveMessage(TeamMessage message) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_MESSAGE_SQL)) {
            ps.setString(1, message.teamId());
            ps.setString(2, message.fromAgent());
            ps.setString(3, message.toAgent());
            ps.setString(4, message.content());
            ps.setTimestamp(5, Timestamp.from(message.createdAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save team message", e);
        }
    }

    @Override
    public void saveTask(SharedTask task) {
        try (Connection c = dataSource.getConnection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(UPDATE_TASK_SQL)) {
                ps.setString(1, task.title());
                ps.setString(2, task.status());
                ps.setString(3, task.assignee());
                ps.setString(4, task.taskId());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = c.prepareStatement(INSERT_TASK_SQL)) {
                    ps.setString(1, task.taskId());
                    ps.setString(2, task.teamId());
                    ps.setString(3, task.title());
                    ps.setString(4, task.status());
                    ps.setString(5, task.assignee());
                    ps.setTimestamp(6, Timestamp.from(task.createdAt()));
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save team task", e);
        }
    }

    @Override
    public List<SharedTask> findTasks(String teamId) {
        List<SharedTask> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_TASKS_SQL)) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SharedTask(
                            rs.getString("task_id"),
                            rs.getString("team_id"),
                            rs.getString("title"),
                            rs.getString("status"),
                            rs.getString("assignee"),
                            rs.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load team tasks", e);
        }
        return result;
    }

    private String serializeTeammates(List<String> teammates) {
        try {
            return mapper.writeValueAsString(teammates == null ? List.of() : teammates);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize teammates", e);
        }
    }

    private List<String> deserializeTeammates(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize teammates", e);
        }
    }
}