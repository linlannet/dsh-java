package net.linlan.dsh.domain.plugin.registry.service;

import java.util.Optional;

import net.linlan.dsh.domain.plugin.IPluginRegistryService;
import net.linlan.dsh.domain.plugin.registry.adapter.port.IPluginArtifactInstallerPort;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginArtifactInstallPlanVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallationResultVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import net.linlan.dsh.domain.plugin.runtime.adapter.port.IRuntimeResolutionPort;
import net.linlan.dsh.domain.plugin.runtime.model.valobj.ResolvedPackage;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 插件登记服务：负责插件登记、持久化状态变更和卸载清理。
 * <p>核心流程：installPlugin 落库为 REGISTERED -> bridge inspect 成功后 refreshStatus 激活 ->
 * disable/enable 只改变状态 -> uninstall 先标记状态再调用端口清理工件。
 * <p>数据示例：pluginId="time-tools"，runtimeType=JAVA_NATIVE 时安装模式为 NATIVE；
 * 激活成功后状态为 ACTIVE，可注册为 Agent 可调用工具。
 *
 *
 */
@Service
public class PluginRegistryService implements IPluginRegistryService {

    private final IHarnessPluginRepository harnessPluginRepository;
    private final IPluginArtifactInstallerPort pluginArtifactInstallerPort;
    private final IRuntimeResolutionPort runtimeResolutionPort;

    public PluginRegistryService(
            IHarnessPluginRepository harnessPluginRepository,
            IPluginArtifactInstallerPort pluginArtifactInstallerPort
    ) {
        this(harnessPluginRepository, pluginArtifactInstallerPort, null);
    }

    @Autowired
    public PluginRegistryService(
            IHarnessPluginRepository harnessPluginRepository,
            IPluginArtifactInstallerPort pluginArtifactInstallerPort,
            IRuntimeResolutionPort runtimeResolutionPort
    ) {
        this.harnessPluginRepository = harnessPluginRepository;
        this.pluginArtifactInstallerPort = pluginArtifactInstallerPort;
        this.runtimeResolutionPort = runtimeResolutionPort;
    }

    /**
     * 校验输入并把插件工件安装到登记目录。
     * <p>执行流程：字段非空校验 -> 解析 runtimeType -> 决定 NATIVE 或 NODE_BRIDGE ->
     * 调用安装端口复制/规范化工件 -> 构造实体并持久化为 REGISTERED。
     * <p>数据示例：runtimeType="JAVA_NATIVE" 且 sourcePath="/tmp/time.jar"，安装后 sourcePath
     * 会替换为 install-root 内的规范化路径。
     */
    @Override
    public PluginInstallationResultVO installPlugin(
            String pluginId,
            String displayName,
            String pluginVersion,
            String runtimeType,
            String sourcePath,
            String entrypoint
    ) {
        requireText(pluginId, "pluginId");
        requirePluginId(pluginId);
        requireText(displayName, "displayName");
        requireText(pluginVersion, "pluginVersion");
        requireText(runtimeType, "runtimeType");
        requireText(sourcePath, "sourcePath");
        requireText(entrypoint, "entrypoint");

        PluginRuntimeTypeEnumVO resolvedRuntimeType = PluginRuntimeTypeEnumVO.valueOf(runtimeType);
        PluginInstallModeEnumVO installMode = resolvedRuntimeType == PluginRuntimeTypeEnumVO.JAVA_NATIVE
                ? PluginInstallModeEnumVO.NATIVE
                : PluginInstallModeEnumVO.NODE_BRIDGE;
        Instant installedAt = Instant.now();
        PluginArtifactInstallPlanVO installPlan = pluginArtifactInstallerPort.install(pluginId, sourcePath, entrypoint);

        HarnessPluginEntity plugin = new HarnessPluginEntity(
                pluginId,
                displayName,
                pluginVersion,
                resolvedRuntimeType,
                installMode,
                installPlan.installedSourcePath(),
                installPlan.normalizedEntrypoint(),
                PluginInstallStatusEnumVO.REGISTERED,
                installedAt
        );
        harnessPluginRepository.save(plugin);

        return new PluginInstallationResultVO(
                plugin.getPluginId(),
                plugin.getRuntimeType(),
                plugin.getInstallMode(),
                plugin.getStatus(),
                plugin.getInstallMode() == PluginInstallModeEnumVO.NATIVE,
                plugin.getInstalledAt()
        );
    }

