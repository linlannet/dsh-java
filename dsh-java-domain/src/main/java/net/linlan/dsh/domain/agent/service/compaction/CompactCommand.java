package net.linlan.dsh.domain.agent.service.compaction;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * CompactCommand 是 /compact slash 命令的实现。
 * <p>立即触发上下文压缩，返回压缩后摘要。
 */
public final class CompactCommand implements SlashCommand {

    private static final String NAME = "/compact";
    private static final String DESCRIPTION = "立即触发上下文压缩并返回压缩后摘要";

    private final CompactionEngine compactionEngine;
    private final Function<String, SessionLog> sessionLoader;
    private final Function<String, AgentOptions> optionsProvider;

    public CompactCommand(
            CompactionEngine compactionEngine,
            Function<String, SessionLog> sessionLoader,
            Function<String, AgentOptions> optionsProvider
    ) {
        if (compactionEngine == null) {
            throw new IllegalArgumentException("compactionEngine must not be null");
        }
        this.compactionEngine = compactionEngine;
        this.sessionLoader = sessionLoader;
        this.optionsProvider = optionsProvider;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public CompletableFuture<String> execute(String args, SlashCommandContext context) {
        if (context == null || context.sessionId() == null) {
            return CompletableFuture.completedFuture("Error: no active session");
        }
        SessionLog session = sessionLoader != null ? sessionLoader.apply(context.sessionId()) : null;
        if (session == null) {
            return CompletableFuture.completedFuture("Error: session not found: " + context.sessionId());
        }
        AgentOptions options = optionsProvider != null ? optionsProvider.apply(context.sessionId()) : null;
        if (options == null) {
            return CompletableFuture.completedFuture("Error: no agent options for session: " + context.sessionId());
        }
        return compactionEngine.compactNow(session, options)
                .thenApply(result -> result != null ? result.summary() : "Compaction completed (no summary)");
    }
}
