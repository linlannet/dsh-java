package net.linlan.dsh.domain.tool.schedule;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.schedule.IScheduleService;
import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 调度提醒工具组：schedule_create / schedule_list / schedule_delete。
 * <p>
 * Mirrors TS {@code tool-schedule}. 提醒在当前会话内到期后被派发为一条提示。
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public final class ScheduleTools {

    /**
     * 处理调度 Tools。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    private ScheduleTools() {}

    /**
     * Base 定义当前模块中的class契约与职责。
     */
    private abstract static class Base implements ToolDefinition {
        final IScheduleService schedules;
        final String sessionId;

        Base(IScheduleService schedules, String sessionId) {
            this.schedules = schedules;
            this.sessionId = sessionId;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> argMap(Object args) {
            return args instanceof Map ? (Map<String, Object>) args : Map.of();
        }
        /**
         * 转换为字符串。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param Map<String Map String参数。
         * @param m m参数。
         * @param k k参数。
         * @return 方法执行结果。
         */


        static String str(Map<String, Object> m, String k) {
            Object v = m.get(k);
            return v == null ? "" : String.valueOf(v).trim();
        }
        /**
         * long Val。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param Map<String Map String参数。
         * @param m m参数。
         * @param k k参数。
         * @param def def参数。
         * @return 方法执行结果。
         */


        static long longVal(Map<String, Object> m, String k, long def) {
            Object v = m.get(k);
            if (v instanceof Number n) return n.longValue();
            if (v == null) return def;
            try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return def; }
        }

        CompletableFuture<ToolExecutionResult> ok(String text) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(text))));
        }

        CompletableFuture<ToolExecutionResult> fail(String msg, String code) {
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(msg, code));
        }
        /**
         * record Line。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param r r参数。
         * @return 方法执行结果。
         */


        static String recordLine(ScheduleRecord r) {
            return "- " + r.id() + " [" + r.kind() + "] at " + r.scheduledAt() + ": " + r.prompt();
        }
    }

        /**
         * 创建。
         */
    /** schedule_create：创建一次性或周期性提醒。 */
    public static final class Create extends Base {
        /**
         * 创建。
         */
        public Create(IScheduleService schedules, String sessionId) { super(schedules, sessionId); }
        @Override public String name() { return "schedule_create"; }
        @Override public String description() {
            return "Create a reminder for this session: after N seconds (once), at an ISO instant (once), " +
                    "or every N seconds (recurring, min 300s). The reminder is delivered into this session when due.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "prompt", Map.of("type", "string", "description", "Reminder content delivered when due."),
                            "after_seconds", Map.of("type", "integer", "description", "One-shot: fire after N seconds."),
                            "at", Map.of("type", "string", "description", "One-shot: fire at ISO-8601 instant (UTC)."),
                            "every_seconds", Map.of("type", "integer", "description", "Recurring: fire every N seconds (min 300).")),
                    "required", List.of("prompt"));
        /**
         * 执行工具调用。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param args 启动参数。
         * @param ctx ctx参数。
         * @return 方法执行结果。
         */

        }
        @Override public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            Map<String, Object> m = argMap(args);
            String prompt = str(m, "prompt");
            if (prompt.isEmpty()) return fail("prompt is required", "MISSING_PARAM");
            try {
                ScheduleRecord r;
                if (m.get("every_seconds") != null) {
                    r = schedules.createEvery(sessionId, prompt,
                            Duration.ofSeconds(longVal(m, "every_seconds", 300)));
                } else if (m.get("at") != null) {
                    r = schedules.createAt(sessionId, prompt, Instant.parse(str(m, "at")));
                } else if (m.get("after_seconds") != null) {
                    r = schedules.createAfter(sessionId, prompt,
                            Duration.ofSeconds(longVal(m, "after_seconds", 60)));
                } else {
                    return fail("one of after_seconds / at / every_seconds is required", "MISSING_PARAM");
                }
                return ok("Schedule created:\n" + recordLine(r));
            } catch (Exception e) {
                return fail("schedule_create failed: " + e.getMessage(), "EXECUTION_ERROR");
            }
        }
    }

    /** schedule_list：列出会话内提醒。 */
    public static final class ListAll extends Base {
        /**
         * 列出All。
         */
        public ListAll(IScheduleService schedules, String sessionId) { super(schedules, sessionId); }
        @Override public String name() { return "schedule_list"; }
        @Override public String description() {
            return "List active reminders for this session.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of());
        }
        @Override public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            List<ScheduleView> views = schedules.list(sessionId);
            if (views.isEmpty()) return ok("No active reminders.");
            StringBuilder sb = new StringBuilder("Active reminders:\n");
            for (ScheduleView v : views) {
                sb.append(recordLine(v.record())).append(" [").append(v.state()).append("]\n");
            }
            return ok(sb.toString());
        /**
         * 判断当前状态是否满足条件。
         * @param args 启动参数。
         * @return 布尔结果：满足条件为 true，否则 false。
         */

        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
    }

    /** schedule_delete：删除提醒。 */
    public static final class Delete extends Base {
        /**
         * 删除目标对象。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param schedules schedules参数。
         * @param sessionId 会话 ID。
         * @return 方法执行结果。
         */
        public Delete(IScheduleService schedules, String sessionId) { super(schedules, sessionId); }
        @Override public String name() { return "schedule_delete"; }
        @Override public String description() {
            return "Delete a reminder by id.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "id", Map.of("type", "string", "description", "Reminder id.")),
                    "required", List.of("id"));
        }
        @Override public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            String id = str(argMap(args), "id");
            if (id.isEmpty()) return fail("id is required", "MISSING_PARAM");
            boolean removed = schedules.delete(sessionId, id);
            return removed
                    ? ok("Reminder " + id + " deleted.")
                    : fail("reminder not found: " + id, "NOT_FOUND");
        }
    }
}
