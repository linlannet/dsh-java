package net.linlan.dsh.infrastructure.adapter.storage;

import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IllegalArgumentException("input file not found: " + inputPath);
        }
        File outDir = new File(outputDir);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("cannot create output dir: " + outputDir);
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    sofficeCommand,
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", outputDir,
                    inputPath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("soffice conversion timed out");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException("soffice conversion failed with exit code: " + exitCode);
            }
            String baseName = inputFile.getName();
            int dotIdx = baseName.lastIndexOf('.');
            String pdfName = (dotIdx > 0 ? baseName.substring(0, dotIdx) : baseName) + ".pdf";
            Path pdfPath = Path.of(outputDir, pdfName);
            if (!pdfPath.toFile().exists()) {
                throw new IllegalStateException("PDF output not found: " + pdfPath);
            }
            return pdfPath.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run soffice", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("soffice conversion interrupted", e);
        }
    }
}
