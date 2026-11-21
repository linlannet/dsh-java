package net.linlan.dsh.trigger.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 企业身份（linlan-emis OIDC）配置。secret 仅环境变量注入，不落日志。
 */
@Component
public class EmisOidcProperties {

    private final boolean enabled;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String scopes;
    private final String issuer;
    private final String postLogoutRedirectUri;
    private final int timeoutMs;

    public EmisOidcProperties(
            @Value("${dsh.emis.oidc.enabled:false}") boolean enabled,
            @Value("${dsh.emis.oidc.base-url:http://localhost:8080}") String baseUrl,
            @Value("${dsh.emis.oidc.client-id:}") String clientId,
            @Value("${dsh.emis.oidc.client-secret:}") String clientSecret,
            @Value("${dsh.emis.oidc.redirect-uri:http://localhost:8090/api/emis/callback}") String redirectUri,
            @Value("${dsh.emis.oidc.scopes:openid profile tenant}") String scopes,
            @Value("${dsh.emis.oidc.issuer:http://localhost:8080}") String issuer,
            @Value("${dsh.emis.oidc.post-logout-redirect-uri:http://localhost:8090/}") String postLogoutRedirectUri,
            @Value("${dsh.emis.oidc.timeout-ms:3000}") int timeoutMs) {
        this.enabled = enabled;
        this.baseUrl = strip(baseUrl);
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.redirectUri = redirectUri;
        this.scopes = scopes == null ? "" : scopes;
        this.issuer = issuer == null ? "" : strip(issuer);
        this.postLogoutRedirectUri = postLogoutRedirectUri;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000;
    }

    public boolean isEnabled() { return enabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public String getScopes() { return scopes; }
    public String getIssuer() { return issuer; }
    public String getPostLogoutRedirectUri() { return postLogoutRedirectUri; }
    public int getTimeoutMs() { return timeoutMs; }

    /** 启用所需的最小配置是否齐全。 */
    public boolean isConfigured() {
        return enabled && !clientId.isBlank() && !clientSecret.isBlank()
                && !redirectUri.isBlank() && !baseUrl.isBlank();
    }

    private static String strip(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }
}
