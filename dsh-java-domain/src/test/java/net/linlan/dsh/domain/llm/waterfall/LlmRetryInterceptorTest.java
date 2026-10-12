package net.linlan.dsh.domain.llm.waterfall;

import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.retry.model.valobj.LlmRetryPolicy;
import net.linlan.dsh.domain.llm.retry.service.RetryableErrorClassifier;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmRetryInterceptor LLM 流式重试拦截器")
class LlmRetryInterceptorTest {

    @Test
    @DisplayName("priority 固定返回 10")
    void priorityIsTen() {
        var interceptor = new LlmRetryInterceptor(3);
        assertEquals(10, interceptor.priority());
    }

    @Test
    @DisplayName("canTerminate 返回 false（不可终止链）")
    void cannotTerminate() {
        var interceptor = new LlmRetryInterceptor(3);
        assertFalse(interceptor.canTerminate());
    }

    @Test
    @DisplayName("handle 包装后的流首次成功时直接透传")
    void handleWrapsAndPassthroughOnSuccess() {
        var chunks = List.<StreamChunk>of(
                new StreamChunk.TextDelta(0, "ok"),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var callCount = new AtomicInteger(0);
        Supplier<Flow.Publisher<StreamChunk>> supplier = () -> {
            callCount.incrementAndGet();
            return new ListPublisher<>(chunks);
        };

        var handoff = LlmStreamHandoff.initial(defaultOptions(), supplier);
        var interceptor = new LlmRetryInterceptor(3);
        var outcome = interceptor.handle(WaterfallEvent.of("request", handoff), handoff);

        assertInstanceOf(WaterfallOutcome.Continue.class, outcome);
        var wrapped = outcome.output();
        assertNotSame(supplier, wrapped.streamSupplier(),
                "handle 应替换为重试包装的 supplier");

        var collected = collectAll(wrapped.streamSupplier().get());
        assertEquals(2, collected.size());
        assertEquals("ok", ((StreamChunk.TextDelta) collected.get(0)).text());
        assertEquals(1, callCount.get(), "首次成功不应触发重试");
    }

    @Test
    @DisplayName("maxRetries=3：首次错误后重试成功，共调用 2 次")
    void retrySucceedsOnSecondAttempt() {
        var chunks = List.<StreamChunk>of(
                new StreamChunk.TextDelta(0, "recovered"),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var callCount = new AtomicInteger(0);
        Supplier<Flow.Publisher<StreamChunk>> supplier = () -> {
            if (callCount.incrementAndGet() == 1) {
                return new ErrorPublisher<>(new RuntimeException("first fails"));
            }
            return new ListPublisher<>(chunks);
        };

        var handoff = LlmStreamHandoff.initial(defaultOptions(), supplier);
        var interceptor = new LlmRetryInterceptor(3);
        var outcome = interceptor.handle(WaterfallEvent.of("request", handoff), handoff);

        var collected = collectAll(outcome.output().streamSupplier().get());
        assertEquals(2, collected.size());
        assertEquals("recovered", ((StreamChunk.TextDelta) collected.get(0)).text());
        assertEquals(2, callCount.get());
    }

    @Test
    @DisplayName("maxRetries=2：始终失败时共调用 3 次后传播错误")
    void retryExhaustedPropagatesError() {
        var callCount = new AtomicInteger(0);
        Supplier<Flow.Publisher<StreamChunk>> supplier = () -> {
            callCount.incrementAndGet();
            return new ErrorPublisher<>(new RuntimeException("always fails"));
        };

        var handoff = LlmStreamHandoff.initial(defaultOptions(), supplier);
        var interceptor = new LlmRetryInterceptor(2);
        var outcome = interceptor.handle(WaterfallEvent.of("request", handoff), handoff);

        var error = collectError(outcome.output().streamSupplier().get());
        assertNotNull(error);
        assertEquals("always fails", error.getMessage());
        assertEquals(3, callCount.get(), "1 次原始 + 2 次重试 = 3 次");
    }

    @Test
    @DisplayName("maxRetries=0：失败不重试，直接传播错误")
    void zeroRetriesNoRetry() {
        var callCount = new AtomicInteger(0);
        Supplier<Flow.Publisher<StreamChunk>> supplier = () -> {
            callCount.incrementAndGet();
            return new ErrorPublisher<>(new RuntimeException("no retry"));
        };

        var handoff = LlmStreamHandoff.initial(defaultOptions(), supplier);
        var interceptor = new LlmRetryInterceptor(0);
        var outcome = interceptor.handle(WaterfallEvent.of("request", handoff), handoff);

        var error = collectError(outcome.output().streamSupplier().get());
        assertNotNull(error);
        assertEquals(1, callCount.get(), "maxRetries=0 不应重试");
    }

    @Test
    @DisplayName("带 RetryPolicy+classifier：可重试错误码（RATE_LIMIT）触发重试")
    void policyRetryableCodeTriggersRetry() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), null);
        var classifier = new RetryableErrorClassifier();
        var chunks = List.<StreamChunk>of(
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var callCount = new AtomicInteger(0);
        Supplier<Flow.Publisher<StreamChunk>> supplier = () -> {
            if (callCount.incrementAndGet() == 1) {
                return new ErrorPublisher<>(new RuntimeException("rate limit 429"));
            }
            return new ListPublisher<>(chunks);
        };

        var handoff = LlmStreamHandoff.initial(defaultOptions(), supplier);
        var interceptor = new LlmRetryInterceptor(policy, classifier);
        var outcome = interceptor.handle(WaterfallEvent.of("request", handoff), handoff);

        var collected = collectAll(outcome.output().streamSupplier().get());
        assertFalse(collected.isEmpty());
        assertEquals(2, callCount.get(), "RATE_LIMIT 可重试应触发 1 次重试");
    }

