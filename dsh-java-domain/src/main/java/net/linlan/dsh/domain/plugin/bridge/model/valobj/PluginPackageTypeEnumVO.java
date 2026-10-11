package net.linlan.dsh.domain.plugin.bridge.model.valobj;

/**
 * Enumerates the plugin package forms that the bridge inspector can recognize.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public enum PluginPackageTypeEnumVO {
    DSH_PACKAGE,
    CODEX_PLUGIN,
    CORDIS_PROFILE,
    UNKNOWN
}
