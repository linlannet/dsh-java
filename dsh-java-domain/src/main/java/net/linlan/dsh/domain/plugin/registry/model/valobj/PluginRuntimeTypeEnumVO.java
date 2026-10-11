package net.linlan.dsh.domain.plugin.registry.model.valobj;

/**
 * Enumerates the plugin runtime families supported by the harness.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public enum PluginRuntimeTypeEnumVO {
    JAVA_NATIVE,
    DSH_NODE_BRIDGE
}
