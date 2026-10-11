package net.linlan.dsh.domain.spi;

/**
 * 插件生命周期状态机。
 * <p>
 * 流程：PENDING → LOADING → ACTIVE；停用时进入 UNLOADING → DISPOSED；任一阶段失败进入 FAILED。
 * <p>
 * 示例：插件清单加载成功后置为 ACTIVE，卸载完成后置为 DISPOSED。
 */
public enum PluginLifecycleState {
    PENDING,
    LOADING,
    ACTIVE,
    UNLOADING,
    DISPOSED,
    FAILED
}
