package net.linlan.dsh.infrastructure.adapter.port;

import net.linlan.dsh.domain.plugin.bridge.adapter.port.IPluginRuntimeBridgePort;
import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginPackageTypeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import net.linlan.dsh.infrastructure.adapter.plugin.java.JavaPluginRuntimeManager;
import net.linlan.dsh.infrastructure.gateway.PluginManifestGatewayService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves execution plans for both Java-native and Node-bridge plugins.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Component
public class PluginRuntimeBridgePort implements IPluginRuntimeBridgePort {

    private final PluginManifestGatewayService pluginManifestGatewayService;
    private final JavaPluginRuntimeManager javaPluginRuntimeManager;

    /**
     * 初始化插件运行时桥接端口。
     * <p>流程：适配插件协议 → 转换工具调用 → 回传执行结果。</p>
     */
    public PluginRuntimeBridgePort(
            PluginManifestGatewayService pluginManifestGatewayService,
            JavaPluginRuntimeManager javaPluginRuntimeManager
    ) {
        this.pluginManifestGatewayService = pluginManifestGatewayService;
        this.javaPluginRuntimeManager = javaPluginRuntimeManager;
    }

    @Override
    public PluginBridgePlanVO inspect(HarnessPluginEntity plugin) {
        Path sourcePath = Path.of(plugin.getSourcePath()).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath)) {
            return new PluginBridgePlanVO(
                    PluginPackageTypeEnumVO.UNKNOWN,
                    null,
                    sourcePath.toString(),
                    null,
                    null,
                    false,
                    "plugin source path does not exist"
            );
        }

        if (plugin.getRuntimeType() == PluginRuntimeTypeEnumVO.JAVA_NATIVE) {
            return inspectJavaNative(plugin, sourcePath);
        }
        return inspectNodeBridge(plugin, sourcePath);
    }

    /**
     * 检查 Java Native。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param plugin 插件参数。
     * @param sourcePath 源码或制品路径。
     * @return 方法执行结果。
     */
    private PluginBridgePlanVO inspectJavaNative(HarnessPluginEntity plugin, Path sourcePath) {
        Optional<Path> jarPath = javaPluginRuntimeManager.resolveJarPath(
                sourcePath.toString(), plugin.getEntrypoint());

        if (jarPath.isEmpty()) {
            return new PluginBridgePlanVO(
                    PluginPackageTypeEnumVO.UNKNOWN,
                    null,
                    sourcePath.toString(),
                    null,
                    null,
                    false,
                    "java-native plugin JAR not found under source path"
            );
        }

        Path jar = jarPath.get();
        String manifestPath = jar.toString();
        String runtimeCommand = "java-in-process:" + plugin.getPluginId();

        return new PluginBridgePlanVO(
                PluginPackageTypeEnumVO.DSH_PACKAGE,
                manifestPath,
                jar.getParent() != null ? jar.getParent().toString() : sourcePath.toString(),
                jar.toString(),
                runtimeCommand,
                true,
                "java-native plugin JAR ready for in-process loading"
        );
    }

    /**
     * 检查 Node Bridge。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param plugin 插件参数。
     * @param sourcePath 源码或制品路径。
     * @return 方法执行结果。
     */
    private PluginBridgePlanVO inspectNodeBridge(HarnessPluginEntity plugin, Path sourcePath) {
        Path codexManifestPath = sourcePath.resolve(".codex-plugin/plugin.json");
        Path packageJsonPath = sourcePath.resolve("package.json");
        Path cordisPath = sourcePath.resolve("cordis.yml");

        if (Files.exists(codexManifestPath)) {
            return inspectCodexPlugin(plugin, sourcePath, codexManifestPath);
        }
        if (Files.exists(packageJsonPath)) {
            return inspectDshPackage(plugin, sourcePath, packageJsonPath, cordisPath);
        }
        if (Files.exists(cordisPath)) {
            return new PluginBridgePlanVO(
                    PluginPackageTypeEnumVO.CORDIS_PROFILE,
                    cordisPath.toString(),
                    sourcePath.toString(),
                    null,
                    "pnpm dsh --profile headless --patch " + cordisPath,
                    false,
                    "cordis profile detected, but direct activation still needs a dedicated dsh sidecar session runner"
            );
        }

        return new PluginBridgePlanVO(
                PluginPackageTypeEnumVO.UNKNOWN,
                null,
                sourcePath.toString(),
                null,
                null,
                false,
                "no supported plugin manifest found under source path"
        );
    }

    /**
     * 检查 Codex 插件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param plugin 插件参数。
     * @param sourcePath 源码或制品路径。
     * @param codexManifestPath codex 清单 路径参数。
     * @return 方法执行结果。
     */
    private PluginBridgePlanVO inspectCodexPlugin(HarnessPluginEntity plugin, Path sourcePath, Path codexManifestPath) {
        Optional<Map<String, Object>> manifest = pluginManifestGatewayService.readJson(codexManifestPath);
        String message = manifest
                .map(map -> "codex plugin '" + map.getOrDefault("name", plugin.getPluginId()) + "' detected; metadata is readable but execution still depends on Codex host semantics")
                .orElse("codex plugin manifest detected");
        return new PluginBridgePlanVO(
                PluginPackageTypeEnumVO.CODEX_PLUGIN,
                codexManifestPath.toString(),
                sourcePath.toString(),
                null,
                null,
                false,
                message
        );
    }

    /**
     * 检查 Dsh Package。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param plugin 插件参数。
     * @param sourcePath 源码或制品路径。
     * @param packageJsonPath package JSON 路径参数。
     * @param cordisPath cordis 路径参数。
     * @return 方法执行结果。
     */
    private PluginBridgePlanVO inspectDshPackage(HarnessPluginEntity plugin, Path sourcePath, Path packageJsonPath, Path cordisPath) {
        Map<String, Object> manifest = pluginManifestGatewayService.readJson(packageJsonPath)
                .orElseThrow(() -> new IllegalStateException("package.json exists but could not be parsed"));
        String entrypoint = resolveEntrypoint(plugin, sourcePath, manifest);
        boolean hasEntrypoint = entrypoint != null;
        String runtimeCommand = hasEntrypoint ? "node " + entrypoint : null;
        String message;
        if (hasEntrypoint) {
            message = "dsh package detected with executable Node entrypoint";
        } else if (Files.exists(cordisPath)) {
            message = "dsh package detected, but only cordis composition is present; bridge execution needs a dsh sidecar launcher";
        } else {
            message = "dsh package detected, but no executable entrypoint was resolved";
        }
        return new PluginBridgePlanVO(
                PluginPackageTypeEnumVO.DSH_PACKAGE,
                packageJsonPath.toString(),
                sourcePath.toString(),
                entrypoint,
                runtimeCommand,
                hasEntrypoint,
                message
        );
    }

    /**
     * 解析e Entrypoint。
     */
    private String resolveEntrypoint(HarnessPluginEntity plugin, Path sourcePath, Map<String, Object> manifest) {
        String requestedEntrypoint = plugin.getEntrypoint();
        if (requestedEntrypoint != null && !requestedEntrypoint.isBlank()) {
            Path requestedPath = sourcePath.resolve(requestedEntrypoint).normalize();
            if (Files.exists(requestedPath)) {
                return requestedPath.toString();
            }
        }
        Object main = manifest.get("main");
        if (main instanceof String mainValue && !mainValue.isBlank()) {
            Path mainPath = sourcePath.resolve(mainValue).normalize();
            if (Files.exists(mainPath)) {
                return mainPath.toString();
            }
        }
        return null;
    }
}
