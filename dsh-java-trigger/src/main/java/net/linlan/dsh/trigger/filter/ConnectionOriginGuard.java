package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ConnectionOriginGuard 在连接监听器活动时校验匿名路由的来源。
 *
 * <p>调用流程：读取 Origin → 同源放行 → 白名单放行 → 其余拒绝。</p>
 * <p>示例：{@code Origin: https://evil.example} 在监听器活动时返回 403。</p>
 */
@Component
public class ConnectionOriginGuard {

    private final boolean active;
    private final Set<String> allowedOrigins;

    public ConnectionOriginGuard(@Value("${harness.auth.connection-listener.enabled:false}") boolean active,
                                 @Value("${harness.auth.allowed-origins:}") String allowedOrigins) {
        this.active = active;
        this.allowedOrigins = parse(allowedOrigins);
    }

    /**
     * 连接监听器是否活动。
     *
     * @return true 表示启用来源检查
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 判断匿名路由请求是否允许通过来源检查。
     *
     * @param request HTTP 请求
     * @return true 表示允许
     */
    public boolean allow(HttpServletRequest request) {
        if (!active) {
            return true;
        }
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (origin.equals(sameOrigin(request))) {
            return true;
        }
        return allowedOrigins.contains(origin);
    }

    private String sameOrigin(HttpServletRequest request) {
        String scheme = request.getScheme() == null ? "http" : request.getScheme();
        String host = request.getServerName() == null ? "" : request.getServerName();
        int port = request.getServerPort();
        StringBuilder builder = new StringBuilder(scheme).append("://").append(host);
        boolean defaultPort = "http".equals(scheme) && port == 80 || "https".equals(scheme) && port == 443;
        if (!defaultPort && port > 0) {
            builder.append(':').append(port);
        }
        return builder.toString();
    }

    private Set<String> parse(String configured) {
        Set<String> origins = new LinkedHashSet<>();
        if (configured == null || configured.isBlank()) {
            return origins;
        }
        for (String value : configured.split(",")) {
            String origin = value.trim();
            if (!origin.isEmpty()) {
                origins.add(origin);
            }
        }
        return origins;
    }
}
