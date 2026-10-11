package net.linlan.dsh.cases.plugin.lifecycle;

/**
 * 插件运行期命令契约。
 * <p>流程：由 PluginExecutionInput 等实现携带目标插件和期望动作 → 供运行期 Pipeline 各节点读取。</p>
 * <p>数据示例：{@code pluginId=weather-forecast, kind=ACTIVATE}。</p>
 */
public interface PluginRuntimeCommand {

    String pluginId();

    PluginRuntimeKind kind();
}
