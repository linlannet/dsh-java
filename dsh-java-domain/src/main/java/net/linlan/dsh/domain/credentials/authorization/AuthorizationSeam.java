package net.linlan.dsh.domain.credentials.authorization;

import net.linlan.dsh.domain.credentials.authorization.adapter.port.ICredentialDialogPort;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AuthorizationSeam {

    private final ICredentialDialogPort dialogPort;
    private final Map<String, Credential> cache;

    public AuthorizationSeam(ICredentialDialogPort dialogPort) {
        if (dialogPort == null) {
            throw new IllegalArgumentException("dialog port required");
        }
        this.dialogPort = dialogPort;
        this.cache = new ConcurrentHashMap<>();
    }

    public Credential acquireCredential(String service, String scope) {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service required");
        }
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope required");
        }
        String cacheKey = cacheKey(service, scope);
        Credential cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }
        Optional<String> prompted = dialogPort.promptCredential(service, scope);
        if (prompted.isEmpty()) {
            throw new IllegalStateException("credential not available for service: " + service + ", scope: " + scope);
        }
        Credential credential = new Credential(service, scope, prompted.get(), Instant.now().plusSeconds(3600));
        cache.put(cacheKey, credential);
        return credential;
    }

    public Optional<Credential> getCached(String service, String scope) {
        Objects.requireNonNull(service, "service required");
        Objects.requireNonNull(scope, "scope required");
        return Optional.ofNullable(cache.get(cacheKey(service, scope)));
    }

    public void invalidate(String service, String scope) {
        cache.remove(cacheKey(service, scope));
    }

    private String cacheKey(String service, String scope) {
        return service + "::" + scope;
    }

    public record Credential(String service, String scope, String value, Instant expiresAt) {
        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }
}
