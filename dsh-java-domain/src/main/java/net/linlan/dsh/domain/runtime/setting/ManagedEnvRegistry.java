package net.linlan.dsh.domain.runtime.setting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ManagedEnvRegistry DSH_ 前缀受管环境变量注册表。
 *
 * <p>调用流程：校验前缀 → 注册/注销 → 注入子进程。</p>
 * <p>示例：register("DSH_API_KEY", "xxx") 后 inject(builder) 将变量加入子进程环境。</p>
 *
 */
public final class ManagedEnvRegistry {

    private static final String PREFIX = "DSH_";

    private final Map<String, String> entries = new ConcurrentHashMap<>();

    /**
     * 注册受管环境变量。
     * @param key   键（必须以 DSH_ 开头）。
     * @param value 值。
     */
    public void register(String key, String value) {
        validate(key);
        entries.put(key, value);
    }

    /**
     * 注销受管环境变量。
     * @param key 键。
     */
    public void unregister(String key) {
        entries.remove(key);
    }

    /**
     * 列出所有受管环境变量。
     * @return 不可变 Map。
     */
    public Map<String, String> list() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /**
     * 校验键是否合法。
     * @param key 键。
     * @return true 表示合法。
     */
    public boolean validate(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                    "managed env key must start with " + PREFIX + ": " + key);
        }
        return true;
    }

    /**
     * 将受管环境变量注入到 ProcessBuilder。
     * @param builder ProcessBuilder。
     */
    public void inject(ProcessBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder must not be null");
        }
        Map<String, String> env = builder.environment();
        env.putAll(entries);
    }

    /**
     * 判断键是否已注册。
     * @param key 键。
     * @return true 表示已注册。
     */
    public boolean contains(String key) {
        return entries.containsKey(key);
    }

    /**
     * 获取指定键的值。
     * @param key 键。
     * @return 值，不存在返回 null。
     */
    public String get(String key) {
        return entries.get(key);
    }

    /**
     * 清空所有注册。
     */
    public void clear() {
        entries.clear();
    }

    /**
     * 返回注册数量。
     * @return 数量。
     */
    public int size() {
        return entries.size();
    }
}
