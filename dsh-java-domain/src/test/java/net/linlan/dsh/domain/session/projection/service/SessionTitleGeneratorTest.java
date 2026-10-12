package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionTitleGenerator 会话标题生成")
class SessionTitleGeneratorTest {

    private SessionLog newSession() {
        String sid = "sess-" + System.nanoTime();
        var header = SessionHeader.create(sid, Instant.now(), "/tmp", "default");
        return new SessionLog(sid, header);
    }

    @Test
    @DisplayName("空会话生成默认标题")
    void emptySessionDefaultTitle() {
        var session = newSession();
        var gen = new SessionTitleGenerator();
        String title = gen.generate(session);
        assertTrue(title.startsWith("New Session "));
    }

    @Test
    @DisplayName("首轮用户消息后生成标题取自消息内容")
    void titleFromFirstUserMessage() {
        var session = newSession();
        session.append(SessionEventFactory.userMessage("帮我重构这段代码"));
        var gen = new SessionTitleGenerator();
        String title = gen.generate(session);
        assertEquals("帮我重构这段代码", title);
    }

    @Test
    @DisplayName("长标题截断至 60 字符")
    void longTitleTruncated() {
        var session = newSession();
        String longText = "a".repeat(100);
        session.append(SessionEventFactory.userMessage(longText));
        var gen = new SessionTitleGenerator();
        String title = gen.generate(session);
        assertEquals(63, title.length()); // 60 + "..."
        assertTrue(title.endsWith("..."));
    }

    @Test
    @DisplayName("update 无用户消息时保留旧标题")
    void updateKeepsOldTitleWhenNoUserMessage() {
        var session = newSession();
        var gen = new SessionTitleGenerator();
        String updated = gen.update("旧标题", session);
        assertEquals("旧标题", updated);
    }

    @Test
    @DisplayName("update 有用户消息时生成新标题")
    void updateGeneratesNewTitle() {
        var session = newSession();
        session.append(SessionEventFactory.userMessage("新需求描述"));
        var gen = new SessionTitleGenerator();
        String updated = gen.update("旧标题", session);
        assertEquals("新需求描述", updated);
    }
}
