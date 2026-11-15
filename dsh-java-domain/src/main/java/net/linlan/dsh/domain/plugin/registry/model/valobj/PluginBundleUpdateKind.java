package net.linlan.dsh.domain.plugin.registry.model.valobj;

/**
 * PluginBundleUpdateKind 表示 Bundle 可用的更新来源。
 */
public enum PluginBundleUpdateKind {
    /** registry 存在更新版本。 */
    REGISTRY,
    /** Git spec 固定到完整提交且存在更新提交。 */
    GIT
}
