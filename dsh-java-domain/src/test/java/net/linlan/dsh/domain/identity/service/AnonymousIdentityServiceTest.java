package net.linlan.dsh.domain.identity.service;

import net.linlan.dsh.domain.identity.adapter.port.IAnonymousIdStoragePort;
import net.linlan.dsh.domain.identity.model.valobj.AnonymousUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnonymousIdentityService 匿名标识服务")
class AnonymousIdentityServiceTest {

    private static class InMemoryStorage implements IAnonymousIdStoragePort {
        final AtomicReference<AnonymousUserId> store = new AtomicReference<>();

        @Override
        public Optional<AnonymousUserId> read() {
            return Optional.ofNullable(store.get());
        }

        @Override
        public void write(AnonymousUserId id) {
            store.set(id);
        }

        @Override
        public boolean exists() {
            return store.get() != null;
        }
    }

    @Test
    @DisplayName("首次调用生成新标识")
    void firstCallGenerates() {
        var storage = new InMemoryStorage();
        var service = new AnonymousIdentityService(storage);

        var id = service.getOrCreate();
        assertNotNull(id);
        assertTrue(storage.exists());
    }

    @Test
    @DisplayName("跨重启保留相同标识")
    void persistAcrossRestart() {
        var storage = new InMemoryStorage();
        var id1 = new AnonymousIdentityService(storage).getOrCreate();
        var id2 = new AnonymousIdentityService(storage).getOrCreate();

        assertEquals(id1, id2);
    }

    @Test
    @DisplayName("删除后重新生成不同标识")
    void regenerateAfterDelete() {
        var storage = new InMemoryStorage();
        var service = new AnonymousIdentityService(storage);

        var id1 = service.getOrCreate();
        storage.store.set(null);
        var id2 = service.getOrCreate();

        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("regenerate 强制重新生成")
    void regenerateForced() {
        var storage = new InMemoryStorage();
        var service = new AnonymousIdentityService(storage);

        var id1 = service.getOrCreate();
        var id2 = service.regenerate();

        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("current 返回当前标识")
    void currentReturnsExisting() {
        var storage = new InMemoryStorage();
        var service = new AnonymousIdentityService(storage);

        assertTrue(service.current().isEmpty());
        service.getOrCreate();
        assertTrue(service.current().isPresent());
    }
}
