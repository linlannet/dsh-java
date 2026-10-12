package net.linlan.dsh.domain.tool.goal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RalphLoopTool fresh-agent Ralph loop 工具")
class RalphLoopToolTest {

    @Test
    @DisplayName("start 设置 RUNNING 状态和 goal")
    void startSetsRunning() {
        var tool = new RalphLoopTool();
        var result = tool.start("build feature X");
        assertEquals("RUNNING", result.get("status"));
        assertEquals("build feature X", result.get("goal"));
    }

    @Test
    @DisplayName("start 空 goal 抛异常")
    void startBlankGoalThrows() {
        var tool = new RalphLoopTool();
        assertThrows(IllegalArgumentException.class, () -> tool.start(""));
        assertThrows(IllegalArgumentException.class, () -> tool.start(null));
    }

    @Test
    @DisplayName("重复 start 抛异常")
    void doubleStartThrows() {
        var tool = new RalphLoopTool();
        tool.start("goal1");
        assertThrows(IllegalStateException.class, () -> tool.start("goal2"));
    }

    @Test
    @DisplayName("getStatus 返回当前状态")
    void getStatusReturnsCurrent() {
        var tool = new RalphLoopTool();
        var idle = tool.getStatus();
        assertEquals("IDLE", idle.get("status"));
        assertEquals("", idle.get("goal"));
        tool.start("my goal");
        var running = tool.getStatus();
        assertEquals("RUNNING", running.get("status"));
        assertEquals("my goal", running.get("goal"));
        assertEquals(0, running.get("rounds"));
    }

    @Test
    @DisplayName("incrementRound 递增轮次")
    void incrementRound() {
        var tool = new RalphLoopTool();
        tool.start("goal");
        assertEquals(1, tool.incrementRound());
        assertEquals(2, tool.incrementRound());
        assertEquals(2, tool.getStatus().get("rounds"));
    }

    @Test
    @DisplayName("incrementRound 未运行时抛异常")
    void incrementRoundWhenNotRunningThrows() {
        var tool = new RalphLoopTool();
        assertThrows(IllegalStateException.class, tool::incrementRound);
    }

    @Test
    @DisplayName("stop 设置 STOPPED 状态")
    void stopSetsStopped() {
        var tool = new RalphLoopTool();
        tool.start("goal");
        tool.incrementRound();
        var result = tool.stop();
        assertEquals("STOPPED", result.get("status"));
        assertEquals(1, result.get("rounds"));
        assertEquals("goal", result.get("goal"));
    }

    @Test
    @DisplayName("stop 幂等")
    void stopIdempotent() {
        var tool = new RalphLoopTool();
        tool.stop();
        var result = tool.stop();
        assertEquals("STOPPED", result.get("status"));
    }
}
