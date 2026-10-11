package net.linlan.dsh.infrastructure.adapter.plugin.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;

/**
 * 监听插件安装目录中 JAR 文件变化的热重载器。
 * <p>核心流程：start 创建 WatchService -> watch 登记插件 JAR -> 目录发生 CREATE/MODIFY ->
 * 匹配已登记 JAR -> 回调 HotReloadListener 执行 stop -> 重新加载 -> start。
 * <p>数据示例：watch("time", /plugins/time/time.jar) 后替换该 JAR，会触发
 * hotReload("time", /plugins/time/time.jar)。
 * <p>监听线程为 daemon 线程；Spring 关闭时通过 @PreDestroy 中断并停止。
 */
@Component
public final class PluginHotReloader {

    private static final Logger log = LoggerFactory.getLogger(PluginHotReloader.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, String> watchedPlugins = new ConcurrentHashMap<>();
    private Thread watchThread;
    private volatile HotReloadListener listener;

    /**
     * 插件 JAR 变化后的热重载回调。
     */
    public interface HotReloadListener {
        void onPluginChanged(String pluginId, Path jarPath);
    }

    private final Path installRoot;

    public PluginHotReloader(HarnessExtensionsProperties extensions) {
        this.installRoot = Path.of(extensions.getPlugins().getInstallRoot()).toAbsolutePath().normalize();
    }

    /**
     * 启动安装目录监听线程。
     * <p>使用 CAS 保证只启动一次；重复调用直接忽略。installRoot 来自插件配置。
     */
    public void start(HotReloadListener reloadListener) {
        Objects.requireNonNull(reloadListener, "listener");
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.listener = reloadListener;
        Path dir = installRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            running.set(false);
            log.error("Plugin hot reloader could not create install directory {}: {}", dir, exception.getMessage(), exception);
            return;
        }
        watchThread = new Thread(() -> watchLoop(dir), "plugin-hot-reloader");
        watchThread.setDaemon(true);
        watchThread.start();
        log.info("Plugin hot reloader started, watching {}", dir);
    }

    /**
     * 登记需要热重载监听的插件 JAR。
     *
     * @param pluginId 插件登记 ID
     * @param jarPath  JAR 的绝对路径
     */
    public void watch(String pluginId, Path jarPath) {
        Path absolute = jarPath.toAbsolutePath().normalize();
        watchedPlugins.put(absolute.toString(), pluginId);
        log.debug("Watching plugin {} at {}", pluginId, absolute);
    }

    /**
     * 停止监听线程；Spring 上下文关闭时自动调用。
     */
    @PreDestroy
    public void stop() {
        running.set(false);
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    /**
     * 持续监听插件安装目录。
     * <p>只处理 `.jar` 文件的 CREATE/MODIFY 事件；路径必须与已登记 JAR 完全一致。
     * WatchKey 失效或目录不可监听时结束循环，热重载回调异常只记录日志，不停止监听。
     */
    private void watchLoop(Path directory) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

            while (running.get()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    Path changed = directory.resolve((Path) event.context());
                    String fileName = changed.getFileName().toString();
                    if (!fileName.endsWith(".jar")) continue;

                    String pluginId = watchedPlugins.get(changed.toString());
                    if (pluginId != null && listener != null) {
                        log.info("Plugin JAR changed: {} ({}), triggering hot reload", pluginId, fileName);
                        try {
                            listener.onPluginChanged(pluginId, changed);
                        } catch (Exception e) {
                            log.error("Hot reload failed for plugin {}: {}", pluginId, e.getMessage(), e);
                        }
                    }
                }
                if (!key.reset()) {
                    log.warn("Watch key no longer valid, stopping hot reloader");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Plugin hot reloader interrupted");
        } catch (IOException e) {
            log.error("Plugin hot reloader failed to start: {}", e.getMessage(), e);
        }
    }
}
