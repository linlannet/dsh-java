package net.linlan.dsh.infrastructure.adapter.goal;

import net.linlan.dsh.domain.goal.round.adapter.port.IGoalRoundPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InProcessGoalRoundTest {

    private InProcessGoalRound round;

    @BeforeEach
    void setUp() {
        round = new InProcessGoalRound();
    }

    @Test
    void implementsGoalRoundPort() {
        assertTrue(round instanceof IGoalRoundPort);
    }

    @Test
    void isRoundFinishedReturnsFalseForUnknownRound() {
        assertFalse(round.isRoundFinished("unknown"));
    }

    @Test
    void beginRoundMakesRoundNotFinished() {
        round.beginRound("r1");
        assertFalse(round.isRoundFinished("r1"));
    }

    @Test
    void endRoundMarksRoundFinished() {
        round.beginRound("r1");
        round.endRound("r1");
        assertTrue(round.isRoundFinished("r1"));
    }

    @Test
    void endRoundWithoutBeginAlsoMarksFinished() {
        round.endRound("solo");
        assertTrue(round.isRoundFinished("solo"));
    }

    @Test
    void acquireNextGoalReturnsNull() {
        round.beginRound("r1");
        assertNull(round.acquireNextGoal("r1"));
    }

    @Test
    void acquireNextGoalReturnsNullForUnknownRound() {
        assertNull(round.acquireNextGoal("unknown"));
    }

    @Test
    void markGoalCompleteDoesNotThrowAndDoesNotFinishRound() {
        round.beginRound("r1");
        round.markGoalComplete("r1", "g1");
        assertFalse(round.isRoundFinished("r1"));
    }

    @Test
    void multipleRoundsAreIndependent() {
        round.beginRound("a");
        round.endRound("a");
        round.beginRound("b");
        assertTrue(round.isRoundFinished("a"));
        assertFalse(round.isRoundFinished("b"));
    }

    @Test
    void reEndingRoundKeepsFinished() {
        round.beginRound("r1");
        round.endRound("r1");
        round.endRound("r1");
        assertTrue(round.isRoundFinished("r1"));
    }
}
