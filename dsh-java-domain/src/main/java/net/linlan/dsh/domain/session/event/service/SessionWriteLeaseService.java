package net.linlan.dsh.domain.session.event.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class SessionWriteLeaseService {

    private static final Duration MINIMUM_TTL = Duration.ofSeconds(1);

    private final ConcurrentMap<String, Lease> leases = new ConcurrentHashMap<>();

    public Lease acquire(String sessionId, String owner, Duration ttl) {
        require(sessionId, owner, ttl);
        while (true) {
            Instant now = Instant.now();
            Lease lease = new Lease(UUID.randomUUID().toString(), owner, now.plus(ttl));
            Lease previous = leases.putIfAbsent(sessionId, lease);
            if (previous == null) {
                return lease;
            }
            if (previous.isExpiredAt(now) && leases.replace(sessionId, previous, lease)) {
                return lease;
            }
            if (!previous.isExpiredAt(now)) {
                throw conflict(sessionId);
            }
        }
    }

    public Lease renew(String sessionId, String leaseId, Duration ttl) {
        require(sessionId, leaseId, ttl);
        Lease current = leases.get(sessionId);
        if (current == null || !current.leaseId().equals(leaseId) || current.isExpiredAt(Instant.now())) {
            throw conflict(sessionId);
        }
        Lease renewed = new Lease(current.leaseId(), current.owner(), Instant.now().plus(ttl));
        if (!leases.replace(sessionId, current, renewed)) {
            throw conflict(sessionId);
        }
        return renewed;
    }

    public void release(String sessionId, String leaseId) {
        Lease current = leases.get(sessionId);
        if (current != null && current.leaseId().equals(leaseId)) {
            leases.remove(sessionId, current);
        }
    }

    public void requireWritable(String sessionId, String leaseId) {
        Lease current = leases.get(sessionId);
        if (current != null && current.isExpiredAt(Instant.now())) {
            leases.remove(sessionId, current);
            current = null;
        }
        if (current != null && !current.leaseId().equals(leaseId)) {
            throw conflict(sessionId);
        }
    }

    private void require(String sessionId, String value, Duration ttl) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(value, "lease value");
        if (sessionId.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("sessionId and lease value must be non-blank");
        }
        if (ttl == null || ttl.compareTo(MINIMUM_TTL) < 0) {
            throw new IllegalArgumentException("lease TTL must be at least " + MINIMUM_TTL);
        }
    }

    private SessionWriteLeaseConflictException conflict(String sessionId) {
        return new SessionWriteLeaseConflictException("session write lease is held: " + sessionId);
    }

    public record Lease(String leaseId, String owner, Instant expiresAt) {
        public boolean isExpiredAt(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
