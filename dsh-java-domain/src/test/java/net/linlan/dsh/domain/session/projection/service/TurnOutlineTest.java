package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TurnOutline 轮次大纲投影")
class TurnOutlineTest {

    @Test
    @DisplayName("type 返回 TURN_OUTLINE")
    void typeIsTurnOutline() {
        var outline = new TurnOutline();
        assertEquals(ProjectionType.TURN_OUTLINE, outline.type());
    }

    @Test
    @DisplayName("initial 返回空列表")
    void initialIsEmpty() {
        var outline = new TurnOutline();
        assertTrue(outline.initial().isEmpty());
    }

    @Test
    @DisplayName("TurnStart 事件新增一轮")
    void turnStartAddsTurn() {
        var outline = new TurnOutline();
        var current = outline.initial();
        var next = outline.applyIncremental(current, SessionEventFactory.turnStart());
        assertEquals(1, next.size());
        assertEquals(1, next.get(0).turn());
    }

    @Test
    @DisplayName("TurnEnd 事件标记当前轮完成")
    void turnEndMarksCompleted() {
        var outline = new TurnOutline();
        var current = outline.initial();
        current = outline.applyIncremental(current, SessionEventFactory.turnStart());
        current = outline.applyIncremental(current, SessionEventFactory.turnEnd(SessionEventFactory.completed()));
        assertEquals(1, current.size());
        assertEquals("completed", current.get(0).result());
    }

    @Test
    @DisplayName("多轮交替正确")
    void multipleTurnsAlternate() {
        var outline = new TurnOutline();
        var current = outline.initial();
        current = outline.applyIncremental(current, SessionEventFactory.turnStart());
        current = outline.applyIncremental(current, SessionEventFactory.turnEnd(SessionEventFactory.completed()));
        current = outline.applyIncremental(current, SessionEventFactory.turnStart());
        current = outline.applyIncremental(current, SessionEventFactory.turnEnd(SessionEventFactory.completed()));
        assertEquals(2, current.size());
        assertEquals(1, current.get(0).turn());
        assertEquals(2, current.get(1).turn());
        assertEquals("completed", current.get(1).result());
    }

    @Test
    @DisplayName("非 Turn 事件不改变大纲")
    void nonTurnEventNoChange() {
        var outline = new TurnOutline();
        var current = outline.initial();
        var next = outline.applyIncremental(current, SessionEventFactory.userMessage("hello"));
        assertSame(current, next);
    }
}
