package net.linlan.dsh.plugin.connectors.feishu;

import net.linlan.dsh.domain.spi.PluginContext;

/** 飞书连接器配置：优先读插件配置，缺失时回退环境变量，便于无 DB 的冒烟验证。 */
public record FeishuConfig(String appId, String appSecret, String tokenEndpoint) {

    /** 读取插件配置；缺失时回退 飞书 环境变量，便于无 DB 环境冒烟。 */
    public static FeishuConfig from(PluginContext context) {
        String appId = context.getConfig("feishu.appId")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_FEISHU_APP_ID")));
        String appSecret = context.getConfig("feishu.appSecret")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_FEISHU_APP_SECRET")));
        String endpoint = context.getConfig("feishu.tokenEndpoint")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_FEISHU_TOKEN_ENDPOINT")));
        return new FeishuConfig(appId, appSecret, endpoint);
    }

    /** 是否已配置飞书凭据。 */
    public boolean hasCredentials() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    private static String firstNonBlank(String candidate) {
        return candidate == null || candidate.isBlank() ? null : candidate;
    }
}
