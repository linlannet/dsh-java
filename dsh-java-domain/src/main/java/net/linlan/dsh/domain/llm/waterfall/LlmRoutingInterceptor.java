package net.linlan.dsh.domain.llm.waterfall;

import net.linlan.dsh.domain.core.waterfall.WaterfallExpert;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * LlmRoutingInterceptor 是 LLM 流式请求重定向拦截器。
 * <p>根据 {@code router} 函数判断是否需要重定向到另一个 Provider：
 * 若返回的 options 与当前不同，则更新 streamSupplier 指向新 Provider，
 * 保留原始请求参数与 trace 上下文，并终止链（canTerminate=true）。
 */
public final class LlmRoutingInterceptor implements WaterfallExpert<LlmStreamHandoff> {

    private final ILlmRuntimePort llmRuntimePort;
    private final Function<GenerateOptions, GenerateOptions> router;

    public LlmRoutingInterceptor(ILlmRuntimePort llmRuntimePort,
                                 Function<GenerateOptions, GenerateOptions> router) {
        this.llmRuntimePort = Objects.requireNonNull(llmRuntimePort, "llmRuntimePort");
        this.router = Objects.requireNonNull(router, "router");
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public boolean canTerminate() {
        return true;
    }

    @Override
    public WaterfallOutcome<LlmStreamHandoff> handle(WaterfallEvent<LlmStreamHandoff> event, LlmStreamHandoff current) {
        var routedOptions = router.apply(current.options());
        if (routedOptions == null || routedOptions.equals(current.options())) {
            return WaterfallOutcome.continueWith(current);
        }
        Supplier<Flow.Publisher<StreamChunk>> newSupplier =
                () -> llmRuntimePort.stream(routedOptions);
        var newHandoff = current
                .preserveOriginalOptions(current.originalOptions())
                .withOptions(routedOptions)
                .withStreamSupplier(newSupplier);
        return WaterfallOutcome.terminateWith(newHandoff);
    }
}
