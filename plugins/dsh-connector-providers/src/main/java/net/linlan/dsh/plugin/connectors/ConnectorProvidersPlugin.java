package net.linlan.dsh.plugin.connectors;

import net.linlan.dsh.domain.spi.AbstractHarnessPlugin;
import net.linlan.dsh.domain.spi.PluginContext;
import net.linlan.dsh.plugin.connectors.dingtalk.DingTalkAccountProvider;
import net.linlan.dsh.plugin.connectors.dingtalk.DingTalkConfig;
import net.linlan.dsh.plugin.connectors.feishu.FeishuAccountProvider;
import net.linlan.dsh.plugin.connectors.feishu.FeishuConfig;
import net.linlan.dsh.plugin.connectors.wecom.WeComAccountProvider;
import net.linlan.dsh.plugin.connectors.wecom.WeComConfig;

/**
 * 渠道连接器账号提供方插件。
 * <p>在 {@code configure} 中把各渠道的 {@code IConnectorAccountPort} 注册进宿主连接器目录；
 * 当前接入飞书 / 企业微信 / 钉钉（均为服务端取号，无浏览器 OAuth），后续可按同模板扩展 Notion / Linear。
 */
public class ConnectorProvidersPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "dsh-connector-providers";

    public ConnectorProvidersPlugin() {
        super(PLUGIN_ID);
    }

    @Override
    public void configure(PluginContext context) {
        FeishuConfig feishu = FeishuConfig.from(context);
        context.registerConnectorAccount(
                new FeishuAccountProvider(feishu.appId(), feishu.appSecret(), feishu.tokenEndpoint()));

        WeComConfig wecom = WeComConfig.from(context);
        context.registerConnectorAccount(
                new WeComAccountProvider(wecom.corpId(), wecom.corpSecret(), wecom.tokenEndpoint()));

        DingTalkConfig dingtalk = DingTalkConfig.from(context);
        context.registerConnectorAccount(
                new DingTalkAccountProvider(dingtalk.appKey(), dingtalk.appSecret(), dingtalk.tokenEndpoint()));

        log.info("[{}] 已注册连接器账号提供方：feishu/wecom/dingtalk（凭据已配置 feishu={}, wecom={}, dingtalk={}）",
                pluginId(), feishu.hasCredentials(), wecom.hasCredentials(), dingtalk.hasCredentials());
    }
}
