package net.linlan.dsh.domain.tool.office;

import net.linlan.dsh.domain.document.office.model.error.OfficeKitError;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Office 工具")
class OfficeToolsTest {

    private final OfficeKitConfig config = new OfficeKitConfig(null, 0, 0, null);

    @Test
    @DisplayName("office_render 返回 PDF 产物路径")
    void renderReturnsOutput(@TempDir Path tempDir) {
        var tool = new OfficeRenderTool(new StubConverter("out/report.pdf"), config);
        var result = tool.execute(Map.of("path", "report.docx", "output_dir", tempDir.toString()), null).join();

        var success = assertInstanceOf(ToolExecutionResult.Success.class, result);
        assertEquals("out/report.pdf", success.meta().get("output"));
        assertEquals("pdf", success.meta().get("format"));
    }

    @Test
    @DisplayName("office_render 拒绝非 Office 扩展名")
    void renderRejectsUnsupportedExtension() {
        var tool = new OfficeRenderTool(new StubConverter("x"), config);
        var result = tool.execute(Map.of("path", "notes.txt"), null).join();

        assertFailure(result, OfficeKitError.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("office_convert 转换到目标格式")
    void convertToTargetFormat() {
        var tool = new OfficeConvertTool(new StubConverter("out/deck.pdf"), config);
        var result = tool.execute(Map.of("path", "deck.pptx", "format", "PDF"), null).join();

        var success = assertInstanceOf(ToolExecutionResult.Success.class, result);
        assertEquals("out/deck.pdf", success.meta().get("output"));
        assertEquals("pdf", success.meta().get("format"));
    }

    @Test
    @DisplayName("office_convert 拒绝不支持的格式")
    void convertRejectsUnsupportedTarget() {
        var tool = new OfficeConvertTool(new StubConverter("x"), config);
        var result = tool.execute(Map.of("path", "deck.pptx", "format", "txt"), null).join();

        assertFailure(result, OfficeKitError.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("Kit 缺失映射为 OFFICE_KIT_UNAVAILABLE")
    void missingKitMapsToUnavailable() {
        IOfficeConverterPort converter = new IOfficeConverterPort() {
            @Override
            public String convertToPdf(String inputPath, String outputDir) {
                throw OfficeKitError.unavailable("soffice not found", null);
            }
        };
        var tool = new OfficeRenderTool(converter, config);

        var result = tool.execute(Map.of("path", "report.docx"), null).join();

        assertFailure(result, OfficeKitError.UNAVAILABLE);
    }

    @Test
    @DisplayName("office_recalculate 仅接受表格扩展名")
    void recalculateValidatesSpreadsheet(@TempDir Path tempDir) {
        var tool = new OfficeRecalculateTool(new StubConverter("out/budget.xlsx"), config);

        var success = assertInstanceOf(ToolExecutionResult.Success.class,
                tool.execute(Map.of("path", "budget.xlsx", "output_dir", tempDir.toString()), null).join());
        assertEquals("out/budget.xlsx", success.meta().get("output"));

        var rejected = tool.execute(Map.of("path", "report.docx"), null).join();
        assertFailure(rejected, OfficeKitError.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("缺少 path 返回 ToolArgsError/INVALID_ARGS")
    void missingPathIsInvalidArgs() {
        var tool = new OfficeRenderTool(new StubConverter("x"), config);
        var result = tool.execute(Map.of(), null).join();

        var failure = assertInstanceOf(ToolExecutionResult.Failure.class, result);
        assertEquals("ToolArgsError", failure.error().info().name());
        assertEquals("INVALID_ARGS", failure.error().info().code());
    }

    @Test
    @DisplayName("OfficeKitConfig 空值回落到默认值")
    void configDefaults() {
        var defaults = new OfficeKitConfig("", 0, 0, "");
        assertTrue(defaults.outputDirectory().endsWith("office-kit/outputs"));
        assertEquals(86_400_000L, defaults.outputRetentionMs());
        assertEquals(268_435_456L, defaults.maxOutputStoreBytes());
        assertEquals("workspace-dependencies", defaults.pythonEnvironment());
    }

    @Test
    @DisplayName("OfficeKitError 保持稳定错误码")
    void errorIdentity() {
        var error = OfficeKitError.unavailable("missing soffice", null);
        assertEquals("OfficeKitError", error.errorName());
        assertEquals(OfficeKitError.UNAVAILABLE, error.errorCode());
        assertEquals(OfficeKitError.CONVERSION_FAILED,
                OfficeKitError.conversionFailed("boom", null).errorCode());
    }

    private static void assertFailure(ToolExecutionResult result, String expectedCode) {
        var failure = assertInstanceOf(ToolExecutionResult.Failure.class, result);
        assertEquals(expectedCode, failure.error().info().code());
    }

    private record StubConverter(String output) implements IOfficeConverterPort {

        @Override
        public String convertToPdf(String inputPath, String outputDir) {
            return output;
        }

        @Override
        public String convertTo(String inputPath, String outputDir, String targetFormat) {
            return output;
        }
    }
}
