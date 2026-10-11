package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.spi.PluginLifecycleState;
import net.linlan.dsh.domain.spi.PluginManifest;
import net.linlan.dsh.domain.spi.PluginEvent;
import net.linlan.dsh.domain.spi.PluginEventBus;
import net.linlan.dsh.domain.spi.PluginConfigStore;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.SystemPromptAssembler;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 进程内 Java 原生插件生命周期管理器。
 * <p>与 Node sidecar 不同，Java 插件不拉起子进程，而是在宿主 JVM 中通过隔离
 * URLClassLoader 加载。核心流程为：加载 JAR -> configure(PluginContext) ->
 * 插件注册工具/事件/Hook/配置/提示词 -> 状态 ACTIVE -> JAR 变化时热重载 ->
 * 停止时逆序回收贡献项并关闭 ClassLoader。
 * <p>数据示例：插件 id="time"，工具 name="now"，注册后的 Agent 工具名为
 * `plugin__time__now`；JAR 被替换后，stop + start 会重建同一工具名。
 *
 *
 */
@Component
public class JavaPluginRuntimeManager {

    private static final Logger log = LoggerFactory.getLogger(JavaPluginRuntimeManager.class);

    private final JavaPluginLoader loader;
    private final ToolRegistry toolRegistry;
    private final PluginEventBus eventBus;
    private final InProcessPluginHookRegistry hookRegistry;
    private final PluginConfigStore configStore;
    private final SystemPromptAssembler promptAssembler;
    private final ScopedSystemPromptAssembler scopedPromptAssembler;
    private final Path installRoot;
    private final PluginHotReloader hotReloader;

    private final Map<String, RunningJavaPlugin> runningPlugins = new ConcurrentHashMap<>();

    /**
     * 注入插件运行所需的宿主服务。
     * <p>包括类加载器、工具注册表、事件总线、Hook 注册表、配置存储、
     * 提示词装配器、热重载器和安装根目录。
     */
    public JavaPluginRuntimeManager(
            JavaPluginLoader loader,
            ToolRegistry toolRegistry,
            PluginEventBus eventBus,
            InProcessPluginHookRegistry hookRegistry,
            PluginConfigStore configStore,
            SystemPromptAssembler promptAssembler,
            ScopedSystemPromptAssembler scopedPromptAssembler,
            PluginHotReloader hotReloader,
            HarnessExtensionsProperties extensions
    ) {
        this.loader = loader;
        this.toolRegistry = toolRegistry;
        this.eventBus = eventBus;
        this.hookRegistry = hookRegistry;
        this.configStore = configStore;
        this.promptAssembler = promptAssembler;
        this.scopedPromptAssembler = scopedPromptAssembler;
        this.hotReloader = hotReloader;
        this.installRoot = Path.of(extensions.getPlugins().getInstallRoot()).toAbsolutePath().normalize();
    }

