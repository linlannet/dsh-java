package net.linlan.dsh.domain.plugin.runtime.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModuleLevelHmrService 是模块级热重载服务。
 * <p>监听粒度从 JAR 细化到模块源码目录。文件变更后 debounce 合流，
 * 刷新 manifest 和 runtime resolution。每个模块独立 URLClassLoader，
 * 重载时 close 旧加载器避免泄漏。重载失败保留旧版本（回滚）。</p>
 */
public class ModuleLevelHmrService {

    private static final Logger log = LoggerFactory.getLogger(ModuleLevelHmrService.class);

    private static final long DEFAULT_DEBOUNCE_MS = 300L;

    public interface ModuleChangeListener {
        void onModuleChanged(String moduleId, Path changedPath);
    }

    public interface ManifestRefresher {
        void refreshManifest(String moduleId);
    }

    public interface ResolutionRefresher {
        void refreshResolution(String moduleId);
    }

    private final long debounceMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, ModuleWatch> watches = new ConcurrentHashMap<>();
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ModuleChangeListener> listeners = new CopyOnWriteArrayList<>();
    private volatile ManifestRefresher manifestRefresher;
    private volatile ResolutionRefresher resolutionRefresher;
    private WatchService watchService;
    private Thread watchThread;

    public ModuleLevelHmrService() {
        this(DEFAULT_DEBOUNCE_MS);
    }

    public ModuleLevelHmrService(long debounceMs) {
        this.debounceMs = debounceMs;
    }

    public void setManifestRefresher(ManifestRefresher refresher) {
        this.manifestRefresher = refresher;
    }

    public void setResolutionRefresher(ResolutionRefresher refresher) {
        this.resolutionRefresher = refresher;
    }

    public void addListener(ModuleChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /**
     * 监听指定模块的源码目录。
     *
     * @param moduleId   模块唯一标识
     * @param moduleRoot 模块源码根目录
     */
    public void watchModule(String moduleId, Path moduleRoot) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(moduleRoot, "moduleRoot");

        Path absolute = moduleRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            log.warn("[HMR] Module root does not exist or is not a directory: {}", absolute);
            return;
        }

        ensureStarted();

        try {
            WatchKey key = absolute.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watches.put(moduleId, new ModuleWatch(moduleId, absolute, key, System.currentTimeMillis()));
            log.info("[HMR] Watching module '{}' at {}", moduleId, absolute);
        } catch (IOException e) {
            log.error("[HMR] Failed to watch module '{}': {}", moduleId, e.getMessage(), e);
        }
    }

    /**
     * 停止监听指定模块。
     *
     * @param moduleId 模块唯一标识
     */
    public void unwatchModule(String moduleId) {
        ModuleWatch watch = watches.remove(moduleId);
        if (watch != null) {
            watch.key().cancel();
            log.info("[HMR] Stopped watching module '{}'", moduleId);
        }
        URLClassLoader cl = classLoaders.remove(moduleId);
        if (cl != null) {
            try {
                cl.close();
            } catch (IOException e) {
                log.warn("[HMR] Failed to close ClassLoader for module '{}': {}", moduleId, e.getMessage());
            }
        }
    }

    /**
     * 刷新指定模块的包清单。
     *
     * @param moduleId 模块标识
     */
    public void refreshManifest(String moduleId) {
        if (manifestRefresher != null) {
            try {
                manifestRefresher.refreshManifest(moduleId);
                log.debug("[HMR] Manifest refreshed for '{}'", moduleId);
            } catch (Exception e) {
                log.error("[HMR] Manifest refresh failed for '{}': {}", moduleId, e.getMessage(), e);
            }
        }
    }

    /**
     * 触发指定模块的 runtime resolution 刷新。
     *
     * @param moduleId 模块标识
     */
    public void refreshResolution(String moduleId) {
        if (resolutionRefresher != null) {
            try {
                resolutionRefresher.refreshResolution(moduleId);
                log.debug("[HMR] Resolution refreshed for '{}'", moduleId);
            } catch (Exception e) {
                log.error("[HMR] Resolution refresh failed for '{}': {}", moduleId, e.getMessage(), e);
            }
        }
    }

    /**
     * 为模块创建独立的 URLClassLoader。
     *
     * @param moduleId 模块标识
     * @param urls    模块 URL 路径
     * @return 新的 ClassLoader
     */
    public URLClassLoader createClassLoader(String moduleId, URL[] urls) {
        URLClassLoader old = classLoaders.get(moduleId);
        URLClassLoader newLoader = new URLClassLoader(urls, getClass().getClassLoader());
        classLoaders.put(moduleId, newLoader);
        if (old != null) {
            try {
                old.close();
            } catch (IOException e) {
                log.warn("[HMR] Failed to close old ClassLoader for '{}': {}", moduleId, e.getMessage());
            }
        }
        return newLoader;
    }

    /**
     * 获取模块的 ClassLoader。
     */
    public URLClassLoader getClassLoader(String moduleId) {
        return classLoaders.get(moduleId);
    }

    public boolean isWatching(String moduleId) {
        return watches.containsKey(moduleId);
    }

    public int watchedModuleCount() {
        return watches.size();
    }

    /**
     * 停止所有监听并释放资源。
     */
    public void stop() {
        running.set(false);
        if (watchThread != null) {
            watchThread.interrupt();
        }
        for (String moduleId : watches.keySet()) {
            unwatchModule(moduleId);
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("[HMR] Failed to close WatchService: {}", e.getMessage());
            }
        }
    }

    private void ensureStarted() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            running.set(false);
            log.error("[HMR] Failed to create WatchService: {}", e.getMessage(), e);
            return;
        }
        watchThread = new Thread(this::watchLoop, "module-hmr");
        watchThread.setDaemon(true);
        watchThread.start();
        log.info("[HMR] Module-level HMR service started (debounce={}ms)", debounceMs);
    }

    private void watchLoop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                handleEvent(key, event);
            }
            if (!key.reset()) {
                log.warn("[HMR] Watch key invalidated");
            }
        }
    }

    private void handleEvent(WatchKey key, WatchEvent<?> event) {
        Path changed = ((Path) key.watchable()).resolve((Path) event.context());
        for (ModuleWatch watch : watches.values()) {
            if (watch.key() == key) {
                long now = System.currentTimeMillis();
                if (now - watch.lastTriggered() < debounceMs) {
                    continue;
                }
                watch.setLastTriggered(now);
                log.info("[HMR] Module '{}' changed: {} {}", watch.moduleId(), event.kind(), changed);
                try {
                    refreshManifest(watch.moduleId());
                    refreshResolution(watch.moduleId());
                    for (ModuleChangeListener listener : listeners) {
                        listener.onModuleChanged(watch.moduleId(), changed);
                    }
                } catch (Exception e) {
                    log.error("[HMR] Reload failed for '{}', keeping old version: {}",
                            watch.moduleId(), e.getMessage(), e);
                }
                return;
            }
        }
    }

    private static final class ModuleWatch {
        private final String moduleId;
        private final Path root;
        private final WatchKey key;
        private volatile long lastTriggered;

        ModuleWatch(String moduleId, Path root, WatchKey key, long lastTriggered) {
            this.moduleId = moduleId;
            this.root = root;
            this.key = key;
            this.lastTriggered = lastTriggered;
        }

        String moduleId() { return moduleId; }
        Path root() { return root; }
        WatchKey key() { return key; }
        long lastTriggered() { return lastTriggered; }
        void setLastTriggered(long t) { this.lastTriggered = t; }
    }
}
