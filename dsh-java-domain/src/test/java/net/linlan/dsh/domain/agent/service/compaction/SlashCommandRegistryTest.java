package net.linlan.dsh.domain.agent.service.compaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SlashCommandRegistry slash 命令注册")
class SlashCommandRegistryTest {

    @Test
    @DisplayName("注册命令后 resolve 可解析")
    void registerAndResolve() {
        var registry = new SlashCommandRegistry();
        var cmd = new StubCommand("/test", "test command");
        registry.register(cmd);
        Optional<SlashCommand> resolved = registry.resolve("/test");
        assertTrue(resolved.isPresent());
        assertSame(cmd, resolved.get());
    }

    @Test
    @DisplayName("未注册命令 resolve 返回 empty")
    void unregisteredReturnsEmpty() {
        var registry = new SlashCommandRegistry();
        assertTrue(registry.resolve("/unknown").isEmpty());
    }

    @Test
    @DisplayName("注销命令后 resolve 返回 empty")
    void unregisterRemovesCommand() {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/test", "test"));
        registry.unregister("/test");
        assertTrue(registry.resolve("/test").isEmpty());
    }

    @Test
    @DisplayName("size 返回已注册命令数")
    void sizeCorrect() {
        var registry = new SlashCommandRegistry();
        assertEquals(0, registry.size());
        registry.register(new StubCommand("/a", "a"));
        registry.register(new StubCommand("/b", "b"));
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("null 命令抛异常")
    void nullCommandRejected() {
        var registry = new SlashCommandRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    static final class StubCommand implements SlashCommand {
        private final String name;
        private final String desc;

        StubCommand(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        @Override public String name() { return name; }
        @Override public String description() { return desc; }
        @Override public java.util.concurrent.CompletableFuture<String> execute(String args, SlashCommandContext context) {
            return java.util.concurrent.CompletableFuture.completedFuture("ok");
        }
    }
}