    /**
     * 启动一个 Java 原生插件。
     * <p>执行流程：重复启动直接返回 -> 创建 SpringPluginContext -> loader.load 加载并执行 onStart ->
     * 插件 configure 注入贡献项 -> 宿主补充提示词段 -> 状态 ACTIVE -> 登记 JAR 热重载 ->
     * 发布 plugin.started 事件。
     * <p>失败回滚：先逆序关闭 context 中的注册项，再关闭插件 ClassLoader，保证没有悬挂工具/Hook。
     * <p>数据示例：jarPath=/plugins/time/time.jar 包含 now 工具，注册后工具名为
     * plugin__time__now。
     *
     * @param pluginId 插件登记 ID
     * @param jarPath  已安装插件 JAR 的路径
     * @return 启动结果
     */
    public synchronized JavaPluginStartResult start(String pluginId, Path jarPath) {
        RunningJavaPlugin existing = runningPlugins.get(pluginId);
        if (existing != null) {
            try {
                existing.context().close();
            } catch (Exception e) {
                log.warn("[plugin] Agent={} stale registration rollback failed: {}", pluginId, e.getMessage(), e);
            }
            runningPlugins.remove(pluginId);
            hookRegistry.unregisterAll(pluginId);
            return new JavaPluginStartResult(pluginId, true, "java plugin already running",
                    existing.manifest.name(), existing.manifest.version(), existing.startedAt);
        }

        SpringPluginContext context = new SpringPluginContext(
                pluginId, toolRegistry, eventBus, hookRegistry, configStore, scopedPromptAssembler);
        JavaPluginLoader.LoadedPlugin loaded = null;
        try {
            loaded = loader.load(jarPath);
            loaded.instance().configure(context);
            List<String> toolNames = context.registeredToolNames();
            Instant startedAt = Instant.now();
            AutoCloseable promptSection = registerPromptSection(
                    pluginId, toolNames, loaded.manifest().description());
            if (promptSection != null) {
                context.registerDisposer(promptSection);
            }
            RunningJavaPlugin plugin = new RunningJavaPlugin(
                    loaded, loaded.manifest(), context, startedAt, toolNames
            );
            runningPlugins.put(pluginId, plugin);
            plugin.state().set(PluginLifecycleState.ACTIVE);
            hotReloader.watch(pluginId, jarPath);
            eventBus.publish(new PluginEvent("host", "plugin.started", pluginId, null));
            return new JavaPluginStartResult(pluginId, true, "java plugin started",
                    loaded.manifest().name(), loaded.manifest().version(), startedAt);
        } catch (Exception | LinkageError e) {
            rollbackQuietly(context);
            if (loaded != null) {
                loader.unload(loaded);
            }
            log.error("Failed to start Java plugin {}: {}", pluginId, e.getMessage(), e);
            return new JavaPluginStartResult(pluginId, false,
                "failed to start: " + e.getMessage(), null, null, null);
        }
    }

    /**
     * 热重载一个 Java 插件。
     * <p>执行流程：stop 彻底回收旧实例 -> 使用同一路径 start 重建新 ClassLoader 和注册项。
     * 重载期间同步持锁，避免新旧插件工具/Hook 同时暴露。
     * <p>数据示例：time 插件 JAR 更新后，旧 plugin__time__now 被注销，随后同名工具重新注册。
     */
    public synchronized JavaPluginStartResult hotReload(String pluginId, Path jarPath) {
        log.info("Hot reloading plugin {} from {}", pluginId, jarPath);
        stop(pluginId);
        return start(pluginId, jarPath);
    }

    /**
     * 在 Spring 应用完全就绪后启动插件热重载器。
     * <p>使用 ApplicationReadyEvent 而不是构造函数，避免启动早期文件系统抖动触发重建。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startHotReloader() {
        hotReloader.start(this::hotReload);
    }

    /**
     * 停止一个运行中的 Java 插件。
     * <p>执行顺序：从运行表移除 -> 状态 UNLOADING -> 发布 plugin.stopping ->
     * 逆序关闭 context 贡献项 -> 执行插件 onStop 并关闭 ClassLoader -> 状态 DISPOSED ->
     * 清理插件残留 Hook -> 发布 plugin.stopped。
     * <p>单个注册项关闭失败不会阻塞其他清理动作，但会记录警告。
     */
    public synchronized JavaPluginStatusResult stop(String pluginId) {
        RunningJavaPlugin plugin = runningPlugins.remove(pluginId);
        if (plugin == null) {
            return new JavaPluginStatusResult(pluginId, false, "java plugin not running");
        }
        plugin.state().set(PluginLifecycleState.UNLOADING);
        eventBus.publish(new PluginEvent("host", "plugin.stopping", pluginId, null));
        rollbackQuietly(plugin.context());
        loader.unload(plugin.loaded);
        plugin.state().set(PluginLifecycleState.DISPOSED);
        hookRegistry.unregisterAll(pluginId);
        eventBus.publish(new PluginEvent("host", "plugin.stopped", pluginId, null));
        return new JavaPluginStatusResult(pluginId, false, "java plugin stopped");
    }

    /**
     * 查询 Java 插件的进程内运行状态。
     */
    public JavaPluginStatusResult status(String pluginId) {
        RunningJavaPlugin plugin = runningPlugins.get(pluginId);
        if (plugin == null) {
            return new JavaPluginStatusResult(pluginId, false, "java plugin not running");
        }
        return new JavaPluginStatusResult(pluginId, true, "java plugin is running");
    }

