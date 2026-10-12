package net.linlan.dsh.domain.llm.extension.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * LlmRequestExtensionRegistry 管理 LLM 请求的扩展字段注册与解析。
 * <p>插件可注册额外请求字段（如 {@code thinking_budget}、{@code response_format}），
 * 解析时合并所有已注册字段，不影响已有字段。
 */
public final class LlmRequestExtensionRegistry {

    private final ConcurrentHashMap<String, Function<Map<String, Object>, Object>> providers = new ConcurrentHashMap<>();

    /**
     * 注册一个扩展字段提供者。
     *
     * @param field    扩展字段名
     * @param provider 字段值提供者，接收当前请求上下文返回字段值
     */
    public void register(String field, Function<Map<String, Object>, Object> provider) {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("field must not be null or empty");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        providers.put(field, provider);
    }

    /**
     * 注销一个扩展字段。
     *
     * @param field 扩展字段名
     */
    public void unregister(String field) {
        if (field != null) {
            providers.remove(field);
        }
    }

    /**
     * 解析所有已注册扩展字段。
     *
     * @param requestContext 当前请求上下文
     * @return 扩展字段 Map（可能为空，不含 null 值）
     */
    public Map<String, Object> resolve(Map<String, Object> requestContext) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        Map<String, Object> ctx = requestContext == null ? Map.of() : requestContext;
        for (var entry : providers.entrySet()) {
            try {
                Object value = entry.getValue().apply(ctx);
                if (value != null) {
                    result.put(entry.getKey(), value);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }

    /**
     * 返回已注册的扩展字段数量。
     */
    public int size() {
        return providers.size();
    }

    /**
     * 判断指定字段是否已注册。
     */
    public boolean isRegistered(String field) {
        return field != null && providers.containsKey(field);
    }
}
