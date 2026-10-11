package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

public record DigitalHumanEndpointPO(String id, String digitalHumanId, String endpointType, String baseUrl, String credentialRef, String protocolVersion, String agentCard, String healthState, Instant lastCheckedAt, Integer latencyMs) {
}

