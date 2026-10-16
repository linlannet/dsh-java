package net.linlan.dsh.infrastructure.adapter.guard;

import net.linlan.dsh.domain.guard.timeout.PerToolDeadlinePolicy;
import net.linlan.dsh.domain.guard.timeout.PerToolDeadlinePolicyLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Properties;

/**
 * PerToolDeadlinePolicyConfig 从 Spring 配置属性构建 PerToolDeadlinePolicy Bean。
 *
 * <p>配置格式（application.yml）：</p>
 * <pre>
 * dsh:
 *   guard:
 *     deadline:
 *       default: 30s
 *       write_file: 10s
 *       read_file: 5s
 *       bash: 60s
 * </pre>
 */
@Configuration
public class PerToolDeadlinePolicyConfig {

    @Bean
    public PerToolDeadlinePolicy perToolDeadlinePolicy(
            @Value("${dsh.guard.deadline.default:30s}") String defaultDeadline,
            @Value("#{${dsh.guard.deadline.tools:{}}}") Map<String, String> toolDeadlines) {

        Properties props = new Properties();
        props.setProperty("default", defaultDeadline);
        if (toolDeadlines != null) {
            toolDeadlines.forEach(props::setProperty);
        }
        return PerToolDeadlinePolicyLoader.fromProperties(props);
    }
}
