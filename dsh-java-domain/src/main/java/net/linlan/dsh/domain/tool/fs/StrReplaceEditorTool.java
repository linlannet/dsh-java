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
 * 字符串替换编辑工具。
 * <p>
 * 执行流程：
 * 1. 接收查看、创建、替换或插入命令；
 * 2. 对目标文件执行对应编辑动作；
 * 3. 返回编辑结果或错误信息。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class StrReplaceEditorTool implements ToolDefinition {

    private static final String NAME = "str_replace_editor";

    private static final String DESCRIPTION = """
            Custom editing tool for viewing, creating and editing files.
            * 状态会在多次命令调用和用户对话之间保持。
            * 如果 `path` 是文件，`view` 会按 `cat -n` 的效果显示内容。
              如果 `path` 是目录，`view` 会列出最多两层的非隐藏文件和目录。
            * 如果指定 `path` 已存在且是文件，则不能使用 `create`。
            * 如果 `command` 输出过长，会被截断。

            Notes for using the `str_replace` command:
            * `old_str` 参数必须与原文件中的一行或多行内容完全一致，注意空白字符。
            * 如果 `old_str` 在文件中不唯一，就不会执行替换；请补充足够上下文以保证唯一性。
            * `new_str` 参数应包含要替换 `old_str` 的新内容。
            """;

    private static final int MAX_OUTPUT_CHARS = 50_000;

    private final IFsPort fsService;
    private final Supplier<String> cwdSupplier;

    /**
     * 处理Str Replace Editor 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fsService fs Service参数。
     * @return 方法执行结果。
     */
    public StrReplaceEditorTool(IFsPort fsService) {
        this(fsService, () -> null);
    }

    /**
     * 支持自定义当前工作目录解析的构造。
     * @param fsService   fs Service参数。
     * @param cwdSupplier 当前工作目录提供器。
     */
    public StrReplaceEditorTool(IFsPort fsService, Supplier<String> cwdSupplier) {
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
                        "command", Map.of(
                                "type", "string",
                                "enum", List.of("view", "create", "str_replace", "insert"),
                                "description", "The command to execute."
                        ),
                        "path", Map.of(
                                "type", "string",
                                "description", "Absolute or relative path to the file or directory."
                        ),
                        "old_str", Map.of(
                                "type", "string",
                                "description", "Required for str_replace. The exact string to replace."
                        ),
                        "new_str", Map.of(
                                "type", "string",
                                "description", "Required for str_replace and create. The new string."
                        ),
                        "insert_line", Map.of(
                                "type", "integer",
                                "description", "Required for insert. Line number after which to insert (0 = beginning)."
                        )
                ),
                "required", List.of("command", "path")
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
                        "output", Map.of("type", "string")
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
            String command = String.valueOf(argMap.get("command"));
            String path = resolveAbsolute(String.valueOf(argMap.get("path")));

            if (command == null || command.isBlank()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("command parameter is required", "MISSING_PARAM"));
            }

            String result = switch (command) {
                case "view" -> doView(path);
                case "create" -> doCreate(path, String.valueOf(argMap.getOrDefault("new_str", "")));
                case "str_replace" -> doStrReplace(
                        path,
                        String.valueOf(argMap.get("old_str")),
                        String.valueOf(argMap.get("new_str")));
                case "insert" -> doInsert(
                        path,
                        String.valueOf(argMap.get("new_str")),
                        ((Number) argMap.get("insert_line")).intValue());
                default -> "Unknown command: " + command;
            };

            boolean wrote = ("create".equals(command) || "str_replace".equals(command) || "insert".equals(command))
                    && !result.startsWith("Error:")
                    && !result.startsWith("Unknown");
            if (wrote && ctx != null) {
                ctx.recordArtifact(path);
            }

            if (result.length() > MAX_OUTPUT_CHARS) {
                result = result.substring(0, MAX_OUTPUT_CHARS)
                        + "\n<response clipped><NOTE>Output truncated. Use `view` with a specific path to see more.</NOTE>";
            }

            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(result))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("str_replace_editor failed: " + e.getMessage(), "EXECUTION_ERROR"));
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
     * 处理do View。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param path 文件或资源路径。
     * @return 方法执行结果。
     */
    private String doView(String path) {
        if (!fsService.exists(path)) {
            return "File not found: " + path;
        }

        // 如果是目录，列出内容
        try {
            List<IFsPort.DirEntry> entries = fsService.listDir(path);
            StringBuilder sb = new StringBuilder();
            sb.append("Directory: ").append(path).append("\n");
            for (IFsPort.DirEntry e : entries) {
                String icon = e.type().equals("directory") ? "[DIR] " : "      ";
                sb.append(icon).append(e.name());
                if (e.type().equals("file")) {
                    sb.append(" (").append(e.size()).append(" bytes)");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception ignored) {
            // 不是目录，则按文件处理
        }

        // 读取文件并附加行号
        String content = fsService.read(path, 1, 0);
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int lineNumWidth = String.valueOf(lines.length).length();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%" + lineNumWidth + "d", i + 1))
              .append("→ ")
              .append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 处理do Create。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param path 文件或资源路径。
     * @param content 内容文本。
     * @return 方法执行结果。
     */
    private String doCreate(String path, String content) {
        if (fsService.exists(path)) {
            return "Error: File already exists: " + path + ". Use str_replace to edit it.";
        }
        fsService.write(path, content);
        return "File created successfully: " + path;
    }

    /**
     * 处理do Str Replace。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param path 文件或资源路径。
     * @param oldStr old Str参数。
     * @param newStr new Str参数。
     * @return 方法执行结果。
     */
    private String doStrReplace(String path, String oldStr, String newStr) {
        if (oldStr == null || oldStr.isEmpty()) {
            return "Error: old_str is required for str_replace command.";
        }
        if (newStr == null) {
            newStr = "";
        }

        String content = fsService.read(path, 1, 0);

        // 统计出现次数
        int count = countOccurrences(content, oldStr);
        if (count == 0) {
            return "Error: old_str not found in file. Make sure it matches exactly, including whitespace.";
        }
        if (count > 1) {
            return "Error: old_str appears " + count + " times in the file. "
                    + "Provide more context to make it unique.";
        }

        String updated = content.replace(oldStr, newStr);
        fsService.write(path, updated);
        return "File updated successfully: " + path;
    }

    /**
     * 处理do Insert。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param path 文件或资源路径。
     * @param content 内容文本。
     * @param insertLine insert Line参数。
     * @return 方法执行结果。
     */
    private String doInsert(String path, String content, int insertLine) {
        String existing = fsService.read(path, 1, 0);
        String[] lines = existing.split("\n", -1);

        if (insertLine < 0) insertLine = 0;
        if (insertLine > lines.length) insertLine = lines.length;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == insertLine) {
                sb.append(content);
                if (!content.endsWith("\n")) sb.append("\n");
            }
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        // 追加到文件末尾
        if (insertLine >= lines.length) {
            sb.append(content);
        }

        fsService.write(path, sb.toString());
        return "Text inserted at line " + (insertLine + 1) + " in: " + path;
    }

    /**
     * 处理数量 Occurrences。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param text text参数。
     * @param substr substr参数。
     * @return 方法执行结果。
     */
    private static int countOccurrences(String text, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }
}
