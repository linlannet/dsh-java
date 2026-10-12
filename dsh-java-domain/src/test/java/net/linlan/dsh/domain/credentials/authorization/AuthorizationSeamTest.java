package net.linlan.dsh.domain.credentials.authorization;

import net.linlan.dsh.domain.credentials.authorization.adapter.port.ICredentialDialogPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationSeamTest {

    @Test
    void shouldAcquireCredentialViaDialog() {
        StubDialog dialog = new StubDialog("secret-token");
        AuthorizationSeam seam = new AuthorizationSeam(dialog);

        AuthorizationSeam.Credential cred = seam.acquireCredential("github", "repo");

        assertEquals("github", cred.service());
        assertEquals("repo", cred.scope());
        assertEquals("secret-token", cred.value());
    }

    @Test
    void shouldCacheCredentialOnSecondCall() {
        StubDialog dialog = new StubDialog("token");
        AuthorizationSeam seam = new AuthorizationSeam(dialog);

        seam.acquireCredential("svc", "scp");
        seam.acquireCredential("svc", "scp");

        assertEquals(1, dialog.invocationCount.get());
    }

    @Test
    void shouldThrowWhenDialogReturnsEmpty() {
        StubDialog dialog = new StubDialog(null);
        AuthorizationSeam seam = new AuthorizationSeam(dialog);

        assertThrows(IllegalStateException.class, () -> seam.acquireCredential("svc", "scp"));
    }

    @Test
    void shouldRejectNullService() {
        AuthorizationSeam seam = new AuthorizationSeam(new StubDialog("x"));
        assertThrows(IllegalArgumentException.class, () -> seam.acquireCredential(null, "scp"));
    }

    @Test
    void shouldRejectBlankService() {
        AuthorizationSeam seam = new AuthorizationSeam(new StubDialog("x"));
        assertThrows(IllegalArgumentException.class, () -> seam.acquireCredential("  ", "scp"));
    }

    @Test
    void shouldRejectNullScope() {
        AuthorizationSeam seam = new AuthorizationSeam(new StubDialog("x"));
        assertThrows(IllegalArgumentException.class, () -> seam.acquireCredential("svc", null));
    }

    @Test
    void shouldRejectBlankScope() {
        AuthorizationSeam seam = new AuthorizationSeam(new StubDialog("x"));
        assertThrows(IllegalArgumentException.class, () -> seam.acquireCredential("svc", "  "));
    }

    @Test
    void shouldRejectNullDialog() {
        assertThrows(IllegalArgumentException.class, () -> new AuthorizationSeam(null));
    }

    @Test
    void shouldGetCachedCredential() {
        StubDialog dialog = new StubDialog("token");
        AuthorizationSeam seam = new AuthorizationSeam(dialog);

        seam.acquireCredential("svc", "scp");
        Optional<AuthorizationSeam.Credential> cached = seam.getCached("svc", "scp");

        assertTrue(cached.isPresent());
        assertEquals("token", cached.get().value());
    }

    @Test
    void shouldReturnEmptyForMissingCached() {
        AuthorizationSeam seam = new AuthorizationSeam(new StubDialog("x"));
        assertTrue(seam.getCached("svc", "scp").isEmpty());
    }

    @Test
    void shouldInvalidateCredential() {
        StubDialog dialog = new StubDialog("token");
        AuthorizationSeam seam = new AuthorizationSeam(dialog);

        seam.acquireCredential("svc", "scp");
        seam.invalidate("svc", "scp");

        assertTrue(seam.getCached("svc", "scp").isEmpty());
    }

    @Test
    void shouldReacquireAfterInvalidate() {
        StubDialog dialog = new StubDialog("token");
        AuthorizationSeam seam = new AuthorizationSeam(dialog);

        seam.acquireCredential("svc", "scp");
        seam.invalidate("svc", "scp");
        seam.acquireCredential("svc", "scp");

        assertEquals(2, dialog.invocationCount.get());
    }

    static class StubDialog implements ICredentialDialogPort {
        final String value;
        final AtomicInteger invocationCount = new AtomicInteger(0);

        StubDialog(String value) {
            this.value = value;
        }

        @Override
        public Optional<String> promptCredential(String service, String scope) {
            invocationCount.incrementAndGet();
            return Optional.ofNullable(value);
        }
    }
}
