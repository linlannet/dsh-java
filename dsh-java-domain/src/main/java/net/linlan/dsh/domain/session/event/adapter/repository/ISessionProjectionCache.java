package net.linlan.dsh.domain.session.event.adapter.repository;

import java.util.Optional;

public interface ISessionProjectionCache {

    <T> Optional<T> get(String sessionId, long lastSeq, Class<T> projectionType);

    <T> void put(String sessionId, long lastSeq, Class<T> projectionType, T projection);

    void evict(String sessionId);
}
