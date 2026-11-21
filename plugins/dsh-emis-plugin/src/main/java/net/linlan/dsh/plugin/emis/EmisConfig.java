package net.linlan.dsh.plugin.emis;

import net.linlan.dsh.domain.spi.PluginContext;

/** 插件配置（全部来自宿主 preset/plugin.yaml，非敏感）。 */
public record EmisConfig(String baseUrl, String issuer, String jwksPath, int timeoutMs, long cacheMs) {

    public static EmisConfig from(PluginContext context) {
        String baseUrl = stripTrailing(context.getConfig("emis.base-url", "http://localhost:8080"));
        String issuer = context.getConfig("emis.issuer", baseUrl);
        String jwksPath = context.getConfig("emis.jwks-path", "/oauth2/jwks");
        int timeoutMs = parsePositive(context.getConfig("emis.timeout-ms", "3000"), 3000);
        long cacheMs = parsePositive(context.getConfig("emis.jwks-cache-ms", "600000"), 600_000L);
        return new EmisConfig(baseUrl, issuer, jwksPath, timeoutMs, cacheMs);
    }

    private static String stripTrailing(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static int parsePositive(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static long parsePositive(String value, long fallback) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }
}
