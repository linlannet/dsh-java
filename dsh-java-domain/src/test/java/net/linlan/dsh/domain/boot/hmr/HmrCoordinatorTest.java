package net.linlan.dsh.domain.boot.hmr;

import net.linlan.dsh.domain.plugin.runtime.service.ModuleLevelHmrService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HmrCoordinator 热重载协调")
class HmrCoordinatorTest {

    @Test
    @DisplayName("reloadModule 获取并释放重载锁")
    void reloadModuleAcquiresLock() {
        var hmr = new ModuleLevelHmrService();
        var coordinator = new HmrCoordinator(hmr);

        coordinator.reloadModule("test-module");
        assertFalse(coordinator.isReloadInProgress());
    }

    @Test
    @DisplayName("reloadConfig 在锁内执行")
    void reloadConfigUnderLock() {
        var hmr = new ModuleLevelHmrService();
        var coordinator = new HmrCoordinator(hmr);
        var executed = new boolean[]{false};

        coordinator.reloadConfig(() -> {
            executed[0] = coordinator.isReloadInProgress();
        });

        assertTrue(executed[0]);
        assertFalse(coordinator.isReloadInProgress());
    }

    @Test
    @DisplayName("executeWithReloadLock 互斥执行")
    void executeWithLockMutex() {
        var hmr = new ModuleLevelHmrService();
        var coordinator = new HmrCoordinator(hmr);
        var insideLock = new boolean[]{false};

        coordinator.executeWithReloadLock(() -> {
            insideLock[0] = coordinator.isReloadInProgress();
        });

        assertTrue(insideLock[0]);
        assertFalse(coordinator.isReloadInProgress());
    }
}
