package net.linlan.dsh.api.gateway.dto;

import java.time.Instant;
import java.util.Map;

public record GatewayStreamEventDTO(
        String source,
        String event,
        Instant occurredAt,
        Object payload
) {
    public static GatewayStreamEventDTO of(String source, String event, Object payload) {
        return new GatewayStreamEventDTO(source, event, Instant.now(), payload);
    }

    public GatewayStreamEventDTO {
        if (payload == null) {
            payload = Map.of();
        }
    }
}
