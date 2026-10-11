package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.projection.adapter.port.ProjectionProvider;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionCacheEntry;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * ProjectionCache 是投影写后合流缓存。
 * <p>事件追加后触发增量合并：读取 {@link ProjectionCacheEntry#lastEventStepIndex()}，
 * 若 {@code event.seq() > lastEventStepIndex} 则调 {@link ProjectionProvider#applyIncremental}
 * 增量合并并 CAS 更新缓存，否则跳过（幂等）。
 * <p>合流异步触发，不阻塞事件追加主流程；合流失败不阻塞主流程。
 */
public final class ProjectionCache {

    private final ProjectionRegistry registry;
    private final ConcurrentHashMap<ProjectionType, AtomicReference<ProjectionCacheEntry<?>>> cache;
    private final ExecutorService mergeExecutor;

    public ProjectionCache(ProjectionRegistry registry) {
        this(registry, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "projection-merge");
            t.setDaemon(true);
            return t;
        }));
    }

    public ProjectionCache(ProjectionRegistry registry, ExecutorService mergeExecutor) {
        this.registry = registry;
        this.cache = new ConcurrentHashMap<>();
        this.mergeExecutor = mergeExecutor;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<ProjectionCacheEntry<T>> get(ProjectionType type) {
        AtomicReference<ProjectionCacheEntry<?>> ref = cache.get(type);
        if (ref == null) {
            return Optional.empty();
        }
        ProjectionCacheEntry<?> entry = ref.get();
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of((ProjectionCacheEntry<T>) entry);
    }

    public <T> void put(ProjectionType type, ProjectionCacheEntry<T> entry) {
        cache.compute(type, (k, existing) -> {
            if (existing == null) {
                return new AtomicReference<>(entry);
            }
            existing.set(entry);
            return existing;
        });
    }

    @SuppressWarnings("unchecked")
    public <T> ProjectionCacheEntry<T> computeIfAbsent(ProjectionType type,
                                                       Supplier<ProjectionCacheEntry<T>> supplier) {
        AtomicReference<ProjectionCacheEntry<?>> ref =
                cache.computeIfAbsent(type, k -> new AtomicReference<>(supplier.get()));
        return (ProjectionCacheEntry<T>) ref.get();
    }

    public void onEventAppended(ProjectionType type, SessionEvent event) {
        try {
            mergeExecutor.submit(() -> doMerge(type, event));
        } catch (RejectedExecutionException e) {
            doMerge(type, event);
        }
    }

    public void onEventAppended(SessionEvent event) {
        for (ProjectionType type : registry.registeredTypes()) {
            onEventAppended(type, event);
        }
    }

    public void onEventAppendedSync(ProjectionType type, SessionEvent event) {
        doMerge(type, event);
    }

    @SuppressWarnings("unchecked")
    private <T> void doMerge(ProjectionType type, SessionEvent event) {
        try {
            Optional<ProjectionProvider<?>> providerOpt = registry.resolve(type);
            if (providerOpt.isEmpty()) {
                return;
            }
            ProjectionProvider<T> provider = (ProjectionProvider<T>) providerOpt.get();

            AtomicReference<ProjectionCacheEntry<?>> ref = cache.get(type);
            if (ref == null) {
                ProjectionCacheEntry<T> initialEntry =
                        ProjectionCacheEntry.initial(provider.initial());
                ref = cache.computeIfAbsent(type, k -> new AtomicReference<>(initialEntry));
            }

            while (true) {
                ProjectionCacheEntry<T> current = (ProjectionCacheEntry<T>) ref.get();
                if (event.seq() <= current.lastEventStepIndex()) {
                    return;
                }
                T newState = provider.applyIncremental(current.state(), event);
                ProjectionCacheEntry<T> updated = current.update(newState, event.seq());
                if (ref.compareAndSet(current, updated)) {
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public int size() {
        return cache.size();
    }

    public void shutdown() {
        mergeExecutor.shutdown();
    }
}
