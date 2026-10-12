package net.linlan.dsh.domain.tool.goal;

import java.util.Map;

public final class RalphLoopTool {

    public enum LoopStatus {
        IDLE, RUNNING, STOPPED
    }

    private LoopStatus status = LoopStatus.IDLE;
    private String currentGoal;
    private int roundCount = 0;

    public Map<String, Object> start(String goal) {
        if (status == LoopStatus.RUNNING) {
            throw new IllegalStateException("ralph loop already running");
        }
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal must not be blank");
        }
        this.currentGoal = goal;
        this.status = LoopStatus.RUNNING;
        this.roundCount = 0;
        return Map.of("status", status.name(), "goal", currentGoal);
    }

    public Map<String, Object> getStatus() {
        return Map.of(
                "status", status.name(),
                "goal", currentGoal == null ? "" : currentGoal,
                "rounds", roundCount
        );
    }

    public Map<String, Object> stop() {
        if (status != LoopStatus.RUNNING) {
            status = LoopStatus.STOPPED;
            return Map.of("status", status.name());
        }
        status = LoopStatus.STOPPED;
        return Map.of("status", status.name(), "goal", currentGoal, "rounds", roundCount);
    }

    public int incrementRound() {
        if (status != LoopStatus.RUNNING) {
            throw new IllegalStateException("ralph loop not running");
        }
        return ++roundCount;
    }
}
