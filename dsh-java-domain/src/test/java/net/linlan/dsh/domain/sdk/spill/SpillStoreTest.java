package net.linlan.dsh.domain.sdk.spill;

import net.linlan.dsh.domain.sdk.spill.adapter.port.ISpillStorePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.034 SpillStore 溢出存储")
class SpillStoreTest {

    @Test
    @DisplayName("spill 返回含 refId 与 originalSize 的 SpillRef")
    void spillReturnsRef() {
        var port = new InMemorySpillPort();
        var store = new SpillStore(port);
        var ref = store.spill("hello world");
        assertNotNull(ref.refId());
        assertEquals(11, ref.originalSize());
        assertNotNull(ref.storedAt());
    }

    @Test
    @DisplayName("相同内容 spill 返回相同 refId")
    void sameContentSameRefId() {
        var port = new InMemorySpillPort();
        var store = new SpillStore(port);
        var ref1 = store.spill("same content");
        var ref2 = store.spill("same content");
        assertEquals(ref1.refId(), ref2.refId());
    }

    @Test
    @DisplayName("retrieve 返回原始内容")
    void retrieveReturnsContent() {
        var port = new InMemorySpillPort();
        var store = new SpillStore(port);
        var ref = store.spill("retrieve me");
        assertEquals("retrieve me", store.retrieve(ref.refId()));
    }

    @Test
    @DisplayName("retrieve 未知 refId 抛异常")
    void retrieveUnknownThrows() {
        var port = new InMemorySpillPort();
        var store = new SpillStore(port);
        assertThrows(IllegalStateException.class, () -> store.retrieve("nonexistent"));
    }

    @Test
    @DisplayName("retrieve null refId 抛异常")
    void retrieveNullThrows() {
        var port = new InMemorySpillPort();
        var store = new SpillStore(port);
        assertThrows(IllegalArgumentException.class, () -> store.retrieve(null));
    }

    @Test
    @DisplayName("spill null content 抛异常")
    void spillNullThrows() {
        var port = new InMemorySpillPort();
        var store = new SpillStore(port);
        assertThrows(IllegalArgumentException.class, () -> store.spill(null));
    }

    @Test
    @DisplayName("exists 返回已存储内容状态")
    void existsChecksStored() {
        var port = new InMemorySpillPort();
        var store = new SpillStore(port);
        var ref = store.spill("exists check");
        assertTrue(store.exists(ref.refId()));
        assertFalse(store.exists("nonexistent"));
    }

    @Test
    @DisplayName("null port 抛异常")
    void nullPortThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SpillStore(null));
    }

    static final class InMemorySpillPort implements ISpillStorePort {
        final Map<String, String> map = new HashMap<>();

        @Override
        public void save(String refId, String content) {
            map.put(refId, content);
        }

        @Override
        public String load(String refId) {
            return map.get(refId);
        }

        @Override
        public boolean exists(String refId) {
            return map.containsKey(refId);
        }
    }
}
