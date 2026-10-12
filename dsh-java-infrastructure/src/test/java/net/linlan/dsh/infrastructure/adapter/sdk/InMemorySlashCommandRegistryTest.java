package net.linlan.dsh.infrastructure.adapter.sdk;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;
import net.linlan.dsh.domain.sdk.command.adapter.port.ISlashCommandPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class InMemorySlashCommandRegistryTest {

    private InMemorySlashCommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemorySlashCommandRegistry();
    }

    @Test
    void implementsSlashCommandPort() {
        assertTrue(registry instanceof ISlashCommandPort);
    }

    @Test
    void saveAndFindByName() {
        SlashCommand cmd = new TestSlashCommand("/test", "desc");
        registry.save(cmd);
        Optional<SlashCommand> found = registry.findByName("/test");
        assertTrue(found.isPresent());
        assertEquals("/test", found.get().name());
        assertEquals("desc", found.get().description());
    }

    @Test
    void findByNameReturnsEmptyForMissing() {
        assertTrue(registry.findByName("/missing").isEmpty());
    }

    @Test
    void findByNameNullReturnsEmpty() {
        assertTrue(registry.findByName(null).isEmpty());
    }

    @Test
    void saveNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> registry.save(null));
    }

    @Test
    void saveWithNullNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> registry.save(new TestSlashCommand(null, "d")));
    }

    @Test
    void findAllReturnsAllSaved() {
        registry.save(new TestSlashCommand("/a", "a desc"));
        registry.save(new TestSlashCommand("/b", "b desc"));
        assertEquals(2, registry.findAll().size());
    }

    @Test
    void findAllReturnsEmptyInitially() {
        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void saveOverwritesSameName() {
        registry.save(new TestSlashCommand("/x", "old"));
        registry.save(new TestSlashCommand("/x", "new"));
        assertEquals(1, registry.findAll().size());
        assertEquals("new", registry.findByName("/x").get().description());
    }

    @Test
    void disableAndIsDisabled() {
        registry.save(new TestSlashCommand("/d", "d"));
        assertFalse(registry.isDisabled("/d"));
        registry.disable("/d");
        assertTrue(registry.isDisabled("/d"));
    }

    @Test
    void enableRemovesDisabled() {
        registry.save(new TestSlashCommand("/e", "e"));
        registry.disable("/e");
        assertTrue(registry.isDisabled("/e"));
        registry.enable("/e");
        assertFalse(registry.isDisabled("/e"));
    }

    @Test
    void isDisabledNullReturnsFalse() {
        assertFalse(registry.isDisabled(null));
    }

    @Test
    void isDisabledUnknownReturnsFalse() {
        assertFalse(registry.isDisabled("/unknown"));
    }

    @Test
    void disableNullIsNoop() {
        registry.disable(null);
        assertFalse(registry.isDisabled(null));
    }

    @Test
    void enableNullIsNoop() {
        registry.enable(null);
        assertFalse(registry.isDisabled(null));
    }

    @Test
    void enableUnknownIsNoop() {
        registry.enable("/never-disabled");
        assertFalse(registry.isDisabled("/never-disabled"));
    }

    @Test
    void deleteRemovesCommand() {
        registry.save(new TestSlashCommand("/del", "del"));
        registry.delete("/del");
        assertTrue(registry.findByName("/del").isEmpty());
    }

    @Test
    void deleteAlsoClearsDisabled() {
        registry.save(new TestSlashCommand("/del", "del"));
        registry.disable("/del");
        registry.delete("/del");
        assertFalse(registry.isDisabled("/del"));
    }

    @Test
    void deleteNullIsNoop() {
        registry.delete(null);
        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void deleteUnknownIsNoop() {
        registry.delete("/nope");
        assertTrue(registry.findAll().isEmpty());
    }

    @Test
    void disableDoesNotAffectFindByName() {
        registry.save(new TestSlashCommand("/cmd", "cmd"));
        registry.disable("/cmd");
        assertTrue(registry.findByName("/cmd").isPresent());
        assertEquals("/cmd", registry.findByName("/cmd").get().name());
    }

    static class TestSlashCommand implements SlashCommand {
        private final String name;
        private final String description;

        TestSlashCommand(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String name() { return name; }

        @Override
        public String description() { return description; }

        @Override
        public CompletableFuture<String> execute(String args, SlashCommandContext context) {
            return CompletableFuture.completedFuture("ok");
        }
    }
}
