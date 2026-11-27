package net.linlan.dsh.domain.connector;

import java.util.Objects;

/** 连接器端点结果：已授权，或需要用户在 authorizationUrl 完成 OAuth。 */
public final class ConnectorConnection {
    private final ConnectorTokenGrant tokenGrant;
    private final String authorizationUrl;
    private final String authorizationState;

    private ConnectorConnection(ConnectorTokenGrant tokenGrant, String authorizationUrl, String authorizationState) {
        this.tokenGrant = tokenGrant;
        this.authorizationUrl = authorizationUrl;
        this.authorizationState = authorizationState;
    }

    public static ConnectorConnection authorized(ConnectorTokenGrant tokenGrant) {
        return new ConnectorConnection(Objects.requireNonNull(tokenGrant, "tokenGrant"), null, null);
    }

    public static ConnectorConnection authorizationRequired(String authorizationUrl, String authorizationState) {
        if (authorizationUrl == null || authorizationUrl.isBlank()) {
            throw new IllegalArgumentException("authorizationUrl must not be blank");
        }
        if (authorizationState == null || authorizationState.isBlank()) {
            throw new IllegalArgumentException("authorizationState must not be blank");
        }
        return new ConnectorConnection(null, authorizationUrl, authorizationState);
    }

    public boolean authorizationRequired() {
        return tokenGrant == null;
    }

    public ConnectorTokenGrant tokenGrant() {
        return tokenGrant;
    }

    public String authorizationUrl() {
        return authorizationUrl;
    }

    public String authorizationState() {
        return authorizationState;
    }

    @Override
    public String toString() {
        return authorizationRequired()
                ? "ConnectorConnection[authorizationRequired=true]"
                : "ConnectorConnection[authorizationRequired=false, tokenGrant=<redacted>]";
    }
}
