package net.linlan.dsh.domain.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** RuntimeContextSnapshot 使用共用章节模型渲染运行时上下文提醒。 */
public final class RuntimeContextSnapshot {

    public static final String FIRST_PREFIX = "Current runtime context (initial snapshot).";
    public static final String SUBSEQUENT_PREFIX =
            "Current runtime context. This snapshot supersedes earlier runtime-context snapshots.";

    private final Map<String, SnapshotState> states = new ConcurrentHashMap<>();

    /** 标记缺少 sections 且需保留原前缀的旧快照。 */
    public void seedLegacy(String key, String prefix) {
        states.put(stateKey(key), new SnapshotState(prefix == null || prefix.isBlank() ? SUBSEQUENT_PREFIX
                : prefix, null));
    }

    public int refreshCount(String key) {
        SnapshotState state = states.get(stateKey(key));
        return state == null ? 0 : state.refreshCount();
    }

    /** 保留既有字符串章节入口。 */
    public Optional<String> render(String key, List<String> sections) {
        List<SystemMessageSection> typedSections = new ArrayList<>();
        if (sections != null) {
            for (int index = 0; index < sections.size(); index++) {
                String text = sections.get(index);
                if (text != null) {
                    typedSections.add(new SystemMessageSection("section-" + index, text));
                }
            }
        }
        return renderSections(key, typedSections);
    }

    /** 直接消费累计器合并后的来源章节。 */
    public Optional<String> renderAccumulator(String key, SystemMessageAccumulator accumulator) {
        return renderSections(key, accumulator == null ? List.of() : accumulator.sections());
    }

    public Optional<String> renderSections(String key, List<SystemMessageSection> sections) {
        List<SystemMessageSection> effective = sections == null ? List.of() : sections.stream()
                .filter(section -> section != null && !section.text().isBlank())
                .toList();
        String stateKey = stateKey(key);
        List<String> renderedSections = effective.stream().map(SystemMessageSection::text).toList();

        if (effective.isEmpty()) {
            if (states.remove(stateKey) != null) {
                return Optional.of(wrap("form=snapshot\nsections=[]"));
            }
            return Optional.empty();
        }

        SnapshotState state = states.get(stateKey);
        if (state == null) {
            states.put(stateKey, new SnapshotState(SUBSEQUENT_PREFIX, effective));
            return Optional.of(wrap(FIRST_PREFIX + "\n\n" + String.join("\n\n", renderedSections)));
        }
        if (state.sections() == null) {
            state.markRefreshed(effective);
        } else {
            state.replace(effective);
        }
        return Optional.of(wrap(state.prefix() + "\n\n" + String.join("\n\n", renderedSections)));
    }

    public List<SystemMessageSection> sections(String key) {
        SnapshotState state = states.get(stateKey(key));
        return state == null || state.sections() == null ? List.of() : state.sections();
    }

    private static String stateKey(String key) {
        return key == null || key.isBlank() ? "default" : key;
    }

    private static String wrap(String body) {
        return "<system-reminder>\n" + body + "\n</system-reminder>";
    }

    private static final class SnapshotState {
        private final String prefix;
        private List<SystemMessageSection> sections;
        private int refreshCount;

        SnapshotState(String prefix, List<SystemMessageSection> sections) {
            this.prefix = prefix;
            this.sections = sections == null ? null : List.copyOf(sections);
        }

        String prefix() {
            return prefix;
        }

        List<SystemMessageSection> sections() {
            return sections;
        }

        int refreshCount() {
            return refreshCount;
        }

        void markRefreshed(List<SystemMessageSection> refreshed) {
            this.sections = List.copyOf(refreshed);
            this.refreshCount++;
        }

        void replace(List<SystemMessageSection> refreshed) {
            this.sections = List.copyOf(refreshed);
        }
    }
}
