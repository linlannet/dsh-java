package net.linlan.dsh.domain.session.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemorySessionProjectionCacheTest {

    private final InMemorySessionProjectionCache cache = new InMemorySessionProjectionCache();

    @Test
    void shouldReturnProjectionWhenLastSeqMatches() {
        cache.put("session-1", 7L, String.class, "projection");

        Optional<String> actual = cache.get("session-1", 7L, String.class);

        assertEquals(Optional.of("projection"), actual);
    }

    @Test
    void shouldTreatProjectionAsStaleWhenLastSeqChanges() {
        cache.put("session-1", 7L, String.class, "projection");

        Optional<String> actual = cache.get("session-1", 8L, String.class);

        assertTrue(actual.isEmpty());
    }

    @Test
    void shouldIsolateProjectionsByType() {
        cache.put("session-1", 7L, String.class, "projection");

        assertEquals(Optional.empty(), cache.get("session-1", 7L, Integer.class));
        assertEquals(Optional.of("projection"), cache.get("session-1", 7L, String.class));
    }

    @Test
    void shouldEvictAllProjectionTypesForSession() {
        cache.put("session-1", 7L, String.class, "projection");
        cache.put("session-1", 7L, Integer.class, 42);

        cache.evict("session-1");

        assertEquals(Optional.empty(), cache.get("session-1", 7L, String.class));
        assertEquals(Optional.empty(), cache.get("session-1", 7L, Integer.class));
    }
}
