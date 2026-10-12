package net.linlan.dsh.domain.agent.service.compaction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * SlashCommand 表示 slash 命令的领域契约。
 */
public interface SlashCommand {

    /**
     * 命令名称（如 /compact）。
     */
    String name();

    /**
     * 命令描述。
     */
    String description();

    /**
     * 执行命令。
     *
     * @param args    命令参数
     * @param context 执行上下文
     * @return 执行结果
     */
    CompletableFuture<String> execute(String args, SlashCommandContext context);

    /**
     * SlashCommandContext 表示命令执行上下文。
     *
     * @param sessionId 会话 ID
     * @param cwd       当前工作目录
     */
    record SlashCommandContext(String sessionId, String cwd) {}
}
