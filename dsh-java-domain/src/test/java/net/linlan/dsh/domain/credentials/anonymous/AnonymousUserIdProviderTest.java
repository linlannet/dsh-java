package net.linlan.dsh.domain.credentials.anonymous;

import net.linlan.dsh.domain.credentials.anonymous.adapter.port.IAnonymousIdStorePort;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AnonymousUserIdProviderTest {

    @Test
    void shouldGenerateIdForNewFingerprint() {
        StubStore store = new StubStore();
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(store);

        String id = provider.getOrCreateId("fp-1");

        assertNotNull(id);
        assertTrue(id.startsWith("anon-"));
    }

    @Test
    void shouldReturnSameIdForSameFingerprint() {
        StubStore store = new StubStore();
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(store);

        String id1 = provider.getOrCreateId("fp-1");
        String id2 = provider.getOrCreateId("fp-1");

        assertEquals(id1, id2);
    }

    @Test
    void shouldReturnDifferentIdsForDifferentFingerprints() {
        StubStore store = new StubStore();
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(store);

        String id1 = provider.getOrCreateId("fp-1");
        String id2 = provider.getOrCreateId("fp-2");

        assertNotEquals(id1, id2);
    }

    @Test
    void shouldPersistIdInStore() {
        StubStore store = new StubStore();
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(store);

        String id = provider.getOrCreateId("fp-1");

        Optional<String> found = store.findById("fp-1");
        assertTrue(found.isPresent());
        assertEquals(id, found.get());
    }

    @Test
    void shouldRejectNullFingerprint() {
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(new StubStore());
        assertThrows(IllegalArgumentException.class, () -> provider.getOrCreateId(null));
    }

    @Test
    void shouldRejectBlankFingerprint() {
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(new StubStore());
        assertThrows(IllegalArgumentException.class, () -> provider.getOrCreateId("  "));
    }

    @Test
    void shouldRejectNullStore() {
        assertThrows(IllegalArgumentException.class, () -> new AnonymousUserIdProvider(null));
    }

    @Test
    void shouldLookupExistingFingerprint() {
        StubStore store = new StubStore();
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(store);

        String id = provider.getOrCreateId("fp-1");
        Optional<String> looked = provider.lookup("fp-1");

        assertTrue(looked.isPresent());
        assertEquals(id, looked.get());
    }

    @Test
    void shouldReturnEmptyForMissingLookup() {
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(new StubStore());
        assertTrue(provider.lookup("missing").isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullLookup() {
        AnonymousUserIdProvider provider = new AnonymousUserIdProvider(new StubStore());
        assertTrue(provider.lookup(null).isEmpty());
    }

    @Test
    void shouldReturnStableIdAcrossProviderInstances() {
        StubStore store = new StubStore();
        AnonymousUserIdProvider p1 = new AnonymousUserIdProvider(store);
        String id1 = p1.getOrCreateId("fp-stable");

        AnonymousUserIdProvider p2 = new AnonymousUserIdProvider(store);
        String id2 = p2.getOrCreateId("fp-stable");

        assertEquals(id1, id2);
    }

    static class StubStore implements IAnonymousIdStorePort {
        final Map<String, String> data = new LinkedHashMap<>();

        @Override
        public Optional<String> findById(String fingerprint) {
            return Optional.ofNullable(data.get(fingerprint));
        }

        @Override
        public void save(String fingerprint, String anonymousId) {
            data.put(fingerprint, anonymousId);
        }
    }
}
