package net.linlan.dsh.domain.connector;

import java.time.Instant;
import java.util.Objects;

/** 仅供服务端账号端口与加密缓存使用的令牌授予。 */
public final class ConnectorTokenGrant {
    private final String accessToken;
    private final String refreshToken;
    private final Instant expiresAt;

    public ConnectorTokenGrant(String accessToken, String refreshToken, Instant expiresAt) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        this.accessToken = accessToken;
        this.refreshToken = refreshToken == null || refreshToken.isBlank() ? null : refreshToken;
        this.expiresAt = expiresAt;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    @Override
    public String toString() {
        return "ConnectorTokenGrant[accessToken=<redacted>, refreshToken=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
