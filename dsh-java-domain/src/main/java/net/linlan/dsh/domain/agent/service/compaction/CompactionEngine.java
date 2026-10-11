package net.linlan.dsh.domain.agent.service.compaction;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

import java.util.concurrent.CompletableFuture;

/**
 * CompactionEngine 表示会话上下文压缩的领域引擎契约。
 */
public interface CompactionEngine {

    /**
     * 根据当前会话状态判断是否执行上下文压缩。
     */
    CompletableFuture<CompactionResult> compactIfNeeded(
            SessionLog session,
            AgentOptions options,
            CompactionTrigger trigger
    );

    /**
     * 执行会话上下文压缩并返回结果。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param session 会话。
     * @return 领域处理结果。
     */
    CompletableFuture<CompactionResult> compactNow(
            SessionLog session,
            AgentOptions options
    );

    /**
     * CompactionTrigger 表示CompactionTrigger触发原因。
     */
    enum CompactionTrigger {
        PRESSURE,
        CONTEXT_OVERFLOW
    }
}
