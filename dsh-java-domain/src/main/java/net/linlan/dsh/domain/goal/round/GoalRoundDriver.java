package net.linlan.dsh.domain.goal.round;

import net.linlan.dsh.domain.goal.round.adapter.port.IGoalRoundPort;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class GoalRoundDriver {

    public record RoundState(
            String roundId,
            int totalGoals,
            int completedGoals,
            String currentGoal,
            boolean finished
    ) {}

    private final IGoalRoundPort port;
    private final AtomicInteger roundCounter = new AtomicInteger(0);
    private String activeRoundId;
    private final List<String> pendingGoals = new ArrayList<>();
    private final Set<String> completedGoals = new LinkedHashSet<>();
    private int totalGoals;

    public GoalRoundDriver(IGoalRoundPort port) {
        this.port = Objects.requireNonNull(port, "port must not be null");
    }

    public String startRound(List<String> goals) {
        if (activeRoundId != null) {
            throw new IllegalStateException("round already active: " + activeRoundId);
        }
        if (goals == null || goals.isEmpty()) {
            throw new IllegalArgumentException("goals must not be empty");
        }
        String roundId = "round-" + roundCounter.incrementAndGet();
        this.activeRoundId = roundId;
        this.pendingGoals.clear();
        this.pendingGoals.addAll(goals);
        this.completedGoals.clear();
        this.totalGoals = goals.size();
        port.beginRound(roundId);
        return roundId;
    }

    public String nextGoal() {
        if (activeRoundId == null) {
            throw new IllegalStateException("no active round");
        }
        if (pendingGoals.isEmpty()) {
            return null;
        }
        String goal = pendingGoals.get(0);
        String acquired = port.acquireNextGoal(activeRoundId);
        return acquired != null ? acquired : goal;
    }

    public void completeGoal(String goalId) {
        if (activeRoundId == null) {
            throw new IllegalStateException("no active round");
        }
        if (!pendingGoals.remove(goalId)) {
            throw new IllegalArgumentException("goal not in pending list: " + goalId);
        }
        completedGoals.add(goalId);
        port.markGoalComplete(activeRoundId, goalId);
        if (pendingGoals.isEmpty()) {
            port.endRound(activeRoundId);
            activeRoundId = null;
        }
    }

    public RoundState getRoundState() {
        String current = pendingGoals.isEmpty() ? null : pendingGoals.get(0);
        return new RoundState(
                activeRoundId,
                totalGoals,
                completedGoals.size(),
                current,
                activeRoundId == null
        );
    }
}
