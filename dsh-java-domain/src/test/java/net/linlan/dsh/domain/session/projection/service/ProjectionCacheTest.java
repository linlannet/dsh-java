package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.projection.adapter.port.ProjectionProvider;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionCacheEntry;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectionCache 写后合流缓存")
class ProjectionCacheTest {

    @Test
    @DisplayName("put 后 get 返回对应条目")
    void putAndGet() {
        var registry = new ProjectionRegistry();
        var cache = new ProjectionCache(registry, directExecutor());

        var entry = new ProjectionCacheEntry<>(List.of("a"), 5L);
        cache.put(ProjectionType.SURFACE, entry);

        var result = cache.<List<String>>get(ProjectionType.SURFACE);
        assertTrue(result.isPresent());
        assertEquals(5L, result.get().lastEventStepIndex());
        assertEquals(List.of("a"), result.get().state());
    }

    @Test
    @DisplayName("get 未缓存类型返回 empty")
    void getUncachedReturnsEmpty() {
        var registry = new ProjectionRegistry();
        var cache = new ProjectionCache(registry, directExecutor());

        assertTrue(cache.<List<String>>get(ProjectionType.STATISTICS).isEmpty());
    }

    @Test
    @DisplayName("computeIfAbsent 首次调用创建条目，再次调用返回已有条目")
    void computeIfAbsent() {
        var registry = new ProjectionRegistry();
        var cache = new ProjectionCache(registry, directExecutor());

        var first = cache.computeIfAbsent(ProjectionType.SURFACE,
                () -> new ProjectionCacheEntry<>(List.of("init"), -1L));
        assertEquals(List.of("init"), first.state());

        var second = cache.computeIfAbsent(ProjectionType.SURFACE,
                () -> new ProjectionCacheEntry<>(List.of("should-not-use"), 99L));
        assertEquals(List.of("init"), second.state());
        assertSame(first, second);
    }

    @Test
    @DisplayName("onEventAppendedSync 增量合流：event.seq > lastEventStepIndex 时合并")
    void incrementalMerge() {
        var registry = new ProjectionRegistry();
        var provider = new AccumulatingProvider();
        registry.register(provider);
        var cache = new ProjectionCache(registry, directExecutor());

        cache.onEventAppendedSync(ProjectionType.SURFACE, event(0));
        cache.onEventAppendedSync(ProjectionType.SURFACE, event(1));
        cache.onEventAppendedSync(ProjectionType.SURFACE, event(2));

        var entry = cache.<List<Long>>get(ProjectionType.SURFACE).orElseThrow();
        assertEquals(2L, entry.lastEventStepIndex());
        assertEquals(List.of(0L, 1L, 2L), entry.state());
    }

    @Test
    @DisplayName("重复合流同一事件无副作用（幂等）")
    void idempotentMerge() {
        var registry = new ProjectionRegistry();
        var provider = new AccumulatingProvider();
        registry.register(provider);
        var cache = new ProjectionCache(registry, directExecutor());

        cache.onEventAppendedSync(ProjectionType.SURFACE, event(0));
        cache.onEventAppendedSync(ProjectionType.SURFACE, event(1));

        cache.onEventAppendedSync(ProjectionType.SURFACE, event(1));
        cache.onEventAppendedSync(ProjectionType.SURFACE, event(0));

        var entry = cache.<List<Long>>get(ProjectionType.SURFACE).orElseThrow();
        assertEquals(1L, entry.lastEventStepIndex());
        assertEquals(List.of(0L, 1L), entry.state());
    }

    @Test
    @DisplayName("lastEventStepIndex 前进：初始 -1 → 合流后等于 event.seq")
    void lastEventStepIndexAdvances() {
        var registry = new ProjectionRegistry();
        var provider = new AccumulatingProvider();
        registry.register(provider);
        var cache = new ProjectionCache(registry, directExecutor());

        var initial = cache.computeIfAbsent(ProjectionType.SURFACE,
                () -> ProjectionCacheEntry.initial(provider.initial()));
        assertEquals(-1L, initial.lastEventStepIndex());

        cache.onEventAppendedSync(ProjectionType.SURFACE, event(3));
        var after = cache.<List<Long>>get(ProjectionType.SURFACE).orElseThrow();
        assertEquals(3L, after.lastEventStepIndex());
    }

