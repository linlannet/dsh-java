package net.linlan.dsh.domain.tool.schedule;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.schedule.IScheduleService;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleCreateRequest;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleEditRequest;
import net.linlan.dsh.domain.schedule.model.valobj.SchedulePage;
import net.linlan.dsh.domain.schedule.model.valobj.SchedulePatch;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleTarget;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;
import net.linlan.dsh.domain.schedule.service.ScheduleService;
import net.linlan.dsh.domain.schedule.service.ScheduleServiceTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScheduleTools 工具组")
class ScheduleToolsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String RRULE = "DTSTART;TZID=UTC:20260101T000000\nRRULE:FREQ=DAILY";

    private static IScheduleService svc() {
        return new ScheduleService(new ScheduleServiceTest.FakeRepo(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String text(ToolExecutionResult r) {
        StringBuilder sb = new StringBuilder();
        r.content().forEach(b -> {
            if (b instanceof TextBlock t) {
                sb.append(t.text());
            }
        });
        return sb.toString();
    }

    private static ToolExecutionResult runResult(ToolDefinition tool, Map<String, Object> args) {
        return tool.execute(args, null).join();
    }

    private static String run(ToolDefinition tool, Map<String, Object> args) {
        return text(runResult(tool, args));
    }

    private static String codeOf(ToolExecutionResult r) {
        if (r instanceof ToolExecutionResult.Failure f) {
            return f.error().info().code();
        }
        return null;
    }

    @Test
    @DisplayName("四个工具名正确")
    void names() {
        assertEquals("schedule_create", new ScheduleTools.Create(svc(), "s1").name());
        assertEquals("schedule_list", new ScheduleTools.ListAll(svc(), "s1").name());
        assertEquals("schedule_view", new ScheduleTools.ViewOne(svc(), "s1").name());
        assertEquals("schedule_edit", new ScheduleTools.Edit(svc(), "s1").name());
    }

    @Test
    @DisplayName("create 缺必填参数返回 MISSING_PARAM")
    void createMissingParam() {
        ScheduleTools.Create tool = new ScheduleTools.Create(svc(), "s1");
        ToolExecutionResult r = runResult(tool, Map.of("title", "t", "prompt", "p", "rrule", RRULE));
        assertTrue(r.isError());
        assertEquals("MISSING_PARAM", codeOf(r));
        assertTrue(text(r).contains("required"), text(r));
    }

    @Test
    @DisplayName("create 成功并回显任务")
    void createSuccess() {
        ScheduleTools.Create tool = new ScheduleTools.Create(svc(), "sessionA");
        String out = run(tool, Map.of(
                "title", "每日晨报", "prompt", "生成晨报", "rrule", RRULE, "target", "this_session"));
        assertTrue(out.contains("Schedule created"), out);
        assertTrue(out.contains("DAILY"), out);
    }

    @Test
    @DisplayName("list 返回 items/shown/total/limit/offset")
    void listFormat() {
        IScheduleService service = svc();
        service.create(new ScheduleCreateRequest("任务1", "p", RRULE, ScheduleTarget.THIS_SESSION, "s1"));
        ScheduleTools.ListAll tool = new ScheduleTools.ListAll(service, "s1");
        String out = run(tool, Map.of("limit", 20, "offset", 0));
        assertTrue(out.contains("shown=1"), out);
        assertTrue(out.contains("total=1"), out);
        assertTrue(out.contains("limit=20"), out);
        assertTrue(out.contains("offset=0"), out);
    }

    @Test
    @DisplayName("view 不存在返回 NOT_FOUND")
    void viewNotFound() {
        ScheduleTools.ViewOne tool = new ScheduleTools.ViewOne(svc(), "s1");
        ToolExecutionResult r = runResult(tool, Map.of("id", "nope"));
        assertTrue(r.isError());
        assertEquals("NOT_FOUND", codeOf(r));
        assertTrue(text(r).contains("schedule not found"), text(r));
    }

    @Test
    @DisplayName("view 存在回显详情")
    void viewFound() {
        IScheduleService service = svc();
        var rec = service.create(new ScheduleCreateRequest("任务2", "p", RRULE, ScheduleTarget.NEW_ONCE, "s1"));
        ScheduleTools.ViewOne tool = new ScheduleTools.ViewOne(service, "s1");
        String out = run(tool, Map.of("id", rec.id()));
        assertTrue(out.contains("Schedule:"), out);
        assertTrue(out.contains(rec.id()), out);
    }

    @Test
    @DisplayName("edit delete 返回 Schedule deleted")
    void editDelete() {
        IScheduleService service = svc();
        var rec = service.create(new ScheduleCreateRequest("任务3", "p", RRULE, ScheduleTarget.THIS_SESSION, "s1"));
        ScheduleTools.Edit tool = new ScheduleTools.Edit(service, "s1");
        String out = run(tool, Map.of("id", rec.id(), "action", "delete"));
        assertTrue(out.contains("Schedule deleted"), out);
    }

    @Test
    @DisplayName("子 agent 调用任意调度工具返回 subagent_session")
    void subagentBlocked() {
        String childSession = "child-abc";
        ToolExecutionResult createOut = runResult(new ScheduleTools.Create(svc(), childSession), Map.of(
                "title", "t", "prompt", "p", "rrule", RRULE, "target", "this_session"));
        assertEquals("subagent_session", codeOf(createOut));
        assertTrue(text(createOut).contains("delegated subagent"), text(createOut));

        assertEquals("subagent_session", codeOf(runResult(new ScheduleTools.ListAll(svc(), childSession), Map.of())));
        assertEquals("subagent_session", codeOf(runResult(new ScheduleTools.ViewOne(svc(), childSession), Map.of("id", "x"))));
        assertEquals("subagent_session", codeOf(runResult(new ScheduleTools.Edit(svc(), childSession), Map.of("id", "x", "action", "delete"))));
    }
}
