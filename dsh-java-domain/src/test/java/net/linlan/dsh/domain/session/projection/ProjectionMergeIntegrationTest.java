package net.linlan.dsh.domain.session.projection;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.valobj.AssistantMessagePayload;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceIntent;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceOp;
import net.linlan.dsh.domain.session.event.model.valobj.UserMessagePayload;
import net.linlan.dsh.domain.session.event.service.SurfaceProjector;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionCacheEntry;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;
import net.linlan.dsh.domain.session.projection.service.ProjectionCache;
import net.linlan.dsh.domain.session.projection.service.ProjectionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("投影写后合流集成测试")
class ProjectionMergeIntegrationTest {

    @Test
    @DisplayName("增量合流：逐事件合流后缓存等于全量投影")
    void incrementalMergeMatchesFullProjection() {
        var projector = new SurfaceProjector();
        var registry = new ProjectionRegistry();
        registry.register(projector);
        var cache = new ProjectionCache(registry, directExecutor());

        List<SessionEvent> events = List.of(
                userMessage(0, "Hello"),
                assistantMessage(1, "Hi there"),
                userMessage(2, "How are you?"),
                assistantMessage(3, "I'm fine")
        );

        for (SessionEvent event : events) {
            cache.onEventAppendedSync(ProjectionType.SURFACE, event);
        }

        var cached = cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow();
        var fullProjection = projector.project(events);

        assertEquals(fullProjection.size(), cached.state().size());
        for (int i = 0; i < fullProjection.size(); i++) {
            assertEquals(fullProjection.get(i).seq(), cached.state().get(i).seq());
        }
        assertEquals(3L, cached.lastEventStepIndex());
    }

    @Test
    @DisplayName("幂等：重复合流同一事件无副作用")
    void idempotentReMerge() {
        var projector = new SurfaceProjector();
        var registry = new ProjectionRegistry();
        registry.register(projector);
        var cache = new ProjectionCache(registry, directExecutor());

        var e0 = userMessage(0, "Hello");
        var e1 = assistantMessage(1, "Hi");

        cache.onEventAppendedSync(ProjectionType.SURFACE, e0);
        cache.onEventAppendedSync(ProjectionType.SURFACE, e1);

        var before = cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow();

        cache.onEventAppendedSync(ProjectionType.SURFACE, e1);
        cache.onEventAppendedSync(ProjectionType.SURFACE, e0);

        var after = cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow();

        assertEquals(before.lastEventStepIndex(), after.lastEventStepIndex());
        assertEquals(before.state().size(), after.state().size());
    }

    @Test
    @DisplayName("lastEventStepIndex 前进：初始 -1 → 逐事件递增")
    void lastEventStepIndexAdvances() {
        var projector = new SurfaceProjector();
        var registry = new ProjectionRegistry();
        registry.register(projector);
        var cache = new ProjectionCache(registry, directExecutor());

        var initial = cache.computeIfAbsent(ProjectionType.SURFACE,
                () -> ProjectionCacheEntry.initial(projector.initial()));
        assertEquals(-1L, initial.lastEventStepIndex());

        cache.onEventAppendedSync(ProjectionType.SURFACE, userMessage(0, "A"));
        assertEquals(0L, cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow().lastEventStepIndex());

        cache.onEventAppendedSync(ProjectionType.SURFACE, assistantMessage(1, "B"));
        assertEquals(1L, cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow().lastEventStepIndex());

        cache.onEventAppendedSync(ProjectionType.SURFACE, userMessage(5, "C"));
        assertEquals(5L, cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow().lastEventStepIndex());
    }

