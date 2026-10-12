package net.linlan.dsh.domain.credentials.anonymous;

import net.linlan.dsh.domain.credentials.anonymous.adapter.port.IAnonymousIdStorePort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class AnonymousUserIdProvider {

    private final IAnonymousIdStorePort store;

    public AnonymousUserIdProvider(IAnonymousIdStorePort store) {
        if (store == null) {
            throw new IllegalArgumentException("store required");
        }
        this.store = store;
    }

    public String getOrCreateId(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint required");
        }
        Optional<String> existing = store.findById(fingerprint);
        if (existing.isPresent()) {
            return existing.get();
        }
        String anonymousId = generateStableId(fingerprint);
        store.save(fingerprint, anonymousId);
        return anonymousId;
    }

    public Optional<String> lookup(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }
        return store.findById(fingerprint);
    }

    private String generateStableId(String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return "anon-" + sb.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            return "anon-" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)).toString();
        }
    }
}
