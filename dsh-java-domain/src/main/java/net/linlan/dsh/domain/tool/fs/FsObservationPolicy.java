package net.linlan.dsh.domain.tool.fs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * FsObservationPolicy 定义文件系统观察策略，文件变更按策略观察并通知。
 *
 * <p>调用流程：应用领域规则 → 更新领域对象 → 返回结果。</p>
 * <p>示例：注册路径与 glob pattern 后，notify 触发对应回调。</p>
 *
 */
public final class FsObservationPolicy {

    private static final String DEFAULT_PATTERN = "*";

    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

    /**
     * Registration 记录单条观察注册信息。
     */
    private record Registration(String pattern, Consumer<String> callback) {}

    /**
     * 注册文件变更观察。
     * @param path     观察路径。
     * @param pattern  glob pattern（null 视为 *）。
     * @param callback 变更回调。
     */
    public void register(String path, String pattern, Consumer<String> callback) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        registrations.put(path, new Registration(pattern == null ? DEFAULT_PATTERN : pattern, callback));
    }

    /**
     * 取消注册。
     * @param path 观察路径。
     */
    public void unregister(String path) {
        registrations.remove(path);
    }

    /**
     * 通知指定路径发生变更。
     * @param path 变更路径。
     * @param kind 变更类型。
     */
    public void notify(String path, String kind) {
        if (path == null) {
            return;
        }
        Registration reg = registrations.get(path);
        if (reg == null) {
            return;
        }
        String event = kind == null ? "change" : kind;
        if (matches(reg.pattern(), path)) {
            reg.callback().accept(event);
        }
    }

    /**
     * 判断当前路径是否已注册。
     * @param path 路径。
     * @return true 表示已注册。
     */
    public boolean isRegistered(String path) {
        return registrations.containsKey(path);
    }

    /**
     * 返回当前注册数量。
     * @return 注册数量。
     */
    public int registrationCount() {
        return registrations.size();
    }

    private static boolean matches(String pattern, String path) {
        if (pattern == null || "*".equals(pattern)) {
            return true;
        }
        String fileName = lastSegment(path);
        return globMatch(pattern, fileName);
    }

    private static String lastSegment(String path) {
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static boolean globMatch(String pattern, String text) {
        int p = 0, t = 0, starIdx = -1, match = 0;
        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == text.charAt(t))) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                starIdx = p;
                match = t;
                p++;
            } else if (starIdx != -1) {
                p = starIdx + 1;
                match++;
                t = match;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }
}
