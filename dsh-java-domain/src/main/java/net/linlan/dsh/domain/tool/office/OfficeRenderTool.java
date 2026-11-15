package net.linlan.dsh.domain.tool.office;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * office_render 将 Office 文档渲染为 PDF 预览产物。
 *
 * <p>调用流程：校验路径 → 调用转换端口 → 记录输出工件。</p>
 * <p>示例：{@code office_render(path="report.docx")} 输出 PDF 路径。</p>
 */
public final class OfficeRenderTool implements ToolDefinition {

    private static final String NAME = "office_render";

    private final IOfficeConverterPort converter;
    private final OfficeKitConfig config;

    public OfficeRenderTool(IOfficeConverterPort converter, OfficeKitConfig config) {
        this.converter = Objects.requireNonNull(converter, "converter must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Render an Office document (docx/xlsx/pptx/doc/xls/ppt) to PDF for preview. "
                + "Returns the generated PDF path.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "Path to the Office document."),
                        "output_dir", Map.of("type", "string",
                                "description", "Optional output directory. Defaults to office-kit outputs.")
                ),
                "required", List.of("path")
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
            String outputDir = OfficeToolSupport.resolveOutputDir(
                    OfficeToolSupport.optionalString(argMap, "output_dir"), config);

            String output = converter.convertTo(path, outputDir, "pdf");
            if (exec != null) {
                exec.recordArtifact(output);
            }
            OfficeToolSupport.pruneOutputs(config);
            return CompletableFuture.completedFuture(new ToolExecutionResult.Success(
                    Map.of("output", output),
                    List.<ContentBlock>of(new TextBlock("rendered " + output)),
                    Map.of("output", output, "format", "pdf"),
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
