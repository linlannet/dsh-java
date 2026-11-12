package net.linlan.dsh.api.dto;

/**
 * 配对成功结果。token 仅在签发时返回一次，服务端只保存哈希。
 *
 * @param deviceId   服务端签发的设备 ID
 * @param deviceName 设备名称
 * @param token      设备级 Bearer Token（仅此一次返回）
 * @param issuedAt   签发时间（ISO-8601）
 */
public record DevicePairResponseDTO(String deviceId, String deviceName, String token, String issuedAt) {
}