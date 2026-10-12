package net.linlan.dsh.domain.goal.round;

import net.linlan.dsh.domain.goal.round.adapter.port.IGoalRoundPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GoalRoundDriver 竞争隔离的 goal-round 驱动")
class GoalRoundDriverTest {

    static final class StubPort implements IGoalRoundPort {
        final List<String> begun = new ArrayList<>();
        final List<String> ended = new ArrayList<>();
        final List<String> completed = new ArrayList<>();

        @Override
        public void beginRound(String roundId) {
            begun.add(roundId);
        }

        @Override
        public String acquireNextGoal(String roundId) {
            return null;
        }

        @Override
        public void markGoalComplete(String roundId, String goalId) {
            completed.add(goalId);
        }

        @Override
        public boolean isRoundFinished(String roundId) {
            return ended.contains(roundId);
        }

        @Override
        public void endRound(String roundId) {
            ended.add(roundId);
        }
    }

    @Test
    @DisplayName("startRound 返回 roundId 并调用端口 beginRound")
    void startRoundReturnsId() {
        var port = new StubPort();
        var driver = new GoalRoundDriver(port);
        String id = driver.startRound(List.of("g1", "g2"));
        assertNotNull(id);
        assertTrue(id.startsWith("round-"));
        assertEquals(List.of(id), port.begun);
    }

    @Test
    @DisplayName("startRound 空 goals 抛异常")
    void startRoundEmptyThrows() {
        var driver = new GoalRoundDriver(new StubPort());
        assertThrows(IllegalArgumentException.class, () -> driver.startRound(List.of()));
        assertThrows(IllegalArgumentException.class, () -> driver.startRound(null));
    }

    @Test
    @DisplayName("nextGoal 返回第一个待执行 goal")
    void nextGoalReturnsFirst() {
        var driver = new GoalRoundDriver(new StubPort());
        driver.startRound(List.of("g1", "g2"));
        assertEquals("g1", driver.nextGoal());
    }

    @Test
    @DisplayName("nextGoal 无活跃 round 抛异常")
    void nextGoalNoActiveRoundThrows() {
        var driver = new GoalRoundDriver(new StubPort());
        assertThrows(IllegalStateException.class, driver::nextGoal);
    }

    @Test
    @DisplayName("completeGoal 移除待执行并记录完成")
    void completeGoalAdvances() {
        var port = new StubPort();
        var driver = new GoalRoundDriver(port);
        driver.startRound(List.of("g1", "g2"));
        driver.completeGoal("g1");
        assertEquals(List.of("g1"), port.completed);
        assertEquals("g2", driver.nextGoal());
    }

    @Test
    @DisplayName("所有 goal 完成后 round 自动结束")
    void roundAutoEnds() {
        var port = new StubPort();
        var driver = new GoalRoundDriver(port);
        String id = driver.startRound(List.of("g1", "g2"));
        driver.completeGoal("g1");
        driver.completeGoal("g2");
        assertEquals(List.of(id), port.ended);
        var state = driver.getRoundState();
        assertTrue(state.finished());
    }

    @Test
    @DisplayName("getRoundState 反映当前进度")
    void roundStateReflectsProgress() {
        var driver = new GoalRoundDriver(new StubPort());
        driver.startRound(List.of("g1", "g2", "g3"));
        driver.completeGoal("g1");
        var state = driver.getRoundState();
        assertEquals(3, state.totalGoals());
        assertEquals(1, state.completedGoals());
        assertEquals("g2", state.currentGoal());
        assertFalse(state.finished());
    }

    @Test
    @DisplayName("round 结束后可启动新 round")
    void newRoundAfterFinish() {
        var driver = new GoalRoundDriver(new StubPort());
        driver.startRound(List.of("g1"));
        driver.completeGoal("g1");
        String id2 = driver.startRound(List.of("g2"));
        assertNotNull(id2);
    }

    @Test
    @DisplayName("重复 startRound 抛异常")
    void doubleStartThrows() {
        var driver = new GoalRoundDriver(new StubPort());
        driver.startRound(List.of("g1"));
        assertThrows(IllegalStateException.class, () -> driver.startRound(List.of("g2")));
    }

    @Test
    @DisplayName("completeGoal 未知 goal 抛异常")
    void completeUnknownGoalThrows() {
        var driver = new GoalRoundDriver(new StubPort());
        driver.startRound(List.of("g1"));
        assertThrows(IllegalArgumentException.class, () -> driver.completeGoal("nope"));
    }

    @Test
    @DisplayName("completeGoal 无活跃 round 抛异常")
    void completeNoActiveRoundThrows() {
        var driver = new GoalRoundDriver(new StubPort());
        assertThrows(IllegalStateException.class, () -> driver.completeGoal("g1"));
    }

    @Test
    @DisplayName("null port 抛异常")
    void nullPortRejected() {
        assertThrows(NullPointerException.class, () -> new GoalRoundDriver(null));
    }
}
