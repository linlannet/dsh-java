package net.linlan.dsh.domain.storage.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import net.linlan.dsh.domain.storage.model.valobj.StorageException;
import org.junit.jupiter.api.Test;

class ContentAddressableAttachmentStoreTest {

    @Test
    void shouldStoreContentAndReturnHash() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        String hash = store.store("hello".getBytes());

        assertNotNull(hash);
        assertEquals(64, hash.length());
    }

    @Test
    void shouldRetrieveStoredContent() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        byte[] content = "hello world".getBytes();
        String hash = store.store(content);

        byte[] retrieved = store.retrieve(hash);
        assertEquals(new String(content), new String(retrieved));
    }

    @Test
    void shouldDeduplicateSameContent() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        byte[] content = "duplicate".getBytes();
        String hash1 = store.store(content);
        String hash2 = store.store(content);

        assertEquals(hash1, hash2);
        assertEquals(1, backend.putCount);
    }

    @Test
    void shouldProduceDifferentHashesForDifferentContent() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        String hash1 = store.store("content-a".getBytes());
        String hash2 = store.store("content-b".getBytes());

        assertFalse(hash1.equals(hash2));
    }

    @Test
    void shouldRejectNullContent() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        assertThrows(IllegalArgumentException.class, () -> store.store(null));
    }

    @Test
    void shouldRejectBlankHashOnRetrieve() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        assertThrows(IllegalArgumentException.class, () -> store.retrieve("  "));
    }

    @Test
    void shouldThrowWhenRetrievingUnknownHash() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        assertThrows(StorageException.class, () -> store.retrieve("nonexistenthash"));
    }

    @Test
    void shouldReportExistence() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend);

        String hash = store.store("exists".getBytes());

        assertTrue(store.exists(hash));
        assertFalse(store.exists("unknown"));
        assertFalse(store.exists(null));
        assertFalse(store.exists("  "));
    }

    @Test
    void shouldRejectNullBackend() {
        assertThrows(IllegalArgumentException.class, () ->
                new ContentAddressableAttachmentStore(null));
    }

    @Test
    void shouldSupportCustomHashFunction() {
        StubBackend backend = new StubBackend();
        ContentAddressableAttachmentStore.HashFunction constantHash = content -> "constant-hash";
        ContentAddressableAttachmentStore store = new ContentAddressableAttachmentStore(backend, constantHash);

        String hash = store.store("anything".getBytes());

        assertEquals("constant-hash", hash);
    }

    static class StubBackend implements IStorageBackendPort {
        final Map<String, Map<String, String>> data = new LinkedHashMap<>();
        int putCount = 0;

        @Override
        public KvSnapshot load(KvUnitDescriptor descriptor) {
            return new KvSnapshot(data);
        }

        @Override
        public void put(KvUnitDescriptor descriptor, String table, String key, String value) {
            data.computeIfAbsent(table, t -> new LinkedHashMap<>()).put(key, value);
            putCount++;
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
                putCount += entry.getValue().size();
            }
        }
    }
}
