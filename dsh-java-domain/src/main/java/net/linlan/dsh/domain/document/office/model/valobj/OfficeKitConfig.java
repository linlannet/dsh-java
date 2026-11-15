package net.linlan.dsh.domain.document.office.model.valobj;

import java.nio.file.Path;

/**
 * OfficeKitConfig 定义 Office Kit 输出治理配置。
 *
 * <p>调用流程：配置绑定 → 工具读取默认输出目录/保留时长/容量上限。</p>
 * <p>示例：默认输出目录为 {@code $DSH_HOME/office-kit/outputs}。</p>
 *
 * @param outputDirectory      输出目录
 * @param outputRetentionMs    输出保留时长（毫秒）
 * @param maxOutputStoreBytes  输出存储容量上限（字节）
 * @param pythonEnvironment    Office 技能 Python 环境（workspace-dependencies/user）
 */
public record OfficeKitConfig(
        String outputDirectory,
        long outputRetentionMs,
        long maxOutputStoreBytes,
        String pythonEnvironment
) {

    private static final long DEFAULT_RETENTION_MS = 86_400_000L;
    private static final long DEFAULT_MAX_BYTES = 268_435_456L;
    private static final String DEFAULT_PYTHON_ENV = "workspace-dependencies";

    public OfficeKitConfig {
        outputDirectory = outputDirectory == null || outputDirectory.isBlank()
                ? defaultOutputDirectory()
                : outputDirectory;
        outputRetentionMs = outputRetentionMs > 0 ? outputRetentionMs : DEFAULT_RETENTION_MS;
        maxOutputStoreBytes = maxOutputStoreBytes > 0 ? maxOutputStoreBytes : DEFAULT_MAX_BYTES;
        pythonEnvironment = pythonEnvironment == null || pythonEnvironment.isBlank()
                ? DEFAULT_PYTHON_ENV
                : pythonEnvironment;
    }

    /**
     * 返回默认配置。
     *
     * @return 默认配置
     */
    public static OfficeKitConfig defaults() {
        return new OfficeKitConfig(null, DEFAULT_RETENTION_MS, DEFAULT_MAX_BYTES, DEFAULT_PYTHON_ENV);
    }

    /**
     * 计算默认输出目录：优先 DSH_HOME，其次用户目录下的 .dsh。
     *
     * @return 默认输出目录
     */
    public static String defaultOutputDirectory() {
        String dshHome = System.getenv("DSH_HOME");
        Path base = dshHome == null || dshHome.isBlank()
                ? Path.of(System.getProperty("user.home"), ".dsh")
                : Path.of(dshHome);
        return base.resolve("office-kit").resolve("outputs").toString();
    }
}
