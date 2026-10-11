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
 * 内容搜索工具（grep）。
 * <p>
 * 执行流程：
 * 1. 接收正则/字面量模式与目录；
 * 2. 递归搜索匹配行（path:line:content）；
 * 3. 返回截断后的匹配列表。
 * <p>
 * 与 {@code fs_search}（glob 文件名匹配）互补：本工具按文件内容匹配。
 * Mirrors TS {@code tool-fs-search} (ripgrep-backed grep).
 *
 *
 */
public class FsGrepTool implements ToolDefinition {

    private static final String NAME = "fs_grep";
    private static final String DESCRIPTION =
            "Search file CONTENTS for a regex or literal pattern within a directory (like grep/ripgrep). " +
            "Returns matching lines as path:line:content. " +
            "Use this to find where a symbol, keyword, or string appears. " +
            "For finding files by NAME use fs_search instead.";

    private static final int DEFAULT_MAX = 100;

    private final IFsPort fsService;

    /**
     * 处理Fs Grep 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param fsService fs Service参数。
     * @return 方法执行结果。
     */
    public FsGrepTool(IFsPort fsService) {
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
                        "pattern", Map.of(
                                "type", "string",
                                "description", "Regex (or literal with is_regex=false) to search for in file contents."
                        ),
                        "directory", Map.of(
                                "type", "string",
                                "description", "Root directory to search in. Defaults to the agent's working directory."
                        ),
                        "include", Map.of(
                                "type", "string",
                                "description", "Optional glob to limit files, e.g. *.java. Default: all text files."
                        ),
                        "is_regex", Map.of(
                                "type", "boolean",
                                "description", "Treat pattern as a regular expression. Default true."
                        ),
                        "max_results", Map.of(
                                "type", "integer",
                                "description", "Maximum number of matching lines. Default 100."
                        )
                ),
                "required", List.of("pattern")
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
                        "matches", Map.of("type", "array"),
                        "total", Map.of("type", "integer")
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
            String pattern = String.valueOf(argMap.get("pattern"));
            if (pattern == null || pattern.isBlank() || "null".equals(pattern)) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("pattern parameter is required", "MISSING_PARAM"));
            }
            String directory = argMap.get("directory") != null
                    ? String.valueOf(argMap.get("directory")) : ".";
            String include = argMap.get("include") != null
                    ? String.valueOf(argMap.get("include")) : null;
            boolean isRegex = argMap.get("is_regex") == null
                    || Boolean.parseBoolean(String.valueOf(argMap.get("is_regex")));
            int maxResults = argMap.get("max_results") != null
                    ? ((Number) argMap.get("max_results")).intValue() : DEFAULT_MAX;

            List<String> matches = fsService.grep(directory, pattern, include, isRegex, maxResults);
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(matches.size()).append(" matching lines for '")
              .append(pattern).append("' in ").append(directory).append(":\n");
            for (String m : matches) sb.append(m).append("\n");
            if (matches.size() >= maxResults) sb.append("... [truncated at ").append(maxResults).append(" results]\n");
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("fs_grep failed: " + e.getMessage(), "EXECUTION_ERROR"));
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
