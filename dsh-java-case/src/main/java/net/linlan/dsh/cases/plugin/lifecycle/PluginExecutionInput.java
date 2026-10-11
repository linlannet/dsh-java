package net.linlan.dsh.cases.plugin.lifecycle;

/**
 * 插件运行期命令入参。
 * <p>流程：Trigger/API 组装 pluginId 和 kind → 作为 Pipeline 请求进入运行期链路。</p>
 * <p>数据示例：{@code pluginId=weather-forecast, kind=RUN}。</p>
 */
public record PluginExecutionInput(String pluginId, PluginRuntimeKind kind) implements PluginRuntimeCommand {
}