    @Test
    @DisplayName("并发安全：多线程合流不崩溃，缓存一致无重复")
    void concurrentMerge() throws InterruptedException {
        var registry = new ProjectionRegistry();
        var provider = new AccumulatingProvider();
        registry.register(provider);
        var cache = new ProjectionCache(registry, directExecutor());

        int threadCount = 8;
        int eventsPerThread = 50;
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(threadCount);
        var pool = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int base = t * eventsPerThread;
            pool.submit(() -> {
                try { startLatch.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < eventsPerThread; i++) {
                    cache.onEventAppendedSync(ProjectionType.SURFACE, event(base + i));
                }
                doneLatch.countDown();
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        var entry = cache.<List<Long>>get(ProjectionType.SURFACE).orElseThrow();
        var stateSet = new java.util.HashSet<>(entry.state());
        assertEquals(stateSet.size(), entry.state().size());
        long maxSeq = entry.state().stream().mapToLong(x -> x).max().orElse(-1);
        assertEquals(maxSeq, entry.lastEventStepIndex());
    }

    @Test
    @DisplayName("合流失败不阻塞：provider 抛异常后续仍可 get")
    void mergeFailureDoesNotBlock() {
        var registry = new ProjectionRegistry();
        var provider = new ThrowingProvider();
        registry.register(provider);
        var cache = new ProjectionCache(registry, directExecutor());

        cache.onEventAppendedSync(ProjectionType.SURFACE, event(0));

        var entry = cache.<List<String>>get(ProjectionType.SURFACE);
        assertTrue(entry.isPresent());
    }

    @Test
    @DisplayName("未注册 provider 的类型：onEventAppended 无副作用")
    void unregisteredTypeNoOp() {
        var registry = new ProjectionRegistry();
        var cache = new ProjectionCache(registry, directExecutor());

        cache.onEventAppendedSync(ProjectionType.SURFACE, event(0));

        assertTrue(cache.<List<Long>>get(ProjectionType.SURFACE).isEmpty());
    }

    @Test
    @DisplayName("异步 onEventAppended 最终完成合流")
    void asyncMergeCompletes() throws InterruptedException {
        var registry = new ProjectionRegistry();
        var provider = new AccumulatingProvider();
        registry.register(provider);
        var cache = new ProjectionCache(registry);

        cache.onEventAppended(ProjectionType.SURFACE, event(0));
        cache.onEventAppended(ProjectionType.SURFACE, event(1));
        cache.onEventAppended(ProjectionType.SURFACE, event(2));

        Thread.sleep(200);
        cache.shutdown();

        var entry = cache.<List<Long>>get(ProjectionType.SURFACE).orElseThrow();
        assertEquals(2L, entry.lastEventStepIndex());
        assertEquals(List.of(0L, 1L, 2L), entry.state());
    }

    @Test
    @DisplayName("put 覆盖已有条目")
    void putOverwrites() {
        var registry = new ProjectionRegistry();
        var cache = new ProjectionCache(registry, directExecutor());

        cache.put(ProjectionType.SURFACE, new ProjectionCacheEntry<>(List.of("old"), 3L));
        cache.put(ProjectionType.SURFACE, new ProjectionCacheEntry<>(List.of("new"), 7L));

        var entry = cache.<List<String>>get(ProjectionType.SURFACE).orElseThrow();
        assertEquals(List.of("new"), entry.state());
        assertEquals(7L, entry.lastEventStepIndex());
    }

    // ── Helpers ───────────────────────────────────────────────

    private static SessionEvent event(long seq) {
        return new SessionEvent.TurnStart(seq, Instant.now());
    }

    private static ExecutorService directExecutor() {
        return new DirectExecutor();
    }

    private static final class DirectExecutor implements ExecutorService {
        @Override public void execute(Runnable command) { command.run(); }
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
            try { return new CompletedFuture<>(task.call()); }
            catch (Exception e) { return new CompletedFuture<>(null); }
        }
        @Override public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
            task.run(); return new CompletedFuture<>(result);
        }
        @Override public java.util.concurrent.Future<?> submit(Runnable task) {
            task.run(); return new CompletedFuture<>(null);
        }
        @Override public <T> List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { throw new UnsupportedOperationException(); }
        @Override public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }

    private static final class CompletedFuture<T> implements java.util.concurrent.Future<T> {
        private final T value;
        CompletedFuture(T value) { this.value = value; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public T get() { return value; }
        @Override public T get(long timeout, TimeUnit unit) { return value; }
    }

    private static final class AccumulatingProvider implements ProjectionProvider<List<Long>> {
        @Override public ProjectionType type() { return ProjectionType.SURFACE; }
        @Override public Class<List<Long>> projectionClass() { return (Class) List.class; }
        @Override public List<Long> initial() { return List.of(); }
        @Override public List<Long> applyIncremental(List<Long> current, SessionEvent event) {
            var next = new ArrayList<>(current);
            next.add(event.seq());
            return List.copyOf(next);
        }
    }

    private static final class ThrowingProvider implements ProjectionProvider<List<String>> {
        private final AtomicInteger callCount = new AtomicInteger(0);
        @Override public ProjectionType type() { return ProjectionType.SURFACE; }
        @Override public Class<List<String>> projectionClass() { return (Class) List.class; }
        @Override public List<String> initial() { return List.of(); }
        @Override public List<String> applyIncremental(List<String> current, SessionEvent event) {
            callCount.incrementAndGet();
            throw new RuntimeException("simulated merge failure");
        }
    }
}
