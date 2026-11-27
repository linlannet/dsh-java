package net.linlan.dsh.infrastructure.adapter.connector;

import net.linlan.dsh.domain.connector.ConnectorTokenGrant;
import net.linlan.dsh.domain.connector.IConnectorTokenCache;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 使用 JDK AES-GCM 的服务端进程内存令牌缓存；Map 中仅保存随机 IV 与密文。 */
public class EncryptedConnectorTokenCache implements IConnectorTokenCache {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_TOKEN_BYTES = 64 * 1024;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, EncryptedGrant> entries = new ConcurrentHashMap<>();

    /** 无配置密钥时使用随机会话密钥；因此令牌不会跨进程恢复。 */
    public EncryptedConnectorTokenCache() {
        this.key = generateKey();
    }

    /** @param base64Key Base64/Base64URL 编码的 256 位 AES 密钥，由服务端配置注入。 */
    public EncryptedConnectorTokenCache(String base64Key) {
        this.key = base64Key == null || base64Key.isBlank() ? generateKey() : decodeKey(base64Key);
    }

    @Override
    public void save(String connectorId, ConnectorTokenGrant tokenGrant) {
        byte[] plaintext = serialize(tokenGrant);
        try {
            entries.put(requireConnectorId(connectorId), new EncryptedGrant(encrypt(connectorId, plaintext)));
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Override
    public Optional<ConnectorTokenGrant> find(String connectorId) {
        EncryptedGrant entry = entries.get(requireConnectorId(connectorId));
        if (entry == null) {
            return Optional.empty();
        }
        byte[] plaintext = decrypt(connectorId, entry.payload());
        try {
            return Optional.of(deserialize(plaintext));
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Override
    public void remove(String connectorId) {
        entries.remove(requireConnectorId(connectorId));
    }

    private String encrypt(String connectorId, byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(connectorId.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("connector token encryption failed", exception);
        }
    }

    private byte[] decrypt(String connectorId, String payload) {
        try {
            byte[] encrypted = Base64.getUrlDecoder().decode(payload);
            if (encrypted.length <= IV_BYTES + TAG_BITS / Byte.SIZE) {
                throw new IllegalStateException("connector token cache entry is invalid");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(encrypted, 0, IV_BYTES)));
            cipher.updateAAD(connectorId.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(encrypted, IV_BYTES, encrypted.length - IV_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("connector token cache entry cannot be decrypted", exception);
        }
    }

    private static byte[] serialize(ConnectorTokenGrant grant) {
        byte[] access = grant.accessToken().getBytes(StandardCharsets.UTF_8);
        byte[] refresh = grant.refreshToken() == null ? new byte[0]
                : grant.refreshToken().getBytes(StandardCharsets.UTF_8);
        if (access.length > MAX_TOKEN_BYTES || refresh.length > MAX_TOKEN_BYTES) {
            throw new IllegalArgumentException("connector token exceeds 64 KiB");
        }
        long expiresAt = grant.expiresAt() == null ? Long.MIN_VALUE : grant.expiresAt().toEpochMilli();
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + access.length + Integer.BYTES + refresh.length + Long.BYTES);
        buffer.putInt(access.length).put(access).putInt(refresh.length).put(refresh).putLong(expiresAt);
        Arrays.fill(access, (byte) 0);
        Arrays.fill(refresh, (byte) 0);
        return buffer.array();
    }

    private static ConnectorTokenGrant deserialize(byte[] plaintext) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(plaintext);
            byte[] access = readBytes(buffer, false);
            byte[] refresh = readBytes(buffer, true);
            long expiresAt = buffer.getLong();
            if (buffer.hasRemaining()) {
                throw new IllegalStateException("connector token cache entry has trailing data");
            }
            String accessToken = new String(access, StandardCharsets.UTF_8);
            String refreshToken = refresh.length == 0 ? null : new String(refresh, StandardCharsets.UTF_8);
            Arrays.fill(access, (byte) 0);
            Arrays.fill(refresh, (byte) 0);
            return new ConnectorTokenGrant(accessToken, refreshToken,
                    expiresAt == Long.MIN_VALUE ? null : Instant.ofEpochMilli(expiresAt));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("connector token cache entry is invalid", exception);
        }
    }

    private static byte[] readBytes(ByteBuffer buffer, boolean nullable) {
        if (buffer.remaining() < Integer.BYTES) {
            throw new IllegalStateException("connector token cache entry is truncated");
        }
        int length = buffer.getInt();
        if (length < 0 || (!nullable && length == 0) || length > MAX_TOKEN_BYTES || length > buffer.remaining()) {
            throw new IllegalStateException("connector token cache entry has invalid token length");
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return value;
    }

    private static SecretKey generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-256 is unavailable", exception);
        }
    }

    private static SecretKey decodeKey(String encoded) {
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            decoded = Base64.getDecoder().decode(encoded);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("connector token encryption key must be a Base64-encoded 256-bit AES key");
        }
        return new SecretKeySpec(decoded, "AES");
    }

    private static String requireConnectorId(String connectorId) {
        if (connectorId == null || connectorId.isBlank()) {
            throw new IllegalArgumentException("connectorId must not be blank");
        }
        return connectorId;
    }

    private record EncryptedGrant(String payload) {
    }
}
