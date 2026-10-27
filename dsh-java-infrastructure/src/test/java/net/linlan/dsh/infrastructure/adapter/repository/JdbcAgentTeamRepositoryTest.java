package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.agent.team.model.entity.SharedTask;
import net.linlan.dsh.domain.agent.team.model.entity.TeamMessage;
import net.linlan.dsh.domain.agent.team.model.valobj.AgentTeamRoster;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcAgentTeamRepositoryTest 多代理团队 JDBC 仓储单元测试。
 *
 * <p>使用 H2 内存库（MySQL 兼容模式）验证 agent_team / team_task / team_message
 * 三张表的读写契约：幂等更新、往返一致性、按 teamId 过滤与 createdAt 升序。</p>
 */
@DisplayName("JdbcAgentTeamRepository 多代理团队 JDBC 仓储")
public class JdbcAgentTeamRepositoryTest {

    private static final String URL = "jdbc:h2:mem:agent-team-test;MODE=MySQL;DB_CLOSE_DELAY=-1";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private Connection holdConnection;
    private DataSource dataSource;
    private JdbcAgentTeamRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        // 维持一条长连接，避免 H2 内存库在无连接时被回收
        holdConnection = DriverManager.getConnection(URL, USER, PASSWORD);
        dataSource = new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(URL, USER, PASSWORD);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return DriverManager.getConnection(URL, username, password);
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getGlobal();
            }
        };
        repository = new JdbcAgentTeamRepository(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Statement st = holdConnection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS team_message");
            st.execute("DROP TABLE IF EXISTS team_task");
            st.execute("DROP TABLE IF EXISTS agent_team");
        }
        holdConnection.close();
    }

    private Connection newConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private int countRows(String table) throws SQLException {
        try (Connection c = newConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 按自增主键顺序读取消息三元组，用于验证追加写入的字段内容。 */
    private List<String> queryMessageContents() throws SQLException {
        List<String> contents = new ArrayList<>();
        try (Connection c = newConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT content FROM team_message ORDER BY id ASC")) {
            while (rs.next()) {
                contents.add(rs.getString("content"));
            }
        }
        return contents;
    }

    /** 读取指定消息的接收方与创建时间，用于验证字段级往返。 */
    private Object[] queryMessageDetail() throws SQLException {
        try (Connection c = newConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT to_agent, created_at FROM team_message ORDER BY id ASC LIMIT 1")) {
            if (rs.next()) {
                return new Object[]{rs.getString("to_agent"), rs.getTimestamp("created_at").toInstant()};
            }
            return null;
        }
    }

    // ================= agent_team =================

    // 场景：save 后 findById 应完整往返团队花名册（含 teammates 列表与消息策略）
    @Test
    @DisplayName("save 后 findById 往返花名册全部字段")
    void saveThenFindByIdRoundTrip() {
        AgentTeamRoster roster = new AgentTeamRoster("team-1", "leader-a", List.of("m1", "m2"), "broadcast");

        repository.save(roster);

        Optional<AgentTeamRoster> loaded = repository.findById("team-1");
        assertTrue(loaded.isPresent(), "刚保存的团队应可加载");
        assertEquals("team-1", loaded.get().teamId(), "teamId 应往返一致");
        assertEquals("leader-a", loaded.get().leader(), "leader 应往返一致");
        assertEquals(List.of("m1", "m2"), loaded.get().teammates(), "teammates 列表应保持顺序与内容");
        assertEquals("broadcast", loaded.get().messageStrategy(), "messageStrategy 应往返一致");
    }

    // 场景：teammates 传入 null 时，值对象归一化为空列表并持久化后往返为空列表
    @Test
    @DisplayName("teammates 为 null 时持久化为空列表")
    void nullTeammatesPersistedAsEmptyList() {
        repository.save(new AgentTeamRoster("team-null", "leader-n", null, "direct"));

        Optional<AgentTeamRoster> loaded = repository.findById("team-null");
        assertTrue(loaded.isPresent(), "应能加载团队");
        assertTrue(loaded.get().teammates().isEmpty(), "null teammates 应归一化为空列表");
    }

    // 场景：同一 teamId 二次 save 应走 UPDATE 覆盖原记录而非新增行
    @Test
    @DisplayName("同 teamId 二次 save 为更新且不新增行")
    void saveIsIdempotentUpdate() throws SQLException {
        repository.save(new AgentTeamRoster("team-up", "leader-1", List.of("a"), "direct"));
        repository.save(new AgentTeamRoster("team-up", "leader-2", List.of("a", "b"), "broadcast"));

        assertEquals(1, countRows("agent_team"), "同 teamId 二次保存不应新增行");
        Optional<AgentTeamRoster> loaded = repository.findById("team-up");
        assertTrue(loaded.isPresent(), "更新后应仍可加载");
        assertEquals("leader-2", loaded.get().leader(), "leader 应被更新");
        assertEquals(List.of("a", "b"), loaded.get().teammates(), "teammates 应被更新");
        assertEquals("broadcast", loaded.get().messageStrategy(), "messageStrategy 应被更新");
    }

    // 场景：查询不存在的 teamId 应返回 empty
    @Test
    @DisplayName("findById 对不存在的团队返回 empty")
    void findByIdMissingTeamReturnsEmpty() {
        assertTrue(repository.findById("no-such-team").isEmpty(), "不存在的团队应返回 empty");
    }

    // 场景：构造仓储时自动建表，重复构造不应报错
    @Test
    @DisplayName("构造时自动建表且幂等")
    void ensureTableIdempotent() {
        assertDoesNotThrow(() -> new JdbcAgentTeamRepository(dataSource), "重复构造不应抛出异常");
    }

    // ================= team_message =================

    // 场景：saveMessage 每次插入一行，追加写入并在字段级保持内容与时间一致
    @Test
    @DisplayName("saveMessage 追加写入并保持字段一致")
    void saveMessageAppendsRows() throws SQLException {
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-01T00:01:00Z");

        repository.saveMessage(new TeamMessage("team-msg", "from-1", "to-1", "hi-1", first));
        repository.saveMessage(new TeamMessage("team-msg", "from-2", "to-2", "hi-2", second));

        assertEquals(2, countRows("team_message"), "两次 saveMessage 应追加两行");
        assertEquals(List.of("hi-1", "hi-2"), queryMessageContents(), "消息内容应按写入顺序保存");

        Object[] detail = queryMessageDetail();
        assertEquals("to-1", detail[0], "首条消息接收方应正确");
        assertEquals(first, detail[1], "首条消息创建时间应往返一致");
    }

    // ================= team_task =================

    // 场景：同 taskId 二次 saveTask 应走 UPDATE，仅保留一行且字段为最新值
    @Test
    @DisplayName("同 taskId 二次 saveTask 走更新")
    void saveTaskInsertsThenUpdates() throws SQLException {
        Instant createdAt = Instant.parse("2026-01-02T00:00:00Z");
        repository.saveTask(new SharedTask("task-1", "team-t", "title-1", "open", "alice", createdAt));

        assertEquals(1, countRows("team_task"), "首次 saveTask 应插入一行");

        repository.saveTask(new SharedTask("task-1", "team-t", "title-2", "done", "bob", createdAt));

        assertEquals(1, countRows("team_task"), "同 taskId 二次保存不应新增行");
        List<SharedTask> tasks = repository.findTasks("team-t");
        assertEquals(1, tasks.size(), "更新后仅一条任务");
        assertEquals("title-2", tasks.get(0).title(), "title 应被更新");
        assertEquals("done", tasks.get(0).status(), "status 应被更新");
        assertEquals("bob", tasks.get(0).assignee(), "assignee 应被更新");
        assertEquals(createdAt, tasks.get(0).createdAt(), "createdAt 应保持不变");
    }

    // 场景：findTasks 应按 createdAt 升序返回，即使插入顺序为乱序
    @Test
    @DisplayName("findTasks 按 createdAt 升序返回")
    void findTasksOrdersByCreatedAtAscending() {
        repository.saveTask(new SharedTask("task-late", "team-o", "late", "open", "a",
                Instant.parse("2026-01-03T00:00:00Z")));
        repository.saveTask(new SharedTask("task-early", "team-o", "early", "open", "a",
                Instant.parse("2026-01-01T00:00:00Z")));
        repository.saveTask(new SharedTask("task-mid", "team-o", "mid", "open", "a",
                Instant.parse("2026-01-02T00:00:00Z")));

        List<SharedTask> tasks = repository.findTasks("team-o");

        assertEquals(3, tasks.size(), "应返回全部任务");
        assertEquals(List.of("task-early", "task-mid", "task-late"),
                tasks.stream().map(SharedTask::taskId).toList(), "应按 createdAt 升序排列");
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), tasks.get(0).createdAt(),
                "最早任务时间应往返一致");
    }

    // 场景：findTasks 应按 teamId 过滤，不同团队的任务互不干扰
    @Test
    @DisplayName("findTasks 按 teamId 过滤且团队间隔离")
    void findTasksFiltersByTeamIdAndIsolatesTeams() {
        repository.saveTask(new SharedTask("ta-1", "team-A", "A1", "open", "a", Instant.parse("2026-01-01T00:00:00Z")));
        repository.saveTask(new SharedTask("ta-2", "team-A", "A2", "open", "a", Instant.parse("2026-01-02T00:00:00Z")));
        repository.saveTask(new SharedTask("tb-1", "team-B", "B1", "open", "b", Instant.parse("2026-01-01T00:00:00Z")));

        List<SharedTask> teamA = repository.findTasks("team-A");
        List<SharedTask> teamB = repository.findTasks("team-B");

        assertEquals(List.of("ta-1", "ta-2"), teamA.stream().map(SharedTask::taskId).toList(),
                "team-A 应只返回自己的任务");
        assertEquals(List.of("tb-1"), teamB.stream().map(SharedTask::taskId).toList(),
                "team-B 应只返回自己的任务");
    }

    // 场景：查询不存在团队的任务应返回空列表
    @Test
    @DisplayName("findTasks 对不存在的团队返回空列表")
    void findTasksMissingTeamReturnsEmpty() {
        assertTrue(repository.findTasks("no-such-team").isEmpty(), "不存在的团队应返回空列表");
    }

    // 场景：assignee 为 null 的任务应可保存并原样往返为 null
    @Test
    @DisplayName("assignee 为 null 时往返仍为 null")
    void nullAssigneeRoundTrip() {
        repository.saveTask(new SharedTask("task-null-assignee", "team-n", "t", "open", null,
                Instant.parse("2026-01-05T00:00:00Z")));

        List<SharedTask> tasks = repository.findTasks("team-n");
        assertEquals(1, tasks.size(), "应返回一条任务");
        assertEquals("task-null-assignee", tasks.get(0).taskId(), "任务标识应往返一致");
        assertNull(tasks.get(0).assignee(), "assignee 为 null 时应往返为 null");
        assertEquals(Instant.parse("2026-01-05T00:00:00Z"), tasks.get(0).createdAt(),
                "createdAt 应往返一致");
    }

    // ================= 异常转换（catch 块）=================

    /** 构造一个在第 successCount 次之后开始抛 SQLException 的 DataSource，用于触发 catch 分支。 */
    private DataSource flakyDataSource(int successCount) {
        AtomicInteger calls = new AtomicInteger();
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                if (calls.incrementAndGet() > successCount) {
                    throw new SQLException("simulated database failure");
                }
                return DriverManager.getConnection(URL, USER, PASSWORD);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getGlobal();
            }
        };
    }

    // 场景：初始化建表失败时应转换为运行时异常并保留 SQLException 原因
    @Test
    @DisplayName("构造建表失败转换为运行时异常")
    void constructorWrapsSqlExceptionFromEnsureTable() {
        DataSource alwaysFailing = flakyDataSource(0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> new JdbcAgentTeamRepository(alwaysFailing), "建表失败应转换为运行时异常");

        assertEquals("Failed to ensure agent_team tables", ex.getMessage(), "异常信息应指明建表失败");
        assertTrue(ex.getCause() instanceof SQLException, "应保留原始 SQLException 作为原因");
    }

    // 场景：save 遇到数据库故障时应转换为运行时异常
    @Test
    @DisplayName("save 遇数据库故障转换为运行时异常")
    void saveWrapsSqlException() {
        JdbcAgentTeamRepository repo =
                assertDoesNotThrow(() -> new JdbcAgentTeamRepository(flakyDataSource(1)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> repo.save(new AgentTeamRoster("team-x", "leader", List.of("a"), "direct")));

        assertEquals("Failed to save agent team", ex.getMessage(), "异常信息应指明保存失败");
        assertTrue(ex.getCause() instanceof SQLException, "应保留原始 SQLException 作为原因");
    }

    // 场景：findById 遇到数据库故障时应转换为运行时异常
    @Test
    @DisplayName("findById 遇数据库故障转换为运行时异常")
    void findByIdWrapsSqlException() {
        JdbcAgentTeamRepository repo =
                assertDoesNotThrow(() -> new JdbcAgentTeamRepository(flakyDataSource(1)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> repo.findById("any"));

        assertEquals("Failed to load agent team", ex.getMessage(), "异常信息应指明加载失败");
        assertTrue(ex.getCause() instanceof SQLException, "应保留原始 SQLException 作为原因");
    }

    // 场景：saveMessage 遇到数据库故障时应转换为运行时异常
    @Test
    @DisplayName("saveMessage 遇数据库故障转换为运行时异常")
    void saveMessageWrapsSqlException() {
        JdbcAgentTeamRepository repo =
                assertDoesNotThrow(() -> new JdbcAgentTeamRepository(flakyDataSource(1)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> repo.saveMessage(
                new TeamMessage("team-x", "a", "b", "hi", Instant.parse("2026-01-01T00:00:00Z"))));

        assertEquals("Failed to save team message", ex.getMessage(), "异常信息应指明消息保存失败");
        assertTrue(ex.getCause() instanceof SQLException, "应保留原始 SQLException 作为原因");
    }

    // 场景：saveTask 遇到数据库故障时应转换为运行时异常
    @Test
    @DisplayName("saveTask 遇数据库故障转换为运行时异常")
    void saveTaskWrapsSqlException() {
        JdbcAgentTeamRepository repo =
                assertDoesNotThrow(() -> new JdbcAgentTeamRepository(flakyDataSource(1)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> repo.saveTask(new SharedTask(
                "task-x", "team-x", "t", "open", "a", Instant.parse("2026-01-01T00:00:00Z"))));

        assertEquals("Failed to save team task", ex.getMessage(), "异常信息应指明任务保存失败");
        assertTrue(ex.getCause() instanceof SQLException, "应保留原始 SQLException 作为原因");
    }

    // 场景：findTasks 遇到数据库故障时应转换为运行时异常
    @Test
    @DisplayName("findTasks 遇数据库故障转换为运行时异常")
    void findTasksWrapsSqlException() {
        JdbcAgentTeamRepository repo =
                assertDoesNotThrow(() -> new JdbcAgentTeamRepository(flakyDataSource(1)));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> repo.findTasks("team-x"));

        assertEquals("Failed to load team tasks", ex.getMessage(), "异常信息应指明任务加载失败");
        assertTrue(ex.getCause() instanceof SQLException, "应保留原始 SQLException 作为原因");
    }

    // 场景：teammates_json 为 null 时反序列化降级为空列表
    @Test
    @DisplayName("teammates_json 为 null 时返回空列表")
    void nullTeammatesJsonReturnsEmptyList() throws SQLException {
        insertTeamRow("team-null-json", null);

        Optional<AgentTeamRoster> loaded = repository.findById("team-null-json");

        assertTrue(loaded.isPresent(), "应能加载团队");
        assertTrue(loaded.get().teammates().isEmpty(), "null JSON 应降级为空列表");
    }

    // 场景：teammates_json 为非法 JSON 时反序列化失败，应转换为运行时异常
    @Test
    @DisplayName("非法 teammates_json 转换为运行时异常")
    void malformedTeammatesJsonWrapsException() throws SQLException {
        insertTeamRow("team-bad-json", "{not-an-array}");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> repository.findById("team-bad-json"));

        assertEquals("Failed to deserialize teammates", ex.getMessage(), "异常信息应指明反序列化失败");
    }

    /** 直接写入一行 agent_team，用于构造绕过领域对象的异常输入。 */
    private void insertTeamRow(String teamId, String teammatesJson) throws SQLException {
        try (Connection c = newConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO agent_team (team_id, leader, teammates_json, message_strategy, created_at)"
                             + " VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, teamId);
            ps.setString(2, "leader");
            ps.setString(3, teammatesJson);
            ps.setString(4, "direct");
            ps.setTimestamp(5, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
            ps.executeUpdate();
        }
    }

    // 场景：teammates_json 为空字符串（非 null）时反序列化降级为空列表
    @Test
    @DisplayName("teammates_json 为空串时返回空列表")
    void blankTeammatesJsonReturnsEmptyList() throws SQLException {
        insertTeamRow("team-blank-json", "");

        Optional<AgentTeamRoster> loaded = repository.findById("team-blank-json");

        assertTrue(loaded.isPresent(), "应能加载团队");
        assertEquals("team-blank-json", loaded.get().teamId(), "teamId 应往返一致");
        assertTrue(loaded.get().teammates().isEmpty(), "空白 JSON 应降级为空列表");
    }
}