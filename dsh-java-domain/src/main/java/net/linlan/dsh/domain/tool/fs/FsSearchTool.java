package net.linlan.dsh.domain.tool.fs;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 文件搜索工具。
 * <p>
 * 执行流程：
 * 1. 接收 glob 搜索模式；
 * 2. 在文件系统中查找匹配文件；
 * 3. 返回搜索结果。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class FsSearchTool implements ToolDefinition {

    private static final String NAME = "fs_search";
    private static final String DESCRIPTION =
            "Search for files matching a glob pattern within a directory. " +
            "Returns a list of matching file paths. " +
            "Common patterns: *.java, *.ts, *.json, **/*.py";

    private static final int DEFAULT_MAX = 100;

    private final IFsPort fsService;

    /**
     * 处理Fs 搜索 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fsService fs Service参数。
     * @return 方法执行结果。
     */
    public FsSearchTool(IFsPort fsService) {
        this.fsService = fsService;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "directory", Map.of(
                                "type", "string",
                                "description", "Root directory to search in. Defaults to the agent's working directory."
                        ),
                        "pattern", Map.of(
                                "type", "string",
                                "description", "Glob pattern to match files. Examples: *.java, **/*.json, *.ts"
                        ),
                        "max_results", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results. Default 100."
                        )
                ),
                "required", List.of("directory", "pattern")
        );
    }

    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", true,
                "properties", Map.of(
                        "matches", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        ),
                        "count", Map.of("type", "integer")
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
            String directory = String.valueOf(argMap.get("directory"));
            String pattern = String.valueOf(argMap.get("pattern"));

            if (directory == null || directory.isBlank()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("directory parameter is required", "MISSING_PARAM"));
            }
            if (pattern == null || pattern.isBlank()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("pattern parameter is required", "MISSING_PARAM"));
            }

            int maxResults = argMap.get("max_results") != null
                    ? ((Number) argMap.get("max_results")).intValue()
                    : DEFAULT_MAX;

            List<String> matches = fsService.search(directory, pattern, maxResults);

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(matches.size()).append(" files matching '")
              .append(pattern).append("' in ").append(directory).append(":\n");
            for (String match : matches) {
                sb.append("  ").append(match).append("\n");
            }

            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("fs_search failed: " + e.getMessage(), "EXECUTION_ERROR"));
        }
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }
}
