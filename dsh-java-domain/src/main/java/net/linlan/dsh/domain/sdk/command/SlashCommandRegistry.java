package net.linlan.dsh.domain.sdk.command;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;
import net.linlan.dsh.domain.agent.service.compaction.SlashCommand.SlashCommandContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SlashCommandRegistry {

    private final ConcurrentHashMap<String, SlashCommand> commands = new ConcurrentHashMap<>();
    private final Set<String> disabled = ConcurrentHashMap.newKeySet();

    public void register(SlashCommand command) {
        if (command == null || command.name() == null) {
            throw new IllegalArgumentException("command and name must not be null");
        }
        commands.put(command.name(), command);
    }

    public Optional<SlashCommand> resolve(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(commands.get(name));
    }

    public List<SlashCommand> listCommands() {
        return new ArrayList<>(commands.values());
    }

    public List<SlashCommand> listEnabledCommands() {
        List<SlashCommand> result = new ArrayList<>();
        for (SlashCommand cmd : commands.values()) {
            if (!disabled.contains(cmd.name())) {
                result.add(cmd);
            }
        }
        return result;
    }

    public boolean disable(String name) {
        if (name == null || !commands.containsKey(name)) {
            return false;
        }
        disabled.add(name);
        return true;
    }

    public boolean enable(String name) {
        if (name == null) {
            return false;
        }
        return disabled.remove(name);
    }

    public boolean isDisabled(String name) {
        return name != null && disabled.contains(name);
    }

    public String help() {
        StringBuilder sb = new StringBuilder();
        for (SlashCommand cmd : commands.values()) {
            String flag = disabled.contains(cmd.name()) ? " (disabled)" : "";
            sb.append(cmd.name()).append(" - ").append(cmd.description()).append(flag).append('\n');
        }
        return sb.toString();
    }

    public int size() {
        return commands.size();
    }

    public CompletableFuture<String> execute(String input, SlashCommandContext ctx) {
        if (input == null || input.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        String trimmed = input.trim();
        String name;
        String args;
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx < 0) {
            name = trimmed;
            args = "";
        } else {
            name = trimmed.substring(0, spaceIdx);
            args = trimmed.substring(spaceIdx + 1);
        }
        SlashCommand cmd = commands.get(name);
        if (cmd == null) {
            return CompletableFuture.completedFuture("Unknown command: " + name);
        }
        if (disabled.contains(name)) {
            return CompletableFuture.completedFuture("Command disabled: " + name);
        }
        return cmd.execute(args, ctx);
    }
}
