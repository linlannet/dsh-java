package net.linlan.dsh.domain.session.event.model.valobj;

/**
 * SessionEventType 表示会话事件类型。
 */
public enum SessionEventType {

    // ── Turn boundaries ───────────────────────────────────────────
    TURN_START("turn/start", false),
    TURN_END("turn/end", false),

    // ── Step boundaries ───────────────────────────────────────────
    STEP_START("step/start", false),
    STEP_END("step/end", false),

    // ── Messages ──────────────────────────────────────────────────
    USER_MESSAGE("user/message", false),
    ASSISTANT_CHUNK("assistant/chunk", true),   // chunks are replay-only; a reader can skip
    ASSISTANT_MESSAGE("assistant/message", false),

    // ── Tool calls ────────────────────────────────────────────────
    TOOL_CALL("tool/call", false),
    TOOL_RESULT("tool/result", false),

    // ── Todo ──────────────────────────────────────────────────────
    TODO_WRITE("todo/write", true),              // UI-state only; non-critical for replay

    // ── Request metadata ─────────────────────────────────────────
    REQUEST_HEADER("request/header", false),
    REQUEST_CONTEXT("request/context", true),    // routing metadata; informational

    // ── Session lifecycle ────────────────────────────────────────
    SESSION_END_SEED("session/end-seed", false),

    // ── Plan mode ────────────────────────────────────────────────
    PLAN_MODE_CHANGE("plan/mode", false),

    // ── Agent inbox ───────────────────────────────────────────────
    AGENT_INBOX_SPLICED("agent/inbox/spliced", false),

    // ── Agent lifecycle events (emitted via AgentEventListener) ──
    AGENT_EVENT("agent/event", true),  // informational; e.g. agent/cancelled, turn lifecycle

    // ── Legacy compatibility (mapped from old event types) ───────
    TASK_SUBMITTED("task/submitted", true),
    TASK_AUTO_APPROVED("task/auto-approved", true),
    TASK_APPROVAL_REQUIRED("task/approval.required", true),
    TASK_APPROVED("task/approved", true),
    TASK_EXECUTION_STARTED("task.execution/started", true),
    TASK_EXECUTION_COMPLETED("task.execution/completed", true),
    TASK_EXECUTION_FAILED("task.execution/failed", true);

    private final String wireName;
    private final boolean ignorable;

    SessionEventType(String wireName, boolean ignorable) {
        this.wireName = wireName;
        this.ignorable = ignorable;
    }

    /**
     * 处理wire 名称。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public String wireName() {
        return wireName;
    }

    /**
     * 处理ignorable。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public boolean ignorable() {
        return ignorable;
    }

    /**
     * 根据 Wire 名称。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param name 名称。
     * @return 创建的领域对象。
     */
    public static SessionEventType fromWireName(String name) {
        for (SessionEventType t : values()) {
            if (t.wireName.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown session event type: " + name);
    }
}
