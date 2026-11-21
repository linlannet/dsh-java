package net.linlan.dsh.plugin.emis;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmisTokenVerifierTest {

    private static HttpServer server;
    private static String baseUrl;
    private static KeyPair keyPair;

    @BeforeAll
    static void startJwksServer() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String n = b64(unsigned(publicKey.getModulus().toByteArray()));
        String e = b64(unsigned(publicKey.getPublicExponent().toByteArray()));
        String body = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test-kid\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/jwks", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifiesValidRs256Token() {
        long exp = Instant.now().plusSeconds(300).getEpochSecond();
        String token = token("RS256", "test-kid", exp,
                "{\"sub\":\"7\",\"tenant_id\":2,\"role_level\":2,\"user_name\":\"alice\","
                        + "\"scope\":[\"openid\",\"binding:read\"],\"iss\":\"" + baseUrl + "\"}");

        TokenClaims claims = verifier().verify(token);

        assertEquals("7", claims.subject());
        assertEquals(2L, claims.tenantId());
        assertEquals(2, claims.roleLevel());
        assertEquals("alice", claims.userName());
        assertTrue(claims.scopes().contains("binding:read"));
    }

    @Test
    void rejectsHs256Token() {
        String token = token("HS256", "test-kid", Instant.now().plusSeconds(300).getEpochSecond(),
                "{\"sub\":\"7\",\"iss\":\"" + baseUrl + "\",\"scope\":\"openid\"}");

        TokenException ex = assertThrows(TokenException.class, () -> verifier().verify(token));
        assertEquals("EMIS_TOKEN_INVALID", ex.getCode());
    }

    @Test
    void rejectsExpiredToken() {
        String token = token("RS256", "test-kid", Instant.now().minusSeconds(10).getEpochSecond(),
                "{\"sub\":\"7\",\"iss\":\"" + baseUrl + "\",\"scope\":[\"openid\"]}");

        TokenException ex = assertThrows(TokenException.class, () -> verifier().verify(token));
        assertEquals("EMIS_TOKEN_EXPIRED", ex.getCode());
    }

    private static EmisTokenVerifier verifier() {
        return new EmisTokenVerifier(new EmisConfig(baseUrl, baseUrl, "/oauth2/jwks", 3000, 600_000));
    }

    private static String token(String alg, String kid, long exp, String payloadJson) {
        String header = "{\"alg\":\"" + alg + "\",\"kid\":\"" + kid + "\"}";
        String payload = payloadJson.replaceFirst("\\{", "{\"exp\":" + exp + ",");
        String signingInput = b64(header.getBytes(StandardCharsets.UTF_8)) + "."
                + b64(payload.getBytes(StandardCharsets.UTF_8));
        try {
            if (!"RS256".equals(alg)) {
                return signingInput + "." + b64(new byte[]{1, 2, 3});
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] unsigned(byte[] value) {
        if (value.length > 1 && value[0] == 0) {
            byte[] trimmed = new byte[value.length - 1];
            System.arraycopy(value, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return value;
    }

    private static String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}