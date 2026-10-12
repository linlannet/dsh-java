package net.linlan.dsh.domain.sdk.command;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;
import net.linlan.dsh.domain.agent.service.compaction.SlashCommand.SlashCommandContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.032 SlashCommandRegistry 统一命令注册表")
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
    @DisplayName("listCommands 返回所有已注册命令")
    void listCommandsReturnsAll() {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/a", "a"));
        registry.register(new StubCommand("/b", "b"));
        assertEquals(2, registry.listCommands().size());
    }

    @Test
    @DisplayName("disable 后命令被禁用")
    void disableCommand() {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/test", "test"));
        assertTrue(registry.disable("/test"));
        assertTrue(registry.isDisabled("/test"));
    }

    @Test
    @DisplayName("disable 未知命令返回 false")
    void disableUnknownReturnsFalse() {
        var registry = new SlashCommandRegistry();
        assertFalse(registry.disable("/unknown"));
    }

    @Test
    @DisplayName("enable 恢复禁用命令")
    void enableRestoresCommand() {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/test", "test"));
        registry.disable("/test");
        assertTrue(registry.enable("/test"));
        assertFalse(registry.isDisabled("/test"));
    }

    @Test
    @DisplayName("listEnabledCommands 排除禁用命令")
    void listEnabledExcludesDisabled() {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/a", "a"));
        registry.register(new StubCommand("/b", "b"));
        registry.disable("/a");
        assertEquals(1, registry.listEnabledCommands().size());
        assertEquals("/b", registry.listEnabledCommands().get(0).name());
    }

    @Test
    @DisplayName("execute 正常分发命令")
    void executeDispatches() throws Exception {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/echo", "echo"));
        String result = registry.execute("/echo hello", new SlashCommandContext("s1", "/tmp")).get();
        assertEquals("ok:hello", result);
    }

    @Test
    @DisplayName("execute 禁用命令返回提示")
    void executeDisabledReturnsMessage() throws Exception {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/test", "test"));
        registry.disable("/test");
        String result = registry.execute("/test", new SlashCommandContext("s1", "/tmp")).get();
        assertEquals("Command disabled: /test", result);
    }

    @Test
    @DisplayName("execute 未知命令返回提示")
    void executeUnknownReturnsMessage() throws Exception {
        var registry = new SlashCommandRegistry();
        String result = registry.execute("/nope", new SlashCommandContext("s1", "/tmp")).get();
        assertEquals("Unknown command: /nope", result);
    }

    @Test
    @DisplayName("help 包含命令描述")
    void helpContainsDescriptions() {
        var registry = new SlashCommandRegistry();
        registry.register(new StubCommand("/a", "desc a"));
        String help = registry.help();
        assertTrue(help.contains("/a"));
        assertTrue(help.contains("desc a"));
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
        @Override public CompletableFuture<String> execute(String args, SlashCommandContext context) {
            return CompletableFuture.completedFuture("ok:" + args);
        }
    }
}
