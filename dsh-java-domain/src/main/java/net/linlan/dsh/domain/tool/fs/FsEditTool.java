package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 精确文本替换编辑工具。
 * <p>
 * 执行流程：
 * 1. 读取目标文件；
 * 2. 校验 old_string 唯一（除非 replace_all）；
 * 3. 替换并写回。
 * <p>
 * 对齐 Claude Code 的 Edit 工具语义：old_string 必须唯一匹配，避免歧义修改。
 * 与 str_replace_editor 的差别：本工具专注单点替换，接口更窄。
 *
 *
 */
public class FsEditTool implements ToolDefinition {

    private static final String NAME = "fs_edit";
    private static final String DESCRIPTION =
            "Replace an exact string in a file with new content. " +
            "Fails if old_string is not found or matches multiple times (unless replace_all=true). " +
            "Prefer this over fs_write for modifying existing files — it prevents accidental full-file overwrites.";

    private final IFsPort fsService;
    private final Supplier<String> cwdSupplier;

    /**
     * 处理Fs Edit 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fsService fs Service参数。
     * @return 方法执行结果。
     */
    public FsEditTool(IFsPort fsService) {
        this(fsService, () -> null);
    }

    /**
     * 支持自定义当前工作目录解析的构造。
     * @param fsService   fs Service参数。
     * @param cwdSupplier 当前工作目录提供器。
     */
    public FsEditTool(IFsPort fsService, Supplier<String> cwdSupplier) {
        this.fsService = fsService;
        this.cwdSupplier = cwdSupplier != null ? cwdSupplier : () -> null;
    }

    private String resolveAbsolute(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        Path p = Paths.get(path);
        String cwd = cwdSupplier.get();
        if (!p.isAbsolute() && cwd != null && !cwd.isBlank()) {
            return Paths.get(cwd).toAbsolutePath().normalize().resolve(p).normalize().toString();
        }
        return path;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Path to the file to edit."
                        ),
                        "old_string", Map.of(
                                "type", "string",
                                "description", "The exact text to replace. Must match the file content exactly (including whitespace/indentation)."
                        ),
                        "new_string", Map.of(
                                "type", "string",
                                "description", "The replacement text. Must differ from old_string."
                        ),
                        "replace_all", Map.of(
                                "type", "boolean",
                                "description", "Replace every occurrence of old_string. Default false (requires unique match)."
                        )
                ),
                "required", List.of("path", "old_string", "new_string")
        );
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", true,
                "properties", Map.of(
                        "path", Map.of("type", "string"),
                        "replacements", Map.of("type", "integer")
                )
        );
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        try {
            Map<String, Object> argMap = (Map<String, Object>) args;
            String path = resolveAbsolute(String.valueOf(argMap.get("path")));
            Object oldObj = argMap.get("old_string");
            Object newObj = argMap.get("new_string");
            if (path == null || path.isBlank() || "null".equals(path)) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("path parameter is required", "MISSING_PARAM"));
            }
            if (oldObj == null || newObj == null) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("old_string and new_string are required", "MISSING_PARAM"));
            }
            String oldString = String.valueOf(oldObj);
            String newString = String.valueOf(newObj);
            boolean replaceAll = argMap.get("replace_all") != null
                    && Boolean.parseBoolean(String.valueOf(argMap.get("replace_all")));

            if (oldString.equals(newString)) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("old_string and new_string must differ", "NO_OP_EDIT"));
            }
            if (oldString.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("old_string must not be empty", "INVALID_ARGS"));
            }

            String content = fsService.read(path, 1, -1);
            int occurrences = countOccurrences(content, oldString);
            if (occurrences == 0) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("old_string not found in " + path, "STRING_NOT_FOUND"));
            }
            if (occurrences > 1 && !replaceAll) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail(
                                "old_string matches " + occurrences + " times in " + path
                                        + "; provide more context to make it unique or set replace_all=true",
                                "AMBIGUOUS_MATCH"));
            }

            String updated = replaceAll
                    ? content.replace(oldString, newString)
                    : replaceFirst(content, oldString, newString);
            fsService.write(path, updated);
            if (ctx != null) {
                ctx.recordArtifact(path);
            }

            int replaced = replaceAll ? occurrences : 1;
            return CompletableFuture.completedFuture(ToolExecutionResult.ok(List.of(
                    new TextBlock("Edited " + path + ": replaced " + replaced + " occurrence(s)"))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("fs_edit failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return false; }

    /**
     * 处理数量 Occurrences。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param haystack haystack参数。
     * @param needle needle参数。
     * @return 方法执行结果。
     */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * 处理replace First。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param content 内容文本。
     * @param oldString old String参数。
     * @param newString new String参数。
     * @return 方法执行结果。
     */
    private static String replaceFirst(String content, String oldString, String newString) {
        int idx = content.indexOf(oldString);
        return content.substring(0, idx) + newString + content.substring(idx + oldString.length());
    }
}
