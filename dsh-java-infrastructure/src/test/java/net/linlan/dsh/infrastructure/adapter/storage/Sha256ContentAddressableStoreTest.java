package net.linlan.dsh.infrastructure.adapter.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import net.linlan.dsh.domain.storage.model.valobj.StorageException;
import net.linlan.dsh.domain.storage.attachment.ContentAddressableAttachmentStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("T2.028 Sha256ContentAddressableStore SHA-256 内容寻址存储")
class Sha256ContentAddressableStoreTest {

    @Test
    @DisplayName("store 返回 64 字符 SHA-256 hex")
    void storeReturns64CharSha256Hex() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        String hash = store.store("hello".getBytes(StandardCharsets.UTF_8));

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("store 后 retrieve 返回原内容")
    void storeThenRetrieveReturnsOriginalContent() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        String hash = store.store(content);
        byte[] retrieved = store.retrieve(hash);

        assertEquals(new String(content, StandardCharsets.UTF_8),
                new String(retrieved, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("相同内容去重：第二次 store 不调用 backend.put")
    void deduplicateSameContentDoesNotCallPutTwice() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);
        byte[] content = "duplicate".getBytes(StandardCharsets.UTF_8);

        String hash1 = store.store(content);
        String hash2 = store.store(content);

        assertEquals(hash1, hash2);
        assertEquals(1, backend.putCount);
    }

    @Test
    @DisplayName("不同内容产生不同 hash")
    void differentContentProducesDifferentHash() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        String hash1 = store.store("content-a".getBytes(StandardCharsets.UTF_8));
        String hash2 = store.store("content-b".getBytes(StandardCharsets.UTF_8));

        assertFalse(hash1.equals(hash2));
    }

    @Test
    @DisplayName("SHA-256 已知向量验证")
    void sha256KnownVector() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        String hash = store.store("abc".getBytes(StandardCharsets.UTF_8));

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }

    @Test
    @DisplayName("exists 对已存储 hash 返回 true")
    void existsReturnsTrueForStoredHash() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        String hash = store.store("data".getBytes(StandardCharsets.UTF_8));

        assertTrue(store.exists(hash));
    }

    @Test
    @DisplayName("exists 对未存储 hash 返回 false")
    void existsReturnsFalseForUnknownHash() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        assertFalse(store.exists("0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    @DisplayName("exists 对 null/blank hash 返回 false")
    void existsReturnsFalseForNullOrBlankHash() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        assertFalse(store.exists(null));
        assertFalse(store.exists(""));
        assertFalse(store.exists("   "));
    }

    @Test
    @DisplayName("retrieve 对未存储 hash 抛 StorageException")
    void retrieveUnknownHashThrowsStorageException() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        assertThrows(StorageException.class, () ->
                store.retrieve("nonexistent-hash"));
    }

    @Test
    @DisplayName("retrieve 对 null/blank hash 抛 IllegalArgumentException")
    void retrieveNullOrBlankHashThrowsIllegalArgument() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        assertThrows(IllegalArgumentException.class, () -> store.retrieve(null));
        assertThrows(IllegalArgumentException.class, () -> store.retrieve(""));
        assertThrows(IllegalArgumentException.class, () -> store.retrieve("  "));
    }

    @Test
    @DisplayName("store null 内容抛 IllegalArgumentException")
    void storeNullContentThrowsIllegalArgument() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);

        assertThrows(IllegalArgumentException.class, () -> store.store(null));
    }

    @Test
    @DisplayName("Sha256ContentAddressableStore 是 ContentAddressableAttachmentStore 子类型")
    void isContentAddressableAttachmentStoreSubtype() {
        StubBackend backend = new StubBackend();
        Sha256ContentAddressableStore store = new Sha256ContentAddressableStore(backend);
        assertTrue(store instanceof ContentAddressableAttachmentStore);
    }

    @Test
    @DisplayName("Sha256HashFunction 对 null 内容抛 IllegalArgumentException")
    void sha256HashFunctionRejectsNullContent() {
        Sha256ContentAddressableStore.Sha256HashFunction hf =
                new Sha256ContentAddressableStore.Sha256HashFunction();
        assertThrows(IllegalArgumentException.class, () -> hf.hash(null));
    }

    @Test
    @DisplayName("Sha256HashFunction 直接计算 SHA-256 hex")
    void sha256HashFunctionComputesKnownVector() {
        Sha256ContentAddressableStore.Sha256HashFunction hf =
                new Sha256ContentAddressableStore.Sha256HashFunction();
        String hash = hf.hash("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }

    static final class StubBackend implements IStorageBackendPort {
        final Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        int putCount = 0;

        @Override
        public KvSnapshot load(KvUnitDescriptor descriptor) {
            return new KvSnapshot(tables);
        }

        @Override
        public void put(KvUnitDescriptor descriptor, String table, String key, String value) {
            putCount++;
            tables.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(key, value);
        }

        @Override
        public void remove(KvUnitDescriptor descriptor, String table, String key) {
            Map<String, String> t = tables.get(table);
            if (t != null) {
                t.remove(key);
            }
        }

        @Override
        public void putAll(KvUnitDescriptor descriptor, Map<String, Map<String, String>> puts) {
            puts.forEach((table, kv) -> {
                Map<String, String> t = tables.computeIfAbsent(table, k -> new LinkedHashMap<>());
                t.putAll(kv);
            });
        }
    }
}
