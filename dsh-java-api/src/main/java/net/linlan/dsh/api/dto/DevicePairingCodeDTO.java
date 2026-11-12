package net.linlan.dsh.api.dto;

/**
 * 桌面端生成的移动设备配对码。
 *
 * @param pairingCode      一次性配对码
 * @param expiresAt        过期时间（ISO-8601）
 * @param expiresInSeconds 有效秒数
 */
public record DevicePairingCodeDTO(String pairingCode, String expiresAt, long expiresInSeconds) {
}