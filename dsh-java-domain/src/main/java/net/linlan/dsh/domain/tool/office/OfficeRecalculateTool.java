package net.linlan.dsh.domain.tool.office;

import net.linlan.dsh.domain.document.office.model.error.OfficeKitError;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.storage.conversion.adapter.port.IOfficeConverterPort;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * office_recalculate 通过重新保存表格触发公式重算。
 *
 * <p>调用流程：校验表格扩展名 → 转换到 xlsx 临时目录 → 返回重算后文件。</p>
 * <p>示例：{@code office_recalculate(path="budget.xlsx")}。</p>
 */
public final class OfficeRecalculateTool implements ToolDefinition {

    private static final String NAME = "office_recalculate";
    private static final Set<String> SPREADSHEET_EXTENSIONS = Set.of("xlsx", "xls", "ods");
    private static final String RECALCULATED_DIR = "recalculated";

    private final IOfficeConverterPort converter;
    private final OfficeKitConfig config;

    public OfficeRecalculateTool(IOfficeConverterPort converter, OfficeKitConfig config) {
        this.converter = Objects.requireNonNull(converter, "converter must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Recalculate spreadsheet formulas by re-saving the workbook as xlsx. "
                + "Supports xlsx/xls/ods and returns the recalculated file path.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "path", Map.of("type", "string",
                                "description", "Path to the spreadsheet."),
                        "output_dir", Map.of("type", "string",
                                "description", "Optional output directory. Defaults to office-kit recalculation dir.")
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
            String extension = OfficeToolSupport.extension(path).toLowerCase(Locale.ROOT);
            if (!SPREADSHEET_EXTENSIONS.contains(extension)) {
                throw new OfficeKitError(OfficeKitError.UNSUPPORTED_FORMAT,
                        "office_recalculate supports xlsx/xls/ods: " + path, null);
            }
            String baseDir = OfficeToolSupport.optionalString(argMap, "output_dir");
            String outputDir = OfficeToolSupport.resolveOutputDir(
                    baseDir == null
                            ? Path.of(config.outputDirectory(), RECALCULATED_DIR).toString()
                            : baseDir,
                    config);

            String output = converter.convertTo(path, outputDir, "xlsx");
            if (exec != null) {
                exec.recordArtifact(output);
            }
            OfficeToolSupport.pruneOutputs(config);
            return CompletableFuture.completedFuture(new ToolExecutionResult.Success(
                    Map.of("output", output),
                    List.<ContentBlock>of(new TextBlock("recalculated " + output)),
                    Map.of("output", output, "format", "xlsx"),
                    List.of(),
                    false));
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(OfficeToolSupport.failure(error));
        }
    }

    @Override
    public boolean isConcurrencySafe(Object args) {
        return false;
    }
}
