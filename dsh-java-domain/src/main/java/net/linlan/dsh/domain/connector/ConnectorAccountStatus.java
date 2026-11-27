package net.linlan.dsh.domain.connector;

import java.time.Instant;
import java.util.Objects;

/** 不含任何访问或刷新令牌的账号快照。 */
public record ConnectorAccountStatus(
        String connectorId,
        ConnectorAccountState state,
        String authorizationUrl,
        Instant expiresAt,
        Instant updatedAt,
        ConnectorDiagnostic diagnostic
) {
    public ConnectorAccountStatus {
        Objects.requireNonNull(connectorId, "connectorId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public boolean connected() {
        return state == ConnectorAccountState.CONNECTED;
    }
}
