package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key / Cookie 认证拦截器。
 *
 * <p>路由默认要求认证，只有显式 {@link RouteAuth.Level#ANONYMOUS} 的方法/控制器或公开静态资源
 * 才会跳过凭据校验；未配置主密钥时保留本地开发放行语义。</p>
 *
 * <p>调用流程：静态资源判断 → 认证级别解析 → 认证提供方校验 → 401/403。</p>
 * <p>示例：{@code Cookie: dsh_session=...} 可同时认证页面与 API。</p>
 */
@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthInterceptor.class);
    private static final String GATEWAY_STREAM_PATH = "/api/gateway/stream";

    private final WebAuthProvider authProvider;
    private final ConnectionOriginGuard originGuard;
    private final RouteAuth.Level defaultLevel;

    /**
     * 构造拦截器。
     *
     * @param authProvider 认证提供方
     * @param originGuard  连接监听器来源检查
     * @param authDefault  未声明路由的默认认证级别（required/anonymous）
     */
    public ApiKeyAuthInterceptor(WebAuthProvider authProvider,
                                 ConnectionOriginGuard originGuard,
                                 @Value("${harness.webserver.auth-default:required}") String authDefault) {
        this.authProvider = authProvider;
        this.originGuard = originGuard;
        this.defaultLevel = "anonymous".equalsIgnoreCase(authDefault)
                ? RouteAuth.Level.ANONYMOUS : RouteAuth.Level.REQUIRED;
    }

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

        // 静态资源与公开探测端点：显式公开，连接监听器活动时仍做来源检查
        if (RouteAuthResolver.isPublicResource(request)) {
            return allowAnonymous(request, response, gatewayStreamRequest, "public-resource");
        }

        // 路由未声明时默认 required；显式 anonymous 才允许匿名访问
        if (RouteAuthResolver.resolve(handler, defaultLevel) == RouteAuth.Level.ANONYMOUS) {
            return allowAnonymous(request, response, gatewayStreamRequest, "route-anonymous");
        }

        if (authProvider.authenticate(request).isPresent()) {
            if (gatewayStreamRequest) {
                log.info("[GatewayStream] HTTP 请求认证通过，method={}，path={}", request.getMethod(), path);
            }
            return true;
        }

        // 设备级 Token 即使未配置主密钥也必须校验，吊销后立即 401
        if (authProvider.presentsDeviceToken(request)) {
            reject(response, 401, "{\"error\":\"Unauthorized\",\"message\":\"Device credential revoked or invalid\"}");
            return false;
        }

        // 未配置主密钥时全部放行，用于本地开发模式
        if (!authProvider.isConfigured()) {
            if (gatewayStreamRequest) {
                log.info("[GatewayStream] HTTP 请求认证放行，本地未配置 API Key，method={}，path={}",
                        request.getMethod(), path);
            }
            return true;
        }

        if (gatewayStreamRequest) {
            log.warn("[GatewayStream] HTTP 请求认证失败，method={}，path={}，remoteAddr={}，hasCredential={}",
                    request.getMethod(), path, request.getRemoteAddr(),
                    hasText(request.getHeader("X-API-Key")) || hasText(request.getHeader("Authorization")));
        }
        reject(response, 401, "{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
        return false;
    }

    private boolean allowAnonymous(HttpServletRequest request, HttpServletResponse response,
                                   boolean gatewayStreamRequest, String routeType) throws Exception {
        if (!originGuard.allow(request)) {
            if (gatewayStreamRequest) {
                log.warn("[GatewayStream] 匿名路由来源被拒，method={}，path={}，origin={}",
                        request.getMethod(), request.getRequestURI(), request.getHeader("Origin"));
            }
            reject(response, 403, "{\"error\":\"Forbidden\",\"message\":\"Origin not allowed\"}");
            return false;
        }
        if (gatewayStreamRequest) {
            log.info("[GatewayStream] 匿名路由放行，routeType={}，method={}，path={}",
                    routeType, request.getMethod(), request.getRequestURI());
        }
        return true;
    }

    private void reject(HttpServletResponse response, int status, String body) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
