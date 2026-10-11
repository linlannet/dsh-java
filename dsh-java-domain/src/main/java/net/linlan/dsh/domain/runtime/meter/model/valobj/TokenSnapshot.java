package net.linlan.dsh.domain.runtime.meter.model.valobj;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;

/**
 * TokenSnapshot 是 Token 计量的持久化快照。
 *
 * @param lastEventStepIndex 快照覆盖到的最后事件步索引
 * @param totalTokens        累计 Token 用量
 * @param modelCalls         累计模型调用次数
 */
public record TokenSnapshot(
        long lastEventStepIndex,
        TokenUsage totalTokens,
        long modelCalls
) {

    public TokenSnapshot {
        totalTokens = totalTokens == null ? new TokenUsage(0, 0) : totalTokens;
    }

    public static TokenSnapshot initial() {
        return new TokenSnapshot(-1, new TokenUsage(0, 0), 0);
    }

    public TokenSnapshot advanceTo(long newStepIndex, TokenUsage deltaTokens, long deltaModelCalls) {
        return new TokenSnapshot(
                newStepIndex,
                totalTokens.plus(deltaTokens),
                modelCalls + deltaModelCalls
        );
    }
}
