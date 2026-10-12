package net.linlan.dsh.domain.sdk.spill;

import net.linlan.dsh.domain.sdk.spill.adapter.port.ISpillStorePort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;

public final class SpillStore {

    public record SpillRef(String refId, int originalSize, Instant storedAt) {
        public SpillRef {
            Objects.requireNonNull(refId, "refId");
            Objects.requireNonNull(storedAt, "storedAt");
        }
    }

    private final ISpillStorePort port;

    public SpillStore(ISpillStorePort port) {
        if (port == null) {
            throw new IllegalArgumentException("port required");
        }
        this.port = port;
    }

    public SpillRef spill(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content required");
        }
        String refId = sha256(content);
        if (!port.exists(refId)) {
            port.save(refId, content);
        }
        return new SpillRef(refId, content.length(), Instant.now());
    }

    public String retrieve(String refId) {
        if (refId == null || refId.isBlank()) {
            throw new IllegalArgumentException("refId required");
        }
        String content = port.load(refId);
        if (content == null) {
            throw new IllegalStateException("spill content not found: " + refId);
        }
        return content;
    }

    public boolean exists(String refId) {
        return refId != null && !refId.isBlank() && port.exists(refId);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
