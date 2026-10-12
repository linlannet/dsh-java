package net.linlan.dsh.domain.agent.service.compaction;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SlashCommandRegistry 是 slash 命令的注册与路由中心。
 */
public final class SlashCommandRegistry {

    private final ConcurrentHashMap<String, SlashCommand> commands = new ConcurrentHashMap<>();

    /**
     * 注册 slash 命令。
     *
     * @param command 命令实例
     */
    public void register(SlashCommand command) {
        if (command == null || command.name() == null) {
            throw new IllegalArgumentException("command and name must not be null");
        }
        commands.put(command.name(), command);
    }

    /**
     * 注销命令。
     *
     * @param name 命令名称
     */
    public void unregister(String name) {
        if (name != null) {
            commands.remove(name);
        }
    }

    /**
     * 按名称解析命令。
     *
     * @param name 命令名称
     * @return 命令实例（未注册时返回 Optional.empty()）
     */
    public Optional<SlashCommand> resolve(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(commands.get(name));
    }

    /**
     * 返回已注册命令数量。
     */
    public int size() {
        return commands.size();
    }
}
