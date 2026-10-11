package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 简单的 API Key 认证拦截器。
 * <p>
 * 当 harness.auth.api-keys 为空时，所有请求直接放行，用于本地开发。
 * 否则要求携带 X-API-Key 或 Bearer Token。
 *
 * <p>调用流程：HTTP/Web 层 → API 适配器 → 应用门面。</p>
 * <p>示例：控制器只负责协议转换，不写业务规则。</p>
 *
 */
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthInterceptor.class);
    private static final String GATEWAY_STREAM_PATH = "/api/gateway/stream";

    @Value("${harness.auth.api-keys:}")
    private String configuredKeys;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        boolean gatewayStreamRequest = GATEWAY_STREAM_PATH.equals(path);

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (gatewayStreamRequest) {
            log.info("[GatewayStream] HTTP 请求进入认证拦截器，method={}，path={}，remoteAddr={}，contentType={}，hasApiKey={}，hasAuthorization={}，userAgent={}",
                    request.getMethod(), path, request.getRemoteAddr(), request.getContentType(),
                    hasText(request.getHeader("X-API-Key")), hasText(request.getHeader("Authorization")),
                    request.getHeader("User-Agent"));
        }

        // 放行公开静态资源
        if (path.equals("/") || path.equals("/index.html") || path.equals("/app.js") || path.equals("/app.css")
                || path.startsWith("/lib/") || path.equals("/favicon.ico") || path.startsWith("/actuator")
                || path.startsWith("/.well-known/")) {
            return true;
        }

        // 未配置密钥时全部放行，用于本地开发模式
        if (configuredKeys == null || configuredKeys.isBlank()) {
            if (gatewayStreamRequest) {
                log.info("[GatewayStream] HTTP 请求认证放行，本地未配置 API Key，method={}，path={}",
                        request.getMethod(), path);
            }
            return true;
        }

        // 校验 X-API-Key 或 Authorization 请求头
        String provided = request.getHeader("X-API-Key");
        if (provided == null || provided.isBlank()) {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                provided = auth.substring(7);
            }
        }

        if (provided != null) {
            for (String key : configuredKeys.split(",")) {
                if (provided.trim().equals(key.trim())) {
                    if (gatewayStreamRequest) {
                        log.info("[GatewayStream] HTTP 请求认证通过，method={}，path={}",
                                request.getMethod(), path);
                    }
                    return true;
                }
            }
        }

        if (gatewayStreamRequest) {
            log.warn("[GatewayStream] HTTP 请求认证失败，method={}，path={}，remoteAddr={}，hasCredential={}",
                    request.getMethod(), path, request.getRemoteAddr(), hasText(provided));
        }
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
