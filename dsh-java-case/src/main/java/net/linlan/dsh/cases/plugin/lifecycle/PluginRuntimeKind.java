package net.linlan.dsh.cases.plugin.lifecycle;

/**
 * 插件运行期动作类型。
 * <p>流程：BindBridgeNode 根据该值路由到激活或运行节点。</p>
 * <p>数据示例：{@code ACTIVATE} 返回激活信息；{@code RUN} 启动插件进程。</p>
 */
public enum PluginRuntimeKind {
    ACTIVATE,
    RUN
}
