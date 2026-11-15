package net.linlan.dsh.trigger.filter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RouteAuth 声明路由的认证要求。
 *
 * <p>调用流程：路由注册 → 解析注解 → 认证拦截器判定。</p>
 * <p>示例：{@code @RouteAuth(RouteAuth.Level.ANONYMOUS)} 表示公开路由。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RouteAuth {

    /**
     * 认证级别。
     */
    enum Level {
        /** 必须认证（默认）。 */
        REQUIRED,
        /** 公开访问。 */
        ANONYMOUS
    }

    /**
     * 认证级别，默认 required。
     *
     * @return 认证级别
     */
    Level value() default Level.REQUIRED;
}
