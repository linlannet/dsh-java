package net.linlan.dsh.infrastructure.adapter.goal;

import net.linlan.dsh.domain.goal.round.adapter.port.IGoalRoundPort;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InProcessGoalRound implements IGoalRoundPort {

    private final Map<String, Boolean> rounds = new ConcurrentHashMap<>();

    @Override
    public void beginRound(String roundId) {
        rounds.put(roundId, false);
    }

    @Override
    public String acquireNextGoal(String roundId) {
        return null;
    }

    @Override
    public void markGoalComplete(String roundId, String goalId) {
    }

    @Override
    public boolean isRoundFinished(String roundId) {
        return Boolean.TRUE.equals(rounds.get(roundId));
    }

    @Override
    public void endRound(String roundId) {
        rounds.put(roundId, true);
    }
}
