package net.linlan.dsh.domain.device.model;

/**
 * 设备凭据聚合：仅保存 token 摘要，不保存明文。
 *
 * @param deviceId    设备 ID
 * @param deviceName  设备名称
 * @param fingerprint 指纹摘要
 * @param tokenHash   Token 的 SHA-256 摘要
 * @param createdAt   创建时间
 * @param lastSeenAt  最近活跃时间
 */
public record DeviceCredential(
        String deviceId,
        String deviceName,
        String fingerprint,
        String tokenHash,
        String createdAt,
        String lastSeenAt) {
}