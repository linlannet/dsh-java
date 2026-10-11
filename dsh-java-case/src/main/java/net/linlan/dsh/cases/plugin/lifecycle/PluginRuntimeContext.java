package net.linlan.dsh.cases.plugin.lifecycle;

import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import java.time.Instant;

/**
 * 插件运行期 Pipeline 上下文。
 * <p>流程：ResolvePluginNode 写入插件 → InspectBridgeNode 写入桥接计划 → BindBridgeNode 更新状态，最终由终点节点使用。</p>
 * <p>数据示例：{@code plugin=HarnessPluginEntity(weather-forecast), bridgePlan=PluginBridgePlanVO(ready), checkedAt=2026-09-07T10:00:00Z}。</p>
 */
public class PluginRuntimeContext {

    private HarnessPluginEntity plugin;
    private PluginBridgePlanVO bridgePlan;
    private PluginInstallStatusEnumVO status;
    private Instant checkedAt;

    /**
     * 读取上下文中的当前值。
     */
    public HarnessPluginEntity getPlugin() {
        return plugin;
    }

    /**
     * 写入上下文中的当前值，供后续节点使用。
     */
    public void setPlugin(HarnessPluginEntity plugin) {
        this.plugin = plugin;
    }

    /**
     * 读取上下文中的当前值。
     */
    public PluginBridgePlanVO getBridgePlan() {
        return bridgePlan;
    }

    /**
     * 写入上下文中的当前值，供后续节点使用。
     */
    public void setBridgePlan(PluginBridgePlanVO bridgePlan) {
        this.bridgePlan = bridgePlan;
    }

    /**
     * 读取上下文中的当前值。
     */
    public PluginInstallStatusEnumVO getStatus() {
        return status;
    }

    /**
     * 写入上下文中的当前值，供后续节点使用。
     */
    public void setStatus(PluginInstallStatusEnumVO status) {
        this.status = status;
    }

    /**
     * 读取上下文中的当前值。
     */
    public Instant getCheckedAt() {
        return checkedAt;
    }

    /**
     * 写入上下文中的当前值，供后续节点使用。
     */
    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }
}
