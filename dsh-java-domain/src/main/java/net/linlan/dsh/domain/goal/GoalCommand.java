package net.linlan.dsh.domain.goal;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;
import net.linlan.dsh.domain.goal.model.valobj.GoalAction;
import net.linlan.dsh.domain.goal.model.valobj.GoalCommandVO;
import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class GoalCommand implements SlashCommand {

    private static final String NAME = "/goal";
    private static final String DESCRIPTION = "启动或管理 goal 驱动流程";

    private final IGoalCommandService goalCommandService;

    public GoalCommand(IGoalCommandService goalCommandService) {
        this.goalCommandService = Objects.requireNonNull(goalCommandService, "goalCommandService must not be null");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public CompletableFuture<String> execute(String args, SlashCommandContext context) {
        if (context == null || context.sessionId() == null) {
            return CompletableFuture.completedFuture("Error: no active session");
        }
        GoalAction action = parseAction(args);
        String title = (action == GoalAction.CREATE && args != null && !args.isBlank()) ? args : null;
        GoalCommandVO command = new GoalCommandVO(action, null, title, null, null, null, null);
        try {
            GoalSnapshotVO snapshot = goalCommandService.apply(context.sessionId(), command);
            return CompletableFuture.completedFuture(formatSnapshot(snapshot));
        } catch (Exception e) {
            return CompletableFuture.completedFuture("Error: " + e.getMessage());
        }
    }

    private GoalAction parseAction(String args) {
        if (args == null || args.isBlank()) {
            return GoalAction.CREATE;
        }
        String trimmed = args.trim().toLowerCase();
        return switch (trimmed) {
            case "pause" -> GoalAction.PAUSE;
            case "resume" -> GoalAction.RESUME;
            case "complete" -> GoalAction.COMPLETE;
            case "block" -> GoalAction.BLOCK;
            case "clear" -> GoalAction.CLEAR;
            case "update" -> GoalAction.UPDATE;
            default -> GoalAction.CREATE;
        };
    }

    private String formatSnapshot(GoalSnapshotVO snapshot) {
        return "goal: id=" + snapshot.goalId() + ", status=" + snapshot.status() + ", title=" + snapshot.title();
    }
}
