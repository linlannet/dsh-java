package net.linlan.dsh.api.dto;

/**
 * 小程序会话（短期令牌）。
 *
 * @param token     会话令牌
 * @param expiresAt 过期时间（ISO-8601）
 */
public record MiniAppSessionDTO(String token, String expiresAt) {
}