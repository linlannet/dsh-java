package net.linlan.dsh.plugin.emis;

import java.time.Instant;
import java.util.List;

/** 验签后的身份声明（不含原始令牌）。 */
public record TokenClaims(String subject, Long tenantId, Integer roleLevel, String userName,
                          List<String> scopes, String issuer, Instant expiresAt) {
}
