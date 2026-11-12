package net.linlan.dsh.api.dto;

/**
 * 已配对设备视图（不含任何凭据）。
 *
 * @param deviceId    设备 ID
 * @param deviceName  设备名称
 * @param fingerprint 指纹摘要
 * @param createdAt   创建时间
 * @param lastSeenAt  最近活跃时间
 */
public record DeviceDTO(String deviceId, String deviceName, String fingerprint, String createdAt, String lastSeenAt) {
}