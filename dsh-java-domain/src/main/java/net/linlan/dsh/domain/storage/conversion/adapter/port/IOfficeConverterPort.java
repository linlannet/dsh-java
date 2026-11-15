package net.linlan.dsh.domain.storage.conversion.adapter.port;

/**
 * IOfficeConverterPort 定义 Office 文档转换端口。
 *
 * <p>调用流程：Office 工具 → 端口 → LibreOffice/其他后端。</p>
 * <p>示例：{@code convertTo(path, dir, "pdf")} 输出 PDF 路径。</p>
 */
public interface IOfficeConverterPort {

    String convertToPdf(String inputPath, String outputDir);

    /**
     * 转换到指定格式，默认实现仅支持 PDF。
     *
     * @param inputPath   输入文件路径
     * @param outputDir   输出目录
     * @param targetFormat 目标格式（pdf/docx/xlsx/pptx 等）
     * @return 输出文件路径
     */
    default String convertTo(String inputPath, String outputDir, String targetFormat) {
        if (targetFormat == null || targetFormat.isBlank()) {
            throw new IllegalArgumentException("target format required");
        }
        if ("pdf".equalsIgnoreCase(targetFormat)) {
            return convertToPdf(inputPath, outputDir);
        }
        throw new UnsupportedOperationException("converter does not support: " + targetFormat);
    }
}
