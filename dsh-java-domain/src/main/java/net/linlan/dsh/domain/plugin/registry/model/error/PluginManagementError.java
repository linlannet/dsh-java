package net.linlan.dsh.domain.plugin.registry.model.error;

import net.linlan.dsh.domain.common.error.HarnessError;

/**
 * PluginManagementError 表示插件管理错误。
 *
 * <p>调用流程：安装预期校验 → 包或版本不匹配 → 抛出本错误。</p>
 * <p>示例：expected 为 time-tools@1.0.0，实际命中其他包时返回 {@code unexpected-package}。</p>
 */
public class PluginManagementError extends HarnessError {

    /** 安装命中非预期包或版本。 */
    public static final String UNEXPECTED_PACKAGE = "unexpected-package";

    public PluginManagementError(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建预期不符错误。
     *
     * @param expected 预期包@版本
     * @param actual   实际包@版本
     * @return 管理错误
     */
    public static PluginManagementError unexpectedPackage(String expected, String actual) {
        return new PluginManagementError(UNEXPECTED_PACKAGE,
                "unexpected package: expected " + expected + " but got " + actual);
    }
}
