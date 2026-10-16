package net.linlan.dsh.domain.session.search.service;

import net.linlan.dsh.domain.session.search.adapter.port.ISessionSearchIndexPort;
import net.linlan.dsh.domain.session.search.model.valobj.SearchHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IndexGovernanceService 索引治理")
class IndexGovernanceServiceTest {

    @Test
    @DisplayName("cleanupExpired 按 TTL 清理过期文档")
    void cleanupExpiredByTtl() {
        var port = new TrackingPort();
        port.count = 10;
        var svc = new IndexGovernanceService(port, Duration.ofHours(1), 0);

        int removed = svc.cleanupExpired();
        assertTrue(removed >= 0);
        assertTrue(port.removeOlderThanCalled);
    }

    @Test
    @DisplayName("cleanupExpired TTL 为零时不清理")
    void cleanupExpiredNoTtl() {
        var port = new TrackingPort();
        port.count = 10;
        var svc = new IndexGovernanceService(port, Duration.ZERO, 0);

        int removed = svc.cleanupExpired();
        assertEquals(0, removed);
        assertFalse(port.removeOlderThanCalled);
    }

    @Test
    @DisplayName("enforceCapacity 超限时驱逐超额文档")
    void enforceCapacityEvicts() {
        var port = new TrackingPort();
        port.count = 100;
        var svc = new IndexGovernanceService(port, Duration.ZERO, 50);

        int evicted = svc.enforceCapacity();
        assertEquals(50, evicted);
        assertTrue(port.removeOldestCalled);
        assertEquals(50, port.removeOldestCount);
    }

    @Test
    @DisplayName("enforceCapacity 未超限时不驱逐")
    void enforceCapacityNoEviction() {
        var port = new TrackingPort();
        port.count = 30;
        var svc = new IndexGovernanceService(port, Duration.ZERO, 50);

        int evicted = svc.enforceCapacity();
        assertEquals(0, evicted);
        assertFalse(port.removeOldestCalled);
    }

    @Test
    @DisplayName("runGovernance 同时执行 TTL 清理与容量驱逐")
    void runGovernance() {
        var port = new TrackingPort();
        port.count = 100;
        var svc = new IndexGovernanceService(port, Duration.ofHours(1), 50);

        int total = svc.runGovernance();
        assertTrue(total > 0);
        assertTrue(port.removeOlderThanCalled);
        assertTrue(port.removeOldestCalled);
    }

    @Test
    @DisplayName("isOverCapacity 判断是否超限")
    void isOverCapacity() {
        var port = new TrackingPort();
        port.count = 60;
        var svc = new IndexGovernanceService(port, Duration.ZERO, 50);

        assertTrue(svc.isOverCapacity());

        port.count = 40;
        assertFalse(svc.isOverCapacity());
    }

    @Test
    @DisplayName("documentCount 返回当前文档数")
    void documentCount() {
        var port = new TrackingPort();
        port.count = 42;
        var svc = new IndexGovernanceService(port, Duration.ZERO, 0);

        assertEquals(42, svc.documentCount());
    }

    private static class TrackingPort implements ISessionSearchIndexPort {
        int count = 0;
        boolean removeOlderThanCalled = false;
        boolean removeOldestCalled = false;
        int removeOldestCount = 0;

        @Override
        public void index(String sessionId, long seq, String content) {}

        @Override
        public List<SearchHit> search(String query, int limit) { return List.of(); }

        @Override
        public void clear() { count = 0; }

        @Override
        public void remove(String sessionId) { count--; }

        @Override
        public void removeOlderThan(Instant threshold) {
            removeOlderThanCalled = true;
            count = Math.max(0, count - 5);
        }

        @Override
        public void removeOldest(int count) {
            removeOldestCalled = true;
            removeOldestCount = count;
            this.count = Math.max(0, this.count - count);
        }

        @Override
        public int documentCount() { return count; }
    }
}
