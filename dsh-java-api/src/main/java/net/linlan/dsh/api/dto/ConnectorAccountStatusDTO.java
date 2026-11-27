package net.linlan.dsh.api.dto;

import java.time.Instant;

/** 插件页账号状态；契约中不包含 accessToken、refreshToken 或授权码。 */
public record ConnectorAccountStatusDTO(
        String connectorId,
        String state,
        boolean connected,
        String authorizationUrl,
        Instant expiresAt,
        Instant updatedAt,
        ConnectorDiagnosticDTO diagnostic
) {
}
