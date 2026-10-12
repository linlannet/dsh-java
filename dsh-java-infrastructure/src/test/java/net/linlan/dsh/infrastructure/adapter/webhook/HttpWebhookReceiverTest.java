package net.linlan.dsh.infrastructure.adapter.webhook;

import net.linlan.dsh.domain.webhook.GitHubWebhookHandler;
import net.linlan.dsh.domain.webhook.WebhookReceiver;
import net.linlan.dsh.domain.webhook.adapter.port.IWebhookPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpWebhookReceiverTest {

    private WebhookReceiver receiver;
    private HttpWebhookReceiver http;

    @BeforeEach
    void setUp() {
        receiver = new WebhookReceiver(new GitHubWebhookHandler());
        http = new HttpWebhookReceiver(receiver);
    }

    @Test
    void implementsWebhookPort() {
        assertTrue(http instanceof IWebhookPort);
    }

    @Test
    void nullReceiverThrowsNpe() {
        assertThrows(NullPointerException.class, () -> new HttpWebhookReceiver(null));
    }

    @Test
    void receiveRejectsMissingSignature() {
        String result = http.receive("push", "{}", Map.of());
        assertEquals("rejected: missing signature", result);
    }

    @Test
    void receiveRejectsInvalidSignature() {
        String result = http.receive("push", "{}", Map.of(
                "X-Hub-Signature-256", "sha256=invalid",
                "X-Webhook-Secret", "any-secret"));
        assertEquals("rejected: invalid signature", result);
    }

    @Test
    void receiveRejectsSignatureWithoutPrefix() {
        String result = http.receive("push", "{}", Map.of(
                "X-Hub-Signature-256", "deadbeef",
                "X-Webhook-Secret", "any-secret"));
        assertEquals("rejected: invalid signature", result);
    }

    @Test
    void receiveAcceptsValidSignature() {
        String payload = "{}";
        String secret = "test-secret";
        String sig = "sha256=" + hmacSha256Hex(payload, secret);
        String result = http.receive("push", payload, Map.of(
                "X-Hub-Signature-256", sig,
                "X-Webhook-Secret", secret));
        assertTrue(result.startsWith("handled: event=push"));
        assertTrue(result.contains("payloadSize=" + payload.length()));
    }

    @Test
    void receiveUsesLowercaseHeaderKeys() {
        String result = http.receive("push", "{}", Map.of(
                "x-hub-signature-256", "sha256=bad",
                "x-webhook-secret", "any-secret"));
        assertEquals("rejected: invalid signature", result);
    }

    @Test
    void receiveHandlesEventWithValidSignature() {
        String payload = "{\"action\":\"opened\"}";
        String secret = "s";
        String sig = "sha256=" + hmacSha256Hex(payload, secret);
        String result = http.receive("pull_request", payload, Map.of(
                "X-Hub-Signature-256", sig,
                "X-Webhook-Secret", secret));
        assertTrue(result.startsWith("handled: event=pull_request"));
    }

    @Test
    void receiveRejectsMissingSignatureWithLowercaseLookup() {
        String result = http.receive("push", "{}", Map.of("X-Webhook-Secret", "s"));
        assertEquals("rejected: missing signature", result);
    }

    private static String hmacSha256Hex(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