    /**
     * 解析已安装插件对应的 JAR 文件。
     * <p>优先使用 sourcePath 本身；其次尝试 entrypoint 指向的 JAR；
     * 最后尝试 `<目录名>.jar` 约定路径，或在目录中找到 plugin.yaml 时返回该目录。
     */
    public Optional<Path> resolveJarPath(String sourcePath, String entrypoint) {
        Path source = Path.of(sourcePath).toAbsolutePath().normalize();
        if (Files.isRegularFile(source) && source.toString().endsWith(".jar")) {
            return Optional.of(source);
        }
        if (entrypoint != null && entrypoint.endsWith(".jar")) {
            Path jar = source.resolve(entrypoint).normalize();
            if (Files.isRegularFile(jar)) return Optional.of(jar);
        }
        Path defaultJar = source.resolve(source.getFileName().toString() + ".jar");
        if (Files.isRegularFile(defaultJar)) return Optional.of(defaultJar);
        Path pluginYaml = source.resolve("META-INF/plugin.yaml");
        if (Files.exists(pluginYaml)) return Optional.of(source);
        return Optional.empty();
    }

    /**
     * 在系统提示词中注册插件能力说明。
     * <p>只有 promptAssembler 是 ScopedSystemPromptAssembler 且插件声明了工具时才注册。
     * 返回值加入 context 的 registrations，插件停止时随其他贡献项一起移除。
     */
    private AutoCloseable registerPromptSection(
            String pluginId,
            List<String> toolNames,
            String description
    ) {
        if (!(promptAssembler instanceof ScopedSystemPromptAssembler scoped) || toolNames.isEmpty()) {
            return null;
        }
        String capability = description == null || description.isBlank()
                ? "No plugin description provided." : description;
        String text = "## Plugin capabilities: " + pluginId + "\n"
                + capability + "\nAvailable tools: " + String.join(", ", toolNames) + "\n"
                + "Use the matching tool when the request needs this capability.";
        ScopedSystemPromptAssembler.PromptSection section = new ScopedSystemPromptAssembler.PromptSection(
                promptSectionName(pluginId), 150, text);
        scoped.registerSection(section);
        return () -> scoped.removeSection(promptSectionName(pluginId));
    }

    /**
     * 生成插件提示词段的稳定名称。
     */
    private String promptSectionName(String pluginId) {
        return "plugin:" + pluginId + ":capabilities";
    }

    /**
     * 静默回滚插件上下文中的全部贡献项。
     * <p>用于启动失败或插件停止；回滚失败只记录日志，不掩盖主流程错误。
     */
    private void rollbackQuietly(SpringPluginContext context) {
        try {
            context.close();
        } catch (Exception e) {
            log.warn("Plugin contribution rollback failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 一个运行中的 Java 插件：保存加载结果、元数据、贡献项上下文、启动时间和状态。
     */
    private static final class RunningJavaPlugin {
        private final JavaPluginLoader.LoadedPlugin loaded;
        private final PluginManifest manifest;
        private final SpringPluginContext context;
        private final Instant startedAt;
        private final List<String> toolNames;
        private final AtomicReference<PluginLifecycleState> state =
                new AtomicReference<>(PluginLifecycleState.LOADING);

        private RunningJavaPlugin(
            JavaPluginLoader.LoadedPlugin loaded,
            PluginManifest manifest,
            SpringPluginContext context,
            Instant startedAt,
            List<String> toolNames
        ) {
            this.loaded = loaded;
            this.manifest = manifest;
            this.context = context;
            this.startedAt = startedAt;
            this.toolNames = List.copyOf(toolNames);
        }
        /**
         * 返回 state。
         * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
         * @return 方法执行结果。
         */


        private AtomicReference<PluginLifecycleState> state() {
            return state;
        }
        /**
         * context。
         * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
         * @return 方法执行结果。
         */


        private SpringPluginContext context() {
            return context;
        }
    }

    /**
     * Java 插件启动结果。
     */
    public record JavaPluginStartResult(
            String pluginId, boolean running, String message,
            String displayName, String version, Instant startedAt
    ) {}

    /**
     * Java 插件运行状态结果。
     */
    public record JavaPluginStatusResult(
            String pluginId, boolean running, String message
    ) {}
}
