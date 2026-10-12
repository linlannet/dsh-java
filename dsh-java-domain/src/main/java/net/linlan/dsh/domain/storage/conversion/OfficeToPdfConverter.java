package net.linlan.dsh.domain.storage.conversion;

import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;

import java.util.Objects;

public final class OfficeToPdfConverter {

    private final IOfficeConverterPort port;
    private final String defaultOutputDir;

    public OfficeToPdfConverter(IOfficeConverterPort port) {
        this(port, System.getProperty("java.io.tmpdir"));
    }

    public OfficeToPdfConverter(IOfficeConverterPort port, String defaultOutputDir) {
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(defaultOutputDir, "defaultOutputDir");
        this.port = port;
        this.defaultOutputDir = defaultOutputDir;
    }

    public String convert(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("inputPath required");
        }
        if (!isSupportedFormat(inputPath)) {
            throw new IllegalArgumentException("unsupported format: " + inputPath);
        }
        return port.convertToPdf(inputPath, defaultOutputDir);
    }

    public String convert(String inputPath, String outputDir) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("inputPath required");
        }
        if (outputDir == null || outputDir.isBlank()) {
            throw new IllegalArgumentException("outputDir required");
        }
        if (!isSupportedFormat(inputPath)) {
            throw new IllegalArgumentException("unsupported format: " + inputPath);
        }
        return port.convertToPdf(inputPath, outputDir);
    }

    public boolean isSupportedFormat(String inputPath) {
        if (inputPath == null) {
            return false;
        }
        String lower = inputPath.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc")
                || lower.endsWith(".xlsx") || lower.endsWith(".xls")
                || lower.endsWith(".pptx") || lower.endsWith(".ppt")
                || lower.endsWith(".odt") || lower.endsWith(".ods") || lower.endsWith(".odp");
    }
}