    /**
     * 加载已登记插件；不存在时抛出明确异常。
     */
    @Override
    public HarnessPluginEntity loadPlugin(String pluginId) {
        requireText(pluginId, "pluginId");
        return harnessPluginRepository.findByPluginId(pluginId)
                .orElseThrow(() -> new IllegalArgumentException("plugin not found: " + pluginId));
    }

    /**
     * 按插件 ID 查询登记信息，不存在时返回空 Optional。
     */
    @Override
    public Optional<HarnessPluginEntity> loadPluginOptional(String pluginId) {
        requireText(pluginId, "pluginId");
        return harnessPluginRepository.findByPluginId(pluginId);
    }

    /**
     * 根据桥接检查结果刷新插件生命周期状态。
     * <p>bridgeReady=true 调用 activate；false 调用 fail；结果都会重新持久化。
     * <p>数据示例：Node sidecar 握手失败时状态从 REGISTERED 变为 FAILED。
     */
    @Override
    public PluginInstallStatusEnumVO refreshStatus(HarnessPluginEntity plugin, boolean bridgeReady) {
        if (bridgeReady) {
            plugin.activate();
        } else {
            plugin.fail();
        }
        harnessPluginRepository.save(plugin);
        return plugin.getStatus();
    }

    /**
     * 停用插件但保留登记与安装工件。
     * <p>该操作只修改持久化状态，运行时资源由对应生命周期用例/运行时服务负责移除。
     */
    @Override
    public PluginInstallStatusEnumVO disablePlugin(String pluginId) {
        HarnessPluginEntity plugin = loadPlugin(pluginId);
        plugin.disable();
        harnessPluginRepository.save(plugin);
        return plugin.getStatus();
    }

    /**
     * 启用已登记插件。
     * <p>本方法不重新 inspect 桥接；重新激活前的健康检查由上游用例负责。
     */
    @Override
    public PluginInstallStatusEnumVO enablePlugin(String pluginId) {
        HarnessPluginEntity plugin = loadPlugin(pluginId);
        plugin.enable();
        harnessPluginRepository.save(plugin);
        return plugin.getStatus();
    }

    /**
     * 标记插件卸载并清理安装工件。
     * <p>先持久化终态，再调用 artifact uninstaller；若清理失败，登记状态仍反映已卸载请求，
     * 上游可通过日志和端口实现处理残留文件。
     */
    @Override
    public PluginInstallStatusEnumVO uninstallPlugin(String pluginId) {
        HarnessPluginEntity plugin = loadPlugin(pluginId);
        plugin.uninstall();
        harnessPluginRepository.save(plugin);
        pluginArtifactInstallerPort.uninstall(pluginId);
        return plugin.getStatus();
    }

    /**
     * 刷新指定模块的运行时解析映射。
     * <p>运行时新增/更新插件后触发刷新，新插件可被发现加载。</p>
     *
     * @param moduleId 模块标识
     */
    public void refreshResolution(String moduleId) {
        if (runtimeResolutionPort == null) {
            return;
        }
        var pluginOpt = harnessPluginRepository.findByPluginId(moduleId);
        if (pluginOpt.isPresent()) {
            var plugin = pluginOpt.get();
            runtimeResolutionPort.refresh(moduleId);
            if (runtimeResolutionPort.resolve(moduleId).isEmpty()) {
                runtimeResolutionPort.register(new ResolvedPackage(
                        moduleId,
                        plugin.getEntrypoint(),
                        plugin.getSourcePath(),
                        plugin.getRuntimeType().name(),
                        Instant.now()
                ));
            }
        }
    }

    /**
     * 校验必需条件；不满足时抛出异常。
     */
    /**
     * 校验字符串字段非空且不只包含空白字符。
     */
    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    /**
     * 插件 ID 会参与注册工具名与安装目录路径，必须限制为安全字符集。
     */
    private void requirePluginId(String pluginId) {
        if (!pluginId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException(
                    "pluginId must start with a letter or digit and contain only letters, digits, dot, underscore or dash");
        }
    }
}