    @Test
    @DisplayName("Surface 投影回归：缓存投影与 SurfaceProjector.project 结果一致")
    void surfaceProjectionRegression() {
        var projector = new SurfaceProjector();
        var registry = new ProjectionRegistry();
        registry.register(projector);
        var cache = new ProjectionCache(registry, directExecutor());

        List<SessionEvent> events = new ArrayList<>();
        events.add(userMessage(0, "msg-0"));
        for (int i = 1; i <= 10; i++) {
            if (i % 2 == 0) {
                events.add(userMessage(i, "msg-" + i));
            } else {
                events.add(assistantMessage(i, "resp-" + i));
            }
        }

        for (SessionEvent event : events) {
            cache.onEventAppendedSync(ProjectionType.SURFACE, event);
        }

        var cached = cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow();
        var expected = projector.project(events);

        assertEquals(expected.size(), cached.state().size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).seq(), cached.state().get(i).seq());
            assertEquals(expected.get(i).event().type(), cached.state().get(i).event().type());
        }
    }

    @Test
    @DisplayName("非表面事件跳过：TurnStart/StepStart 不影响投影")
    void nonSurfaceEventsSkipped() {
        var projector = new SurfaceProjector();
        var registry = new ProjectionRegistry();
        registry.register(projector);
        var cache = new ProjectionCache(registry, directExecutor());

        cache.onEventAppendedSync(ProjectionType.SURFACE, new SessionEvent.TurnStart(0, Instant.now()));
        cache.onEventAppendedSync(ProjectionType.SURFACE, new SessionEvent.StepStart(1, Instant.now(), 1, 1));
        cache.onEventAppendedSync(ProjectionType.SURFACE, userMessage(2, "Hello"));
        cache.onEventAppendedSync(ProjectionType.SURFACE, new SessionEvent.StepEnd(3, Instant.now(), 1, 1));

        var cached = cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow();
        assertEquals(1, cached.state().size());
        assertEquals(2L, cached.state().get(0).seq());
    }

    @Test
    @DisplayName("并发：多线程合流后缓存一致无重复")
    void concurrentMergeConsistency() throws InterruptedException {
        var projector = new SurfaceProjector();
        var registry = new ProjectionRegistry();
        registry.register(projector);
        var cache = new ProjectionCache(registry, directExecutor());

        int threadCount = 4;
        int eventsPerThread = 25;
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(threadCount);
        var pool = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int base = t * eventsPerThread;
            pool.submit(() -> {
                try { startLatch.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < eventsPerThread; i++) {
                    int seq = base + i;
                    SessionEvent event = (seq % 2 == 0)
                            ? userMessage(seq, "msg-" + seq)
                            : assistantMessage(seq, "resp-" + seq);
                    cache.onEventAppendedSync(ProjectionType.SURFACE, event);
                }
                doneLatch.countDown();
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        var entry = cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow();
        var seqSet = new java.util.HashSet<Long>();
        for (var node : entry.state()) {
            assertTrue(seqSet.add(node.seq()), "duplicate seq: " + node.seq());
        }
        long maxSeq = entry.state().stream().mapToLong(SurfaceProjector.SurfaceNode::seq).max().orElse(-1);
        assertEquals(maxSeq, entry.lastEventStepIndex());
    }

    @Test
    @DisplayName("onEventAppended(全类型) 触发所有已注册 provider 合流")
    void onEventAppendedAllTypes() {
        var projector = new SurfaceProjector();
        var registry = new ProjectionRegistry();
        registry.register(projector);
        var cache = new ProjectionCache(registry, directExecutor());

        cache.onEventAppended(userMessage(0, "Hello"));
        cache.onEventAppended(assistantMessage(1, "Hi"));

        var cached = cache.<List<SurfaceProjector.SurfaceNode>>get(ProjectionType.SURFACE).orElseThrow();
        assertEquals(2, cached.state().size());
        assertEquals(1L, cached.lastEventStepIndex());
    }

    // ── Helpers ───────────────────────────────────────────────

    private static SessionEvent.UserMessage userMessage(long seq, String content) {
        var msg = Message.createUser(
                List.of(new TextBlock(content)),
                new MessageSource.UserSource()
        );
        return new SessionEvent.UserMessage(
                seq, Instant.now(),
                new UserMessagePayload(0, 0, msg),
                new SurfaceIntent(SurfaceOp.append(), null)
        );
    }

    private static SessionEvent.AssistantMessage assistantMessage(long seq, String content) {
        var msg = Message.createAssistant(
                List.of(new TextBlock(content)),
                new MessageSource.ModelMessageSource("test", "test")
        );
        return new SessionEvent.AssistantMessage(
                seq, Instant.now(),
                new AssistantMessagePayload(0, 0, msg, new TokenUsage(0, 0)),
                new SurfaceIntent(SurfaceOp.append(), null)
        );
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
}
