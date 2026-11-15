package net.linlan.dsh.domain.tool.goal;

import net.linlan.dsh.domain.tool.model.error.ToolArgsError;

import java.util.Map;

/**
 * RalphLoopTool 管理 Ralph 循环的启动、状态与推进。
 *
 * <p>调用流程：状态校验 → 更新循环状态 → 返回快照。</p>
 * <p>示例：循环已运行时再次启动返回 {@code RALPH_LOOP_ALREADY_RUNNING}。</p>
 */
public final class RalphLoopTool {

    public enum LoopStatus {
        IDLE, RUNNING, STOPPED
    }

    private LoopStatus status = LoopStatus.IDLE;
    private String currentGoal;
    private int roundCount = 0;

    public Map<String, Object> start(String goal) {
        if (status == LoopStatus.RUNNING) {
            throw new RalphLoopError(RalphLoopError.ALREADY_RUNNING, "ralph loop already running");
        }
        if (goal == null || goal.isBlank()) {
            throw new ToolArgsError("goal must not be blank");
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
            throw new RalphLoopError(RalphLoopError.NOT_RUNNING, "ralph loop not running");
        }
        return ++roundCount;
    }
}
