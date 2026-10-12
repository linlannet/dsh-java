package net.linlan.dsh.domain.sdk.command.adapter.port;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;

import java.util.List;
import java.util.Optional;

/**
 * ISlashCommandPort 是 slash 命令注册表的持久化端口。
 * <p>用于持久化已注册的 slash 命令元数据，支持跨重启恢复命令注册。</p>
 */
public interface ISlashCommandPort {

    void save(SlashCommand command);

    Optional<SlashCommand> findByName(String name);

    List<SlashCommand> findAll();

    void disable(String name);

    void enable(String name);

    boolean isDisabled(String name);

    void delete(String name);
}
