package net.linlan.dsh.domain.session.checkpoint.adapter.repository;

import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ICheckpointRepository 检查点仓储端口契约")
class ICheckpointRepositoryTest {

    @Test
    @DisplayName("端口声明 save/loadLatest/loadAll 三个方法")
    void portDeclaresThreeMethods() throws NoSuchMethodException {
        var save = ICheckpointRepository.class.getMethod(
                "save", CheckpointSnapshot.class);
        var loadLatest = ICheckpointRepository.class.getMethod(
                "loadLatest", String.class);
        var loadAll = ICheckpointRepository.class.getMethod(
                "loadAll", String.class);

        assertEquals(void.class, save.getReturnType(),
                "save 应返回 void");
        assertEquals(Optional.class, loadLatest.getReturnType(),
                "loadLatest 应返回 Optional");
        assertEquals(List.class, loadAll.getReturnType(),
                "loadAll 应返回 List");
    }

    @Test
    @DisplayName("端口方法数量恰为 3，防止意外变更契约")
    void methodCountIsThree() {
        Method[] publicMethods = java.util.Arrays.stream(ICheckpointRepository.class.getMethods())
                .filter(m -> m.getDeclaringClass() == ICheckpointRepository.class)
                .toArray(Method[]::new);
        assertEquals(3, publicMethods.length,
                "ICheckpointRepository 应只声明 save/loadLatest/loadAll");
    }

    @Test
    @DisplayName("匿名实现：save 写入的快照可被 loadLatest 读出")
    void anonymousImplSaveThenLoadLatest() {
        var holder = new AtomicReference<CheckpointSnapshot>(null);
        ICheckpointRepository repo = new ICheckpointRepository() {
            @Override
            public void save(CheckpointSnapshot snapshot) {
                holder.set(snapshot);
            }
            @Override
            public Optional<CheckpointSnapshot> loadLatest(String sessionId) {
                var s = holder.get();
                return s != null && s.sessionId().equals(sessionId)
                        ? Optional.of(s) : Optional.empty();
            }
            @Override
            public List<CheckpointSnapshot> loadAll(String sessionId) {
                var s = holder.get();
                return s != null && s.sessionId().equals(sessionId) ? List.of(s) : List.of();
            }
        };

        var snap = CheckpointSnapshot.of("sess-x", 5, "before-model", "{}");
        repo.save(snap);
        var loaded = repo.loadLatest("sess-x");
        assertTrue(loaded.isPresent());
        assertEquals(snap, loaded.get());
        assertEquals(1, repo.loadAll("sess-x").size());
    }

    @Test
    @DisplayName("匿名实现：空仓储 loadLatest 返回 empty")
    void anonymousImplEmptyReturnsEmpty() {
        ICheckpointRepository repo = new ICheckpointRepository() {
            @Override
            public void save(CheckpointSnapshot snapshot) {}
            @Override
            public Optional<CheckpointSnapshot> loadLatest(String sessionId) {
                return Optional.empty();
            }
            @Override
            public List<CheckpointSnapshot> loadAll(String sessionId) {
                return List.of();
            }
        };
        assertTrue(repo.loadLatest("any").isEmpty());
        assertTrue(repo.loadAll("any").isEmpty());
    }
}
