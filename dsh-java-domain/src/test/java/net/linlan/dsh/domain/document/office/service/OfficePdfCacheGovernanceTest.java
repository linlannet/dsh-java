package net.linlan.dsh.domain.document.office.service;

import net.linlan.dsh.domain.document.office.adapter.port.IOfficePdfCachePort;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeToPdfResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OfficePdfCacheGovernance 缓存容量治理")
class OfficePdfCacheGovernanceTest {

    private IOfficePdfCachePort inMemoryPort() {
        Map<String, OfficeToPdfResult> store = new HashMap<>();
        return new IOfficePdfCachePort() {
            @Override
            public OfficeToPdfResult load(String key) {
                return store.get(key);
            }
            @Override
            public void save(String key, OfficeToPdfResult result) {
                store.put(key, result);
            }
            @Override
            public void remove(String key) {
                store.remove(key);
            }
            @Override
            public void clear() {
                store.clear();
            }
            @Override
            public int size() {
                return store.size();
            }
        };
    }

    private OfficeToPdfResult sampleResult() {
        return OfficeToPdfResult.of(new byte[]{1, 2, 3}, List.of());
    }

    @Test
    @DisplayName("put 和 get 正常工作")
    void putAndGet() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), 10);
        governance.put("key1", sampleResult());
        assertNotNull(governance.get("key1"));
    }

    @Test
    @DisplayName("get 不存在的 key 返回 null")
    void getNonExistentReturnsNull() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), 10);
        assertNull(governance.get("nonexistent"));
    }

    @Test
    @DisplayName("get 空白 key 返回 null")
    void getBlankKeyReturnsNull() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), 10);
        assertNull(governance.get(""));
        assertNull(governance.get(null));
    }

    @Test
    @DisplayName("容量满时驱逐最旧条目")
    void evictsOldestWhenFull() {
        var port = inMemoryPort();
        var governance = new OfficePdfCacheGovernance(port, 3);
        governance.put("k1", sampleResult());
        governance.put("k2", sampleResult());
        governance.put("k3", sampleResult());
        assertEquals(3, governance.size());
        governance.put("k4", sampleResult());
        assertEquals(3, governance.size());
        assertNull(governance.get("k1"));
        assertNotNull(governance.get("k4"));
    }

    @Test
    @DisplayName("put 同名 key 不触发驱逐")
    void putSameKeyDoesNotEvict() {
        var port = inMemoryPort();
        var governance = new OfficePdfCacheGovernance(port, 2);
        governance.put("k1", sampleResult());
        governance.put("k2", sampleResult());
        governance.put("k1", sampleResult());
        assertEquals(2, governance.size());
        assertNotNull(governance.get("k1"));
        assertNotNull(governance.get("k2"));
    }

    @Test
    @DisplayName("remove 删除条目")
    void removeEntry() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), 10);
        governance.put("k1", sampleResult());
        governance.remove("k1");
        assertNull(governance.get("k1"));
        assertEquals(0, governance.size());
    }

    @Test
    @DisplayName("clear 清空所有条目")
    void clearAll() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), 10);
        governance.put("k1", sampleResult());
        governance.put("k2", sampleResult());
        governance.clear();
        assertEquals(0, governance.size());
        assertNull(governance.get("k1"));
    }

    @Test
    @DisplayName("isFull 在容量满时返回 true")
    void isFullWhenCapacityReached() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), 2);
        assertFalse(governance.isFull());
        governance.put("k1", sampleResult());
        assertFalse(governance.isFull());
        governance.put("k2", sampleResult());
        assertTrue(governance.isFull());
    }

    @Test
    @DisplayName("maxEntries 返回最大条目数")
    void maxEntriesReturnsConfig() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), 50);
        assertEquals(50, governance.maxEntries());
    }

    @Test
    @DisplayName("默认最大条目数为 100")
    void defaultMaxEntriesIs100() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort());
        assertEquals(100, governance.maxEntries());
    }

    @Test
    @DisplayName("负数 maxEntries 回退到 100")
    void negativeMaxEntriesFallsBack() {
        var governance = new OfficePdfCacheGovernance(inMemoryPort(), -1);
        assertEquals(100, governance.maxEntries());
    }

    @Test
    @DisplayName("LRU 驱逐顺序正确")
    void lruEvictionOrder() {
        var port = inMemoryPort();
        var governance = new OfficePdfCacheGovernance(port, 3);
        governance.put("a", sampleResult());
        governance.put("b", sampleResult());
        governance.put("c", sampleResult());
        governance.get("a");
        governance.put("d", sampleResult());
        assertNull(governance.get("b"));
        assertNotNull(governance.get("a"));
        assertNotNull(governance.get("c"));
        assertNotNull(governance.get("d"));
    }
}
