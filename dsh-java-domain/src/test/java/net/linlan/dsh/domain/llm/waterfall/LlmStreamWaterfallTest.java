package net.linlan.dsh.domain.llm.waterfall;

import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmStreamWaterfall 流式拦截器链")
class LlmStreamWaterfallTest {

    @Test
    @DisplayName("无拦截器时直接透传底层流")
    void noInterceptorsPassthrough() {
        var chunks = List.<StreamChunk>of(
                new StreamChunk.TextDelta(0, "Hello"),
                new StreamChunk.TextDelta(0, " World"),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var runtime = new StubLlmRuntimePort(opts -> new ListPublisher<StreamChunk>(chunks));
        var waterfall = new LlmStreamWaterfall(runtime);

        var result = collectAll(waterfall.execute(defaultOptions("provider-a", "model-a")));

        assertEquals(3, result.size());
        assertInstanceOf(StreamChunk.TextDelta.class, result.get(0));
        assertEquals("Hello", ((StreamChunk.TextDelta) result.get(0)).text());
    }

    @Test
    @DisplayName("Retry 拦截器：首次错误后重试成功")
    void retryOnFailure() {
        var chunks = List.<StreamChunk>of(
                new StreamChunk.TextDelta(0, "recovered"),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var callCount = new AtomicInteger(0);
        var runtime = new StubLlmRuntimePort(opts -> {
            if (callCount.incrementAndGet() == 1) {
                return new ErrorPublisher<StreamChunk>(new RuntimeException("first attempt fails"));
            }
            return new ListPublisher<StreamChunk>(chunks);
        });
        var waterfall = new LlmStreamWaterfall(runtime, List.of(
                new LlmRetryInterceptor(3)
        ));

        var result = collectAll(waterfall.execute(defaultOptions("p", "m")));

        assertEquals(2, result.size());
        assertEquals("recovered", ((StreamChunk.TextDelta) result.get(0)).text());
        assertEquals(2, callCount.get());
    }

    @Test
    @DisplayName("Retry 拦截器：超过最大重试次数后传播错误")
    void retryExhausted() {
        var callCount = new AtomicInteger(0);
        var runtime = new StubLlmRuntimePort(opts -> {
            callCount.incrementAndGet();
            return new ErrorPublisher<StreamChunk>(new RuntimeException("always fails"));
        });
        var waterfall = new LlmStreamWaterfall(runtime, List.of(
                new LlmRetryInterceptor(2)
        ));

        var error = collectError(waterfall.execute(defaultOptions("p", "m")));

        assertNotNull(error);
        assertEquals("always fails", error.getMessage());
        assertEquals(3, callCount.get());
    }

    @Test
    @DisplayName("Replay 拦截器：前置 chunk 在流之前发射")
    void replayInjectsPrefixChunks() {
        var liveChunks = List.<StreamChunk>of(
                new StreamChunk.TextDelta(0, "live"),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        var replayChunks = List.<StreamChunk>of(
                new StreamChunk.TextDelta(0, "replayed")
        );
        var runtime = new StubLlmRuntimePort(opts -> new ListPublisher<>(liveChunks));
        var waterfall = new LlmStreamWaterfall(runtime, List.of(
                new LlmReplayInterceptor(opts -> replayChunks)
        ));

        var result = collectAll(waterfall.execute(defaultOptions("p", "m")));

        assertEquals(3, result.size());
        assertEquals("replayed", ((StreamChunk.TextDelta) result.get(0)).text());
        assertEquals("live", ((StreamChunk.TextDelta) result.get(1)).text());
    }

    @Test
    @DisplayName("Routing 拦截器：重定向到新 Provider")
    void routingRedirectsProvider() {
        var providerACalled = new AtomicInteger(0);
        var providerBCalled = new AtomicInteger(0);
        var runtime = new StubLlmRuntimePort(opts -> {
            if (opts.provider().equals("provider-a")) {
                providerACalled.incrementAndGet();
                return new ListPublisher<StreamChunk>(List.of(
                        new StreamChunk.TextDelta(0, "from-A"),
                        new StreamChunk.Finish(new FinishReason.Stop())
                ));
            }
            providerBCalled.incrementAndGet();
            return new ListPublisher<StreamChunk>(List.of(
                    new StreamChunk.TextDelta(0, "from-B"),
                    new StreamChunk.Finish(new FinishReason.Stop())
            ));
        });
        var waterfall = new LlmStreamWaterfall(runtime, List.of(
                new LlmRoutingInterceptor(runtime, opts -> {
                    if (opts.provider().equals("provider-a")) {
                        return new GenerateOptions(
                                "provider-b", opts.model(), opts.reasoningEffort(),
                                opts.messages(), opts.system(), opts.tools(),
                                opts.temperature(), opts.maxTokens(), opts.stop(),
                                opts.sessionId(), opts.purpose(), opts.baseUrl(),
                                opts.apiKey(), opts.protocol()
                        );
                    }
                    return opts;
                })
        ));

        var result = collectAll(waterfall.execute(defaultOptions("provider-a", "model-x")));

        assertEquals(2, result.size());
        assertEquals("from-B", ((StreamChunk.TextDelta) result.get(0)).text());
        assertEquals(0, providerACalled.get());
        assertEquals(1, providerBCalled.get());
    }

    @Test
    @DisplayName("三个拦截器按 priority 依次执行：retry → replay → routing")
    void allInterceptorsInOrder() {
        var runtime = new StubLlmRuntimePort(opts -> {
            if (opts.provider().equals("p-a")) {
                return new ErrorPublisher<StreamChunk>(new RuntimeException("p-a always fails"));
            }
            return new ListPublisher<StreamChunk>(List.<StreamChunk>of(
                    new StreamChunk.TextDelta(0, "live-" + opts.provider()),
                    new StreamChunk.Finish(new FinishReason.Stop())
            ));
        });
        var replayChunks = List.<StreamChunk>of(
                new StreamChunk.TextDelta(0, "prefix")
        );
        var waterfall = new LlmStreamWaterfall(runtime, List.of(
                new LlmRetryInterceptor(3),
                new LlmReplayInterceptor(opts -> replayChunks),
                new LlmRoutingInterceptor(runtime, opts -> {
                    if (opts.provider().equals("p-a")) {
                        return new GenerateOptions(
                                "p-b", opts.model(), opts.reasoningEffort(),
                                opts.messages(), opts.system(), opts.tools(),
                                opts.temperature(), opts.maxTokens(), opts.stop(),
                                opts.sessionId(), opts.purpose(), opts.baseUrl(),
                                opts.apiKey(), opts.protocol()
                        );
                    }
                    return opts;
                })
        ));

        var result = collectAll(waterfall.execute(defaultOptions("p-a", "m")));

        assertTrue(result.size() >= 2);
        assertEquals("prefix", ((StreamChunk.TextDelta) result.get(0)).text());
        assertEquals("live-p-b", ((StreamChunk.TextDelta) result.get(1)).text());
    }

    @Test
    @DisplayName("Routing 保留原始请求参数")
    void routingPreservesOriginalOptions() {
        var runtime = new StubLlmRuntimePort(opts -> new ListPublisher<>(List.<StreamChunk>of(
                new StreamChunk.Finish(new FinishReason.Stop())
        )));
        var originalOpts = defaultOptions("p-a", "m-x");
        var waterfall = new LlmStreamWaterfall(runtime, List.of(
                new LlmRoutingInterceptor(runtime, opts -> {
                    if (opts.provider().equals("p-a")) {
                        return new GenerateOptions(
                                "p-b", opts.model(), opts.reasoningEffort(),
                                opts.messages(), opts.system(), opts.tools(),
                                opts.temperature(), opts.maxTokens(), opts.stop(),
                                opts.sessionId(), opts.purpose(), opts.baseUrl(),
                                opts.apiKey(), opts.protocol()
                        );
                    }
                    return opts;
                })
        ));

        collectAll(waterfall.execute(originalOpts));

        assertEquals("p-a", originalOpts.provider());
        assertEquals("m-x", originalOpts.model());
    }

    // ── Helpers ───────────────────────────────────────────────

    private static GenerateOptions defaultOptions(String provider, String model) {
        return new GenerateOptions(
                provider, model, null,
                List.of(Message.createUser(List.of(), new net.linlan.dsh.domain.model.entity.MessageSource.UserSource())),
                null, null, null, null, null,
                "session-1", "test", null, null, null
        );
    }

    private static List<StreamChunk> collectAll(Flow.Publisher<StreamChunk> publisher) {
        var collected = new ArrayList<StreamChunk>();
        var done = new java.util.concurrent.CountDownLatch(1);
        var error = new AtomicReference<Throwable>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription sub;
            @Override public void onSubscribe(Flow.Subscription s) { sub = s; s.request(Long.MAX_VALUE); }
            @Override public void onNext(StreamChunk item) { collected.add(item); }
            @Override public void onError(Throwable t) { error.set(t); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });
        try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return collected;
    }

    private static Throwable collectError(Flow.Publisher<StreamChunk> publisher) {
        var error = new AtomicReference<Throwable>();
        var done = new java.util.concurrent.CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(StreamChunk item) { }
            @Override public void onError(Throwable t) { error.set(t); done.countDown(); }
            @Override public void onComplete() { done.countDown(); }
        });
        try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return error.get();
    }

    // ── Stubs ─────────────────────────────────────────────────

    private static final class StubLlmRuntimePort implements ILlmRuntimePort {
        private final java.util.function.Function<GenerateOptions, Flow.Publisher<StreamChunk>> streamFn;

        StubLlmRuntimePort(java.util.function.Function<GenerateOptions, Flow.Publisher<StreamChunk>> streamFn) {
            this.streamFn = streamFn;
        }

        @Override public void registerAdapter(String provider, LlmAdapter adapter) { }
        @Override public void unregisterAdapter(String provider) { }
        @Override public List<LlmAdapter.LlmProviderInfo> listProviders() { return List.of(); }
        @Override public List<LlmAdapter.LlmModelInfo> listModels(String provider) { return List.of(); }
        @Override public LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model) { return null; }
        @Override public Flow.Publisher<StreamChunk> stream(GenerateOptions options) { return streamFn.apply(options); }
    }

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
                    if (index >= items.size()) {
                        subscriber.onComplete();
                    }
                }
                @Override public void cancel() { }
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
                @Override public void cancel() { }
            });
        }
    }
}
