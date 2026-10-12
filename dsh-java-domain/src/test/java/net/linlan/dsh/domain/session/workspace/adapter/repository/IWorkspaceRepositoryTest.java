package net.linlan.dsh.domain.session.workspace.adapter.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.linlan.dsh.domain.session.workspace.model.entity.WorkspaceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.025 IWorkspaceRepository 端口契约")
class IWorkspaceRepositoryTest {

    @Test
    @DisplayName("save 持久化 WorkspaceEntity")
    void savePersistsWorkspaceEntity() {
        StubRepository repo = new StubRepository();
        WorkspaceEntity ws = WorkspaceEntity.of("ws-1", "/project");
        repo.save(ws);
        assertEquals(ws, repo.saved);
    }

    @Test
    @DisplayName("findById 返回 Optional<WorkspaceEntity>")
    void findByIdReturnsOptional() {
        StubRepository repo = new StubRepository();
        repo.stored = WorkspaceEntity.of("ws-1", "/project", List.of("s1"));
        Optional<WorkspaceEntity> result = repo.findById("ws-1");
        assertTrue(result.isPresent());
        assertEquals("ws-1", result.get().workspaceId());
    }

    @Test
    @DisplayName("findById 未找到返回空 Optional")
    void findByIdReturnsEmptyWhenMissing() {
        StubRepository repo = new StubRepository();
        Optional<WorkspaceEntity> result = repo.findById("missing");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listAttachedSessions 返回会话 ID 列表")
    void listAttachedSessionsReturnsIds() {
        StubRepository repo = new StubRepository();
        repo.sessions = List.of("s1", "s2");
        List<String> sessions = repo.listAttachedSessions("ws-1");
        assertEquals(List.of("s1", "s2"), sessions);
    }

    @Test
    @DisplayName("实现类实例是 IWorkspaceRepository 子类型")
    void implementationIsPortSubtype() {
        IWorkspaceRepository repo = new StubRepository();
        assertNotNull(repo);
        assert IWorkspaceRepository.class.isInstance(repo);
    }

    static final class StubRepository implements IWorkspaceRepository {
        WorkspaceEntity saved;
        WorkspaceEntity stored;
        List<String> sessions = List.of();

        @Override
        public void save(WorkspaceEntity workspace) {
            this.saved = workspace;
        }

        @Override
        public Optional<WorkspaceEntity> findById(String workspaceId) {
            return stored == null ? Optional.empty() : Optional.of(stored);
        }

        @Override
        public List<String> listAttachedSessions(String workspaceId) {
            return sessions;
        }
    }
}
