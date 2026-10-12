package net.linlan.dsh.domain.guard.reminder;

import net.linlan.dsh.domain.guard.model.valobj.GuardDecision;

import java.util.Objects;

/**
 * DuplicateToolGuard 连续两次同工具同参数调用时触发重复提醒。
 */
public final class DuplicateToolGuard {

    private String lastToolName;
    private String lastArguments;

    public GuardDecision check(String toolName, String arguments) {
        String safeTool = toolName == null ? "" : toolName;
        String safeArgs = arguments == null ? "" : arguments;
        boolean duplicate = safeTool.equals(lastToolName) && safeArgs.equals(lastArguments);
        GuardDecision decision;
        if (duplicate) {
            String reminder = "Duplicate tool call detected: tool=" + safeTool
                    + " called twice in a row with identical arguments. "
                    + "Review the previous result before repeating; consider different arguments or a different tool if progress is stalled.";
            decision = GuardDecision.allowWithReminder(reminder);
        } else {
            decision = GuardDecision.allow();
        }
        lastToolName = safeTool;
        lastArguments = safeArgs;
        return decision;
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
