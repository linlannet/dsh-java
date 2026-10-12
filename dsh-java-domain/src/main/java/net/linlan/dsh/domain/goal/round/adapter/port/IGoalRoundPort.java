package net.linlan.dsh.domain.goal.round.adapter.port;

public interface IGoalRoundPort {

    void beginRound(String roundId);

    String acquireNextGoal(String roundId);

    void markGoalComplete(String roundId, String goalId);

    boolean isRoundFinished(String roundId);

    void endRound(String roundId);
}
