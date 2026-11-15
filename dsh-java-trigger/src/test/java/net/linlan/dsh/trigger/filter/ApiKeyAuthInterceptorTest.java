package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import net.linlan.dsh.api.IDeviceApi;
import net.linlan.dsh.api.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyAuthInterceptorTest {

    @Test
    void undeclaredRouteIsRejectedWithoutCredential() throws Exception {
        ApiKeyAuthInterceptor interceptor = interceptor("key-1", false, "required");
        ServletTestSupport.CapturedResponse response = ServletTestSupport.response();

        boolean allowed = interceptor.preHandle(
                request("GET", "/api/agent/message", Map.of(), null),
                response.raw(), handler(new MixedController(), "required"));

        assertFalse(allowed);
        assertEquals(401, response.status());
    }

    @Test
    void explicitlyAnonymousRouteIsAllowed() throws Exception {
        ApiKeyAuthInterceptor interceptor = interceptor("key-1", false, "required");
        ServletTestSupport.CapturedResponse response = ServletTestSupport.response();

        boolean allowed = interceptor.preHandle(
                request("GET", "/api/public", Map.of(), null),
                response.raw(), handler(new MixedController(), "anonymous"));

        assertTrue(allowed);
        assertEquals(200, response.status());
    }

    @Test
    void cookieAuthenticatesApiRoute() throws Exception {
        ApiKeyAuthInterceptor interceptor = interceptor("key-1", false, "required");
        ServletTestSupport.CapturedResponse response = ServletTestSupport.response();

        boolean allowed = interceptor.preHandle(
                request("GET", "/api/agent/message", Map.of(),
                        new Cookie[]{new Cookie("dsh_session", "key-1")}),
                response.raw(), handler(new MixedController(), "required"));

        assertTrue(allowed);
    }

    @Test
    void optionsRequestIsAlwaysAllowed() throws Exception {
        ApiKeyAuthInterceptor interceptor = interceptor("key-1", false, "required");

        boolean allowed = interceptor.preHandle(
                request("OPTIONS", "/api/agent/message", Map.of(), null),
                ServletTestSupport.response().raw(), handler(new MixedController(), "required"));

        assertTrue(allowed);
    }

    @Test
    void localDevelopmentWithoutKeysIsAllowed() throws Exception {
        ApiKeyAuthInterceptor interceptor = interceptor("", false, "required");

        boolean allowed = interceptor.preHandle(
                request("GET", "/api/agent/message", Map.of(), null),
                ServletTestSupport.response().raw(), handler(new MixedController(), "required"));

        assertTrue(allowed);
    }

    @Test
    void revokedDeviceTokenIsRejectedEvenWithoutKeys() throws Exception {
        ApiKeyAuthInterceptor interceptor = interceptor("", false, "required");
        ServletTestSupport.CapturedResponse response = ServletTestSupport.response();

        boolean allowed = interceptor.preHandle(
                request("GET", "/api/agent/message", Map.of("X-API-Key", "devt_abc"), null),
                response.raw(), handler(new MixedController(), "required"));

        assertFalse(allowed);
        assertEquals(401, response.status());
    }

    @Test
    void anonymousRouteRejectsCrossOriginWhenListenerActive() throws Exception {
        ConnectionOriginGuard guard = new ConnectionOriginGuard(true, "");
        ApiKeyAuthInterceptor interceptor = new ApiKeyAuthInterceptor(
                new DefaultWebAuthProvider(deviceApi(false), "key-1", "dsh_session"), guard, "required");
        ServletTestSupport.CapturedResponse response = ServletTestSupport.response();

        boolean allowed = interceptor.preHandle(
                request("POST", "/api/gateway/stream", Map.of("Origin", "https://evil.example"), null),
                response.raw(), handler(new MixedController(), "anonymous"));

        assertFalse(allowed);
        assertEquals(403, response.status());
    }

    @Test
    void rollbackAuthDefaultAllowsUndeclaredRoute() throws Exception {
        ApiKeyAuthInterceptor interceptor = interceptor("key-1", false, "anonymous");

        boolean allowed = interceptor.preHandle(
                request("GET", "/api/agent/message", Map.of(), null),
                ServletTestSupport.response().raw(), handler(new MixedController(), "required"));

        assertTrue(allowed);
    }

    private ApiKeyAuthInterceptor interceptor(String keys, boolean listenerActive, String authDefault) {
        return new ApiKeyAuthInterceptor(
                new DefaultWebAuthProvider(deviceApi(false), keys, "dsh_session"),
                new ConnectionOriginGuard(listenerActive, ""), authDefault);
    }

    private HandlerMethod handler(Object bean, String methodName) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
    }

    private HttpServletRequest request(String method, String uri, Map<String, String> headers, Cookie[] cookies) {
        return ServletTestSupport.request(method, uri, headers, cookies);
    }

    private IDeviceApi deviceApi(boolean valid) {
        return (IDeviceApi) Proxy.newProxyInstance(
                ApiKeyAuthInterceptorTest.class.getClassLoader(),
                new Class<?>[]{IDeviceApi.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "validateDeviceToken" -> Response.success(valid);
                    case "toString" -> "device-api";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static class MixedController {
        @RouteAuth(RouteAuth.Level.ANONYMOUS)
        public void anonymous() {
        }

        public void required() {
        }
    }
}
