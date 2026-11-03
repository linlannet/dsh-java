package net.linlan.dsh.domain.tool.schedule;

import net.linlan.dsh.domain.agent.service.subagent.SubagentSessions;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.schedule.IScheduleService;
import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import net.linlan.dsh.domain.schedule.model.exception.ScheduleConflictException;
import net.linlan.dsh.domain.schedule.model.exception.ScheduleNotFoundException;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleCreateRequest;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleEditRequest;
import net.linlan.dsh.domain.schedule.model.valobj.SchedulePage;
import net.linlan.dsh.domain.schedule.model.valobj.SchedulePatch;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleTarget;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 调度任务工具组：schedule_create / schedule_list / schedule_view / schedule_edit。
 *
 * <p>对齐参考源 0.2.2：创建只接受 {@code title}、{@code prompt}、{@code rrule} 与必填 {@code target}；
 * 列表返回 {@code { items, shown, total, limit, offset }} 并覆盖活动、暂停与已结束任务；
 * 编辑统一走 {@code schedule_edit}（{@code action=update|delete}）；全部按 id 操作宿主任务。</p>
 *
 * <p>被委派的子 agent 调用时返回错误码 {@code subagent_session}。</p>
 */
public final class ScheduleTools {

    private ScheduleTools() {
    }

    /**
     * 工具基类：共享参数解析、结果包装与子 agent 拦截。
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

        static String str(Map<String, Object> m, String k) {
            Object v = m.get(k);
            return v == null ? "" : String.valueOf(v).trim();
        }

        static Boolean boolVal(Map<String, Object> m, String k) {
            Object v = m.get(k);
            if (v == null) {
                return null;
            }
            if (v instanceof Boolean b) {
                return b;
            }
            String text = String.valueOf(v).trim();
            if (text.isEmpty()) {
                return null;
            }
            return Boolean.parseBoolean(text);
        }

        static int intVal(Map<String, Object> m, String k, int def) {
            Object v = m.get(k);
            if (v instanceof Number n) {
                return n.intValue();
            }
            if (v == null) {
                return def;
            }
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        CompletableFuture<ToolExecutionResult> ok(String text) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(text))));
        }

        CompletableFuture<ToolExecutionResult> fail(String msg, String code) {
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(msg, code));
        }

        /** 子 agent 调用时返回拒绝结果，正常调用返回 null。 */
        CompletableFuture<ToolExecutionResult> guardSubagent() {
            return SubagentSessions.isSubagent(sessionId)
                    ? fail("A delegated subagent cannot use scheduled tasks.", "subagent_session")
                    : null;
        }

        static String detail(ScheduleView view) {
            ScheduleRecord record = view.record();
            StringBuilder sb = new StringBuilder();
            sb.append("- id: ").append(record.id()).append('\n');
            sb.append("  title: ").append(record.title()).append('\n');
            sb.append("  kind: ").append(record.timing().kind()).append('\n');
            sb.append("  target: ").append(record.target().name().toLowerCase())
                    .append(" (").append(view.deliveryMode()).append(")\n");
            sb.append("  state: ").append(view.state()).append('\n');
            sb.append("  next: ").append(view.nextAt() == null ? "-" : view.nextAt()).append('\n');
            sb.append("  paused: ").append(record.paused()).append(", ended: ").append(record.ended()).append('\n');
            sb.append("  prompt: ").append(record.prompt());
            return sb.toString();
        }

