package net.linlan.dsh.domain.agent.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按通知来源累计系统消息章节。同一来源的同名章节后写覆盖，其他来源不受影响。
 */
public final class SystemMessageAccumulator {

    private final Map<String, LinkedHashMap<String, SystemMessageSection>> sectionsBySource =
            new LinkedHashMap<>();

    public synchronized void update(String source, SystemMessageSection section) {
        String sourceKey = requireSource(source);
        if (section == null) {
            return;
        }
        sectionsBySource.computeIfAbsent(sourceKey, ignored -> new LinkedHashMap<>())
                .put(section.name(), section);
    }

    public synchronized void update(String source, List<SystemMessageSection> sections) {
        if (sections == null) {
            return;
        }
        for (SystemMessageSection section : sections) {
            update(source, section);
        }
    }

    /** 用一份完整来源通知替换该来源的旧章节，不影响其他来源。 */
    public synchronized void replace(String source, List<SystemMessageSection> sections) {
        String sourceKey = requireSource(source);
        sectionsBySource.remove(sourceKey);
        update(sourceKey, sections);
    }

    public synchronized void removeSource(String source) {
        if (source != null) {
            sectionsBySource.remove(source);
        }
    }

    public synchronized void clear() {
        sectionsBySource.clear();
    }

    public synchronized List<SystemMessageSection> sections() {
        List<SystemMessageSection> merged = new ArrayList<>();
        for (Map<String, SystemMessageSection> sourceSections : sectionsBySource.values()) {
            merged.addAll(sourceSections.values());
        }
        return List.copyOf(merged);
    }

    public synchronized Map<String, List<SystemMessageSection>> sectionsBySource() {
        Map<String, List<SystemMessageSection>> snapshot = new LinkedHashMap<>();
        sectionsBySource.forEach((source, sections) ->
                snapshot.put(source, List.copyOf(sections.values())));
        return Map.copyOf(snapshot);
    }

    private static String requireSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("system message source required");
        }
        return source;
    }
}
