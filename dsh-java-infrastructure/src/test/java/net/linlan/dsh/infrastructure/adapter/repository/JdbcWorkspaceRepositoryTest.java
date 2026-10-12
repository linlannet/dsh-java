package net.linlan.dsh.infrastructure.adapter.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import net.linlan.dsh.domain.session.workspace.model.entity.WorkspaceEntity;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.025 JdbcWorkspaceRepository H2 持久化")
class JdbcWorkspaceRepositoryTest {

    private static DataSource newH2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:ws-test-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    @DisplayName("构造函数建表不抛异常")
    void constructorCreatesTables() {
        DataSource ds = newH2DataSource();
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(ds);
        assertTrue(repo != null);
    }

    @Test
    @DisplayName("save 后 findById 返回相同 workspace")
    void saveThenFindByIdReturnsWorkspace() {
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(newH2DataSource());
        WorkspaceEntity ws = WorkspaceEntity.of("ws-1", "/project");

        repo.save(ws);
        Optional<WorkspaceEntity> found = repo.findById("ws-1");

        assertTrue(found.isPresent());
        assertEquals("ws-1", found.get().workspaceId());
        assertEquals("/project", found.get().rootPath());
        assertTrue(found.get().attachedSessionIds().isEmpty());
    }

    @Test
    @DisplayName("save 带 attached sessions 后 listAttachedSessions 返回会话列表")
    void saveWithSessionsThenListAttachedSessions() {
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(newH2DataSource());
        WorkspaceEntity ws = new WorkspaceEntity("ws-2", "/repo", List.of("s1", "s2"));

        repo.save(ws);
        List<String> sessions = repo.listAttachedSessions("ws-2");

        assertEquals(List.of("s1", "s2"), sessions);
    }

    @Test
    @DisplayName("save 后 findById 返回带 attached sessions 的实体")
    void saveThenFindByIdReturnsEntityWithSessions() {
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(newH2DataSource());
        WorkspaceEntity ws = new WorkspaceEntity("ws-3", "/repo", List.of("s1"));

        repo.save(ws);
        Optional<WorkspaceEntity> found = repo.findById("ws-3");

        assertTrue(found.isPresent());
        assertEquals(List.of("s1"), found.get().attachedSessionIds());
    }

    @Test
    @DisplayName("save 对已存在 workspace 执行 upsert 更新 rootPath")
    void saveUpsertsExistingWorkspaceRootPath() {
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(newH2DataSource());
        repo.save(WorkspaceEntity.of("ws-4", "/old"));
        repo.save(WorkspaceEntity.of("ws-4", "/new"));

        Optional<WorkspaceEntity> found = repo.findById("ws-4");
        assertTrue(found.isPresent());
        assertEquals("/new", found.get().rootPath());
    }

    @Test
    @DisplayName("save 替换 attached sessions（先 s1,s2 再 s3）")
    void saveReplacesAttachedSessions() {
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(newH2DataSource());
        repo.save(new WorkspaceEntity("ws-5", "/repo", List.of("s1", "s2")));
        repo.save(new WorkspaceEntity("ws-5", "/repo", List.of("s3")));

        List<String> sessions = repo.listAttachedSessions("ws-5");
        assertEquals(List.of("s3"), sessions);
    }

    @Test
    @DisplayName("findById 不存在返回 empty")
    void findByIdReturnsEmptyWhenMissing() {
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(newH2DataSource());
        Optional<WorkspaceEntity> found = repo.findById("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("listAttachedSessions 不存在 workspace 返回空列表")
    void listAttachedSessionsReturnsEmptyForMissingWorkspace() {
        JdbcWorkspaceRepository repo = new JdbcWorkspaceRepository(newH2DataSource());
        List<String> sessions = repo.listAttachedSessions("missing");
        assertTrue(sessions.isEmpty());
    }
}
