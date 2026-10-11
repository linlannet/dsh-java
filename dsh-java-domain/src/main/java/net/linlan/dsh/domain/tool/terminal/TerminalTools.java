package net.linlan.dsh.domain.tool.terminal;

import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.terminal.adapter.port.ITerminalPort;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalReadResultVO;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSendResultVO;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSessionSnapshotVO;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 持久终端会话工具组（terminal_open/send/read/close/list）。
 * <p>
 * 与一次性 {@code shell_execute} 的差别：终端会话跨工具调用保持进程与
 * 环境变量（cd、export 等持久生效），适合交互式/多步命令。
 * Mirrors TS {@code tool-terminal}.
 *
 * <p>调用流程：Agent 发起 tool call → ToolRegistry 查找工具 → Guard 校验 → ToolExecutor 执行 → 结果回填。</p>
 * <p>示例：工具名 fs_read 与 JSON 参数先经过校验，再生成 ToolExecutionResult。</p>
 *
 */
public final class TerminalTools {

    /**
     * 处理终端 Tools。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    private TerminalTools() {}

    /**
     * Base 定义当前模块中的class契约与职责。
     */
    private abstract static class Base implements ToolDefinition {
        final ITerminalPort port;
        Base(ITerminalPort port) { this.port = port; }

        @SuppressWarnings("unchecked")
        static Map<String, Object> argMap(Object args) {
            return args instanceof Map ? (Map<String, Object>) args : Map.of();
        }

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
        /**
         * int Val。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param Map<String Map String参数。
         * @param m m参数。
         * @param k k参数。
         * @param def def参数。
         * @return 方法执行结果。
         */


        static int intVal(Map<String, Object> m, String k, int def) {
            return (int) longVal(m, k, def);
        }

