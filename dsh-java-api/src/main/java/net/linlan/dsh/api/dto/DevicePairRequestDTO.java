package net.linlan.dsh.api.dto;

/**
 * 移动设备提交配对请求。
 *
 * @param pairingCode 一次性配对码
 * @param deviceName  设备名称
 * @param fingerprint 设备指纹（客户端生成，服务端只存摘要）
 */
public record DevicePairRequestDTO(String pairingCode, String deviceName, String fingerprint) {
}