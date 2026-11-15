package net.linlan.dsh.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OfficeKitProperties 绑定 dsh.office.* 配置。
 *
 * <p>调用流程：Spring 绑定 → 转换为领域配置 → Office 工具使用。</p>
 * <p>示例：{@code dsh.office.output-directory} 指定输出目录。</p>
 */
@Component
@ConfigurationProperties(prefix = "dsh.office")
public class OfficeKitProperties {

    private String outputDirectory;
    private long outputRetentionMs = 86_400_000L;
    private long maxOutputStoreBytes = 268_435_456L;
    private String pythonEnvironment = "workspace-dependencies";

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public long getOutputRetentionMs() {
        return outputRetentionMs;
    }

    public void setOutputRetentionMs(long outputRetentionMs) {
        this.outputRetentionMs = outputRetentionMs;
    }

    public long getMaxOutputStoreBytes() {
        return maxOutputStoreBytes;
    }

    public void setMaxOutputStoreBytes(long maxOutputStoreBytes) {
        this.maxOutputStoreBytes = maxOutputStoreBytes;
    }

    public String getPythonEnvironment() {
        return pythonEnvironment;
    }

    public void setPythonEnvironment(String pythonEnvironment) {
        this.pythonEnvironment = pythonEnvironment;
    }
}
