package net.linlan.dsh.domain.bot.model.valobj;

/**
 * 飞书一键配对（设备授权）轮询结果值对象。
 *
 * @param clientId        应用 app_id
 * @param clientSecret    应用 app_secret（仅内存传递，不落库）
 * @param openId          授权用户 open_id
 * @param tenantBrand     租户品牌（feishu / lark）
 * @param error           错误码（authorization_pending / slow_down / access_denied / expired_token 等）
 * @param errorDescription 错误描述
 */
public record FeishuPairingPollVO(String clientId, String clientSecret, String openId, String tenantBrand,
                                  String error, String errorDescription) {

    /**
     * 轮询是否已成功拿到凭证。
     *
     * @return 无错误且 clientId / clientSecret 均非空时返回 true
     */
    public boolean succeeded() {
        return this.error == null && this.clientId != null && !this.clientId.isBlank()
                && this.clientSecret != null && !this.clientSecret.isBlank();
    }
}
