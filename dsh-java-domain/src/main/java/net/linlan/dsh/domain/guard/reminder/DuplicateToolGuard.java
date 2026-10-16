package net.linlan.dsh.domain.guard.reminder;

import net.linlan.dsh.domain.guard.model.valobj.GuardDecision;

import java.util.Objects;

/**
 * DuplicateToolGuard 连续两次同工具同参数调用时触发重复提醒或强制拦截。
 */
public final class DuplicateToolGuard {

    public enum Mode { ADVISORY, ENFORCE }

    private final Mode mode;
    private String lastToolName;
    private String lastArguments;

    public DuplicateToolGuard() {
        this(Mode.ADVISORY);
    }

    public DuplicateToolGuard(Mode mode) {
        this.mode = mode == null ? Mode.ADVISORY : mode;
    }

    public GuardDecision check(String toolName, String arguments) {
        String safeTool = toolName == null ? "" : toolName;
        String safeArgs = arguments == null ? "" : arguments;
        boolean duplicate = safeTool.equals(lastToolName) && safeArgs.equals(lastArguments);
        GuardDecision decision;
        if (duplicate) {
            String message = "Duplicate tool call detected: tool=" + safeTool
                    + " called twice in a row with identical arguments. "
                    + "Review the previous result before repeating; consider different arguments or a different tool if progress is stalled.";
            if (mode == Mode.ENFORCE) {
                decision = GuardDecision.block(message);
            } else {
                decision = GuardDecision.allowWithReminder(message);
            }
        } else {
            decision = GuardDecision.allow();
        }
        lastToolName = safeTool;
        lastArguments = safeArgs;
        return decision;
    }

    public Mode mode() {
        return mode;
    }

    public void reset() {
        lastToolName = null;
        lastArguments = null;
    }

    public boolean isLastCall(String toolName, String arguments) {
        String safeTool = toolName == null ? "" : toolName;
        String safeArgs = arguments == null ? "" : arguments;
        return Objects.equals(safeTool, lastToolName) && Objects.equals(safeArgs, lastArguments);
    }
}
