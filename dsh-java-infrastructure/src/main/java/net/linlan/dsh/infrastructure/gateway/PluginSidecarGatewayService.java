package net.linlan.dsh.infrastructure.gateway;

import net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginProcessStatusVO;
import net.linlan.dsh.domain.plugin.runtime.model.valobj.PluginRunResultVO;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Starts and tracks local Node plugin processes inside the configured install root.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Service
public class PluginSidecarGatewayService {

    private final Map<String, RunningPluginProcess> runningProcesses = new ConcurrentHashMap<>();
    private final boolean nodeBridgeEnabled;
    private final Path installRoot;

    /**
     * 初始化 Node sidecar 插件网关。
     * <p>流程：定位 sidecar 入口 → 启动子进程 → 转发 JSON-RPC 请求。</p>
     */
    public PluginSidecarGatewayService(
            HarnessExtensionsProperties extensions
    ) {
        this.nodeBridgeEnabled = extensions.getPlugins().isNodeBridgeEnabled();
        this.installRoot = Path.of(extensions.getPlugins().getInstallRoot()).toAbsolutePath().normalize();
    }

    /**
     * Starts one Node sidecar process with a validated entrypoint inside the install root.
     */
    public PluginRunResultVO start(String pluginId, String workingDirectory, String entrypointPath) {
        if (!nodeBridgeEnabled) {
            throw new IllegalStateException("node bridge is disabled by configuration");
        }
        RunningPluginProcess current = runningProcesses.get(pluginId);
        if (current != null && current.process().isAlive()) {
            return new PluginRunResultVO(pluginId, current.process().pid(), current.runtimeCommand(), current.workingDirectory(), true, current.startedAt(), "plugin process is already running");
        }

        Path workDir = Path.of(workingDirectory).toAbsolutePath().normalize();
        Path entrypoint = Path.of(entrypointPath).toAbsolutePath().normalize();
        if (!workDir.startsWith(installRoot) || !entrypoint.startsWith(installRoot)) {
            throw new IllegalStateException("plugin execution is limited to the configured install root");
        }
        if (!entrypoint.startsWith(workDir)) {
            throw new IllegalStateException("plugin entrypoint escapes working directory");
        }
        if (!Files.exists(entrypoint) || Files.isDirectory(entrypoint)) {
            throw new IllegalStateException("plugin entrypoint does not exist");
        }
        String fileName = entrypoint.getFileName().toString();
        if (!(fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs"))) {
            throw new IllegalStateException("plugin entrypoint must be a Node-executable script");
        }

        try {
            Path logDir = installRoot.resolve("runtime-logs");
            Files.createDirectories(logDir);
            File logFile = logDir.resolve(pluginId + ".log").toFile();
            ProcessBuilder processBuilder = new ProcessBuilder("node", entrypoint.toString())
                    .directory(workDir.toFile())
                    .redirectError(Redirect.appendTo(logFile));
            Process process = processBuilder.start();
            Instant startedAt = Instant.now();
            String runtimeCommand = "node " + entrypoint;
            runningProcesses.put(pluginId, new RunningPluginProcess(process, runtimeCommand, workDir.toString(), startedAt));
            return new PluginRunResultVO(pluginId, process.pid(), runtimeCommand, workDir.toString(), process.isAlive(), startedAt, "plugin process started");
        } catch (IOException exception) {
            throw new IllegalStateException("failed to start plugin process", exception);
        }
    }

    /**
     * 运行ning Process。
     */
    public java.util.Optional<Process> runningProcess(String pluginId) {
        RunningPluginProcess process = runningProcesses.get(pluginId);
        return process != null && process.process().isAlive()
                ? java.util.Optional.of(process.process())
                : java.util.Optional.empty();
    }

    /**
     * Returns the current process status for one plugin.
     */
    public PluginProcessStatusVO status(String pluginId) {
        RunningPluginProcess runningPluginProcess = runningProcesses.get(pluginId);
        if (runningPluginProcess == null) {
            return new PluginProcessStatusVO(pluginId, null, false, null, null, null, "plugin process not started");
        }
        boolean alive = runningPluginProcess.process().isAlive();
        return new PluginProcessStatusVO(pluginId, runningPluginProcess.process().pid(), alive, runningPluginProcess.runtimeCommand(), runningPluginProcess.workingDirectory(), runningPluginProcess.startedAt(), alive ? "plugin process is running" : "plugin process has exited");
    }

    /**
     * Stops one running plugin sidecar process if present.
     */
    public PluginProcessStatusVO stop(String pluginId) {
        RunningPluginProcess runningPluginProcess = runningProcesses.remove(pluginId);
        if (runningPluginProcess == null) {
            return new PluginProcessStatusVO(pluginId, null, false, null, null, null, "plugin process not started");
        }
        Process process = runningPluginProcess.process();
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        boolean alive = process.isAlive();
        return new PluginProcessStatusVO(pluginId, process.pid(), alive, runningPluginProcess.runtimeCommand(), runningPluginProcess.workingDirectory(), runningPluginProcess.startedAt(), alive ? "plugin process still alive after stop" : "plugin process stopped");
    }

    /**
     * RunningPluginProcess 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private record RunningPluginProcess(Process process, String runtimeCommand, String workingDirectory, Instant startedAt) {
    }
}
