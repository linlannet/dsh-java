package net.linlan.dsh.domain.webhook.github;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Map;
import java.util.Objects;

/**
 * GitHubWebhookAdapter 复用既有 GitHubWebhookHandler 骨架，注册精确 HTTP 路由。
 * 实验性扩展，默认关闭。
 */
public class GitHubWebhookAdapter {

    public boolean verifySignature(String secret, String body, String signatureHeader) {
        Objects.requireNonNull(secret, "secret 不能为空");
        Objects.requireNonNull(body, "body 不能为空");
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] computed = mac.doFinal(body.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : computed) hex.append(String.format("%02x", b));
            String expected = "sha256=" + hex;
            return expected.equals(signatureHeader);
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> projectDelivery(Map<String, Object> githubEvent) {
        Objects.requireNonNull(githubEvent, "githubEvent 不能为空");
        return Map.of(
                "event", githubEvent.getOrDefault("action", ""),
                "repository", githubEvent.getOrDefault("repository", Map.of()),
                "sender", githubEvent.getOrDefault("sender", Map.of())
        );
    }
}
