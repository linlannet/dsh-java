package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IPluginArtifactInstallerPort;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginArtifactInstallPlanVO;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.springframework.stereotype.Component;

/**
 * Copies plugin source trees (Node plugins) or JAR files (Java plugins) into
 * the managed install root.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Component
public class PluginArtifactInstallerPort implements IPluginArtifactInstallerPort {

    private final Path installRoot;

    /**
     * 处理插件 Artifact Installer Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param installRoot install 根路径参数。
     * @return 方法执行结果。
     */
    public PluginArtifactInstallerPort(HarnessExtensionsProperties extensions) {
        this.installRoot = Path.of(extensions.getPlugins().getInstallRoot()).toAbsolutePath().normalize();
    }

    @Override
    public PluginArtifactInstallPlanVO install(String pluginId, String sourcePath, String entrypoint) {
        Path source = Path.of(sourcePath).toAbsolutePath().normalize();
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("plugin source path does not exist: " + sourcePath);
        }

        if (Files.isRegularFile(source) && source.toString().endsWith(".jar")) {
            return installJar(pluginId, source);
        }
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("plugin source path must be a directory or a .jar file");
        }

        return installDirectory(pluginId, source, entrypoint);
    }
    /**
     * uninstall。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     */


    @Override
    public void uninstall(String pluginId) {
        Path targetRoot = installRoot.resolve(pluginId).normalize();
        if (!targetRoot.startsWith(installRoot)) {
            throw new IllegalStateException("plugin uninstall path escapes install root");
        }
        try {
            deleteDirectoryIfExists(targetRoot);
            Path jarFile = installRoot.resolve(pluginId + ".jar");
            Files.deleteIfExists(jarFile);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to remove plugin artifacts", exception);
        }
    }

    /**
     * 安装组件，写入登记信息并返回安装结果。
     */
    private PluginArtifactInstallPlanVO installJar(String pluginId, Path jarSource) {
        try {
            Files.createDirectories(installRoot);
            Path targetJar = installRoot.resolve(pluginId + ".jar").normalize();
            if (!targetJar.startsWith(installRoot)) {
                throw new IllegalStateException("plugin install path escapes install root");
            }
            Files.copy(jarSource, targetJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return new PluginArtifactInstallPlanVO(targetJar.toString(), targetJar.getFileName().toString());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to install plugin JAR", exception);
        }
    }

    /**
     * 安装组件，写入登记信息并返回安装结果。
     */
    private PluginArtifactInstallPlanVO installDirectory(String pluginId, Path sourceRoot, String entrypoint) {
        Path normalizedEntrypoint = normalizeEntrypoint(sourceRoot, entrypoint);
        Path targetRoot = installRoot.resolve(pluginId).normalize();
        if (!targetRoot.startsWith(installRoot)) {
            throw new IllegalStateException("plugin install path escapes install root");
        }

        try {
            Files.createDirectories(installRoot);
            replaceDirectory(sourceRoot, targetRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to install plugin artifacts", exception);
        }

        String relativeEntrypoint = sourceRoot.relativize(normalizedEntrypoint).toString();
        return new PluginArtifactInstallPlanVO(targetRoot.toString(), relativeEntrypoint);
    }

    /**
     * 规范化 入口。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sourceRoot 来源 根路径参数。
     * @param entrypoint 插件入口。
     * @return 方法执行结果。
     */
    private Path normalizeEntrypoint(Path sourceRoot, String entrypoint) {
        Path entrypointPath = Path.of(entrypoint);
        Path normalized = entrypointPath.isAbsolute()
                ? entrypointPath.toAbsolutePath().normalize()
                : sourceRoot.resolve(entrypointPath).normalize();
        if (!normalized.startsWith(sourceRoot)) {
            throw new IllegalArgumentException("plugin entrypoint must stay inside source path");
        }
        if (!Files.exists(normalized) || Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("plugin entrypoint must be an existing file");
        }
        return normalized;
    }

    /**
     * 处理replace 目录。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param sourceRoot 来源 根路径参数。
     * @param targetRoot target 根路径参数。
     */
    private void replaceDirectory(Path sourceRoot, Path targetRoot) throws IOException {
        deleteDirectoryIfExists(targetRoot);
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(dir);
                Files.createDirectories(targetRoot.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceRoot.relativize(file);
                Files.copy(file, targetRoot.resolve(relative), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 删除 目录 If Exists。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param targetRoot target 根路径参数。
     */
    private void deleteDirectoryIfExists(Path targetRoot) throws IOException {
        if (!Files.exists(targetRoot)) {
            return;
        }
        Files.walkFileTree(targetRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
