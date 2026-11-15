package net.linlan.dsh.trigger.filter;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ServletTestSupport 提供鉴权测试用的请求/响应动态代理。
 */
final class ServletTestSupport {

    private ServletTestSupport() {
    }

    static HttpServletRequest request(String method, String uri, Map<String, String> headers, Cookie[] cookies) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                ServletTestSupport.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getHeader" -> headers.get((String) args[0]);
                    case "getCookies" -> cookies;
                    case "getScheme" -> "http";
                    case "getServerName" -> "localhost";
                    case "getServerPort" -> 8080;
                    case "getRemoteAddr" -> "127.0.0.1";
                    case "toString" -> "request " + method + " " + uri;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    static CapturedResponse response() {
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body, true);
        AtomicInteger status = new AtomicInteger(200);
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                ServletTestSupport.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "setStatus" -> {
                        status.set((Integer) args[0]);
                        yield null;
                    }
                    case "getStatus" -> status.get();
                    case "getWriter" -> writer;
                    case "toString" -> "response";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        return new CapturedResponse(response, status, body);
    }

    record CapturedResponse(HttpServletResponse raw, AtomicInteger statusRef, StringWriter bodyRef) {
        int status() {
            return statusRef.get();
        }

        String body() {
            return bodyRef.toString();
        }
    }
}
