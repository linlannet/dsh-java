package net.linlan.dsh.infrastructure.adapter.plugin.java;

import net.linlan.dsh.domain.spi.JavaHarnessPlugin;
import net.linlan.dsh.domain.spi.PluginManifest;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * 使用隔离 {@link URLClassLoader} 加载 Java 原生插件 JAR。
 * <p>优先读取 plugin.yaml 的 entrypoint 实例化入口类；未配置时回退到 Java SPI
 * ServiceLoader。加载完成后调用 onStart 并收集工具定义。
 * <p>数据示例：JAR 中的 plugin.yaml 配置 entrypoint="com.acme.TimePlugin"，
 * 宿主会实例化该类并读取其返回的 ToolDefinition 列表。
 *
 *
 */
@Component
public class JavaPluginLoader {

    private static final Logger log = LoggerFactory.getLogger(JavaPluginLoader.class);

    /**
     * 插件加载结果：包含插件实例、元数据、ClassLoader 和工具定义。
     * <p>ClassLoader 由插件私有，卸载时必须关闭。
     */
    public record LoadedPlugin(
            JavaHarnessPlugin instance,
            PluginManifest manifest,
            URLClassLoader classLoader,
            List<ToolDefinition> tools
    ) {}

    /**
     * 从指定路径加载插件 JAR。
     * <p>执行流程：校验 JAR 存在 -> 创建隔离 ClassLoader -> 读取 META-INF/plugin.yaml ->
     * 实例化插件入口 -> onStart -> 读取工具定义。
     * <p>数据示例：jarPath=/plugins/time/time.jar，manifest.version=1.0.0，返回 2 个工具。
     *
     * @param jarPath 插件 JAR 的绝对路径
     * @return 已加载插件
     * @throws IOException JAR 无法读取时抛出
     */
    public LoadedPlugin load(Path jarPath) throws IOException {
        if (!Files.exists(jarPath) || !Files.isRegularFile(jarPath)) {
            throw new IllegalArgumentException("plugin JAR not found: " + jarPath);
        }

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()
        );

        PluginManifest manifest = readManifest(classLoader).orElseThrow(() -> {
            closeQuietly(classLoader);
            return new IllegalStateException("META-INF/plugin.yaml not found in plugin JAR: " + jarPath);
        });

        JavaHarnessPlugin plugin;
        List<ToolDefinition> tools;
        try {
            plugin = instantiate(manifest, classLoader);
            plugin.onStart();
            tools = plugin.tools();
            if (tools == null) tools = List.of();
        } catch (RuntimeException e) {
            closeQuietly(classLoader);
            throw e;
        }

        log.info("Java plugin loaded: {} v{} ({} tools)", manifest.id(), manifest.version(), tools.size());
        return new LoadedPlugin(plugin, manifest, classLoader, tools);
    }

    /**
     * 卸载已加载插件：先执行插件 onStop，再关闭其 ClassLoader。
     * <p>onStop 异常只记录警告，不阻止资源释放。
     */
    public void unload(LoadedPlugin loaded) {
        try {
            loaded.instance().onStop();
        } catch (Exception e) {
            log.warn("Plugin onStop() failed for {}: {}", loaded.manifest().id(), e.getMessage());
        }
        closeQuietly(loaded.classLoader());
    }

    /**
     * 读取插件 JAR 内的 META-INF/plugin.yaml。
     * <p>id、name、version 是必需字段；缺失或 YAML 为空都视为无效清单。
     */
    private Optional<PluginManifest> readManifest(URLClassLoader classLoader) {
        URL url = classLoader.findResource("META-INF/plugin.yaml");
        if (url == null) return Optional.empty();
        try (InputStream in = url.openStream()) {
            Yaml yaml = new Yaml();
            java.util.Map<String, Object> map = yaml.load(in);
            if (map == null) return Optional.empty();
            String id = str(map.get("id"));
            String name = str(map.get("name"));
            String version = str(map.get("version"));
            String author = str(map.get("author"));
            String description = str(map.get("description"));
            String entrypoint = str(map.get("entrypoint"));
            if (id == null || name == null || version == null) return Optional.empty();
            return Optional.of(new PluginManifest(id, name, version, author, description, entrypoint));
        } catch (IOException e) {
            log.warn("Failed to read plugin.yaml: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 实例化插件入口类。
     * <p>优先按 manifest.entrypoint 加载；否则扫描 SPI 文件。SPI 结果必须与插件 ID 一致，
     * 且只允许找到一个匹配入口，避免同一 JAR 出现多个宿主入口。
     */
    private JavaHarnessPlugin instantiate(PluginManifest manifest, URLClassLoader classLoader) {
        String entrypoint = manifest.entrypoint();

        if (entrypoint != null && !entrypoint.isBlank()) {
            try {
                Class<?> clazz = classLoader.loadClass(entrypoint);
                return (JavaHarnessPlugin) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception | LinkageError e) {
                throw new IllegalStateException(
                        "failed to instantiate plugin entrypoint " + entrypoint + ": " + e.getMessage(), e);
            }
        }

        ServiceLoader<JavaHarnessPlugin> serviceLoader =
                ServiceLoader.load(JavaHarnessPlugin.class, classLoader);
        List<JavaHarnessPlugin> found = new ArrayList<>();
        for (JavaHarnessPlugin candidate : serviceLoader) {
            if (manifest.id().equals(candidate.pluginId())) {
                found.add(candidate);
            }
        }
        if (found.isEmpty()) {
            throw new IllegalStateException(
                    "no ServiceLoader entry found for plugin '" + manifest.id()
                            + "'. Ensure META-INF/services/"
                            + JavaHarnessPlugin.class.getName()
                            + " exists in the JAR, or set 'entrypoint' in plugin.yaml.");
        }
        if (found.size() > 1) {
            throw new IllegalStateException(
                    "multiple ServiceLoader entries found for plugin '" + manifest.id() + "'");
        }
        return found.get(0);
    }

    /**
     * 把 YAML 中的任意值安全转换为字符串。
     */
    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 静默关闭插件 ClassLoader，卸载失败不应覆盖主流程异常。
     */
    private static void closeQuietly(URLClassLoader cl) {
        try {
            cl.close();
        } catch (IOException ignored) {
        }
    }
}