        static String line(ScheduleView view) {
            ScheduleRecord record = view.record();
            return "- " + record.id() + " [" + record.timing().kind() + "] "
                    + view.state() + " next=" + (view.nextAt() == null ? "-" : view.nextAt())
                    + " target=" + record.target().name().toLowerCase()
                    + ": " + record.title();
        }
    }

    /** schedule_create：按 RRULE 与 target 创建宿主任务。 */
    public static final class Create extends Base {
        /**
         * 构造创建工具。
         *
         * @param schedules 调度服务
         * @param sessionId 调用方会话 ID
         */
        public Create(IScheduleService schedules, String sessionId) {
            super(schedules, sessionId);
        }

        @Override
        public String name() {
            return "schedule_create";
        }

        @Override
        public String description() {
            return "Create a scheduled task for this host. Requires title, prompt, rrule (one DTSTART line plus "
                    + "one RRULE line) and target (this_session | new_once | new). Delivery follows target.";
        }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "title", Map.of("type", "string", "description", "Short task title."),
                            "prompt", Map.of("type", "string",
                                    "description", "Prompt delivered when the task fires."),
                            "rrule", Map.of("type", "string",
                                    "description", "Time rule, e.g. DTSTART;TZID=Asia/Shanghai:20261013T090000\n"
                                            + "RRULE:FREQ=DAILY"),
                            "target", Map.of("type", "string",
                                    "enum", List.of("this_session", "new_once", "new"),
                                    "description", "Delivery destination of the fired task.")),
                    "required", List.of("title", "prompt", "rrule", "target"));
        }

        @Override
        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            CompletableFuture<ToolExecutionResult> blocked = guardSubagent();
            if (blocked != null) {
                return blocked;
            }
            Map<String, Object> m = argMap(args);
            String title = str(m, "title");
            String prompt = str(m, "prompt");
            String rrule = str(m, "rrule");
            String target = str(m, "target");
            if (title.isEmpty() || prompt.isEmpty() || rrule.isEmpty() || target.isEmpty()) {
                return fail("title, prompt, rrule and target are required", "MISSING_PARAM");
            }
            try {
                ScheduleRecord record = schedules.create(
                        new ScheduleCreateRequest(title, prompt, rrule, ScheduleTarget.fromWire(target), sessionId));
                return ok("Schedule created:\n" + detail(ScheduleView.of(record, Instant.now())));
            } catch (IllegalArgumentException e) {
                return fail("schedule_create failed: " + e.getMessage(), "INVALID_PARAM");
            }
        }
    }

    /** schedule_list：分页列出宿主任务。 */
    public static final class ListAll extends Base {
        /**
         * 构造列表工具。
         *
         * @param schedules 调度服务
         * @param sessionId 调用方会话 ID
         */
        public ListAll(IScheduleService schedules, String sessionId) {
            super(schedules, sessionId);
        }

        @Override
        public String name() {
            return "schedule_list";
        }

        @Override
        public String description() {
            return "List scheduled tasks of this host, including active, paused and ended ones. "
                    + "Returns { items, shown, total, limit, offset }.";
        }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "active", Map.of("type", "boolean",
                                    "description", "true: only active tasks; false: all tasks."),
                            "limit", Map.of("type", "integer", "description", "Page size, default 20."),
                            "offset", Map.of("type", "integer", "description", "Page offset, default 0.")),
                    "required", List.of());
        }

        @Override
        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            CompletableFuture<ToolExecutionResult> blocked = guardSubagent();
            if (blocked != null) {
                return blocked;
            }
            Map<String, Object> m = argMap(args);
            try {
                SchedulePage page = schedules.list(boolVal(m, "active"),
                        intVal(m, "limit", 20), intVal(m, "offset", 0));
                StringBuilder sb = new StringBuilder()
                        .append("Scheduled tasks: shown=").append(page.shown())
                        .append(" total=").append(page.total())
                        .append(" limit=").append(page.limit())
                        .append(" offset=").append(page.offset());
                if (page.items().isEmpty()) {
                    return ok(sb.append("\nNo scheduled tasks.").toString());
                }
                sb.append('\n');
                for (ScheduleView view : page.items()) {
                    sb.append(line(view)).append('\n');
                }
                return ok(sb.toString());
            } catch (IllegalArgumentException e) {
                return fail("schedule_list failed: " + e.getMessage(), "INVALID_PARAM");
            }
        }

        @Override
        public boolean isConcurrencySafe(Object args) {
            return true;
        }
    }

    /** schedule_view：按 id 读取任务。 */
    public static final class ViewOne extends Base {
        /**
         * 构造查看工具。
         *
         * @param schedules 调度服务
         * @param sessionId 调用方会话 ID
         */
        public ViewOne(IScheduleService schedules, String sessionId) {
            super(schedules, sessionId);
        }

        @Override
        public String name() {
            return "schedule_view";
        }

        @Override
        public String description() {
            return "Read one scheduled task by id.";
        }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "id", Map.of("type", "string", "description", "Task id.")),
                    "required", List.of("id"));
        }

        @Override
        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            CompletableFuture<ToolExecutionResult> blocked = guardSubagent();
            if (blocked != null) {
                return blocked;
            }
            String id = str(argMap(args), "id");
            if (id.isEmpty()) {
                return fail("id is required", "MISSING_PARAM");
            }
            return schedules.view(id)
                    .map(view -> ok("Schedule:\n" + detail(view)))
                    .orElseGet(() -> fail("schedule not found: " + id, "NOT_FOUND"));
        }

        @Override
        public boolean isConcurrencySafe(Object args) {
            return true;
        }
    }

    /** schedule_edit：更新或删除任务。 */
    public static final class Edit extends Base {
        /**
         * 构造编辑工具。
         *
         * @param schedules 调度服务
         * @param sessionId 调用方会话 ID
         */
        public Edit(IScheduleService schedules, String sessionId) {
            super(schedules, sessionId);
        }

        @Override
        public String name() {
            return "schedule_edit";
        }

        @Override
        public String description() {
            return "Edit a scheduled task by id. action=update changes title, prompt, rrule or target, "
                    + "or pauses/resumes it; action=delete removes it.";
        }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "id", Map.of("type", "string", "description", "Task id."),
                            "action", Map.of("type", "string", "enum", List.of("update", "delete"),
                                    "description", "update or delete."),
                            "title", Map.of("type", "string", "description", "New title (update)."),
                            "prompt", Map.of("type", "string", "description", "New prompt (update)."),
                            "rrule", Map.of("type", "string", "description", "New RRULE rule (update)."),
                            "target", Map.of("type", "string",
                                    "enum", List.of("this_session", "new_once", "new"),
                                    "description", "New delivery target (update)."),
                            "paused", Map.of("type", "boolean", "description", "Pause or resume (update)."),
                            "expected_updated_at", Map.of("type", "string",
                                    "description", "Optimistic concurrency token: last known updatedAt (ISO-8601).")),
                    "required", List.of("id", "action"));
        }

        @Override
        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            CompletableFuture<ToolExecutionResult> blocked = guardSubagent();
            if (blocked != null) {
                return blocked;
            }
            Map<String, Object> m = argMap(args);
            String id = str(m, "id");
            String action = str(m, "action");
            if (id.isEmpty() || action.isEmpty()) {
                return fail("id and action are required", "MISSING_PARAM");
            }
            String title = str(m, "title");
            String prompt = str(m, "prompt");
            String rrule = str(m, "rrule");
            String target = str(m, "target");
            String expected = str(m, "expected_updated_at");
            try {
                SchedulePatch patch = new SchedulePatch(
                        title.isEmpty() ? null : title,
                        prompt.isEmpty() ? null : prompt,
                        rrule.isEmpty() ? null : rrule,
                        target.isEmpty() ? null : ScheduleTarget.fromWire(target),
                        boolVal(m, "paused"));
                Instant expectedUpdatedAt = expected.isEmpty() ? null : Instant.parse(expected);
                ScheduleRecord record = schedules.edit(new ScheduleEditRequest(id, action, patch, expectedUpdatedAt));
                if (ScheduleEditRequest.ACTION_DELETE.equalsIgnoreCase(action)) {
                    return ok("Schedule deleted: " + record.id());
                }
                return ok("Schedule updated:\n" + detail(ScheduleView.of(record, Instant.now())));
            } catch (ScheduleNotFoundException e) {
                return fail(e.getMessage(), "NOT_FOUND");
            } catch (ScheduleConflictException e) {
                return fail(e.getMessage(), "schedule_conflict");
            } catch (IllegalArgumentException e) {
                return fail("schedule_edit failed: " + e.getMessage(), "INVALID_PARAM");
            }
        }
    }
}
