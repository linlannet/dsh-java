package net.linlan.dsh.api.dto;

/**
 * 飞书一键配对状态对象。
 *
 * @param pairingId 配对会话 ID
 * @param status    状态（WAITING / SAVED / FAILED / EXPIRED / CANCELLED）
 * @param url       扫码授权链接
 * @param userCode  用户码
 * @param expireIn  剩余有效期（秒）
 * @param botId     绑定成功后创建的 Bot ID
 * @param appId     绑定成功后的 app_id
 * @param error     错误信息
 */
public record FeishuPairingDTO(String pairingId, String status, String url, String userCode, Long expireIn,
                               String botId, String appId, String error) {
}
