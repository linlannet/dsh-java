package net.linlan.dsh.infrastructure.adapter.sdk;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;
import net.linlan.dsh.domain.sdk.command.adapter.port.ISlashCommandPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "dsh.slash-command.enabled", havingValue = "true", matchIfMissing = true)
public class InMemorySlashCommandRegistry implements ISlashCommandPort {

    private final ConcurrentHashMap<String, SlashCommand> commands = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> disabled = new ConcurrentHashMap<>();

    @Override
    public void save(SlashCommand command) {
        if (command == null || command.name() == null) {
            throw new IllegalArgumentException("command and name must not be null");
        }
        commands.put(command.name(), command);
    }

    @Override
    public Optional<SlashCommand> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(commands.get(name));
    }

    @Override
    public List<SlashCommand> findAll() {
        return new ArrayList<>(commands.values());
    }

    @Override
    public void disable(String name) {
        if (name != null) {
            disabled.put(name, true);
        }
    }

    @Override
    public void enable(String name) {
        if (name != null) {
            disabled.remove(name);
        }
    }

    @Override
    public boolean isDisabled(String name) {
        return name != null && disabled.getOrDefault(name, false);
    }

    @Override
    public void delete(String name) {
        if (name != null) {
            commands.remove(name);
            disabled.remove(name);
        }
    }
}
