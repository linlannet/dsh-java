package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 文件写入工具。
 * <p>
 * 执行流程：
 * 1. 接收文件路径和写入内容；
 * 2. 按需创建或覆盖文件；
 * 3. 返回写入结果。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class FsWriteTool implements ToolDefinition {

    private static final String NAME = "fs_write";
    private static final String DESCRIPTION =
            "Write content to a file. Creates the file if it doesn't exist, " +
            "overwrites if it does. Parent directories are created automatically. " +
            "Use this for creating new files, updating configurations, or saving output.";

    private final IFsPort fsService;
    private final Supplier<String> cwdSupplier;

    /**
     * 处理Fs Write 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fsService fs Service参数。
     * @return 方法执行结果。
     */
    public FsWriteTool(IFsPort fsService) {
        this(fsService, () -> null);
    }

    /**
     * 支持自定义当前工作目录解析的构造。
     * @param fsService   fs Service参数。
     * @param cwdSupplier 当前工作目录提供器。
     */
    public FsWriteTool(IFsPort fsService, Supplier<String> cwdSupplier) {
        this.fsService = fsService;
        this.cwdSupplier = cwdSupplier != null ? cwdSupplier : () -> null;
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
                                "description", "Path to the file to write. Relative paths resolve against the agent's working directory."
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "The content to write to the file."
                        )
                ),
                "required", List.of("path", "content")
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
                        "bytes_written", Map.of("type", "integer")
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
            String path = String.valueOf(argMap.get("path"));
            String content = String.valueOf(argMap.get("content"));

            if (path == null || path.isBlank()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("path parameter is required", "MISSING_PARAM"));
            }
            if (content == null) {
                content = "";
            }

            String effectivePath = path;
            Path p = Paths.get(path);
            String cwd = cwdSupplier.get();
            if (!p.isAbsolute() && cwd != null && !cwd.isBlank()) {
                effectivePath = Paths.get(cwd).toAbsolutePath().normalize().resolve(p).normalize().toString();
            }

            fsService.write(effectivePath, content);
            if (ctx != null) {
                ctx.recordArtifact(effectivePath);
            }

            int bytes = content.getBytes(StandardCharsets.UTF_8).length;
            String result = "Wrote " + bytes + " bytes to " + effectivePath;
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(result))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("fs_write failed: " + e.getMessage(), "EXECUTION_ERROR"));
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
}
