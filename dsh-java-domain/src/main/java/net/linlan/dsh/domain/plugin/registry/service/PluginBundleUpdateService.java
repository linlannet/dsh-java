package net.linlan.dsh.domain.plugin.registry.service;

import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginBundleInfo;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginBundleUpdateKind;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * PluginBundleUpdateService 判定 Bundle 是否有可用更新。
 *
 * <p>调用流程：比较 registry 版本 / Git 固定提交 → 产出可选 update。</p>
 * <p>示例：Git spec 固定到 40 位提交且存在更新提交时返回 {@code GIT}。</p>
 */
public final class PluginBundleUpdateService {

    private static final Pattern FULL_COMMIT = Pattern.compile("^[0-9a-fA-F]{40}$");

    /**
     * 判定 registry 更新。
     *
     * @param installedVersion 已安装版本
     * @param latestVersion    最新版本
     * @return 有更新时返回 REGISTRY
     */
    public Optional<PluginBundleUpdateKind> registryUpdate(String installedVersion, String latestVersion) {
        if (installedVersion == null || latestVersion == null) {
            return Optional.empty();
        }
        return installedVersion.equals(latestVersion)
                ? Optional.empty()
                : Optional.of(PluginBundleUpdateKind.REGISTRY);
    }

    /**
     * 判定 Git 更新：spec 必须固定到完整提交，且最新提交不同。
     *
     * @param spec        配置的 Git spec
     * @param latestCommit 最新提交
     * @return 有更新时返回 GIT
     */
    public Optional<PluginBundleUpdateKind> gitUpdate(String spec, String latestCommit) {
        if (spec == null || latestCommit == null) {
            return Optional.empty();
        }
        String pinned = extractCommit(spec);
        if (pinned == null || !FULL_COMMIT.matcher(pinned).matches()) {
            return Optional.empty();
        }
        if (!FULL_COMMIT.matcher(latestCommit.trim()).matches()) {
            return Optional.empty();
        }
        return pinned.equalsIgnoreCase(latestCommit.trim())
                ? Optional.empty()
                : Optional.of(PluginBundleUpdateKind.GIT);
    }

    /**
     * 生成 Bundle 信息。
     *
     * @param bundleId         Bundle 标识
     * @param version          当前版本
     * @param commit           当前提交
     * @param registryUpdate   是否存在 registry 更新
     * @param gitUpdate        是否存在 Git 更新
     * @return Bundle 信息
     */
    public PluginBundleInfo describe(String bundleId, String version, String commit,
                                     boolean registryUpdate, boolean gitUpdate) {
        Optional<PluginBundleUpdateKind> update = registryUpdate
                ? Optional.of(PluginBundleUpdateKind.REGISTRY)
                : gitUpdate ? Optional.of(PluginBundleUpdateKind.GIT) : Optional.empty();
        return new PluginBundleInfo(bundleId, version, commit, update);
    }

    private static String extractCommit(String spec) {
        int hash = spec.indexOf('#');
        return hash >= 0 && hash + 1 < spec.length() ? spec.substring(hash + 1).trim() : null;
    }
}
