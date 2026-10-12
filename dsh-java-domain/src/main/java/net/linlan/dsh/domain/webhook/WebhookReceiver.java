package net.linlan.dsh.domain.webhook;

import java.util.Map;
import java.util.Objects;

public final class WebhookReceiver {

    private final GitHubWebhookHandler handler;

    public WebhookReceiver(GitHubWebhookHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public String receive(String event, String payload, Map<String, String> headers) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(headers, "headers");
        String signature = headers.get("X-Hub-Signature-256");
        if (signature == null) {
            signature = headers.get("x-hub-signature-256");
        }
        if (signature == null) {
            return "rejected: missing signature";
        }
        String secret = headers.getOrDefault("X-Webhook-Secret", headers.getOrDefault("x-webhook-secret", ""));
        if (!handler.verifySignature(payload, signature, secret)) {
            return "rejected: invalid signature";
        }
        return handler.handle(event, payload);
    }
}
