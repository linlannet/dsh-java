package net.linlan.dsh.domain.webhook;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class GitHubWebhookHandler {

    public boolean verifySignature(String payload, String signature, String secret) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(secret, "secret");
        if (!signature.startsWith("sha256=")) {
            return false;
        }
        String expected = signature.substring("sha256=".length());
        String actual = hmacSha256Hex(payload, secret);
        return constantTimeEquals(expected, actual);
    }

    public String handle(String event, String payload) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(payload, "payload");
        return "handled: event=" + event + ", payloadSize=" + payload.length();
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("hmac sha256 failed", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
