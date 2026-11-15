package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import net.linlan.dsh.api.IDeviceApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * DefaultWebAuthProvider 基于 API Key 与设备 Token 校验请求头或 Cookie。
 *
 * <p>调用流程：提取 Bearer/X-API-Key/Cookie → 设备 Token 或主密钥校验。</p>
 * <p>示例：{@code Cookie: dsh_session=<key>} 可认证页面与 API。</p>
 */
@Component
public class DefaultWebAuthProvider implements WebAuthProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    private final IDeviceApi deviceApi;
    private final String configuredKeys;
    private final String cookieName;

    public DefaultWebAuthProvider(IDeviceApi deviceApi,
                                  @Value("${harness.auth.api-keys:}") String configuredKeys,
                                  @Value("${harness.auth.cookie-name:dsh_session}") String cookieName) {
        this.deviceApi = deviceApi;
        this.configuredKeys = configuredKeys;
        this.cookieName = cookieName == null || cookieName.isBlank() ? "dsh_session" : cookieName;
    }

    @Override
    public boolean isConfigured() {
        return configuredKeys != null && !configuredKeys.isBlank();
    }

    @Override
    public Optional<WebPrincipal> authenticate(HttpServletRequest request) {
        String source = "header";
        String token = headerToken(request);
        if (token == null || token.isBlank()) {
            token = cookieToken(request);
            source = "cookie";
        }
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return accepts(token) ? Optional.of(new WebPrincipal(token, source)) : Optional.empty();
    }

    @Override
    public boolean presentsDeviceToken(HttpServletRequest request) {
        String token = headerToken(request);
        if (token == null || token.isBlank()) {
            token = cookieToken(request);
        }
        return token != null && token.trim().startsWith("devt_");
    }

    @Override
    public Optional<String> issueSessionToken(String credential) {
        if (credential == null || credential.isBlank() || !accepts(credential.trim())) {
            return Optional.empty();
        }
        return Optional.of(credential.trim());
    }

    private boolean accepts(String token) {
        String candidate = token.trim();
        if (candidate.startsWith("devt_")) {
            return Boolean.TRUE.equals(deviceApi.validateDeviceToken(candidate).data());
        }
        if (!isConfigured()) {
            return false;
        }
        for (String key : configuredKeys.split(",")) {
            if (candidate.equals(key.trim())) {
                return true;
            }
        }
        return false;
    }

    private String headerToken(HttpServletRequest request) {
        String token = request.getHeader("X-API-Key");
        if (token == null || token.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith(BEARER_PREFIX)) {
                token = auth.substring(BEARER_PREFIX.length());
            }
        }
        return token;
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
