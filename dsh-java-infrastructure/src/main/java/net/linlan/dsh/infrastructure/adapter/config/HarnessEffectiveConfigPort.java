package net.linlan.dsh.infrastructure.adapter.config;

import net.linlan.dsh.domain.runtime.setting.adapter.port.IHarnessEffectiveConfigPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * Projects the merged Spring environment as the effective harness configuration.
 * Sensitive values are masked before the map leaves infrastructure.
 */
@Component
public class HarnessEffectiveConfigPort implements IHarnessEffectiveConfigPort {

    private static final ResolvableType CONFIG_MAP_TYPE = ResolvableType.forClassWithGenerics(
            Map.class, String.class, Object.class
    );
    private static final java.util.regex.Pattern SENSITIVE_URL_CREDENTIAL = java.util.regex.Pattern.compile(
            "(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|session[_-]?token|password|secret|authorization)=[^&\\s]+"
    );

    private final ConfigurableEnvironment environment;

    public HarnessEffectiveConfigPort(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public Map<String, Object> effectiveConfig() {
        Object configValue = Binder.get(environment)
                .bind("harness", Bindable.of(CONFIG_MAP_TYPE))
                .orElseGet(LinkedHashMap::new);
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) configValue;
        return mask(config);
    }

    private Map<String, Object> mask(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(key, shouldMask(key) ? "[MASKED]" : maskValue(value)));
        return target;
    }

    private Object maskValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> target = new LinkedHashMap<>();
            map.forEach((key, item) -> target.put(String.valueOf(key), shouldMask(String.valueOf(key))
                    ? "[MASKED]" : maskValue(item)));
            return target;
        }
        if (value instanceof List<?> list) {
            List<Object> target = new ArrayList<>(list.size());
            list.forEach(item -> target.add(maskValue(item)));
            return target;
        }
        if (value instanceof String text) {
            return maskText(text);
        }
        return value;
    }

    private String maskText(String text) {
        return SENSITIVE_URL_CREDENTIAL.matcher(text).replaceAll("$1=[MASKED]");
    }

    private boolean shouldMask(String key) {
        String normalized = key.toLowerCase().replaceAll("[_-]", "");
        return normalized.contains("apikey")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.equals("token")
                || normalized.endsWith("accesstoken")
                || normalized.endsWith("refreshtoken")
                || normalized.endsWith("sessiontoken");
    }
}
