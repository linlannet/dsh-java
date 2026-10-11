package net.linlan.dsh.domain.llm.waterfall;

import net.linlan.dsh.domain.core.waterfall.WaterfallExpert;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;

import java.util.List;
import java.util.function.Function;

/**
 * LlmReplayInterceptor 是 LLM 流式请求重放拦截器。
 * <p>根据当前请求参数从 {@code replaySource} 获取历史 chunk，注入为前置 chunk。
 * <p>不可终止链（canTerminate=false），后续拦截器仍可获得介入机会。
 */
public final class LlmReplayInterceptor implements WaterfallExpert<LlmStreamHandoff> {

    private final Function<GenerateOptions, List<StreamChunk>> replaySource;

    public LlmReplayInterceptor(Function<GenerateOptions, List<StreamChunk>> replaySource) {
        this.replaySource = replaySource;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean canTerminate() {
        return false;
    }

    @Override
    public WaterfallOutcome<LlmStreamHandoff> handle(WaterfallEvent<LlmStreamHandoff> event, LlmStreamHandoff current) {
        var chunks = replaySource.apply(current.options());
        if (chunks == null || chunks.isEmpty()) {
            return WaterfallOutcome.continueWith(current);
        }
        return WaterfallOutcome.continueWith(current.withAdditionalPrefixChunks(chunks));
    }
}
