package net.linlan.dsh.domain.plugin.runtime.model.valobj;

/**
 * ResolvedPackage 表示一个已解析的插件模块包。
 *
 * @param moduleId     模块唯一标识
 * @param entryPoint   入口标识（类名 / 文件路径）
 * @param sourcePath   源码路径
 * @param packageType  包类型
 * @param resolvedAt   解析时间戳
 */
public record ResolvedPackage(
        String moduleId,
        String entryPoint,
        String sourcePath,
        String packageType,
        java.time.Instant resolvedAt
) {
    public ResolvedPackage {
        java.util.Objects.requireNonNull(moduleId, "moduleId");
        entryPoint = entryPoint == null ? "" : entryPoint;
        sourcePath = sourcePath == null ? "" : sourcePath;
        packageType = packageType == null ? "unknown" : packageType;
        resolvedAt = resolvedAt == null ? java.time.Instant.now() : resolvedAt;
    }
}