    @Test
    @DisplayName("带 RetryPolicy+classifier：不可重试错误码（AUTH）不重试直接传播")
    void policyNonRetryableCodeSkipsRetry() {
        var policy = new LlmRetryPolicy(3, List.of(LlmErrorCodes.RATE_LIMIT), null);
        var classifier = new RetryableErrorClassifier();
        var callCount = new AtomicInteger(0);
        Supplier<Flow.Publisher<StreamChunk>> supplier = () -> {
            callCount.incrementAndGet();
            return new ErrorPublisher<>(new RuntimeException("unauthorized auth"));
        };

        var handoff = LlmStreamHandoff.initial(defaultOptions(), supplier);
        var interceptor = new LlmRetryInterceptor(policy, classifier);
        var outcome = interceptor.handle(WaterfallEvent.of("request", handoff), handoff);

        var error = collectError(outcome.output().streamSupplier().get());
        assertNotNull(error);
        assertTrue(error.getMessage().contains("auth"));
        assertEquals(1, callCount.get(), "AUTH 不可重试应直接传播");
    }

    @Test
    @DisplayName("handle 保留原 handoff 的 options/prefixChunks/metadata")
    void handlePreservesHandoffFields() {
        var chunks = List.<StreamChunk>of(new StreamChunk.Finish(new FinishReason.Stop()));
        Supplier<Flow.Publisher<StreamChunk>> supplier = () -> new ListPublisher<>(chunks);

        var original = LlmStreamHandoff.initial(defaultOptions(), supplier)
                .withMetadata("trace", "abc");
        var interceptor = new LlmRetryInterceptor(3);
        var outcome = interceptor.handle(WaterfallEvent.of("request", original), original);

        var wrapped = outcome.output();
        assertEquals(original.options(), wrapped.options());
        assertEquals(original.prefixChunks(), wrapped.prefixChunks());
        assertEquals("abc", wrapped.metadata().get("trace"));
    }

    // ── helpers ──────────────────────────────────────────────

    private static GenerateOptions defaultOptions() {
        return new GenerateOptions(
                "p", "m", null,
                List.of(Message.createUser(List.of(), new MessageSource.UserSource())),
                null, null, null, null, null,
                "session-1", "test", null, null, null
        );
    }

    private static List<StreamChunk> collectAll(Flow.Publisher<StreamChunk> publisher) {
        var collected = new ArrayList<StreamChunk>();
        var done = new java.util.concurrent.CountDownLatch(1);
        var error = new AtomicReference<Throwable>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(StreamChunk item) { collected.add(item); }
            @Override public void onError(Throwable t) { error.set(t); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });
        try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (error.get() != null) throw new RuntimeException(error.get());
        return collected;
    }

    private static Throwable collectError(Flow.Publisher<StreamChunk> publisher) {
        var error = new AtomicReference<Throwable>();
        var done = new java.util.concurrent.CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(StreamChunk item) {}
            @Override public void onError(Throwable t) { error.set(t); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });
        try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return error.get();
    }

    // ── stubs ────────────────────────────────────────────────

    private static final class ListPublisher<T> implements Flow.Publisher<T> {
        private final List<T> items;
        ListPublisher(List<T> items) { this.items = items; }
        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int index = 0;
                @Override public void request(long n) {
                    for (long i = 0; i < n && index < items.size(); i++) {
                        subscriber.onNext(items.get(index++));
                    }
                    if (index >= items.size()) subscriber.onComplete();
                }
                @Override public void cancel() {}
            });
        }
    }

    private static final class ErrorPublisher<T> implements Flow.Publisher<T> {
        private final Throwable error;
        ErrorPublisher(Throwable error) { this.error = error; }
        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long n) { subscriber.onError(error); }
                @Override public void cancel() {}
            });
        }
    }
}
