package net.linlan.dsh.infrastructure.adapter.storage;

import net.linlan.dsh.domain.document.office.model.error.OfficeKitError;
import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * LibreOfficeConverter 基于 soffice headless 实现 Office 转换。
 *
 * <p>调用流程：校验输入 → 启动 soffice → 校验输出 → 返回路径。</p>
 * <p>示例：Kit 缺失时返回 {@code OFFICE_KIT_UNAVAILABLE}，不阻断应用启动。</p>
 */
@Component
public class LibreOfficeConverter implements IOfficeConverterPort {

    private final String sofficeCommand;
    private final long timeoutSeconds;

    public LibreOfficeConverter() {
        this("soffice", 120L);
    }

    public LibreOfficeConverter(@Value("${dsh.converter.soffice:soffice}") String sofficeCommand,
                                @Value("${dsh.converter.timeout:120}") long timeoutSeconds) {
        this.sofficeCommand = sofficeCommand;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String convertToPdf(String inputPath, String outputDir) {
        return runConversion(inputPath, outputDir, "pdf");
    }

    @Override
    public String convertTo(String inputPath, String outputDir, String targetFormat) {
        if (targetFormat == null || targetFormat.isBlank()) {
            throw new OfficeKitError(OfficeKitError.UNSUPPORTED_FORMAT,
                    "target format required", null);
        }
        return runConversion(inputPath, outputDir, targetFormat.toLowerCase());
    }

    private String runConversion(String inputPath, String outputDir, String targetFormat) {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new OfficeKitError(OfficeKitError.INPUT_NOT_FOUND,
                    "input file not found: " + inputPath, null);
        }
        File outDir = new File(outputDir);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw OfficeKitError.conversionFailed(
                    "cannot create output dir: " + outputDir, null);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    sofficeCommand,
                    "--headless",
                    "--convert-to", targetFormat,
                    "--outdir", outputDir,
                    inputPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw OfficeKitError.conversionFailed("soffice conversion timed out", null);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw OfficeKitError.conversionFailed(
                        "soffice conversion failed with exit code: " + exitCode, null);
            }
            Path targetPath = Path.of(outputDir, baseName(inputFile) + "." + targetFormat);
            if (!targetPath.toFile().exists()) {
                throw OfficeKitError.conversionFailed(
                        "converted output not found: " + targetPath, null);
            }
            return targetPath.toString();
        } catch (IOException e) {
            if (commandMissing(e)) {
                throw OfficeKitError.unavailable(
                        sofficeCommand + " not found; install LibreOffice or set dsh.converter.soffice",
                        e);
            }
            throw OfficeKitError.conversionFailed("failed to run soffice: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OfficeKitError.conversionFailed("soffice conversion interrupted", e);
        }
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dotIdx = name.lastIndexOf('.');
        return dotIdx > 0 ? name.substring(0, dotIdx) : name;
    }

    private static boolean commandMissing(IOException error) {
        String message = error.getMessage();
        return message != null
                && (message.contains("Cannot run program") || message.contains("error=2"));
    }
}
