package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * RouteAuthResolver 解析路由的认证级别。
 *
 * <p>调用流程：读取方法注解 → 回退类注解 → 默认 REQUIRED。</p>
 * <p>示例：未声明注解的控制器方法默认必须认证。</p>
 */
public final class RouteAuthResolver {

    private RouteAuthResolver() {
    }

    /**
     * 解析处理器的认证级别。
     *
     * @param handler Spring MVC 处理器
     * @return 认证级别
     */
    public static RouteAuth.Level resolve(Object handler) {
        return resolve(handler, RouteAuth.Level.REQUIRED);
    }

    /**
     * 解析处理器的认证级别，未声明时使用给定默认级别。
     *
     * @param handler       Spring MVC 处理器
     * @param defaultLevel  默认认证级别
     * @return 认证级别
     */
    public static RouteAuth.Level resolve(Object handler, RouteAuth.Level defaultLevel) {
        if (handler instanceof HandlerMethod handlerMethod) {
            RouteAuth methodLevel = handlerMethod.getMethodAnnotation(RouteAuth.class);
            if (methodLevel != null) {
                return methodLevel.value();
            }
            RouteAuth classLevel = handlerMethod.getBeanType().getAnnotation(RouteAuth.class);
            if (classLevel != null) {
                return classLevel.value();
            }
        }
        return defaultLevel;
    }

    /**
     * 判断请求路径是否为公开静态资源。
     *
     * @param request HTTP 请求
     * @return true 表示公开
     */
    public static boolean isPublicResource(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/") || path.equals("/index.html") || path.equals("/app.js") || path.equals("/app.css")
                || path.startsWith("/lib/") || path.equals("/favicon.ico") || path.startsWith("/actuator")
                || path.startsWith("/.well-known/");
    }
}
