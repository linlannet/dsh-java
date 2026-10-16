package net.linlan.dsh.domain.session.search.service;

import net.linlan.dsh.domain.session.search.adapter.port.ISessionSearchIndexPort;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * IndexGovernanceService 提供 Lucene 索引的容量治理与清理策略。
 *
 * <p>支持 TTL 自动过期清理与容量水位驱逐，缓解单机本地索引的磁盘占用与多实例不共享限制。</p>
 */
public final class IndexGovernanceService {

    private final ISessionSearchIndexPort port;
    private final Duration ttl;
    private final int maxDocuments;

    public IndexGovernanceService(ISessionSearchIndexPort port, Duration ttl, int maxDocuments) {
        this.port = Objects.requireNonNull(port, "port 不能为空");
        this.ttl = ttl != null ? ttl : Duration.ZERO;
        this.maxDocuments = maxDocuments;
    }

    public int cleanupExpired() {
        if (ttl.isZero() || ttl.isNegative()) return 0;
        int before = port.documentCount();
        Instant threshold = Instant.now().minus(ttl);
        port.removeOlderThan(threshold);
        return before - port.documentCount();
    }

    public int enforceCapacity() {
        if (maxDocuments <= 0) return 0;
        int current = port.documentCount();
        if (current <= maxDocuments) return 0;
        int excess = current - maxDocuments;
        port.removeOldest(excess);
        return excess;
    }

    public int runGovernance() {
        int expired = cleanupExpired();
        int evicted = enforceCapacity();
        return expired + evicted;
    }

    public int documentCount() {
        return port.documentCount();
    }

    public boolean isOverCapacity() {
        return maxDocuments > 0 && port.documentCount() > maxDocuments;
    }
}
