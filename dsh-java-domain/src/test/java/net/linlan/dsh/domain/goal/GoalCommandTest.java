package net.linlan.dsh.domain.goal;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;
import net.linlan.dsh.domain.goal.model.valobj.GoalAction;
import net.linlan.dsh.domain.goal.model.valobj.GoalCommandVO;
import net.linlan.dsh.domain.goal.model.valobj.GoalSnapshotVO;
import net.linlan.dsh.domain.goal.model.valobj.GoalStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GoalCommand /goal slash 命令")
class GoalCommandTest {

    static GoalSnapshotVO snapshot(String sessionId, String goalId, GoalStatus status, String title) {
        return new GoalSnapshotVO(sessionId, goalId, 1, title, null, status, null, 256, 0, true, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("name 返回 /goal")
    void nameReturnsGoal() {
        var cmd = new GoalCommand((sid, c) -> snapshot(sid, "g1", GoalStatus.ACTIVE, "t"));
        assertEquals("/goal", cmd.name());
    }

    @Test
    @DisplayName("description 非空")
    void descriptionNotEmpty() {
        var cmd = new GoalCommand((sid, c) -> snapshot(sid, "g1", GoalStatus.ACTIVE, "t"));
        assertNotNull(cmd.description());
        assertFalse(cmd.description().isBlank());
    }

    @Test
    @DisplayName("execute 创建 goal 返回快照摘要")
    void executeCreatesGoal() throws Exception {
        var cmd = new GoalCommand((sid, c) -> snapshot(sid, "g1", GoalStatus.ACTIVE, "build feature"));
        var ctx = new SlashCommand.SlashCommandContext("session-1", "/work");
        String result = cmd.execute("build feature", ctx).get();
        assertTrue(result.contains("g1"));
        assertTrue(result.contains("ACTIVE"));
    }

    @Test
    @DisplayName("execute 无 session 返回错误")
    void executeNoSessionReturnsError() throws Exception {
        var cmd = new GoalCommand((sid, c) -> snapshot(sid, "g1", GoalStatus.ACTIVE, "t"));
        String result = cmd.execute("goal", null).get();
        assertTrue(result.startsWith("Error"));
    }

    @Test
    @DisplayName("execute 无 context sessionId 返回错误")
    void executeNoSessionIdReturnsError() throws Exception {
        var cmd = new GoalCommand((sid, c) -> snapshot(sid, "g1", GoalStatus.ACTIVE, "t"));
        var ctx = new SlashCommand.SlashCommandContext(null, "/work");
        String result = cmd.execute("goal", ctx).get();
        assertTrue(result.startsWith("Error"));
    }

    @Test
    @DisplayName("execute 解析 pause 动作")
    void executeParsesPause() throws Exception {
        var captured = new AtomicReference<GoalCommandVO>();
        var cmd = new GoalCommand((sid, c) -> {
            captured.set(c);
            return snapshot(sid, "g1", GoalStatus.PAUSED, "t");
        });
        var ctx = new SlashCommand.SlashCommandContext("session-1", "/work");
        cmd.execute("pause", ctx).get();
        assertEquals(GoalAction.PAUSE, captured.get().action());
    }

    @Test
    @DisplayName("execute 解析 complete 动作")
    void executeParsesComplete() throws Exception {
        var captured = new AtomicReference<GoalCommandVO>();
        var cmd = new GoalCommand((sid, c) -> {
            captured.set(c);
            return snapshot(sid, "g1", GoalStatus.COMPLETED, "t");
        });
        var ctx = new SlashCommand.SlashCommandContext("session-1", "/work");
        cmd.execute("complete", ctx).get();
        assertEquals(GoalAction.COMPLETE, captured.get().action());
    }

    @Test
    @DisplayName("execute 空参数默认 CREATE 动作")
    void executeEmptyArgsDefaultsCreate() throws Exception {
        var captured = new AtomicReference<GoalCommandVO>();
        var cmd = new GoalCommand((sid, c) -> {
            captured.set(c);
            return snapshot(sid, "g1", GoalStatus.ACTIVE, "t");
        });
        var ctx = new SlashCommand.SlashCommandContext("session-1", "/work");
        cmd.execute("", ctx).get();
        assertEquals(GoalAction.CREATE, captured.get().action());
    }

    @Test
    @DisplayName("execute 服务抛异常时返回 Error")
    void executeServiceExceptionReturnsError() throws Exception {
        var cmd = new GoalCommand((sid, c) -> {
            throw new IllegalStateException("boom");
        });
        var ctx = new SlashCommand.SlashCommandContext("session-1", "/work");
        String result = cmd.execute("goal", ctx).get();
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("boom"));
    }

    @Test
    @DisplayName("null service 抛异常")
    void nullServiceRejected() {
        assertThrows(NullPointerException.class, () -> new GoalCommand(null));
    }
}
