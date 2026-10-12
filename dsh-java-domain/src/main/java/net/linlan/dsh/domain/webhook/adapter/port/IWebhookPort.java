package net.linlan.dsh.domain.webhook.adapter.port;

import java.util.Map;

public interface IWebhookPort {

    String receive(String event, String payload, Map<String, String> headers);
}
