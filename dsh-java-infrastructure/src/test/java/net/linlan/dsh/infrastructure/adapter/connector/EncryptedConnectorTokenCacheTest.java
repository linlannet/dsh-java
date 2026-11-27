package net.linlan.dsh.infrastructure.adapter.connector;

import net.linlan.dsh.domain.connector.ConnectorTokenGrant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedConnectorTokenCacheTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void storesOnlyCiphertextAndRoundTripsGrant() throws Exception {
        EncryptedConnectorTokenCache cache = new EncryptedConnectorTokenCache(KEY);
        Instant expiresAt = Instant.parse("2026-11-27T12:00:00Z");
        ConnectorTokenGrant grant = new ConnectorTokenGrant("access-plaintext-9182", "refresh-plaintext-2718", expiresAt);

        cache.save("notion", grant);
        String firstCiphertext = ciphertext(cache, "notion");
        assertFalse(firstCiphertext.contains("access-plaintext-9182"));
        assertFalse(firstCiphertext.contains("refresh-plaintext-2718"));

        ConnectorTokenGrant restored = cache.find("notion").orElseThrow();
        assertEquals("access-plaintext-9182", restored.accessToken());
        assertEquals("refresh-plaintext-2718", restored.refreshToken());
        assertEquals(expiresAt, restored.expiresAt());

        cache.save("notion", grant);
        assertNotEquals(firstCiphertext, ciphertext(cache, "notion"), "GCM 每次写入必须使用随机 IV");
        cache.remove("notion");
        assertTrue(cache.find("notion").isEmpty());
    }

    @Test
    void aadPreventsMovingCiphertextToAnotherConnector() throws Exception {
        EncryptedConnectorTokenCache cache = new EncryptedConnectorTokenCache(KEY);
        cache.save("notion", new ConnectorTokenGrant("secret", null, null));
        Map<String, Object> entries = entries(cache);
        entries.put("linear", entries.remove("notion"));

        assertThrows(IllegalStateException.class, () -> cache.find("linear"));
    }

    @Test
    void rejectsInvalidEncryptionKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new EncryptedConnectorTokenCache(Base64.getEncoder().encodeToString(new byte[16])));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> entries(EncryptedConnectorTokenCache cache) throws Exception {
        Field field = EncryptedConnectorTokenCache.class.getDeclaredField("entries");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(cache);
    }

    private static String ciphertext(EncryptedConnectorTokenCache cache, String connectorId) throws Exception {
        Object entry = entries(cache).get(connectorId);
        Field field = entry.getClass().getDeclaredField("payload");
        field.setAccessible(true);
        return (String) field.get(entry);
    }
}
