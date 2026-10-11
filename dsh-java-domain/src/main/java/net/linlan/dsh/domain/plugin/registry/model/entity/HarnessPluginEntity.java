package net.linlan.dsh.domain.plugin.registry.model.entity;

import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import java.time.Instant;
import java.util.Objects;

/**
 * Stores one installed plugin registration.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public class HarnessPluginEntity {

    private final String pluginId;
    private final String displayName;
    private final String pluginVersion;
    private final PluginRuntimeTypeEnumVO runtimeType;
    private final PluginInstallModeEnumVO installMode;
    private final String sourcePath;
    private final String entrypoint;
    private PluginInstallStatusEnumVO status;
    private final Instant installedAt;

    /**
     * 创建插件注册实体。
     * <p>用途：承载插件元数据、运行时绑定和状态。</p>
     */
    public HarnessPluginEntity(
            String pluginId,
            String displayName,
            String pluginVersion,
            PluginRuntimeTypeEnumVO runtimeType,
            PluginInstallModeEnumVO installMode,
            String sourcePath,
            String entrypoint,
            PluginInstallStatusEnumVO status,
            Instant installedAt
    ) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.runtimeType = Objects.requireNonNull(runtimeType, "runtimeType");
        this.installMode = Objects.requireNonNull(installMode, "installMode");
        this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
        this.entrypoint = Objects.requireNonNull(entrypoint, "entrypoint");
        this.status = Objects.requireNonNull(status, "status");
        this.installedAt = Objects.requireNonNull(installedAt, "installedAt");
    }

    /**
     * 获取Plugin Id。
     */
    public String getPluginId() { return pluginId; }
    /**
     * 获取DisplayName名称。
     * @return 领域处理结果。
     */
    public String getDisplayName() { return displayName; }
    /**
     * 获取Plugin Version。
     */
    public String getPluginVersion() { return pluginVersion; }
    /**
     * 获取Runtime Type。
     */
    public PluginRuntimeTypeEnumVO getRuntimeType() { return runtimeType; }
    /**
     * 获取Install Mode。
     */
    public PluginInstallModeEnumVO getInstallMode() { return installMode; }
    /**
     * 获取Source Path。
     */
    public String getSourcePath() { return sourcePath; }
    /**
     * 获取Entrypoint。
     */
    public String getEntrypoint() { return entrypoint; }
    /**
     * 获取Status。
     */
    public PluginInstallStatusEnumVO getStatus() { return status; }
    /**
     * 获取Installed At。
     */
    public Instant getInstalledAt() { return installedAt; }

    /**
     * 处理activate。
     * @return 领域处理结果。
     */
    public void activate() { this.status = PluginInstallStatusEnumVO.ACTIVE; }

    /**
     * 处理fail。
     * @return 领域处理结果。
     */
    public void fail() { this.status = PluginInstallStatusEnumVO.FAILED; }

    /**
     * 处理disable。
     * @return 领域处理结果。
     */
    public void disable() { this.status = PluginInstallStatusEnumVO.DISABLED; }

    /**
     * 处理enable。
     * @return 领域处理结果。
     */
    public void enable() { this.status = PluginInstallStatusEnumVO.REGISTERED; }

    /**
     * 处理uninstall。
     * @return 领域处理结果。
     */
    public void uninstall() { this.status = PluginInstallStatusEnumVO.UNINSTALLED; }
}
