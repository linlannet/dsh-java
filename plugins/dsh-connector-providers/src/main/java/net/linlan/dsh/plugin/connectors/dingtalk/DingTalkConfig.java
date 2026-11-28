package net.linlan.dsh.plugin.connectors.dingtalk;

import net.linlan.dsh.domain.spi.PluginContext;

/** 钉钉连接器配置：优先读插件配置，缺失时回退环境变量，便于无 DB 的冒烟验证。 */
public record DingTalkConfig(String appKey, String appSecret, String tokenEndpoint) {

    /** 读取插件配置；缺失时回退 钉钉 环境变量，便于无 DB 环境冒烟。 */
    public static DingTalkConfig from(PluginContext context) {
        String appKey = context.getConfig("dingtalk.appKey")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_DINGTALK_APP_KEY")));
        String appSecret = context.getConfig("dingtalk.appSecret")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_DINGTALK_APP_SECRET")));
        String endpoint = context.getConfig("dingtalk.tokenEndpoint")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_DINGTALK_TOKEN_ENDPOINT")));
        return new DingTalkConfig(appKey, appSecret, endpoint);
    }

    /** 是否已配置钉钉凭据。 */
    public boolean hasCredentials() {
        return appKey != null && !appKey.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    private static String firstNonBlank(String candidate) {
        return candidate == null || candidate.isBlank() ? null : candidate;
    }
}
