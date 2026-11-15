package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAuthResolverTest {

    @Test
    void methodAnnotationOverridesClassAnnotation() throws Exception {
        HandlerMethod handler = handler(new MixedController(), "anonymous");
        assertEquals(RouteAuth.Level.ANONYMOUS, RouteAuthResolver.resolve(handler));
    }

    @Test
    void classAnnotationAppliesToUndeclaredMethod() throws Exception {
        HandlerMethod handler = handler(new AnonymousController(), "handle");
        assertEquals(RouteAuth.Level.ANONYMOUS, RouteAuthResolver.resolve(handler));
    }

    @Test
    void undeclaredRouteDefaultsToRequired() throws Exception {
        HandlerMethod handler = handler(new MixedController(), "required");
        assertEquals(RouteAuth.Level.REQUIRED, RouteAuthResolver.resolve(handler));
    }

    @Test
    void rollbackDefaultCanMakeUndeclaredRouteAnonymous() throws Exception {
        HandlerMethod handler = handler(new MixedController(), "required");
        assertEquals(RouteAuth.Level.ANONYMOUS,
                RouteAuthResolver.resolve(handler, RouteAuth.Level.ANONYMOUS));
    }

    @Test
    void staticResourcesAndWellKnownArePublic() {
        assertTrue(RouteAuthResolver.isPublicResource(request("/")));
        assertTrue(RouteAuthResolver.isPublicResource(request("/index.html")));
        assertTrue(RouteAuthResolver.isPublicResource(request("/.well-known/agent-card.json")));
        assertFalse(RouteAuthResolver.isPublicResource(request("/api/agent/message")));
    }

    private HandlerMethod handler(Object bean, String methodName) throws Exception {
        Method method = bean.getClass().getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    private HttpServletRequest request(String uri) {
        return ServletTestSupport.request("GET", uri, Map.of(), null);
    }

    static class MixedController {
        @RouteAuth(RouteAuth.Level.ANONYMOUS)
        public void anonymous() {
        }

        public void required() {
        }
    }

    @RouteAuth(RouteAuth.Level.ANONYMOUS)
    static class AnonymousController {
        public void handle() {
        }
    }
}
