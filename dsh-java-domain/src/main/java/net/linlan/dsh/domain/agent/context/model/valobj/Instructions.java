package net.linlan.dsh.domain.agent.context.model.valobj;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Instructions 表示从 AGENTS.md 文件解析出的指令集合。
 *
 * @param path      文件路径
 * @param content   原始文本内容
 * @param directives 指令键值对（子级覆盖父级同键）
 */
public record Instructions(
        String path,
        String content,
        Map<String, String> directives
) {
    public Instructions {
        Objects.requireNonNull(path, "path");
        content = content == null ? "" : content;
        directives = directives == null ? Map.of() : Map.copyOf(directives);
    }

    public static Instructions empty(String path) {
        return new Instructions(path, "", Map.of());
    }

    /**
     * 合并两个指令集，other 覆盖 this 同键指令。
     */
    public Instructions merge(Instructions other) {
        if (other == null) {
            return this;
        }
        Map<String, String> merged = new LinkedHashMap<>(this.directives);
        merged.putAll(other.directives);
        return new Instructions(
                other.path,
                this.content + "\n" + other.content,
                merged
        );
    }
}
