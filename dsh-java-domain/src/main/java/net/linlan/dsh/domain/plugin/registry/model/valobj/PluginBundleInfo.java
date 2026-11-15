package net.linlan.dsh.domain.plugin.registry.model.valobj;

import java.util.Optional;

/**
 * PluginBundleInfo 表示可安装/更新的插件 Bundle。
 *
 * <p>调用流程：查询 registry/Git 状态 → 生成可选 update → listBundles 返回。</p>
 * <p>示例：无更新时 {@code update()} 为空，不输出该字段。</p>
 *
 * @param bundleId Bundle 标识
 * @param version  当前版本
 * @param commit   当前 Git 提交（非 Git bundle 为 null）
 * @param update   可用更新来源，无更新时为空
 */
public record PluginBundleInfo(
        String bundleId,
        String version,
        String commit,
        Optional<PluginBundleUpdateKind> update
) {

    public PluginBundleInfo {
        update = update == null ? Optional.empty() : update;
    }

    /**
     * 无更新信息的 Bundle。
     *
     * @param bundleId Bundle 标识
     * @param version  当前版本
     * @param commit   当前提交
     * @return Bundle 信息
     */
    public static PluginBundleInfo withoutUpdate(String bundleId, String version, String commit) {
        return new PluginBundleInfo(bundleId, version, commit, Optional.empty());
    }

    /**
     * 带 registry 更新的 Bundle。
     *
     * @param bundleId Bundle 标识
     * @param version  当前版本
     * @param commit   当前提交
     * @return Bundle 信息
     */
    public static PluginBundleInfo withRegistryUpdate(String bundleId, String version, String commit) {
        return new PluginBundleInfo(bundleId, version, commit,
                Optional.of(PluginBundleUpdateKind.REGISTRY));
    }

    /**
     * 带 Git 更新的 Bundle。
     *
     * @param bundleId Bundle 标识
     * @param version  当前版本
     * @param commit   当前提交
     * @return Bundle 信息
     */
    public static PluginBundleInfo withGitUpdate(String bundleId, String version, String commit) {
        return new PluginBundleInfo(bundleId, version, commit,
                Optional.of(PluginBundleUpdateKind.GIT));
    }
}
