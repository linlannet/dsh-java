package net.linlan.dsh.plugin.connectors.wecom;

import net.linlan.dsh.domain.spi.PluginContext;

/** 企业微信连接器配置：优先读插件配置，缺失时回退环境变量，便于无 DB 的冒烟验证。 */
public record WeComConfig(String corpId, String corpSecret, String tokenEndpoint) {

    /** 读取插件配置；缺失时回退 企业微信 环境变量，便于无 DB 环境冒烟。 */
    public static WeComConfig from(PluginContext context) {
        String corpId = context.getConfig("wecom.corpId")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_WECOM_CORP_ID")));
        String corpSecret = context.getConfig("wecom.corpSecret")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_WECOM_CORP_SECRET")));
        String endpoint = context.getConfig("wecom.tokenEndpoint")
                .orElseGet(() -> firstNonBlank(System.getenv("DSH_WECOM_TOKEN_ENDPOINT")));
        return new WeComConfig(corpId, corpSecret, endpoint);
    }

    /** 是否已配置企业微信凭据。 */
    public boolean hasCredentials() {
        return corpId != null && !corpId.isBlank() && corpSecret != null && !corpSecret.isBlank();
    }

    private static String firstNonBlank(String candidate) {
        return candidate == null || candidate.isBlank() ? null : candidate;
    }
}
