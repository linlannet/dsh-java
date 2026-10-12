package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionStatistics 统计投影")
class SessionStatisticsTest {

    @Test
    @DisplayName("type 返回 STATISTICS")
    void typeIsStatistics() {
        var stats = new SessionStatistics();
        assertEquals(ProjectionType.STATISTICS, stats.type());
    }

    @Test
    @DisplayName("initial 返回全零统计")
    void initialIsZero() {
        var stats = new SessionStatistics();
        var init = stats.initial();
        assertEquals(0, init.messageCount());
        assertEquals(0, init.toolCallCount());
        assertEquals(0, init.inputTokens());
        assertEquals(0, init.outputTokens());
    }

    @Test
    @DisplayName("UserMessage 事件增加 messageCount")
    void userMessageIncrementsMessageCount() {
        var stats = new SessionStatistics();
        var current = stats.initial();
        var event = SessionEventFactory.userMessage("hello");
        var next = stats.applyIncremental(current, event);
        assertEquals(1, next.messageCount());
        assertEquals(0, next.toolCallCount());
    }

    @Test
    @DisplayName("ToolCall 事件增加 toolCallCount")
    void toolCallIncrementsToolCallCount() {
        var stats = new SessionStatistics();
        var current = stats.initial();
        var event = SessionEventFactory.toolCall(1, 1, "call-1", "Read", "{}");
        var next = stats.applyIncremental(current, event);
        assertEquals(0, next.messageCount());
        assertEquals(1, next.toolCallCount());
    }

    @Test
    @DisplayName("多个事件累积统计正确")
    void multipleEventsAccumulate() {
        var stats = new SessionStatistics();
        var current = stats.initial();
        current = stats.applyIncremental(current, SessionEventFactory.userMessage("msg1"));
        current = stats.applyIncremental(current, SessionEventFactory.toolCall(1, 1, "c1", "Read", "{}"));
        current = stats.applyIncremental(current, SessionEventFactory.toolCall(1, 2, "c2", "Write", "{}"));
        assertEquals(1, current.messageCount());
        assertEquals(2, current.toolCallCount());
    }

    @Test
    @DisplayName("Stats.add 合并两个统计")
    void statsAddMerges() {
        var a = new SessionStatistics.Stats(3, 2, 100, 50);
        var b = new SessionStatistics.Stats(1, 4, 200, 30);
        var sum = a.add(b);
        assertEquals(4, sum.messageCount());
        assertEquals(6, sum.toolCallCount());
        assertEquals(300, sum.inputTokens());
        assertEquals(80, sum.outputTokens());
    }
}
