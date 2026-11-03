package net.linlan.dsh.domain.bot.model.valobj;

/**
 * 飞书一键配对（设备授权）begin 结果值对象。
 *
 * @param url          扫码授权链接
 * @param userCode     用户码
 * @param deviceCode   设备码（轮询用）
 * @param expireInSec  有效期（秒）
 * @param intervalSec  轮询间隔（秒）
 */
public record FeishuPairingBeginVO(String url, String userCode, String deviceCode, long expireInSec, int intervalSec) {
}
