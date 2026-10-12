package net.linlan.dsh.domain.storage.domain;

import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import net.linlan.dsh.domain.storage.model.valobj.StorageException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StorageDomainTest {

    @Test
    void shouldPutAndGetRoundTrip() {
        StubBackend backend = new StubBackend();
        StorageDomain domain = new StorageDomain(backend);

        domain.put("name", "alice", null);

        Optional<String> value = domain.get("name");
        assertTrue(value.isPresent());
        assertEquals("alice", value.get());
    }

    @Test
    void shouldReturnEmptyForMissingKey() {
        StubBackend backend = new StubBackend();
        StorageDomain domain = new StorageDomain(backend);

        assertTrue(domain.get("missing").isEmpty());
    }

    @Test
    void shouldRejectNullKeyOnPut() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        assertThrows(IllegalArgumentException.class, () -> domain.put(null, "v", null));
    }

    @Test
    void shouldRejectBlankKeyOnPut() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        assertThrows(IllegalArgumentException.class, () -> domain.put("  ", "v", null));
    }

    @Test
    void shouldRejectNullValueOnPut() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        assertThrows(IllegalArgumentException.class, () -> domain.put("k", null, null));
    }

    @Test
    void shouldValidateNonEmptySchema() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        assertThrows(StorageException.class, () -> domain.put("k", "  ", "non-empty"));
    }

    @Test
    void shouldValidateJsonSchema() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        domain.put("k", "{\"a\":1}", "json");
        assertThrows(StorageException.class, () -> domain.put("k2", "not-json", "json"));
    }

    @Test
    void shouldValidateRegexSchema() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        domain.put("k", "123", "[0-9]+");
        assertThrows(StorageException.class, () -> domain.put("k2", "abc", "[0-9]+"));
    }

    @Test
    void shouldEmitEventOnPut() {
        StubBackend backend = new StubBackend();
        List<StorageDomain.StorageEvent> events = new java.util.ArrayList<>();
        StorageDomain domain = new StorageDomain(backend, events::add);

        domain.put("k", "v", null);

        assertEquals(1, events.size());
        assertEquals("put", events.get(0).op());
        assertEquals("k", events.get(0).key());
        assertEquals("v", events.get(0).value());
    }

    @Test
    void shouldRejectNullBackend() {
        assertThrows(IllegalArgumentException.class, () -> new StorageDomain(null));
    }

    @Test
    void shouldValidateStringSchemaPasses() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        domain.put("k", "anything", "string");
    }

    @Test
    void shouldRejectNullKeyOnGet() {
        StorageDomain domain = new StorageDomain(new StubBackend());
        assertThrows(IllegalArgumentException.class, () -> domain.get(null));
    }

    static class StubBackend implements IStorageBackendPort {
        final Map<String, Map<String, String>> data = new LinkedHashMap<>();

        @Override
        public KvSnapshot load(KvUnitDescriptor descriptor) {
            return new KvSnapshot(data);
        }

        @Override
        public void put(KvUnitDescriptor descriptor, String table, String key, String value) {
            data.computeIfAbsent(table, t -> new LinkedHashMap<>()).put(key, value);
        }

        @Override
        public void remove(KvUnitDescriptor descriptor, String table, String key) {
            Map<String, String> tableData = data.get(table);
            if (tableData != null) {
                tableData.remove(key);
            }
        }

        @Override
        public void putAll(KvUnitDescriptor descriptor, Map<String, Map<String, String>> puts) {
            for (var entry : puts.entrySet()) {
                data.computeIfAbsent(entry.getKey(), t -> new LinkedHashMap<>()).putAll(entry.getValue());
            }
        }
    }
}
