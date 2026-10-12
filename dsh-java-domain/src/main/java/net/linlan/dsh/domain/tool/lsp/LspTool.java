package net.linlan.dsh.domain.tool.lsp;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.lsp.ILspService;
import net.linlan.dsh.domain.lsp.model.valobj.LspLocation;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryResult;
import net.linlan.dsh.domain.lsp.model.valobj.LspQueryType;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code lsp} 工具：语言服务查询（定义/引用/实现/hover）。
 * <p>
 * Mirrors TS {@code tool-lsp}. 依赖 {@link ILspService} 注册的 per-extension provider；
 * 无 provider 时返回明确提示而不是空结果。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public class LspTool implements ToolDefinition {

    private final ILspService lspService;
    private final IFsPort fsService;

    /**
     * 处理Lsp 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param lspService lsp Service参数。
     * @param fsService fs Service参数。
     * @return 方法执行结果。
     */
    public LspTool(ILspService lspService, IFsPort fsService) {
        this.lspService = lspService;
        this.fsService = fsService;
    }

    @Override public String name() { return "lsp"; }

    @Override
    public String description() {
        return "Query language-server intelligence for a symbol at a file position: " +
                "definition, references, implementation, or hover. " +
                "Provide the file path plus 0-based line and character of the symbol.";
    }
    /**
     * 返回 parameters。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "enum", List.of("definition", "references", "implementation", "hover"),
                                "description", "Query type."),
                        "path", Map.of("type", "string", "description", "File path."),
                        "line", Map.of("type", "integer", "description", "0-based line number."),
                        "character", Map.of("type", "integer", "description", "0-based character offset.")
                ),
                "required", List.of("query", "path", "line", "character")
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
            Map<String, Object> m = args instanceof Map ? (Map<String, Object>) args : Map.of();
            String query = m.get("query") == null ? "" : String.valueOf(m.get("query")).trim().toLowerCase();
            String path = m.get("path") == null ? "" : String.valueOf(m.get("path")).trim();
            if (query.isEmpty() || path.isEmpty()) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("query and path are required", "MISSING_PARAM"));
            }
            LspQueryType type = switch (query) {
                case "definition" -> LspQueryType.DEFINITION;
                case "references" -> LspQueryType.REFERENCES;
                case "implementation" -> LspQueryType.IMPLEMENTATION;
                case "hover" -> LspQueryType.HOVER;
                default -> null;
            };
            if (type == null) {
                return CompletableFuture.completedFuture(
                        ToolExecutionResult.fail("unknown query type: " + query, "INVALID_ARGS"));
            }
            int line = m.get("line") instanceof Number n ? n.intValue() : 0;
            int character = m.get("character") instanceof Number n2 ? n2.intValue() : 0;

            String content = fsService.read(path, 1, -1);
            Optional<LspQueryResult> result = lspService.query(type, path, content, line, character);
            if (result.isEmpty()) {
                return CompletableFuture.completedFuture(ToolExecutionResult.ok(List.of(new TextBlock(
                        "No language server registered for this file type, or the query returned nothing: "
                                + path + " (" + query + " @" + line + ":" + character + ")"))));
            }
            LspQueryResult r = result.get();
            StringBuilder sb = new StringBuilder();
            sb.append(query).append(" results for ").append(path)
              .append(" @").append(line).append(":").append(character).append("\n");
            r.hoverText().ifPresent(h -> sb.append("--- hover ---\n").append(h).append("\n"));
            for (LspLocation loc : r.locations()) {
                sb.append("- ").append(loc.toString()).append("\n");
            }
            if (r.locations().isEmpty() && r.hoverText().isEmpty()) {
                sb.append("(no results)");
            }
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(sb.toString()))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("lsp failed: " + e.getMessage(), "EXECUTION_ERROR"));
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

    /**
     * 处理query Definition。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param file file参数。
     * @param line line参数。
     * @param col col参数。
     * @return 方法执行结果。
     */
    public List<LspLocation> queryDefinition(String file, int line, int col) {
        String content = fsService.read(file, 1, -1);
        Optional<LspQueryResult> result = lspService.query(LspQueryType.DEFINITION, file, content, line, col);
        return result.map(LspQueryResult::locations).orElse(List.of());
    }

    /**
     * 处理query References。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param file file参数。
     * @param line line参数。
     * @param col col参数。
     * @return 方法执行结果。
     */
    public List<LspLocation> queryReferences(String file, int line, int col) {
        String content = fsService.read(file, 1, -1);
        Optional<LspQueryResult> result = lspService.query(LspQueryType.REFERENCES, file, content, line, col);
        return result.map(LspQueryResult::locations).orElse(List.of());
    }

    /**
     * 处理query Diagnostics。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param file file参数。
     * @return 方法执行结果。
     */
    public List<String> queryDiagnostics(String file) {
        String content = fsService.read(file, 1, -1);
        Optional<LspQueryResult> result = lspService.query(LspQueryType.HOVER, file, content, 0, 0);
        return result.flatMap(LspQueryResult::hoverText)
                .map(text -> List.of(text.split("\n")))
                .orElse(List.of());
    }
}
