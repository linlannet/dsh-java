package net.linlan.dsh.trigger.filter;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
/**
 * WebMvcConfig 定义当前模块中的class契约与职责。
 * <p>调用流程：HTTP/Web 层 → API 适配器 → 应用门面。</p>
 * <p>示例：控制器只负责协议转换，不写业务规则。</p>
 */
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

    /**
     * 处理Web Mvc 配置。
     * 流程：转换协议 → 调用应用门面 → 返回结果。
     * @param apiKeyAuthInterceptor API 键 Auth Interceptor参数。
     * @return 方法执行结果。
     */
    public WebMvcConfig(ApiKeyAuthInterceptor apiKeyAuthInterceptor) {
        this.apiKeyAuthInterceptor = apiKeyAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor).addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600L);
    }
}
