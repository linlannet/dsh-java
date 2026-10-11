package net.linlan.dsh.domain.llm.waterfall;

import net.linlan.dsh.domain.core.waterfall.WaterfallExpert;
import net.linlan.dsh.domain.core.waterfall.service.WaterfallChain;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;

import java.util.List;
import java.util.concurrent.Flow;

/**
 * LlmStreamWaterfall 用 {@link WaterfallChain} 包装 {@link ILlmRuntimePort#stream}。
 * <p>拦截器按 priority 升序依次获得介入机会，可修改请求参数、注入前置 chunk、
 * 包装流式供应者（重试/重放/重定向）。
 * <p>最终返回的 {@link Flow.Publisher} 先发射 prefixChunks，再转发底层流。
 */
public final class LlmStreamWaterfall {

    private final ILlmRuntimePort llmRuntimePort;
    private final WaterfallChain<LlmStreamHandoff> chain;

    public LlmStreamWaterfall(ILlmRuntimePort llmRuntimePort) {
        this.llmRuntimePort = llmRuntimePort;
        this.chain = new WaterfallChain<>();
    }

    public LlmStreamWaterfall(ILlmRuntimePort llmRuntimePort, List<WaterfallExpert<LlmStreamHandoff>> experts) {
        this.llmRuntimePort = llmRuntimePort;
        this.chain = new WaterfallChain<>(experts);
    }

    public void registerInterceptor(WaterfallExpert<LlmStreamHandoff> expert) {
        chain.registerExpert(expert);
    }

    public int interceptorCount() {
        return chain.expertCount();
    }

    /**
     * 执行 waterfall 链，返回最终流式 Publisher。
     *
     * @param options 原始请求参数
     * @return 经拦截器链处理后的流式 Publisher
     */
    public Flow.Publisher<StreamChunk> execute(GenerateOptions options) {
        var initial = LlmStreamHandoff.initial(
                options,
                () -> llmRuntimePort.stream(options)
        );
        var event = WaterfallEvent.<LlmStreamHandoff>of("llm/stream", initial);
        var result = chain.execute(event, initial);

        var underlying = result.streamSupplier().get();
        var prefix = result.prefixChunks();

        if (prefix.isEmpty()) {
            return underlying;
        }
        return new ConcatPublisher(prefix, underlying);
    }

    // ── ConcatPublisher ────────────────────────────────────────

    private static final class ConcatPublisher implements Flow.Publisher<StreamChunk> {
        private final List<StreamChunk> prefix;
        private final Flow.Publisher<StreamChunk> underlying;

        ConcatPublisher(List<StreamChunk> prefix, Flow.Publisher<StreamChunk> underlying) {
            this.prefix = prefix;
            this.underlying = underlying;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super StreamChunk> subscriber) {
            subscriber.onSubscribe(new ConcatSubscription(subscriber));
        }

        private final class ConcatSubscription implements Flow.Subscription {
            private final Flow.Subscriber<? super StreamChunk> subscriber;
            private int prefixIndex = 0;
            private long demand = 0;
            private Flow.Subscription upstream;
            private boolean upstreamSubscribed = false;
            private boolean cancelled = false;

            ConcatSubscription(Flow.Subscriber<? super StreamChunk> subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public void request(long n) {
                if (n <= 0 || cancelled) return;
                demand += n;
                drain();
            }

            @Override
            public void cancel() {
                cancelled = true;
                if (upstream != null) upstream.cancel();
            }

            private void drain() {
                while (prefixIndex < prefix.size() && demand > 0 && !cancelled) {
                    demand--;
                    subscriber.onNext(prefix.get(prefixIndex++));
                }
                if (prefixIndex >= prefix.size() && !upstreamSubscribed && !cancelled) {
                    upstreamSubscribed = true;
                    underlying.subscribe(new Flow.Subscriber<>() {
                        @Override
                        public void onSubscribe(Flow.Subscription s) {
                            upstream = s;
                            if (cancelled) { s.cancel(); return; }
                            if (demand > 0) s.request(demand);
                        }

                        @Override
                        public void onNext(StreamChunk item) {
                            if (!cancelled) subscriber.onNext(item);
                        }

                        @Override
                        public void onError(Throwable t) {
                            if (!cancelled) subscriber.onError(t);
                        }

                        @Override
                        public void onComplete() {
                            if (!cancelled) subscriber.onComplete();
                        }
                    });
                }
            }
        }
    }
}
