package net.linlan.dsh.domain.session.event.service;

import net.linlan.dsh.domain.session.event.adapter.repository.ISessionProjectionCache;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local accelerator for derived session state. Persistence and cold-start
 * recovery remain the responsibility of the underlying event log.
 */
public final class InMemorySessionProjectionCache implements ISessionProjectionCache {

    private final ConcurrentMap<CacheKey, CachedProjection> cache = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> get(String sessionId, long lastSeq, Class<T> projectionType) {
        CachedProjection cached = cache.get(new CacheKey(
                Objects.requireNonNull(sessionId, "sessionId"),
                Objects.requireNonNull(projectionType, "projectionType")
        ));
        if (cached == null || cached.lastSeq() != lastSeq) {
            return Optional.empty();
        }
        return Optional.ofNullable(projectionType.cast(cached.projection()));
    }

    @Override
    public <T> void put(String sessionId, long lastSeq, Class<T> projectionType, T projection) {
        cache.put(new CacheKey(
                Objects.requireNonNull(sessionId, "sessionId"),
                Objects.requireNonNull(projectionType, "projectionType")
                ),
                new CachedProjection(lastSeq, Objects.requireNonNull(projection, "projection")));
    }

    @Override
    public void evict(String sessionId) {
        cache.keySet().removeIf(key -> key.sessionId().equals(sessionId));
    }

    private record CachedProjection(long lastSeq, Object projection) {}

    private record CacheKey(String sessionId, Class<?> projectionType) {}
}
