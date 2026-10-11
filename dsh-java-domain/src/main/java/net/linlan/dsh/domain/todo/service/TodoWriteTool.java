package net.linlan.dsh.domain.todo.service;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.todo.model.valobj.TodoItem;
import net.linlan.dsh.domain.todo.model.valobj.TodoWriteResultVO;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * `todo_write` 工具。
 * <p>
 * 执行流程：
 * 1. 接收完整待办列表；
 * 2. 交由领域服务做校验和事件写入；
 * 3. 返回统计后的结果文本。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class TodoWriteTool implements ToolDefinition {

    private static final String NAME = "todo_write";
    private static final String DESCRIPTION =
            "Record and update a structured task list for the current work. " +
            "Send the ENTIRE list every call — it REPLACES the previous list " +
            "(there are no partial updates, no per-item edits). Use it to plan " +
            "multi-step work and show progress: add one todo per concrete step " +
            "before you start. Keep AT MOST ONE todo in_progress at a time; " +
            "while work remains, exactly one active task should be in_progress. " +
            "Mark a todo completed the moment it is done (do not batch completions), " +
            "and allow no in_progress item only once all work is complete. " +
            "Skip the list for trivial single-step tasks. " +
            "Statuses: pending (not started), in_progress (being worked on now), " +
            "completed (finished).";

    private final SessionLog session;
    private final boolean allowParallelInProgress;
    private final TodoWriteService todoWriteService;

    /**
     * 转换为 do Write 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param session 会话参数。
     * @param allowParallelInProgress allow Parallel In Progress参数。
     * @param todoWriteService 待办 Write Service参数。
     * @return 方法执行结果。
     */
    public TodoWriteTool(SessionLog session, boolean allowParallelInProgress, TodoWriteService todoWriteService) {
        this.session = session;
        this.allowParallelInProgress = allowParallelInProgress;
        this.todoWriteService = todoWriteService;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "todos", Map.of(
                                "type", "array",
                                "description", "The COMPLETE task list, replacing any previous list.",
                                "items", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "properties", Map.of(
                                                "content", Map.of("type", "string", "description", "What the task is — a short imperative line."),
                                                "status", Map.of("type", "string", "enum", List.of("pending", "in_progress", "completed"),
                                                        "description", "pending (not started) | in_progress (now) | completed (done).")
                                        ),
                                        "required", List.of("content", "status")
                                )
                        )
                ),
                "required", List.of("todos")
        );
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() { return Map.of(); }

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
            List<Map<String, Object>> rawTodos = (List<Map<String, Object>>) argMap.get("todos");
            if (rawTodos == null) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail("todos parameter is required", "MISSING_PARAM"));
            }

            List<TodoItem> todos = new ArrayList<>();
            for (Map<String, Object> raw : rawTodos) {
                Object contentValue = raw.get("content");
                String content = contentValue == null ? null : String.valueOf(contentValue).trim();
                Object statusValue = raw.get("status");
                if (content == null || content.isBlank()) {
                    return CompletableFuture.completedFuture(
                            ToolExecutionResult.fail("invalid todo: content must be a non-empty string", "INVALID_TODO"));
                }
                if (statusValue == null) {
                    return CompletableFuture.completedFuture(
                            ToolExecutionResult.fail("invalid todo: status must not be null", "INVALID_TODO"));
                }
                String statusStr = String.valueOf(statusValue);
                todos.add(new TodoItem(content, TodoItem.Status.fromWire(statusStr)));
            }

            TodoWriteResultVO result = todoWriteService.write(session, todos, allowParallelInProgress);
            String resultText = String.format(
                    "Updated todo list: %d pending, %d in progress, %d completed.",
                    result.pending(), result.inProgress(), result.completed());

            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(resultText))));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("todo_write failed: " + e.getMessage(), "EXECUTION_ERROR"));
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
     * 转换为 Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 转换后的目标对象。
     */
    public ToolSchema toSchema() {
        return new ToolSchema(NAME, DESCRIPTION, parameters());
    }

    /**
     * 从会话事件中折叠最新待办列表。
     * <p>
     * 执行流程：
     * 1. 读取会话事件；
     * 2. 提取最后一次 todo/write；
     * 3. 返回当前待办列表。
     */
    public static List<TodoItem> foldTodos(SessionLog session, TodoWriteService todoWriteService) {
        return todoWriteService.foldTodos(session);
    }
}
