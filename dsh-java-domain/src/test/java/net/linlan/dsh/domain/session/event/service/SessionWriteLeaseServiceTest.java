package net.linlan.dsh.domain.session.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SessionWriteLeaseServiceTest {

    private final SessionWriteLeaseService service = new SessionWriteLeaseService();

    @Test
    void shouldAllowSingleWriterAndRenewal() {
        SessionWriteLeaseService.Lease lease = service.acquire("session", "agent-a", Duration.ofSeconds(5));
        service.requireWritable("session", lease.leaseId());

        SessionWriteLeaseService.Lease renewed = service.renew("session", lease.leaseId(), Duration.ofSeconds(10));

        assertEquals(lease.leaseId(), renewed.leaseId());
    }

    @Test
    void shouldRejectSecondActiveWriter() {
        SessionWriteLeaseService.Lease lease = service.acquire("session", "agent-a", Duration.ofSeconds(5));

        assertThrows(SessionWriteLeaseConflictException.class,
                () -> service.acquire("session", "agent-b", Duration.ofSeconds(5)));
        assertThrows(SessionWriteLeaseConflictException.class,
                () -> service.requireWritable("session", "other-lease"));
        service.release("session", lease.leaseId());
        service.requireWritable("session", "other-lease");
    }

    @Test
    void shouldExpireLeaseAndAllowNewWriter() throws Exception {
        service.acquire("session", "agent-a", Duration.ofSeconds(1));
        Thread.sleep(1_100);

        SessionWriteLeaseService.Lease renewed = service.acquire("session", "agent-b", Duration.ofSeconds(5));

        service.requireWritable("session", renewed.leaseId());
    }
}
