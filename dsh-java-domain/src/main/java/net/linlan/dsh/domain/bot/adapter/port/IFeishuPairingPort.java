package net.linlan.dsh.domain.bot.adapter.port;

import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingBeginVO;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingPollVO;

/**
 * 飞书一键配对（设备授权）端口（由基础设施层适配）。
 */
public interface IFeishuPairingPort {

    /**
     * 发起设备授权。
     *
     * @param appName 应用名称
     * @param appDesc 应用描述
     * @return begin 结果
     */
    FeishuPairingBeginVO begin(String appName, String appDesc);

    /**
     * 轮询设备授权结果。
     *
     * @param baseUrl    授权服务基址
     * @param deviceCode 设备码
     * @return 轮询结果
     */
    FeishuPairingPollVO poll(String baseUrl, String deviceCode);

    /**
     * Lark 国际站授权基址。
     *
     * @return Lark 授权基址
     */
    String larkBaseUrl();
}
