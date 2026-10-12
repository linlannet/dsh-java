package net.linlan.dsh.domain.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitHubWebhookHandler 签名校验与处理")
class GitHubWebhookHandlerTest {

    @Test
    @DisplayName("verifySignature 正确签名返回 true")
    void verifySignatureCorrect() {
        var handler = new GitHubWebhookHandler();
        String payload = "{\"action\":\"opened\"}";
        String secret = "topsecret";
        String sig = "sha256=" + hmacSha256Hex(payload, secret);
        assertTrue(handler.verifySignature(payload, sig, secret));
    }

    @Test
    @DisplayName("verifySignature 错误签名返回 false")
    void verifySignatureWrong() {
        var handler = new GitHubWebhookHandler();
        String payload = "{\"action\":\"opened\"}";
        assertTrue(handler.verifySignature(payload, "sha256=deadbeef", "secret") == false);
    }

    @Test
    @DisplayName("verifySignature 缺少 sha256= 前缀返回 false")
    void verifySignatureMissingPrefix() {
        var handler = new GitHubWebhookHandler();
        assertFalse(handler.verifySignature("payload", "abcdef", "secret"));
    }

    @Test
    @DisplayName("verifySignature 不同密钥返回 false")
    void verifySignatureDifferentSecret() {
        var handler = new GitHubWebhookHandler();
        String payload = "hello";
        String sig = "sha256=" + hmacSha256Hex(payload, "right");
        assertFalse(handler.verifySignature(payload, sig, "wrong"));
    }

    @Test
    @DisplayName("verifySignature null 参数抛出 NPE")
    void verifySignatureNullArgs() {
        var handler = new GitHubWebhookHandler();
        assertThrows(NullPointerException.class, () -> handler.verifySignature(null, "s", "k"));
        assertThrows(NullPointerException.class, () -> handler.verifySignature("p", null, "k"));
        assertThrows(NullPointerException.class, () -> handler.verifySignature("p", "s", null));
    }

    @Test
    @DisplayName("handle 返回事件与负载大小")
    void handleReturnsEventAndSize() {
        var handler = new GitHubWebhookHandler();
        String result = handler.handle("push", "{}");
        assertTrue(result.contains("event=push"));
        assertTrue(result.contains("payloadSize=2"));
    }

    @Test
    @DisplayName("handle null 参数抛出 NPE")
    void handleNullArgs() {
        var handler = new GitHubWebhookHandler();
        assertThrows(NullPointerException.class, () -> handler.handle(null, "p"));
        assertThrows(NullPointerException.class, () -> handler.handle("e", null));
    }

    @Test
    @DisplayName("WebhookReceiver 缺少签名头返回 rejected")
    void receiverMissingSignature() {
        var handler = new GitHubWebhookHandler();
        var receiver = new WebhookReceiver(handler);
        String result = receiver.receive("push", "{}", Map.of());
        assertEquals("rejected: missing signature", result);
    }

    @Test
    @DisplayName("WebhookReceiver 无效签名返回 rejected")
    void receiverInvalidSignature() {
        var handler = new GitHubWebhookHandler();
        var receiver = new WebhookReceiver(handler);
        String result = receiver.receive("push", "{}",
                Map.of("X-Hub-Signature-256", "sha256=bad", "X-Webhook-Secret", "k"));
        assertEquals("rejected: invalid signature", result);
    }

    @Test
    @DisplayName("WebhookReceiver 有效签名委派 handler")
    void receiverValidSignatureDelegates() {
        var handler = new GitHubWebhookHandler();
        var receiver = new WebhookReceiver(handler);
        String payload = "{\"action\":\"opened\"}";
        String secret = "s3cret";
        String sig = "sha256=" + hmacSha256Hex(payload, secret);
        String result = receiver.receive("pull_request", payload,
                Map.of("X-Hub-Signature-256", sig, "X-Webhook-Secret", secret));
        assertTrue(result.startsWith("handled:"));
        assertTrue(result.contains("event=pull_request"));
    }

    @Test
    @DisplayName("WebhookReceiver 小写签名头也可识别")
    void receiverLowercaseHeader() {
        var handler = new GitHubWebhookHandler();
        var receiver = new WebhookReceiver(handler);
        String payload = "data";
        String secret = "k";
        String sig = "sha256=" + hmacSha256Hex(payload, secret);
        String result = receiver.receive("push", payload,
                Map.of("x-hub-signature-256", sig, "x-webhook-secret", secret));
        assertTrue(result.startsWith("handled:"));
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
