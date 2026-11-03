package net.linlan.dsh.api.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 飞书接入启停条件：仅当 {@code dsh.feishu.enabled} 为 true（缺省亦为 true）时装配飞书相关 Bean。
 */
public class FeishuEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String value = context.getEnvironment().getProperty("dsh.feishu.enabled", "true");
        return "true".equalsIgnoreCase(value == null ? "true" : value.trim());
    }
}
