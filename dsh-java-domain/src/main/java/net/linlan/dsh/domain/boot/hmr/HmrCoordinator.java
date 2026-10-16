package net.linlan.dsh.domain.boot.hmr;

import net.linlan.dsh.domain.plugin.runtime.service.ModuleLevelHmrService;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * HmrCoordinator 复用既有 ModuleLevelHmrService，协调模块与配置重载并与包修改互斥执行（重载锁）。
 *
 * <p>重载期间包修改等待不产生半成品状态。</p>
 *
 */
public class HmrCoordinator {

    private final ModuleLevelHmrService hmrService;
    private final ReentrantLock reloadLock = new ReentrantLock();

    public HmrCoordinator(ModuleLevelHmrService hmrService) {
        this.hmrService = Objects.requireNonNull(hmrService, "hmrService 不能为空");
    }

    public void reloadModule(String moduleId) {
        Objects.requireNonNull(moduleId, "moduleId 不能为空");
        reloadLock.lock();
        try {
            hmrService.refreshManifest(moduleId);
            hmrService.refreshResolution(moduleId);
        } finally {
            reloadLock.unlock();
        }
    }

    public void reloadConfig(Runnable configReloadAction) {
        Objects.requireNonNull(configReloadAction, "configReloadAction 不能为空");
        reloadLock.lock();
        try {
            configReloadAction.run();
        } finally {
            reloadLock.unlock();
        }
    }

    public boolean isReloadInProgress() {
        return reloadLock.isLocked();
    }

    public void executeWithReloadLock(Runnable action) {
        Objects.requireNonNull(action, "action 不能为空");
        reloadLock.lock();
        try {
            action.run();
        } finally {
            reloadLock.unlock();
        }
    }
}
