package net.linlan.dsh.domain.agent.service.compaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompactCommand /compact 命令")
class CompactCommandTest {

    @Test
    @DisplayName("name 返回 /compact")
    void nameIsCompact() {
        var cmd = new CompactCommand(new StubEngine(), sid -> null, sid -> null);
        assertEquals("/compact", cmd.name());
    }

    @Test
    @DisplayName("description 非空")
    void descriptionNotEmpty() {
        var cmd = new CompactCommand(new StubEngine(), sid -> null, sid -> null);
        assertTrue(cmd.description().length() > 0);
    }

    @Test
    @DisplayName("null context 返回错误信息")
    void nullContextReturnsError() {
        var cmd = new CompactCommand(new StubEngine(), sid -> null, sid -> null);
        String result = cmd.execute("", null).join();
        assertTrue(result.contains("Error"));
    }

    @Test
    @DisplayName("null sessionId 返回错误信息")
    void nullSessionIdReturnsError() {
        var cmd = new CompactCommand(new StubEngine(), sid -> null, sid -> null);
        var ctx = new SlashCommand.SlashCommandContext(null, "/tmp");
        String result = cmd.execute("", ctx).join();
        assertTrue(result.contains("Error"));
    }

    @Test
    @DisplayName("session 不存在返回错误信息")
    void sessionNotFoundReturnsError() {
        var cmd = new CompactCommand(new StubEngine(), sid -> null, sid -> null);
        var ctx = new SlashCommand.SlashCommandContext("sess-1", "/tmp");
        String result = cmd.execute("", ctx).join();
        assertTrue(result.contains("Error"));
    }

    @Test
    @DisplayName("null compactionEngine 抛异常")
    void nullEngineRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CompactCommand(null, sid -> null, sid -> null));
    }

    static final class StubEngine implements CompactionEngine {
        @Override
        public CompletableFuture<CompactionResult> compactIfNeeded(net.linlan.dsh.domain.session.event.model.entity.SessionLog session, net.linlan.dsh.domain.agent.model.entity.AgentOptions options, CompactionTrigger trigger) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<CompactionResult> compactNow(net.linlan.dsh.domain.session.event.model.entity.SessionLog session, net.linlan.dsh.domain.agent.model.entity.AgentOptions options) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
