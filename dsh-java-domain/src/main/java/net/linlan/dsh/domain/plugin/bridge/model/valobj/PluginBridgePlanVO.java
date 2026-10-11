package net.linlan.dsh.domain.plugin.bridge.model.valobj;

/**
 * Describes how one installed plugin can be activated or bridged.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public record PluginBridgePlanVO(
        PluginPackageTypeEnumVO packageType,
        String manifestPath,
        String workingDirectory,
        String entrypointPath,
        String runtimeCommand,
        boolean bridgeReady,
        String message
) {
}