        CompletableFuture<ToolExecutionResult> ok(String text) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.ok(List.of(new TextBlock(text))));
        }

        CompletableFuture<ToolExecutionResult> fail(String msg, String code) {
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(msg, code));
        }
        /**
         * snapshot Line。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param s s参数。
         * @return 方法执行结果。
         */


        static String snapshotLine(TerminalSessionSnapshotVO s) {
            return "- " + s.sessionId() + " [" + s.status() + "] " + s.name()
                    + " (cwd=" + s.cwd() + ", created " + s.createdAt() + ")";
        }
    }

    /** terminal_open：开启持久终端会话。 */
    public static final class Open extends Base {
        /**
         * 打开目标对象。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param port port参数。
         * @return 方法执行结果。
         */
        public Open(ITerminalPort port) { super(port); }
        @Override public String name() { return "terminal_open"; }
        @Override public String description() {
            return "Open a persistent terminal session (interactive shell). State such as cwd and env vars " +
                    "persists across terminal_send calls. Returns a session_id.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "name", Map.of("type", "string", "description", "Optional session name."),
                            "cwd", Map.of("type", "string", "description", "Optional working directory.")));
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
            try {
                TerminalSessionSnapshotVO s = port.open(str(m, "name").isEmpty() ? null : str(m, "name"),
                        str(m, "cwd").isEmpty() ? null : str(m, "cwd"));
                return ok("Opened terminal session:\n" + snapshotLine(s)
                        + "\nUse terminal_send(session_id=\"" + s.sessionId() + "\", command=...) to run commands.");
            } catch (Exception e) {
                return fail("terminal_open failed: " + e.getMessage(), "EXECUTION_ERROR");
            }
        }
    }

    /** terminal_send：向会话发送命令并等待输出。 */
    public static final class Send extends Base {
        /**
         * 发送数据或请求到目标端。
         */
        public Send(ITerminalPort port) { super(port); }
        @Override public String name() { return "terminal_send"; }
        @Override public String description() {
            return "Send a command to a persistent terminal session and wait for its output. " +
                    "The session keeps its state (cwd, env) between sends.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "session_id", Map.of("type", "string", "description", "Terminal session id."),
                            "command", Map.of("type", "string", "description", "Command to send."),
                            "timeout_ms", Map.of("type", "integer", "description", "Optional wait timeout (default 120s).")),
                    "required", List.of("session_id", "command"));
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
            String sid = str(m, "session_id");
            String cmd = str(m, "command");
            if (sid.isEmpty() || cmd.isEmpty()) {
                return fail("session_id and command are required", "MISSING_PARAM");
            }
            try {
                TerminalSendResultVO r = port.send(sid, cmd, longVal(m, "timeout_ms", 0));
                StringBuilder sb = new StringBuilder();
                sb.append("session ").append(r.sessionId()).append(" [").append(r.status()).append("]");
                if (!r.completed()) sb.append(" (timed out waiting for completion)");
                if (r.truncated()) sb.append(" (output truncated)");
                sb.append("\n").append(r.output() == null ? "" : r.output());
                return ok(sb.toString());
            } catch (Exception e) {
                return fail("terminal_send failed: " + e.getMessage(), "EXECUTION_ERROR");
            }
        }
    }

    /** terminal_read：读取会话尾部输出。 */
    public static final class Read extends Base {
        /**
         * 读取目标对象。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param port port参数。
         * @return 方法执行结果。
         */
        public Read(ITerminalPort port) { super(port); }
        @Override public String name() { return "terminal_read"; }
        @Override public String description() {
            return "Read the latest output tail of a terminal session without sending a command.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "session_id", Map.of("type", "string", "description", "Terminal session id."),
                            "max_chars", Map.of("type", "integer", "description", "Max chars to read (default 8000).")),
                    "required", List.of("session_id"));
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
            String sid = str(m, "session_id");
            if (sid.isEmpty()) return fail("session_id is required", "MISSING_PARAM");
            try {
                TerminalReadResultVO r = port.read(sid, intVal(m, "max_chars", 0));
                return ok("session " + r.sessionId() + " [" + r.status() + "]"
                        + (r.truncated() ? " (truncated)" : "") + "\n"
                        + (r.content() == null ? "" : r.content()));
            } catch (Exception e) {
                return fail("terminal_read failed: " + e.getMessage(), "EXECUTION_ERROR");
            }
        /**
         * 判断当前状态是否满足条件。
         * @param args 启动参数。
         * @return 布尔结果：满足条件为 true，否则 false。
         */

        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
    }

    /** terminal_close：关闭会话。 */
    public static final class Close extends Base {
        /**
         * 释放资源并关闭关联连接。
         */
        public Close(ITerminalPort port) { super(port); }
        @Override public String name() { return "terminal_close"; }
        @Override public String description() {
            return "Close a terminal session and terminate its process.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false,
                    "properties", Map.of(
                            "session_id", Map.of("type", "string", "description", "Terminal session id.")),
                    "required", List.of("session_id"));
        }
        @Override public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            Map<String, Object> m = argMap(args);
            String sid = str(m, "session_id");
            if (sid.isEmpty()) return fail("session_id is required", "MISSING_PARAM");
            try {
                TerminalSessionSnapshotVO s = port.close(sid);
                return ok("Closed terminal session:\n" + snapshotLine(s));
            } catch (Exception e) {
                return fail("terminal_close failed: " + e.getMessage(), "EXECUTION_ERROR");
            }
        }
    }

    /** terminal_list：列出会话。 */
    public static final class ListSessions extends Base {
        /**
         * 列出Sessions。
         */
        public ListSessions(ITerminalPort port) { super(port); }
        @Override public String name() { return "terminal_list"; }
        @Override public String description() {
            return "List all live terminal sessions with their status.";
        }
        @Override public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of());
        }
        @Override public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            List<TerminalSessionSnapshotVO> sessions = port.list();
            if (sessions.isEmpty()) return ok("No live terminal sessions.");
            StringBuilder sb = new StringBuilder("Terminal sessions:\n");
            for (TerminalSessionSnapshotVO s : sessions) sb.append(snapshotLine(s)).append("\n");
            return ok(sb.toString());
        /**
         * 判断当前状态是否满足条件。
         * @param args 启动参数。
         * @return 布尔结果：满足条件为 true，否则 false。
         */

        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
    }
}
