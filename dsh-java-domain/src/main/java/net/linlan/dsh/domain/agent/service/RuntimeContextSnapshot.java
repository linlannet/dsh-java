package net.linlan.dsh.domain.agent.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RuntimeContextSnapshot 渲染运行时上下文提醒，区分首份与后续快照。
 *
 * <p>调用流程：读取快照状态 → 生成 system-reminder → 更新状态（清空/刷新）。</p>
 * <p>示例：首份输出 initial snapshot，后续输出 supersedes 文案；清空输出 {@code sections=[]}。</p>
 */
public final class RuntimeContextSnapshot {

    public static final String FIRST_PREFIX = "Current runtime context (initial snapshot).";
    public static final String SUBSEQUENT_PREFIX =
            "Current runtime context. This snapshot supersedes earlier runtime-context snapshots.";

    private final Map<String, SnapshotState> states = new ConcurrentHashMap<>();

    /**
     * 标记一个旧快照：缺少 sections 且保留原前缀。
     *
     * @param key    快照键
     * @param prefix 旧前缀
     */
    public void seedLegacy(String key, String prefix) {
        states.put(key, new SnapshotState(prefix == null || prefix.isBlank() ? SUBSEQUENT_PREFIX
                : prefix, null));
    }

    /**
     * 返回刷新次数，用于验证旧快照仅刷新一次。
     *
     * @param key 快照键
     * @return 刷新次数
     */
    public int refreshCount(String key) {
        SnapshotState state = states.get(key);
        return state == null ? 0 : state.refreshCount();
    }

    /**
     * 渲染运行时上下文提醒。
     *
     * @param key      快照键（通常为 agentId）
     * @param sections 当前上下文片段
     * @return 提醒文本；无需提醒时为空
     */
    public Optional<String> render(String key, List<String> sections) {
        List<String> effective = sections == null ? List.of() : sections.stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
        String stateKey = key == null || key.isBlank() ? "default" : key;

        if (effective.isEmpty()) {
            if (states.remove(stateKey) != null) {
                return Optional.of(wrap("form=snapshot\nsections=[]"));
            }
            return Optional.empty();
        }

        SnapshotState state = states.get(stateKey);
        if (state == null) {
            states.put(stateKey, new SnapshotState(SUBSEQUENT_PREFIX, List.copyOf(effective)));
            return Optional.of(wrap(FIRST_PREFIX + "\n\n" + String.join("\n\n", effective)));
        }
        if (state.sections() == null) {
            state.markRefreshed(effective);
        }
        return Optional.of(wrap(state.prefix() + "\n\n" + String.join("\n\n", effective)));
    }

    private static String wrap(String body) {
        return "<system-reminder>\n" + body + "\n</system-reminder>";
    }

    private static final class SnapshotState {
        private final String prefix;
        private List<String> sections;
        private int refreshCount;

        SnapshotState(String prefix, List<String> sections) {
            this.prefix = prefix;
            this.sections = sections;
        }

        String prefix() {
            return prefix;
        }

        List<String> sections() {
            return sections;
        }

        int refreshCount() {
            return refreshCount;
        }

        void markRefreshed(List<String> refreshed) {
            this.sections = List.copyOf(refreshed);
            this.refreshCount++;
        }
    }
}
