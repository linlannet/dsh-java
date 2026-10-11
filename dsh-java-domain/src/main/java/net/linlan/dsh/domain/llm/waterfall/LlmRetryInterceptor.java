package net.linlan.dsh.domain.llm.waterfall;

import net.linlan.dsh.domain.core.waterfall.WaterfallExpert;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;

import java.util.concurrent.Flow;
import java.util.function.Supplier;

/**
 * LlmRetryInterceptor 是 LLM 流式请求重试拦截器。
 * <p>包装流式供应者为重试逻辑：流式错误时自动重发请求，最多重试 {@code maxRetries} 次。
 * <p>不可终止链（canTerminate=false），后续拦截器仍可获得介入机会。
 */
public final class LlmRetryInterceptor implements WaterfallExpert<LlmStreamHandoff> {

    private final int maxRetries;

    public LlmRetryInterceptor(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean canTerminate() {
        return false;
    }

    @Override
    public WaterfallOutcome<LlmStreamHandoff> handle(WaterfallEvent<LlmStreamHandoff> event, LlmStreamHandoff current) {
        var originalSupplier = current.streamSupplier();
        Supplier<Flow.Publisher<StreamChunk>> retrySupplier =
                () -> new RetryPublisher(originalSupplier, maxRetries);
        return WaterfallOutcome.continueWith(current.withStreamSupplier(retrySupplier));
    }

    // ── RetryPublisher ─────────────────────────────────────────

    static final class RetryPublisher implements Flow.Publisher<StreamChunk> {
        private final Supplier<Flow.Publisher<StreamChunk>> supplier;
        private final int maxRetries;

        RetryPublisher(Supplier<Flow.Publisher<StreamChunk>> supplier, int maxRetries) {
            this.supplier = supplier;
            this.maxRetries = maxRetries;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super StreamChunk> subscriber) {
            subscriber.onSubscribe(new RetrySubscription(subscriber));
        }

        private final class RetrySubscription implements Flow.Subscription {
            private final Flow.Subscriber<? super StreamChunk> subscriber;
            private int attempts = 0;
            private Flow.Subscription current;
            private long demand = 0;
            private boolean cancelled = false;

            RetrySubscription(Flow.Subscriber<? super StreamChunk> subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public void request(long n) {
                if (n <= 0 || cancelled) return;
                demand += n;
                if (current == null) {
                    attempt();
                } else {
                    current.request(n);
                }
            }

            @Override
            public void cancel() {
                cancelled = true;
                if (current != null) current.cancel();
            }

            private void attempt() {
                attempts++;
                supplier.get().subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription s) {
                        current = s;
                        if (cancelled) { s.cancel(); return; }
                        if (demand > 0) s.request(demand);
                    }

                    @Override
                    public void onNext(StreamChunk item) {
                        if (!cancelled) subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable t) {
                        if (cancelled) return;
                        if (attempts <= maxRetries) {
                            current = null;
                            attempt();
                        } else {
                            subscriber.onError(t);
                        }
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
