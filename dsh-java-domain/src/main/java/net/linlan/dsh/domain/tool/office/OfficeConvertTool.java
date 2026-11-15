package net.linlan.dsh.domain.tool.office;

import net.linlan.dsh.domain.document.office.model.error.OfficeKitError;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * office_convert 将 Office 文档转换为指定格式。
 *
 * <p>调用流程：校验输入与目标格式 → 调用转换端口 → 记录输出工件。</p>
 * <p>示例：{@code office_convert(path="deck.pptx", format="pdf")}。</p>
 */
public final class OfficeConvertTool implements ToolDefinition {

    private static final String NAME = "office_convert";
    private static final Set<String> TARGET_FORMATS = Set.of(
            "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "odt", "ods", "odp");

    private final IOfficeConverterPort converter;
    private final OfficeKitConfig config;

    public OfficeConvertTool(IOfficeConverterPort converter, OfficeKitConfig config) {
        this.converter = Objects.requireNonNull(converter, "converter must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Convert an Office document to another format "
                + "(pdf/docx/xlsx/pptx/odt/ods/odp). Returns the generated file path.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "Path to the source Office document."),
                        "format", Map.of("type", "string",
                                "description", "Target format, e.g. pdf, docx, xlsx, pptx."),
                        "output_dir", Map.of("type", "string",
                                "description", "Optional output directory. Defaults to office-kit outputs.")
                ),
                "required", List.of("path", "format")
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of("type", "object",
                "properties", Map.of("output", Map.of("type", "string")),
                "required", List.of("output"));
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext exec) {
        try {
            Map<String, Object> argMap = OfficeToolSupport.args(args);
            String path = OfficeToolSupport.requiredString(argMap, "path");
            OfficeToolSupport.requireOfficeFile(path);
            String format = OfficeToolSupport.requiredString(argMap, "format").toLowerCase(Locale.ROOT);
            if (!TARGET_FORMATS.contains(format)) {
                throw new OfficeKitError(OfficeKitError.UNSUPPORTED_FORMAT,
                        "unsupported target format: " + format, null);
            }
            String outputDir = OfficeToolSupport.resolveOutputDir(
                    OfficeToolSupport.optionalString(argMap, "output_dir"), config);

            String output = converter.convertTo(path, outputDir, format);
            if (exec != null) {
                exec.recordArtifact(output);
            }
            OfficeToolSupport.pruneOutputs(config);
            return CompletableFuture.completedFuture(new ToolExecutionResult.Success(
                    Map.of("output", output),
                    List.<ContentBlock>of(new TextBlock("converted " + output)),
                    Map.of("output", output, "format", format),
                    List.of(),
                    false));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(OfficeToolSupport.failure(error));
        }
    }

    @Override
    public boolean isConcurrencySafe(Object args) {
        return true;
    }
}
