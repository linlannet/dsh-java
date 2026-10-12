package net.linlan.dsh.infrastructure.adapter.webhook;

import net.linlan.dsh.domain.webhook.WebhookReceiver;
import net.linlan.dsh.domain.webhook.adapter.port.IWebhookPort;

import java.util.Map;
import java.util.Objects;

public final class HttpWebhookReceiver implements IWebhookPort {

    private final WebhookReceiver receiver;

    public HttpWebhookReceiver(WebhookReceiver receiver) {
        this.receiver = Objects.requireNonNull(receiver, "receiver");
    }

    @Override
    public String receive(String event, String payload, Map<String, String> headers) {
        return receiver.receive(event, payload, headers);
    }
}
