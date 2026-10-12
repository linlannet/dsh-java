package net.linlan.dsh.domain.storage.conversion;

import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.035 OfficeToPdfConverter Office 转 PDF")
class OfficeToPdfConverterTest {

    @Test
    @DisplayName("convert 调用端口返回 PDF 路径")
    void convertReturnsPdfPath() {
        var port = new StubPort("/tmp/result.pdf");
        var converter = new OfficeToPdfConverter(port, "/tmp");
        String result = converter.convert("/data/report.docx");
        assertEquals("/tmp/result.pdf", result);
        assertEquals("/data/report.docx", port.lastInputPath);
        assertEquals("/tmp", port.lastOutputDir);
    }

    @Test
    @DisplayName("convert 指定输出目录")
    void convertWithOutputDir() {
        var port = new StubPort("/out/x.pdf");
        var converter = new OfficeToPdfConverter(port, "/tmp");
        String result = converter.convert("/data/report.docx", "/out");
        assertEquals("/out/x.pdf", result);
        assertEquals("/out", port.lastOutputDir);
    }

    @Test
    @DisplayName("convert null 路径抛异常")
    void convertNullThrows() {
        var converter = new OfficeToPdfConverter(new StubPort(""));
        assertThrows(IllegalArgumentException.class, () -> converter.convert(null));
    }

    @Test
    @DisplayName("convert 空路径抛异常")
    void convertBlankThrows() {
        var converter = new OfficeToPdfConverter(new StubPort(""));
        assertThrows(IllegalArgumentException.class, () -> converter.convert("  "));
    }

    @Test
    @DisplayName("不支持的格式抛异常")
    void unsupportedFormatThrows() {
        var converter = new OfficeToPdfConverter(new StubPort(""));
        assertThrows(IllegalArgumentException.class, () -> converter.convert("/data/image.png"));
    }

    @Test
    @DisplayName("isSupportedFormat 识别 docx")
    void supportsDocx() {
        var converter = new OfficeToPdfConverter(new StubPort(""));
        assertTrue(converter.isSupportedFormat("/a/b.docx"));
        assertTrue(converter.isSupportedFormat("/a/b.DOCX"));
        assertTrue(converter.isSupportedFormat("/a/b.xlsx"));
        assertTrue(converter.isSupportedFormat("/a/b.pptx"));
        assertTrue(converter.isSupportedFormat("/a/b.odt"));
        assertFalse(converter.isSupportedFormat("/a/b.png"));
        assertFalse(converter.isSupportedFormat(null));
    }

    @Test
    @DisplayName("null port 抛异常")
    void nullPortThrows() {
        assertThrows(NullPointerException.class, () -> new OfficeToPdfConverter(null));
    }

    static final class StubPort implements IOfficeConverterPort {
        final String result;
        String lastInputPath;
        String lastOutputDir;

        StubPort(String result) {
            this.result = result;
        }

        @Override
        public String convertToPdf(String inputPath, String outputDir) {
            this.lastInputPath = inputPath;
            this.lastOutputDir = outputDir;
            return result;
        }
    }
}
